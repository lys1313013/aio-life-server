-- 目的：替换设备类型的手机、电脑和显示器图标预设。
-- 前置：PostgreSQL 18+，已完成此前的 Flyway 迁移，客户端已包含对应 SVG 资产。
-- 影响：仅更新仍使用这三个原预设的未删除设备类型字典项及其更新时间。
-- 重跑：已替换的字典项不再匹配，执行失败由 Flyway 回滚。
-- 执行：通过 mvn -f tools/flyway/pom.xml compile flyway:migrate 执行，遇错停止。

UPDATE user_dict_data AS dict
SET icon = icons.new_icon,
  update_time = CURRENT_TIMESTAMP
FROM (VALUES
  ('solar:smartphone-line-duotone', 'svg:device-smartphone'),
  ('solar:laptop-2-line-duotone', 'svg:device-laptop'),
  ('solar:monitor-line-duotone', 'svg:device-monitor')
) AS icons(old_icon, new_icon)
WHERE dict.dict_type = 'device_type'
  AND dict.icon = icons.old_icon
  AND dict.is_deleted = 0;
