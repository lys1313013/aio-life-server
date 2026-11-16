-- 目的：切换为物理对象、共享来源、用户附件和导入任务四层模型，成功来源持续复用。
-- 前置：PostgreSQL 18+，已执行 V20261116_10；已有库停写并执行附件准备工具，先备份数据库及对象存储。
-- 影响：保留全部附件并替换UUID；对象引用、封面任务和来源回填，删除冗余物理元数据与视频来源字段。
-- 重跑：Flyway只执行一次；全部DDL/DML在事务中执行，失败整体回滚到准备完成状态；不改写已执行版本。
-- 执行：独立 Flyway migrate；验证 tools/flyway 的附件增量测试及真实实体全字段查询。
DO $$ BEGIN
  IF EXISTS(SELECT 1 FROM file WHERE upgrade_id IS NULL OR (storage_object_id IS NULL AND is_deleted=0)) THEN
    RAISE EXCEPTION '附件字节尚未准备：请停写并执行 tools/attachments/prepare.py，再继续迁移';
  END IF;
  IF EXISTS(SELECT 1 FROM storage_object WHERE is_deleted<>0 OR object_key !~ '^objects/[0-9a-f]{2}/[0-9a-f]{64}\.(jpg|png|gif|bmp|webp|bin)$') THEN
    RAISE EXCEPTION '对象尚未统一：请执行 tools/attachments/prepare.py';
  END IF;
