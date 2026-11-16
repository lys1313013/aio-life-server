#!/usr/bin/env python3
"""停写后为V20261116_11准备真实字节及雪花ID；不删除旧存储文件，不输出连接信息。"""
import argparse
import hashlib
import io
import json
import os
import time
from urllib.parse import urlsplit
from pathlib import Path
from botocore.exceptions import ClientError

import boto3
import psycopg
from PIL import Image, UnidentifiedImageError

EPOCH = 1288834974657
_last_ms = 0
_sequence = 0


def snowflake():
    global _last_ms, _sequence
    now = int(time.time() * 1000)
    if now < _last_ms:
        raise RuntimeError('系统时钟回退，停止准备')
    if now == _last_ms:
        _sequence += 1
        if _sequence >= 4096:
            time.sleep(.002)
            return snowflake()
    else:
        _sequence = 0
    _last_ms = now
    # 停写升级独占worker=1023，不能与应用并行运行。
    return ((now - EPOCH) << 22) | (1023 << 12) | _sequence


def inspect(data):
    if not data or len(data) > 10 * 1024 * 1024:
        raise RuntimeError('文件不满足1字节至10MB限制，请先核对历史数据')
    types = {'JPEG': ('jpg', 'image/jpeg'), 'PNG': ('png', 'image/png'),
             'GIF': ('gif', 'image/gif'), 'BMP': ('bmp', 'image/bmp'), 'WEBP': ('webp', 'image/webp')}
    try:
        with Image.open(io.BytesIO(data)) as image:
            kind = types.get(image.format)
            if kind is None:
                return 'bin', 'application/octet-stream', None, None
            # 新上传的像素预算不能用于丢弃历史附件；只校验编码并读取尺寸，不完整解码大图。
            # 预览使用此处确认的对象类型流式返回，不再套用新上传的像素预算。
            image.verify()
            return *kind, image.width, image.height
    except UnidentifiedImageError:
        return 'bin', 'application/octet-stream', None, None


