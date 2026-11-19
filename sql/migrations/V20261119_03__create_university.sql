-- 目的：建立中国大陆高校公共目录。
-- 前置：PostgreSQL 18+，已执行 V20261119_02 及此前增量，sys_region 已初始化省级目录。
-- 影响：新增 university 表、约束和索引，不回填数据。高校名单由下一版迁移导入。
-- 重跑：Flyway 按版本执行一次；事务失败回滚，排除原因后可重试。
-- 执行：通过独立 Flyway migrate 执行。验证字段注释、省级编码类型和索引。

CREATE TABLE university (
  id BIGINT PRIMARY KEY,
  name VARCHAR(200) NOT NULL,
  province_code VARCHAR(12) COLLATE "C" NOT NULL,
  remark TEXT,
  create_user BIGINT,
  create_time TIMESTAMP(0) WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_user BIGINT,
  update_time TIMESTAMP(0) WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
  is_deleted SMALLINT NOT NULL DEFAULT 0,
  CONSTRAINT ck_university_name_not_blank CHECK (btrim(name) <> ''),
  CONSTRAINT ck_university_is_deleted CHECK (is_deleted IN (0, 1))
);

COMMENT ON TABLE university IS '中国大陆高校公共目录';
COMMENT ON COLUMN university.id IS '高校ID，应用新增使用雪花ID，初始化数据使用固定ID';
COMMENT ON COLUMN university.name IS '学校正式名称';
COMMENT ON COLUMN university.province_code IS '所在省级地区编码，逻辑关联 sys_region.code，目标须有效且 level = 1';
COMMENT ON COLUMN university.remark IS '高校补充说明，未填写时为空';
COMMENT ON COLUMN university.create_user IS '创建人用户ID，系统导入时为空';
COMMENT ON COLUMN university.create_time IS '创建时间';
COMMENT ON COLUMN university.update_user IS '最后更新人用户ID，系统导入时为空';
COMMENT ON COLUMN university.update_time IS '最后更新时间，由应用在更新时维护';
COMMENT ON COLUMN university.is_deleted IS '逻辑删除标记：0=未删除，1=已删除';

COMMENT ON CONSTRAINT ck_university_name_not_blank ON university IS '学校正式名称不可为空字符串或仅含普通空格';
COMMENT ON CONSTRAINT ck_university_is_deleted ON university IS '逻辑删除标记仅允许 0 和 1';

CREATE INDEX idx_university_province ON university (province_code) WHERE is_deleted = 0;
COMMENT ON INDEX idx_university_province IS '按所在省级地区筛选有效高校';
