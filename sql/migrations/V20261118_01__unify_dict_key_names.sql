-- 目的：统一字典表主键与关联列命名，消除 dict_id 在两表中分别表示自身主键和关联字段的歧义，并让两表主键都使用 id。
-- 前置：PostgreSQL 18+；已执行 V20261117_12，sys_dict_type.dict_id、sys_dict_data.dict_code/dict_id 仍为原列名。
-- 影响：只重命名列和唯一索引并更新引用注释，不改类型、默认值、可空性、数据、主键约束或其他索引；已有字典项、类型关联和银行字典均保持原值。
-- 重跑：Flyway 成功后不重跑；手工重复执行会因旧列名不存在而失败，其他结构错误仍失败。
-- 执行：mvn -f tools/flyway/pom.xml compile flyway:migrate flyway:validate，遇错停止。
-- 验证：查询 information_schema.columns 确认新列存在、旧列不存在，并核对两表行数与 uk_sys_dict_data_dict_type_id_value 定义。

ALTER TABLE "sys_dict_type" RENAME COLUMN "dict_id" TO "id";
ALTER TABLE "sys_dict_data" RENAME COLUMN "dict_code" TO "id";
ALTER TABLE "sys_dict_data" RENAME COLUMN "dict_id" TO "dict_type_id";
ALTER INDEX "uk_sys_dict_data_dict_id_value" RENAME TO "uk_sys_dict_data_dict_type_id_value";

COMMENT ON COLUMN "sys_dict_type"."id" IS '字典类型ID，由应用生成雪花ID';
COMMENT ON COLUMN "sys_dict_data"."id" IS '字典项ID，由应用生成雪花ID';
COMMENT ON COLUMN "sys_dict_data"."dict_type_id" IS '所属字典类型ID，关联sys_dict_type.id';
COMMENT ON INDEX "uk_sys_dict_data_dict_type_id_value" IS '同一字典类型下的有效字典值唯一';
COMMENT ON COLUMN "bank_card"."bank_id" IS 'sys_dict_data.id，类型bank；自定义银行时为空';
COMMENT ON COLUMN "bank_card_cover_template"."bank_id" IS 'sys_dict_data.id，类型bank';