def prepare(connection, s3, bucket, missing_archive=None):
    archived = []
    if missing_archive is not None:
        missing_archive = Path(missing_archive)
        if missing_archive.exists():
            archived = json.loads(missing_archive.read_text())

    def archive_missing(file_id, error):
        if missing_archive is None or not isinstance(error, ClientError) or error.response['Error']['Code'] not in ('404', 'NoSuchKey', 'NotFound'):
            raise error
        # 只允许失效的历史待绑定上传；有业务绑定、近期上传或权限/网络错误都必须停止。
        row = connection.execute("""SELECT row_to_json(f),update_time<CURRENT_TIMESTAMP-INTERVAL '7 days' FROM file f
            WHERE id=%s AND storage_object_id IS NULL AND biz_id IS NULL FOR UPDATE""", (file_id,)).fetchone()
        if row is None:
            raise error
        previous = next((x for x in archived if x['id']==file_id and x['file_name']==row[0]['file_name']
                         and x['create_user']==row[0]['create_user']), None)
        if not row[1] and not (row[0]['is_deleted']==1 and previous is not None):
            raise error
        if previous is None:
            archived.append(row[0])
        missing_archive.parent.mkdir(parents=True, mode=0o700, exist_ok=True)
        descriptor = os.open(missing_archive, os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600)
        os.fchmod(descriptor, 0o600)
        with os.fdopen(descriptor, 'w') as output:
            json.dump(archived, output, ensure_ascii=False, indent=2)
            output.flush()
            os.fsync(output.fileno())
        # 保留原行和展示元数据；后续结构只允许已删除附件缺少对象，活动附件仍必须绑定对象。
        connection.execute('UPDATE file SET is_deleted=1,update_user=NULL,update_time=CURRENT_TIMESTAMP WHERE id=%s', (file_id,))
        return None

    def store(source_bucket, key, owner):
        response = s3.get_object(Bucket=source_bucket, Key=key)
        with response['Body'] as body:
            data = body.read(10 * 1024 * 1024 + 1)
        extension, mime, width, height = inspect(data)
        digest = hashlib.sha256(data).hexdigest()
        target = f'objects/{digest[:2]}/{digest}.{extension}'
        # 目的路径相同可重复写入相同字节；不删除原路径，保留回退依据。
        s3.put_object(Bucket=bucket, Key=target, Body=data, ContentType=mime)
        with s3.get_object(Bucket=bucket, Key=target)['Body'] as body:
            if hashlib.sha256(body.read()).hexdigest() != digest:
                raise RuntimeError('对象复制后摘要校验失败')
        row = connection.execute('SELECT id FROM storage_object WHERE bucket=%s AND sha256=%s AND is_deleted=0', (bucket, digest)).fetchone()
        object_id = row[0] if row else snowflake()
        if row:
            connection.execute('UPDATE storage_object SET object_key=%s,file_size=%s,content_type=%s,image_width=%s,image_height=%s WHERE id=%s',
                               (target, len(data), mime, width, height, object_id))
        else:
            connection.execute('INSERT INTO storage_object(id,bucket,object_key,sha256,file_size,content_type,image_width,image_height,create_user,update_user,is_deleted) VALUES(%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,0)',
                               (object_id,bucket,target,digest,len(data),mime,width,height,owner,owner))
        return object_id

    # 对象表里的无附件对象也统一，避免遗漏既有下载后遗留的对象索引。
    originals = connection.execute('SELECT id,bucket,object_key,create_user FROM storage_object ORDER BY id').fetchall()
    for old_id, old_bucket, old_key, owner in originals:
        new_id = store(old_bucket, old_key, owner)
        connection.execute('UPDATE file SET storage_object_id=%s WHERE storage_object_id=%s', (new_id,old_id))
        if new_id != old_id:
            connection.execute('DELETE FROM storage_object WHERE id=%s', (old_id,))
    files = connection.execute('SELECT id,storage_object_id,file_name,create_user,upgrade_id FROM file ORDER BY id').fetchall()
    for old_id, object_id, name, owner, prepared_id in files:
        if object_id is None:
            try:
                object_id = store(bucket,name,owner)
            except ClientError as error:
                object_id = archive_missing(old_id,error)
        connection.execute('UPDATE file SET storage_object_id=%s,upgrade_id=%s WHERE id=%s',
                           (object_id,prepared_id or snowflake(),old_id))
    print(f'已准备 {len(files)} 条附件，原存储文件保持保留；可继续执行下一版Flyway迁移。')


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--stopped-writes', action='store_true', required=True, help='确认应用和所有写入端已停止')
    parser.add_argument('--archive-missing-unbound', type=Path, help='对超过7天且未绑定的缺失文件保留失效行，并将原始元数据另存到受保护的备份JSON；默认遇缺失停止')
    args=parser.parse_args()
    target=urlsplit(os.environ['FLYWAY_URL'].removeprefix('jdbc:'))
    if target.scheme not in ('postgresql','postgres'):
        raise RuntimeError('仅支持PostgreSQL连接')
    s3=boto3.client('s3',endpoint_url=os.environ['AIO_LIFE_MINIO_ENDPOINT'],
                    aws_access_key_id=os.environ['AIO_LIFE_MINIO_ACCESS_KEY'],
                    aws_secret_access_key=os.environ['AIO_LIFE_MINIO_SECRET_KEY'],region_name='us-east-1')
    bucket=os.environ['AIO_LIFE_MINIO_BUCKET_NAME']
    s3.head_bucket(Bucket=bucket)
    with psycopg.connect(host=target.hostname,port=target.port or 5432,dbname=target.path.lstrip('/'),
                         user=os.environ['FLYWAY_USER'],password=os.environ['FLYWAY_PASSWORD']) as connection:
        connection.execute("SELECT pg_advisory_xact_lock(hashtext('aio-attachment-upgrade'))")
        version=connection.execute('SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank DESC LIMIT 1').fetchone()
        if not version or version[0]!='20261116.10':
            raise RuntimeError('必须先将目标库迁移至20261116.10')
        # 老服务不感知升级租约；表锁阻止准备期间出现新的附件写入，普通只读查询仍可继续。
        connection.execute('LOCK TABLE file,storage_object,image_import_task,b_video IN EXCLUSIVE MODE')
        prepare(connection,s3,bucket,args.archive_missing_unbound)


if __name__=='__main__':
    main()
