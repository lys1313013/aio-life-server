"""使用自行创建的PostgreSQL和内存对象存储验证准备工具，不读取业务环境变量。"""
import hashlib
import io
import subprocess
import time
import tempfile
import json
from botocore.exceptions import ClientError
import unittest
from pathlib import Path

import psycopg
from PIL import Image

import prepare


class Objects:
    def __init__(self):
        self.data = {}

    def get_object(self, *, Bucket, Key):
        return {'Body': io.BytesIO(self.data[Bucket, Key])}

    def put_object(self, *, Bucket, Key, Body, ContentType):
        self.data[Bucket, Key] = Body


class PrepareTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.container = subprocess.check_output([
            'docker', 'run', '--rm', '-d', '-e', 'POSTGRES_PASSWORD=attachment-fixture',
            '-p', '127.0.0.1::5432', 'postgres:18-alpine'], text=True).strip()
        try:
            port = subprocess.check_output(['docker', 'port', cls.container, '5432'], text=True).strip().rsplit(':', 1)[1]
            for attempt in range(80):
                try:
                    cls.db = psycopg.connect(host='127.0.0.1', port=port, dbname='postgres',
                                             user='postgres', password='attachment-fixture', autocommit=True)
                    break
                except psycopg.OperationalError:
                    if attempt == 79:
                        raise
                    time.sleep(.25)
            root = Path(__file__).resolve().parents[2]
            for migration in sorted((root / 'sql/migrations').glob('V*.sql')):
                version = tuple(map(int, migration.name.split('__')[0][1:].split('_')))
                if migration.name.startswith('V20261110_02_') or version > (20261116, 10):
                    continue
                cls.db.execute(migration.read_text())
        except BaseException:
            subprocess.run(['docker', 'stop', cls.container], check=True, capture_output=True)
            raise

    @classmethod
    def tearDownClass(cls):
        cls.db.close()
        subprocess.run(['docker', 'stop', cls.container], check=True, capture_output=True)

    def setUp(self):
        self.db.execute('TRUNCATE file,storage_object')
        output = io.BytesIO()
        Image.new('RGB', (2, 2)).save(output, format='PNG')
        self.png = output.getvalue()
        self.objects = Objects()
        self.objects.data['old', 'bvedio/original.png'] = self.png
        self.objects.data['target', 'user/first.png'] = self.png
        self.objects.data['target', 'user/document.bin'] = b'fixture document'
        self.db.execute('INSERT INTO storage_object(id,bucket,object_key,sha256,file_size,content_type) VALUES(11,%s,%s,%s,%s,%s)',
                        ('old', 'bvedio/original.png', hashlib.sha256(self.png).hexdigest(), len(self.png), 'image/png'))
        self.db.execute("INSERT INTO file(id,storage_object_id,file_name,biz_type,create_user) VALUES('a',11,'unused','avatar',42),('b',NULL,'user/first.png','movie',43),('c',NULL,'user/document.bin','honor_record',42)")

    def test_actual_bytes_deduplicate_and_rerun_preserves_ids(self):
        with self.db.transaction():
            prepare.prepare(self.db, self.objects, 'target')
        rows = self.db.execute('SELECT id,storage_object_id,upgrade_id FROM file ORDER BY id').fetchall()
        self.assertEqual(rows[0][1], rows[1][1])
        self.assertNotEqual(rows[0][1], rows[2][1])
        self.assertEqual(len({row[2] for row in rows}), 3)
        self.assertTrue(all(row[2] > 0 for row in rows))
        objects = self.db.execute('SELECT bucket,object_key,image_width,image_height,content_type FROM storage_object ORDER BY content_type').fetchall()
        self.assertEqual(len(objects), 2)
        self.assertTrue(all(row[0] == 'target' and row[1].startswith('objects/') for row in objects))
        self.assertEqual(objects[1][2:4], (2, 2))
        with self.db.transaction():
            prepare.prepare(self.db, self.objects, 'target')
        self.assertEqual(rows, self.db.execute('SELECT id,storage_object_id,upgrade_id FROM file ORDER BY id').fetchall())
        self.assertIn(('old', 'bvedio/original.png'), self.objects.data)

    def test_copy_failure_rolls_back_database_preparation(self):
        del self.objects.data['target', 'user/document.bin']
        with self.assertRaises(KeyError):
            with self.db.transaction():
                prepare.prepare(self.db, self.objects, 'target')
        self.assertEqual(self.db.execute('SELECT count(*) FROM file WHERE upgrade_id IS NOT NULL').fetchone()[0], 0)
        self.assertEqual(self.db.execute('SELECT object_key FROM storage_object WHERE id=11').fetchone()[0], 'bvedio/original.png')
        self.assertTrue(any(key.startswith('objects/') for bucket, key in self.objects.data if bucket == 'target'))

    def test_large_legacy_image_keeps_original_pixels_without_new_upload_limit(self):
        output = io.BytesIO()
        Image.new('L', (4001, 4001)).save(output, format='PNG')
        data = output.getvalue()
        self.assertEqual(prepare.inspect(data), ('png', 'image/png', 4001, 4001))

    def missing_storage(self, code='404'):
        original = self.objects.get_object
        def get_object(**params):
            try:
                return original(**params)
            except KeyError:
                raise ClientError({'Error': {'Code': code, 'Message': 'fixture'}}, 'GetObject')
        self.objects.get_object = get_object
        del self.objects.data['target', 'user/document.bin']

    def test_missing_expired_unbound_preserves_deleted_row_and_original_metadata(self):
        self.missing_storage()
        self.db.execute("UPDATE file SET update_time=CURRENT_TIMESTAMP-INTERVAL '8 days' WHERE id='c'")
        with tempfile.TemporaryDirectory() as folder:
            archive = Path(folder) / 'missing.json'
            with self.db.transaction():
                prepare.prepare(self.db, self.objects, 'target', archive)
            row = self.db.execute("SELECT is_deleted,storage_object_id,upgrade_id FROM file WHERE id='c'").fetchone()
            self.assertEqual(row[:2], (1, None))
            self.assertIsNotNone(row[2])
            original = json.loads(archive.read_text())
            self.assertEqual(original[0]['id'], 'c')
            self.assertEqual(original[0]['is_deleted'], 0)
            self.assertEqual(archive.stat().st_mode & 0o777, 0o600)
            self.assertEqual(self.db.execute('SELECT count(*) FROM file').fetchone()[0], 3)
            with self.db.transaction():
                prepare.prepare(self.db, self.objects, 'target', archive)
            self.assertEqual(json.loads(archive.read_text()), original)
            self.assertEqual(self.db.execute("SELECT upgrade_id FROM file WHERE id='c'").fetchone()[0], row[2])

    def test_bound_missing_file_cannot_be_archived(self):
        self.missing_storage()
        self.db.execute("UPDATE file SET biz_id=123,update_time=CURRENT_TIMESTAMP-INTERVAL '8 days' WHERE id='c'")
        with tempfile.TemporaryDirectory() as folder:
            archive = Path(folder) / 'missing.json'
            with self.assertRaises(ClientError):
                with self.db.transaction():
                    prepare.prepare(self.db, self.objects, 'target', archive)
            self.assertFalse(archive.exists())
            self.assertEqual(self.db.execute("SELECT is_deleted FROM file WHERE id='c'").fetchone()[0], 0)

    def test_permissions_failure_cannot_be_treated_as_missing(self):
        self.missing_storage('AccessDenied')
        self.db.execute("UPDATE file SET update_time=CURRENT_TIMESTAMP-INTERVAL '8 days' WHERE id='c'")
        with tempfile.TemporaryDirectory() as folder:
            with self.assertRaises(ClientError):
                with self.db.transaction():
                    prepare.prepare(self.db, self.objects, 'target', Path(folder) / 'missing.json')


if __name__ == '__main__':
    unittest.main()
