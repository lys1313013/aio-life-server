-- 目的：补齐字典、银行卡和分类覆盖记录的唯一键，收紧时迹时间约束，并清理无生产引用的死表死列。
-- 前置：已执行 V20261110_01、V20261110_02_001 至 V20261110_02_011、V20261111_01、V20261112_01 至 V20261112_03；数据库为 PostgreSQL 18+。
-- 影响：新增部分唯一索引和 CHECK；删除 record_article、record_article_annotation、enum_type、user_dict_type 四张表及三个无映射列。删除表列会丢弃其中数据。
-- 重跑：由 Flyway 版本历史保证只执行一次；失败事务回滚后修正原因再迁移。
-- 执行：通过 mvn -f tools/flyway/pom.xml compile flyway:migrate 执行，遇错停止。

-- 唯一约束建立前先暴露存量重复和非法时迹，避免迁移在索引或 CHECK 处只给出底层错误。
DO $$
DECLARE
  duplicate_groups bigint;
  invalid_rows bigint;
BEGIN
  SELECT count(*) INTO duplicate_groups FROM (
    SELECT user_id, template_id FROM user_dict_data
    WHERE is_deleted = 0 AND template_id IS NOT NULL
    GROUP BY user_id, template_id HAVING count(*) > 1
  ) duplicated;
  IF duplicate_groups > 0 THEN
    RAISE EXCEPTION 'user_dict_data(user_id, template_id) 存在 % 组有效重复覆盖记录，请先处理后再执行迁移', duplicate_groups;
  END IF;

  SELECT count(*) INTO duplicate_groups FROM (
    SELECT user_id, template_id FROM time_tracker_category
    WHERE is_deleted = 0 AND template_id IS NOT NULL
    GROUP BY user_id, template_id HAVING count(*) > 1
  ) duplicated;
  IF duplicate_groups > 0 THEN
    RAISE EXCEPTION 'time_tracker_category(user_id, template_id) 存在 % 组有效重复覆盖记录，请先处理后再执行迁移', duplicate_groups;
  END IF;

  SELECT count(*) INTO duplicate_groups FROM (
    SELECT user_id, card_no_fingerprint FROM bank_card
    WHERE is_deleted = 0 AND card_no_fingerprint IS NOT NULL
    GROUP BY user_id, card_no_fingerprint HAVING count(*) > 1
  ) duplicated;
  IF duplicate_groups > 0 THEN
    RAISE EXCEPTION 'bank_card(user_id, card_no_fingerprint) 存在 % 组有效重复记录，请先处理后再执行迁移', duplicate_groups;
  END IF;

  SELECT count(*) INTO duplicate_groups FROM (
    SELECT user_id, bank_card_id, tag_id FROM bank_card_tag_rel WHERE is_deleted = 0
    GROUP BY user_id, bank_card_id, tag_id HAVING count(*) > 1
  ) duplicated;
  IF duplicate_groups > 0 THEN
    RAISE EXCEPTION 'bank_card_tag_rel(user_id, bank_card_id, tag_id) 存在 % 组有效重复记录，请先处理后再执行迁移', duplicate_groups;
  END IF;

  SELECT count(*) INTO duplicate_groups FROM (
    SELECT dict_type FROM sys_dict_type WHERE is_deleted = 0
    GROUP BY dict_type HAVING count(*) > 1
  ) duplicated;
  IF duplicate_groups > 0 THEN
    RAISE EXCEPTION 'sys_dict_type.dict_type 存在 % 组有效重复记录，请先处理后再执行迁移', duplicate_groups;
  END IF;

  SELECT count(*) INTO duplicate_groups FROM (
    SELECT dict_id, dict_value FROM sys_dict_data WHERE is_deleted = 0
    GROUP BY dict_id, dict_value HAVING count(*) > 1
  ) duplicated;
  IF duplicate_groups > 0 THEN
    RAISE EXCEPTION 'sys_dict_data(dict_id, dict_value) 存在 % 组有效重复记录，请先处理后再执行迁移', duplicate_groups;
  END IF;

  SELECT count(*) INTO invalid_rows FROM time_record
  WHERE start_time NOT BETWEEN 0 AND 1439
     OR end_time NOT BETWEEN 0 AND 1439
     OR start_time > end_time
     OR duration <> end_time - start_time + 1;
  IF invalid_rows > 0 THEN
    RAISE EXCEPTION 'time_record 存在 % 条起止时间或时长不一致的记录，请先修正后再执行迁移', invalid_rows;
  END IF;
END
$$;

-- 删除表列前先暴露存量数据；存在记录时中止，由人工确认、备份或迁移后再执行。
DO $$
DECLARE
  article_rows bigint;
  annotation_rows bigint;
  enum_rows bigint;
  user_type_rows bigint;
  honor_file_rows bigint;
  device_company_rows bigint;
  category_code_rows bigint;
  details text := '';
