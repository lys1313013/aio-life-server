-- 目的：将设备类型的原平板预设替换为圆角描边 iPad 图标。
-- 前置：PostgreSQL 18+，已完成此前的 Flyway 迁移，客户端已包含 svg:ipad。
-- 影响：仅更新使用原平板图标的未删除设备类型字典项及其更新时间。
-- 重跑：已替换的字典项不再匹配，执行失败由 Flyway 回滚。
-- 执行：通过 mvn -f tools/flyway/pom.xml compile flyway:migrate 执行，遇错停止。

UPDATE user_dict_data
SET icon = 'svg:ipad',
  update_time = CURRENT_TIMESTAMP
WHERE dict_type = 'device_type'
  AND icon = 'solar:tablet-line-duotone'
  AND is_deleted = 0;