END $$;
ALTER TABLE storage_object RENAME COLUMN sha256 TO content_hash;
ALTER TABLE storage_object ADD CONSTRAINT ck_storage_object_active CHECK(is_deleted=0);
ALTER TABLE storage_object ADD CONSTRAINT ck_storage_object_hash CHECK(content_hash ~ '^[0-9a-f]{64}$');
ALTER TABLE storage_object ADD CONSTRAINT ck_storage_object_size CHECK(file_size>0);
ALTER TABLE storage_object ADD CONSTRAINT ck_storage_object_dimensions CHECK((image_width IS NULL AND image_height IS NULL) OR (image_width IS NOT NULL AND image_height IS NOT NULL AND image_width>0 AND image_height>0));
COMMENT ON COLUMN storage_object.content_hash IS '文件字节的SHA-256摘要，64位小写十六进制，写入后不可变';
COMMENT ON COLUMN storage_object.object_key IS '内容寻址对象键：objects/摘要前两位/摘要.服务端检测扩展名';
COMMENT ON COLUMN storage_object.is_deleted IS '恒为0；不使用逻辑删除，无引用回收时物理删除';
DROP INDEX storage_object_uk_storage_hash;
DROP INDEX storage_object_uk_storage_key;
CREATE UNIQUE INDEX uk_storage_object_hash ON storage_object(bucket,content_hash);
COMMENT ON INDEX uk_storage_object_hash IS '同一桶相同字节唯一，上传导入与回收共用对象锁';
CREATE UNIQUE INDEX uk_storage_object_key ON storage_object(bucket,object_key);
COMMENT ON INDEX uk_storage_object_key IS '桶内物理对象键唯一';
CREATE TABLE external_resource (
  id BIGINT PRIMARY KEY,
  platform VARCHAR(32) NOT NULL,
  resource_type VARCHAR(32) NOT NULL,
  external_key VARCHAR(255) NOT NULL,
  variant VARCHAR(128) NOT NULL DEFAULT '',
  source_url VARCHAR(1000) NOT NULL,
  storage_object_id BIGINT,
  state VARCHAR(16) NOT NULL DEFAULT 'PENDING',
  attempts INTEGER NOT NULL DEFAULT 0,
  next_attempt_at TIMESTAMP(0),
  lease_token VARCHAR(32),
  lease_until TIMESTAMP(0),
  error_code VARCHAR(64),
  fetched_at TIMESTAMP(0),
  create_user BIGINT,
  create_time TIMESTAMP(0) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_user BIGINT,
  update_time TIMESTAMP(0) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  is_deleted SMALLINT NOT NULL DEFAULT 0
);
COMMENT ON TABLE external_resource IS '跨用户共享的远程来源；成功后永久复用，不设置缓存过期或主动刷新';
COMMENT ON COLUMN external_resource.id IS '来源ID，由应用生成雪花ID';
COMMENT ON COLUMN external_resource.platform IS '平台：douban豆瓣、bilibili哔哩哔哩、jd京东、taobao淘宝';
COMMENT ON COLUMN external_resource.resource_type IS '资源类型：movie_poster影视海报、book_cover书籍封面、video_cover视频封面、product_image商品图';
COMMENT ON COLUMN external_resource.external_key IS '从已校验URL解析的平台资源稳定标识';
COMMENT ON COLUMN external_resource.variant IS '平台尺寸裁剪规格，无规格时为空串';
COMMENT ON COLUMN external_resource.source_url IS '已校验下载地址，仅用于首次下载、失败重试与追溯';
COMMENT ON COLUMN external_resource.storage_object_id IS '成功对象ID；未成功时为空，成功后不再改变';
COMMENT ON COLUMN external_resource.state IS 'PENDING待下载、RUNNING持租约下载、READY成功复用、FAILED失败可重试';
COMMENT ON COLUMN external_resource.attempts IS '本轮下载尝试次数，最多5次，显式重试失败来源后归零';
COMMENT ON COLUMN external_resource.next_attempt_at IS '退避后可重试时间；初次或显式重试时为空';
COMMENT ON COLUMN external_resource.lease_token IS '下载租约令牌，未运行时为空';
COMMENT ON COLUMN external_resource.lease_until IS '下载租约到期时间，未运行时为空';
COMMENT ON COLUMN external_resource.error_code IS '受控失败码，无失败时为空';
COMMENT ON COLUMN external_resource.fetched_at IS '成功下载时间；未成功时为空';
COMMENT ON COLUMN external_resource.create_user IS '创建人用户ID，后台任务可为空';
COMMENT ON COLUMN external_resource.create_time IS '创建时间';
COMMENT ON COLUMN external_resource.update_user IS '最后更新人用户ID，后台任务可为空';
COMMENT ON COLUMN external_resource.update_time IS '最后更新时间';
COMMENT ON COLUMN external_resource.is_deleted IS '逻辑删除标记：0有效，1已删除';
ALTER TABLE external_resource ADD CONSTRAINT ck_external_resource_platform CHECK(platform IN ('douban','bilibili','jd','taobao'));
ALTER TABLE external_resource ADD CONSTRAINT ck_external_resource_state CHECK(state IN ('PENDING','RUNNING','READY','FAILED'));
ALTER TABLE external_resource ADD CONSTRAINT ck_external_resource_object CHECK((state='READY')=(storage_object_id IS NOT NULL));
ALTER TABLE external_resource ADD CONSTRAINT ck_external_resource_lease CHECK((state='RUNNING' AND lease_token IS NOT NULL AND lease_until IS NOT NULL) OR (state<>'RUNNING' AND lease_token IS NULL AND lease_until IS NULL));
ALTER TABLE external_resource ADD CONSTRAINT ck_external_resource_attempts CHECK(attempts BETWEEN 0 AND 5);
ALTER TABLE external_resource ADD CONSTRAINT ck_external_resource_active CHECK(is_deleted=0);
CREATE UNIQUE INDEX uk_external_resource_identity ON external_resource(platform,resource_type,external_key,variant);
COMMENT ON INDEX uk_external_resource_identity IS '同一平台资源规格只保留一个来源，成功后不失效';
CREATE INDEX idx_external_resource_object ON external_resource(storage_object_id);
COMMENT ON INDEX idx_external_resource_object IS '对象回收反查成功来源引用';
CREATE INDEX idx_external_resource_due ON external_resource(state,next_attempt_at,lease_until);
COMMENT ON INDEX idx_external_resource_due IS '扫描待下载或租约到期的来源';
ALTER TABLE file RENAME TO attachment;
-- 原UUID只在准备期使用，新接口只接受雪花ID；无数据库持久化的旧文件ID引用。
ALTER TABLE attachment DROP COLUMN id;
ALTER TABLE attachment RENAME COLUMN upgrade_id TO id;
ALTER TABLE attachment ADD PRIMARY KEY(id);
DROP INDEX uk_file_upgrade_id;
ALTER TABLE attachment ADD COLUMN user_id BIGINT;
ALTER TABLE attachment ADD COLUMN external_resource_id BIGINT;
ALTER TABLE attachment ADD COLUMN sort_order INTEGER NOT NULL DEFAULT 0;
UPDATE attachment SET user_id=CASE WHEN biz_type='bank_card_template_cover' THEN NULL ELSE create_user END,
  biz_type=CASE biz_type WHEN 'movie' THEN 'movie_cover' WHEN 'read' THEN 'read_cover'
    WHEN 'device' THEN 'device_cover' WHEN 'wardrobe_item' THEN 'wardrobe_item_cover'
    WHEN 'bank_card_cover' THEN 'bank_card_face' WHEN 'bank_card_template_cover' THEN 'bank_card_template_face' ELSE biz_type END,
  file_name=regexp_replace(file_name,'^.*/',''),
  is_public=CASE WHEN biz_type='avatar' AND biz_id IS NOT NULL THEN 1
    WHEN biz_type='bank_card_template_cover' AND EXISTS(
      SELECT 1 FROM bank_card_cover_template t JOIN sys_dict_data d ON d.dict_code=t.bank_id
      JOIN sys_dict_type dt ON dt.dict_id=d.dict_id
      WHERE t.id=attachment.biz_id AND t.is_deleted=0 AND t.is_enabled=1
        AND d.is_deleted=0 AND d.is_enabled=1 AND dt.is_deleted=0 AND dt.is_enabled=1 AND dt.dict_type='bank') THEN 1
    ELSE 0 END;
