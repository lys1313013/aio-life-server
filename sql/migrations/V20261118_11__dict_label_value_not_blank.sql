-- 目的：把系统字典项的展示值和实际值从“可空、默认空串”改为必填且非空白，阻止空标签和空键值进入全局字典；用户字典标签保留“NULL 表示继承公共项”的语义，只去掉默认值并禁止空白。
-- 前置：PostgreSQL 18+；已执行 V20261118_10，sys_dict_data、user_dict_data 为当前字典结构。
-- 影响：sys_dict_data 的 dict_label/dict_value 去掉默认值并改为 NOT NULL，新增非空白检查；user_dict_data.dict_label 保持可空，去掉默认值，空白值归一为 NULL 并新增非空白检查；不改其他列、索引和数据，系统字典存在空值或空白值时整次迁移失败。
-- 重跑：Flyway 成功后不重跑；预检失败时先人工修正异常行，再对同一版本重新执行。
-- 执行：mvn -f tools/flyway/pom.xml compile flyway:migrate flyway:validate。
-- 验证：sys_dict_data 的 dict_label、dict_value 为 NOT NULL 且无默认值，两表新检查生效；user_dict_data 中不存在空白标签，NULL 仍可写入。

SET LOCAL lock_timeout = '10s';

-- 1. 存量预检：新约束对逻辑删除行同样生效，因此不按 is_deleted 过滤。
DO $$
DECLARE
    invalid_label_rows bigint;
    invalid_value_rows bigint;
BEGIN
    SELECT count(*) INTO invalid_label_rows FROM sys_dict_data
     WHERE dict_label IS NULL OR btrim(dict_label) = '';
    IF invalid_label_rows > 0 THEN
        RAISE EXCEPTION 'sys_dict_data.dict_label 有 % 行为空或只有空白，请先人工核对', invalid_label_rows;
    END IF;

    SELECT count(*) INTO invalid_value_rows FROM sys_dict_data
     WHERE dict_value IS NULL OR btrim(dict_value) = '';
    IF invalid_value_rows > 0 THEN
        RAISE EXCEPTION 'sys_dict_data.dict_value 有 % 行为空或只有空白，请先人工核对', invalid_value_rows;
    END IF;
END $$;

-- 2. 系统字典：标签和键值都必填，不允许空串或纯空白，也不再提供空串默认值。
ALTER TABLE sys_dict_data
  ALTER COLUMN dict_label DROP DEFAULT,
  ALTER COLUMN dict_label SET NOT NULL,
  ALTER COLUMN dict_value DROP DEFAULT,
  ALTER COLUMN dict_value SET NOT NULL,
  ADD CONSTRAINT ck_sys_dict_data_label_not_blank CHECK (btrim(dict_label) <> ''),
  ADD CONSTRAINT ck_sys_dict_data_value_not_blank CHECK (btrim(dict_value) <> '');

COMMENT ON COLUMN sys_dict_data.dict_label IS '字典标签，必填且不能只有空白';
COMMENT ON COLUMN sys_dict_data.dict_value IS '字典键值，必填且不能只有空白';
COMMENT ON CONSTRAINT ck_sys_dict_data_label_not_blank ON sys_dict_data IS '字典标签必填，不允许空串或纯空白';
COMMENT ON CONSTRAINT ck_sys_dict_data_value_not_blank ON sys_dict_data IS '字典键值必填，不允许空串或纯空白';

-- 3. 用户字典：标签为空表示继承公共项，空白串按继承处理；键值仍允许为空串，待业务键规则确定后再收紧。
UPDATE user_dict_data
   SET dict_label = NULL
 WHERE dict_label IS NOT NULL AND btrim(dict_label) = '';

ALTER TABLE user_dict_data
  ALTER COLUMN dict_label DROP DEFAULT,
  ADD CONSTRAINT ck_user_dict_data_label_not_blank CHECK (dict_label IS NULL OR btrim(dict_label) <> '');

COMMENT ON COLUMN user_dict_data.dict_label IS '字典标签(分类名称)；NULL表示继承公共项标签，非空时不能只有空白';
COMMENT ON CONSTRAINT ck_user_dict_data_label_not_blank ON user_dict_data IS '标签为空表示继承公共项，非空时不允许空串或纯空白';