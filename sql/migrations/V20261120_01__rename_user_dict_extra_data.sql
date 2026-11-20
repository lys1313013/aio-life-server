-- 目的：将用户字典项的补充属性列统一命名为 extra_data。
-- 前置：PostgreSQL 18+，已执行 V20261119_04，user_dict_data.ext_data 为可空 JSONB 列。
-- 影响：重命名列并更新注释，保留现有 JSON 值及空值，无需回填。
-- 重跑：Flyway 记录成功版本后跳过，执行失败时事务回滚。
-- 执行：mvn -f tools/flyway/pom.xml compile flyway:migrate flyway:validate。
-- 验证：确认新列类型为 JSONB 且可空，核对迁移前后列值。

ALTER TABLE user_dict_data RENAME COLUMN ext_data TO extra_data;

COMMENT ON COLUMN user_dict_data.extra_data IS '字典项的补充属性，未配置时为空';
