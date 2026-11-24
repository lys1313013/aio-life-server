-- 目的：将 VR / AR 设备类型的旧眼镜预设换成实心头显图标。
-- 前置：PostgreSQL 18+，已完成此前的 Flyway 迁移，客户端已包含 svg:device-vr-headset。
-- 影响：仅更新使用旧眼镜预设的未删除 VR / AR 设备类型字典项及其更新时间。
-- 重跑：已替换的字典项不再匹配，执行失败由 Flyway 回滚。
-- 执行：通过 mvn -f tools/flyway/pom.xml compile flyway:migrate 执行，遇错停止。

UPDATE user_dict_data
SET icon = 'svg:device-vr-headset',
  update_time = CURRENT_TIMESTAMP
WHERE dict_type = 'device_type'
  AND icon = 'solar:glasses-line-duotone'
  AND is_deleted = 0;