ALTER TABLE attachment ALTER COLUMN file_name DROP NOT NULL;
ALTER TABLE attachment ADD CONSTRAINT ck_attachment_object CHECK(is_deleted=1 OR storage_object_id IS NOT NULL);
ALTER TABLE attachment ALTER COLUMN biz_type SET NOT NULL;
ALTER TABLE attachment ADD CONSTRAINT ck_attachment_owner CHECK(user_id IS NOT NULL OR biz_type='bank_card_template_face');
ALTER TABLE attachment DROP COLUMN file_size;
ALTER TABLE attachment DROP COLUMN file_type;
ALTER TABLE attachment DROP COLUMN hash_value;
COMMENT ON TABLE attachment IS '用户在一个业务位置使用的附件，字节及物理元数据由storage_object持有';
COMMENT ON COLUMN attachment.id IS '附件ID，由应用生成雪花ID；接口以字符串传输';
COMMENT ON COLUMN attachment.storage_object_id IS '物理对象ID，有效附件必填；历史缺失且已删除的附件可为空，应用校验存在性与回收并发';
COMMENT ON COLUMN attachment.user_id IS '归属用户ID；仅系统公共卡面模板可为空';
COMMENT ON COLUMN attachment.external_resource_id IS '远程来源ID；本地上传或系统生成时为空';
COMMENT ON COLUMN attachment.file_name IS '下载展示文件名，不是对象存储路径；未提供时为空';
COMMENT ON COLUMN attachment.biz_type IS '附件用途：movie_cover/read_cover/device_cover/wardrobe_item_cover/b_video_cover/avatar/bank_card_face/bank_card_template_face/feedback/feedback_comment/honor_record/event_record';
COMMENT ON COLUMN attachment.biz_id IS '业务记录ID；上传待绑定或解绑后为空';
COMMENT ON COLUMN attachment.sort_order IS '同一业务多附件的展示顺序，从0开始';
COMMENT ON COLUMN attachment.is_public IS '0私有，1公开；仅服务端按当前头像或已发布模板设置';
CREATE UNIQUE INDEX uk_attachment_single_slot ON attachment(biz_type,biz_id)
  WHERE is_deleted=0 AND biz_id IS NOT NULL AND biz_type IN ('movie_cover','read_cover','device_cover','wardrobe_item_cover','b_video_cover','avatar','bank_card_face','bank_card_template_face');
