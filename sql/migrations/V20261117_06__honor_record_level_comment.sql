-- 目的：修正 honor_record.level 注释，与实际使用的英文枚举保持一致（原注释为 1-5 级数字枚举）。
-- 前置：PostgreSQL 18+；已执行当前初始迁移及其后全部适用增量，honor_record 结构不变。
-- 影响：仅更新 honor_record.level 列注释，不改结构、不改数据、不新增约束。
-- 重跑：Flyway 成功后不重跑；手工重复执行 COMMENT 结果相同，不产生数据影响。
-- 执行：mvn -f tools/flyway/pom.xml compile flyway:migrate flyway:validate；失败事务回滚。

COMMENT ON COLUMN honor_record.level IS '荣誉级别：school-校级/district-区级/city-市级/province-省级/national-国家级/international-国际级/other-其他';
