USE `aio_life`;

-- 前提：会员平台表及原目录已初始化，后端已包含 iCloud 与小米云服务图标。
-- 可重复执行；保留已有平台配置，不自动关联或修改用户会员记录。
START TRANSACTION;

INSERT INTO `membership_provider`
    (`id`, `name`, `code`, `category`, `icon_key`, `sort_order`, `is_enabled`, `create_time`, `update_time`, `is_deleted`)
SELECT 195035, 'iCloud+', 'icloud', 'cloud', 'icloud', 181, 1, NOW(), NOW(), 0
WHERE NOT EXISTS (
    SELECT 1 FROM `membership_provider`
    WHERE `id` = 195035 OR (`code` = 'icloud' AND `is_deleted` = 0)
);

INSERT INTO `membership_provider`
    (`id`, `name`, `code`, `category`, `icon_key`, `sort_order`, `is_enabled`, `create_time`, `update_time`, `is_deleted`)
SELECT 195036, '小米云服务', 'xiaomi_cloud', 'cloud', 'xiaomi_cloud', 182, 1, NOW(), NOW(), 0
WHERE NOT EXISTS (
    SELECT 1 FROM `membership_provider`
    WHERE `id` = 195036 OR (`code` = 'xiaomi_cloud' AND `is_deleted` = 0)
);

COMMIT;