COMMENT ON INDEX uk_attachment_single_slot IS '单值业务位置只允许一条有效附件';
CREATE INDEX idx_attachment_owner ON attachment(user_id,is_deleted);
COMMENT ON INDEX idx_attachment_owner IS '用户附件归属与删除状态检索';
CREATE INDEX idx_attachment_source ON attachment(external_resource_id);
COMMENT ON INDEX idx_attachment_source IS '附件来源追溯';
CREATE INDEX idx_attachment_public_object ON attachment(storage_object_id) WHERE is_public=1 AND is_deleted=0;
COMMENT ON INDEX idx_attachment_public_object IS '公开对象访问反查有效公开附件';
CREATE TABLE import_job (
  id BIGINT PRIMARY KEY,
  biz_type VARCHAR(50) NOT NULL,
  biz_id BIGINT NOT NULL,
  external_resource_id BIGINT NOT NULL,
  state VARCHAR(16) NOT NULL DEFAULT 'PENDING',
  next_attempt_at TIMESTAMP(0),
  error_code VARCHAR(64),
  result_attachment_id BIGINT,
  create_user BIGINT,
  create_time TIMESTAMP(0) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_user BIGINT,
  update_time TIMESTAMP(0) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  is_deleted SMALLINT NOT NULL DEFAULT 0
);
COMMENT ON TABLE import_job IS '业务位置的一次远程导入请求，下载租约由来源持有，等待任务可重复推进';
COMMENT ON COLUMN import_job.id IS '导入任务ID，由应用生成雪花ID';
COMMENT ON COLUMN import_job.biz_type IS '目标附件用途，当前异步导入为b_video_cover';
COMMENT ON COLUMN import_job.biz_id IS '目标业务记录ID';
COMMENT ON COLUMN import_job.external_resource_id IS '等待的共享来源ID';
COMMENT ON COLUMN import_job.state IS 'PENDING等待、RUNNING等待来源、READY已绑定附件、FAILED失败、CANCELLED已取消';
COMMENT ON COLUMN import_job.next_attempt_at IS '下一次可检查时间，未设置时可立即检查';
COMMENT ON COLUMN import_job.error_code IS '受控失败码，无失败时为空';
COMMENT ON COLUMN import_job.result_attachment_id IS '成功产出的附件ID，未成功时为空';
COMMENT ON COLUMN import_job.create_user IS '创建人用户ID，后台任务可为空';
COMMENT ON COLUMN import_job.create_time IS '创建时间';
COMMENT ON COLUMN import_job.update_user IS '最后更新人用户ID，后台任务可为空';
COMMENT ON COLUMN import_job.update_time IS '最后更新时间';
COMMENT ON COLUMN import_job.is_deleted IS '逻辑删除标记：0有效，1已删除';
ALTER TABLE import_job ADD CONSTRAINT ck_import_job_state CHECK(state IN ('PENDING','RUNNING','READY','FAILED','CANCELLED'));
ALTER TABLE import_job ADD CONSTRAINT ck_import_job_deleted CHECK(is_deleted IN (0,1));
ALTER TABLE import_job ADD CONSTRAINT ck_import_job_result CHECK((state='READY')=(result_attachment_id IS NOT NULL));
CREATE UNIQUE INDEX uk_import_job_active_slot ON import_job(biz_type,biz_id) WHERE is_deleted=0 AND state IN ('PENDING','RUNNING');
COMMENT ON INDEX uk_import_job_active_slot IS '同一业务位置同时最多一个在途请求';
CREATE INDEX idx_import_job_source ON import_job(external_resource_id,state);
COMMENT ON INDEX idx_import_job_source IS '来源下载完成后推进等待任务';
CREATE INDEX idx_import_job_slot ON import_job(biz_type,biz_id,create_time DESC,id DESC);
COMMENT ON INDEX idx_import_job_slot IS '读取业务位置最新导入状态';
-- 使用原视频ID作为本次来源与任务的候选ID，均与各自新表的应用雪花ID语义一致。
INSERT INTO external_resource(id,platform,resource_type,external_key,variant,source_url,storage_object_id,state,attempts,fetched_at,create_user,update_user)
SELECT DISTINCT ON (identity_key,variant) id,'bilibili','video_cover',identity_key,variant,cover,storage_id,
  CASE WHEN storage_id IS NOT NULL THEN 'READY' WHEN cover_state='FAILED' THEN 'FAILED' ELSE 'PENDING' END,
  CASE WHEN storage_id IS NULL AND cover_state='FAILED' THEN 5 ELSE 0 END,
  CASE WHEN storage_id IS NOT NULL THEN CURRENT_TIMESTAMP ELSE NULL END,user_id,user_id
