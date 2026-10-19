USE `aio_life`;

-- 前提：原会员平台目录已初始化，三端已支持 lifestyle，后端已包含肯德基图标。
-- 可重复执行；只调整美团预设的原购物分类，保留名称、图标、排序和启停配置。
-- 不修改或自动关联用户已有会员记录。
START TRANSACTION;

UPDATE `membership_provider`
SET `category` = 'lifestyle', `update_time` = NOW()
WHERE `id` = 195014 AND `code` = 'meituan'
  AND `category` = 'shopping' AND `is_deleted` = 0;

INSERT INTO `membership_provider`
    (`id`, `name`, `code`, `category`, `icon_key`, `sort_order`, `is_enabled`, `create_time`, `update_time`, `is_deleted`)
SELECT 195037, '肯德基', 'kfc', 'lifestyle', 'kfc', 141, 1, NOW(), NOW(), 0
WHERE NOT EXISTS (
    SELECT 1 FROM `membership_provider`
    WHERE `id` = 195037 OR (`code` = 'kfc' AND `is_deleted` = 0)
);

COMMIT;