BEGIN
  SELECT count(*) INTO article_rows FROM record_article;
  SELECT count(*) INTO annotation_rows FROM record_article_annotation;
  SELECT count(*) INTO enum_rows FROM enum_type;
  SELECT count(*) INTO user_type_rows FROM user_dict_type;
  SELECT count(*) INTO honor_file_rows FROM honor_record WHERE file_id IS NOT NULL;
  SELECT count(*) INTO device_company_rows FROM device WHERE purchase_company IS NOT NULL;
  SELECT count(*) INTO category_code_rows FROM time_tracker_category WHERE code IS NOT NULL;

  IF article_rows > 0 THEN details := details || format('record_article=%s ', article_rows); END IF;
  IF annotation_rows > 0 THEN details := details || format('record_article_annotation=%s ', annotation_rows); END IF;
  IF enum_rows > 0 THEN details := details || format('enum_type=%s ', enum_rows); END IF;
  IF user_type_rows > 0 THEN details := details || format('user_dict_type=%s ', user_type_rows); END IF;
  IF honor_file_rows > 0 THEN details := details || format('honor_record.file_id=%s ', honor_file_rows); END IF;
  IF device_company_rows > 0 THEN details := details || format('device.purchase_company=%s ', device_company_rows); END IF;
  IF category_code_rows > 0 THEN details := details || format('time_tracker_category.code=%s ', category_code_rows); END IF;
  IF details <> '' THEN
    RAISE EXCEPTION '以下待删除表或列仍有数据，请先确认、备份或迁移：%', details;
  END IF;
END
$$;

-- 覆盖记录与业务唯一键；已逻辑删除的记录不占用。
CREATE UNIQUE INDEX uk_user_dict_data_user_template ON user_dict_data (user_id, template_id)
  WHERE is_deleted = 0 AND template_id IS NOT NULL;
COMMENT ON INDEX uk_user_dict_data_user_template IS '同一用户对同一公共字典只能有一条有效覆盖记录';

CREATE UNIQUE INDEX uk_time_tracker_category_user_template ON time_tracker_category (user_id, template_id)
  WHERE is_deleted = 0 AND template_id IS NOT NULL;
COMMENT ON INDEX uk_time_tracker_category_user_template IS '同一用户对同一公共分类只能有一条有效覆盖记录';

CREATE UNIQUE INDEX uk_bank_card_user_fingerprint ON bank_card (user_id, card_no_fingerprint)
  WHERE is_deleted = 0 AND card_no_fingerprint IS NOT NULL;
COMMENT ON INDEX uk_bank_card_user_fingerprint IS '同一用户同一卡号指纹只能有一条有效银行卡';

CREATE UNIQUE INDEX uk_bank_card_tag_rel_user_card_tag ON bank_card_tag_rel (user_id, bank_card_id, tag_id)
  WHERE is_deleted = 0;
COMMENT ON INDEX uk_bank_card_tag_rel_user_card_tag IS '同一银行卡同一标签只能有一条有效关联';

CREATE UNIQUE INDEX uk_sys_dict_type_dict_type ON sys_dict_type (dict_type)
  WHERE is_deleted = 0;
COMMENT ON INDEX uk_sys_dict_type_dict_type IS '有效系统字典类型编码唯一，已删除类型不占用';

CREATE UNIQUE INDEX uk_sys_dict_data_dict_id_value ON sys_dict_data (dict_id, dict_value)
  WHERE is_deleted = 0;
COMMENT ON INDEX uk_sys_dict_data_dict_id_value IS '同一字典类型下的有效字典值唯一';

-- 时迹起止使用 0～1439 的闭区间分钟，时长由两端派生。
COMMENT ON COLUMN time_record.start_time IS '开始时间（分钟，0-1439）';
COMMENT ON COLUMN time_record.end_time IS '结束时间（分钟，0-1439）';
ALTER TABLE time_record ADD CONSTRAINT ck_time_record_time_range
  CHECK (start_time BETWEEN 0 AND 1439
     AND end_time BETWEEN 0 AND 1439
     AND start_time <= end_time
     AND duration = end_time - start_time + 1);

-- 以下对象无生产代码、导出目录或跨仓库引用，删除后不再保留兼容结构。
DROP TABLE record_article_annotation;
DROP TABLE record_article;
DROP TABLE enum_type;
DROP TABLE user_dict_type;

ALTER TABLE device DROP COLUMN purchase_company;
DROP INDEX time_tracker_category_idx_user_code;
ALTER TABLE time_tracker_category DROP COLUMN code;
ALTER TABLE honor_record DROP COLUMN file_id;