FROM (
  SELECT v.id,v.cover,v.cover_state,v.user_id,a.storage_object_id AS storage_id,
    regexp_replace(split_part(regexp_replace(split_part(v.cover,'?',1),'^https?://[^/]+/',''),'@',1),'\.[A-Za-z0-9]+$','') AS identity_key,
    CASE WHEN position('@' IN split_part(v.cover,'?',1))>0 THEN split_part(split_part(v.cover,'?',1),'@',2) ELSE '' END AS variant
  FROM b_video v LEFT JOIN attachment a ON a.biz_type='b_video_cover' AND a.biz_id=v.id AND a.is_deleted=0
  WHERE v.cover IS NOT NULL AND btrim(v.cover)<>''
) q ORDER BY identity_key,variant,storage_id NULLS LAST,id;
UPDATE attachment a SET external_resource_id=r.id FROM b_video v,external_resource r
WHERE a.biz_type='b_video_cover' AND a.biz_id=v.id AND r.platform='bilibili'
  AND r.external_key=regexp_replace(split_part(regexp_replace(split_part(v.cover,'?',1),'^https?://[^/]+/',''),'@',1),'\.[A-Za-z0-9]+$','')
  AND r.variant=CASE WHEN position('@' IN split_part(v.cover,'?',1))>0 THEN split_part(split_part(v.cover,'?',1),'@',2) ELSE '' END;
INSERT INTO import_job(id,biz_type,biz_id,external_resource_id,state,create_user,update_user)
SELECT v.id,'b_video_cover',v.id,r.id,CASE WHEN r.state='FAILED' THEN 'FAILED' ELSE 'PENDING' END,v.user_id,v.user_id
FROM b_video v JOIN external_resource r ON r.platform='bilibili'
  AND r.external_key=regexp_replace(split_part(regexp_replace(split_part(v.cover,'?',1),'^https?://[^/]+/',''),'@',1),'\.[A-Za-z0-9]+$','')
  AND r.variant=CASE WHEN position('@' IN split_part(v.cover,'?',1))>0 THEN split_part(split_part(v.cover,'?',1),'@',2) ELSE '' END
WHERE v.is_deleted=0 AND NOT EXISTS(SELECT 1 FROM attachment a WHERE a.biz_type='b_video_cover' AND a.biz_id=v.id AND a.is_deleted=0);
DROP TABLE image_import_task;
ALTER TABLE b_video DROP COLUMN cover;
ALTER TABLE b_video DROP COLUMN cover_state;
ALTER TABLE b_video DROP COLUMN cover_version;

-- 约束含义与应用状态机保持一致；关联完整性由应用维护，不使用外键。
COMMENT ON CONSTRAINT ck_storage_object_active ON storage_object IS '对象仅物理回收，不使用逻辑删除状态';
COMMENT ON CONSTRAINT ck_storage_object_hash ON storage_object IS '对象摘要必须为64位小写SHA-256十六进制';
COMMENT ON CONSTRAINT ck_storage_object_size ON storage_object IS '拒绝空对象及负字节数';
COMMENT ON CONSTRAINT ck_storage_object_dimensions ON storage_object IS '图片宽高同时为空或同时为正整数';
COMMENT ON CONSTRAINT ck_external_resource_platform ON external_resource IS '平台限定为已定义的白名单策略';
COMMENT ON CONSTRAINT ck_external_resource_state ON external_resource IS '来源状态只能为等待、运行、成功或失败';
COMMENT ON CONSTRAINT ck_external_resource_object ON external_resource IS '仅成功来源持有物理对象';
COMMENT ON CONSTRAINT ck_external_resource_lease ON external_resource IS '运行来源必须同时持有租约令牌和到期时间';
COMMENT ON CONSTRAINT ck_external_resource_attempts ON external_resource IS '每轮尝试计数限定0至5';
COMMENT ON CONSTRAINT ck_external_resource_active ON external_resource IS '成功来源不按时间或远端状态失效';
COMMENT ON CONSTRAINT ck_attachment_owner ON attachment IS '只有系统公共卡面模板可以没有用户归属';
COMMENT ON CONSTRAINT ck_import_job_state ON import_job IS '导入任务状态限定为已定义的五种状态';
COMMENT ON CONSTRAINT ck_import_job_deleted ON import_job IS '逻辑删除标记限定0或1';
COMMENT ON CONSTRAINT ck_import_job_result ON import_job IS '只有成功任务必须且能够保存结果附件ID';

COMMENT ON CONSTRAINT ck_attachment_object ON attachment IS '有效附件必须绑定物理对象，仅已删除历史缺失附件可无对象';
