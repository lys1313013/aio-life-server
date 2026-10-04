USE `aio_life`;

-- 仅初始化平台目录，不猜测或改写用户已有会员记录的关联。
INSERT INTO `membership_provider` (`id`, `name`, `code`, `category`, `icon_key`, `sort_order`, `is_enabled`, `create_time`, `update_time`, `is_deleted`)
SELECT 195001, '腾讯视频', 'tencent_video', 'video', 'tencent_video', 10, 1, NOW(), NOW(), 0
WHERE NOT EXISTS (SELECT 1 FROM `membership_provider` WHERE `id` = 195001 OR (`code` = 'tencent_video' AND `is_deleted` = 0));
INSERT INTO `membership_provider` (`id`, `name`, `code`, `category`, `icon_key`, `sort_order`, `is_enabled`, `create_time`, `update_time`, `is_deleted`)
SELECT 195002, '爱奇艺', 'iqiyi', 'video', 'iqiyi', 20, 1, NOW(), NOW(), 0
WHERE NOT EXISTS (SELECT 1 FROM `membership_provider` WHERE `id` = 195002 OR (`code` = 'iqiyi' AND `is_deleted` = 0));
INSERT INTO `membership_provider` (`id`, `name`, `code`, `category`, `icon_key`, `sort_order`, `is_enabled`, `create_time`, `update_time`, `is_deleted`)
SELECT 195003, '优酷', 'youku', 'video', 'youku', 30, 1, NOW(), NOW(), 0
WHERE NOT EXISTS (SELECT 1 FROM `membership_provider` WHERE `id` = 195003 OR (`code` = 'youku' AND `is_deleted` = 0));
INSERT INTO `membership_provider` (`id`, `name`, `code`, `category`, `icon_key`, `sort_order`, `is_enabled`, `create_time`, `update_time`, `is_deleted`)
SELECT 195004, '哔哩哔哩', 'bilibili', 'video', 'bilibili', 40, 1, NOW(), NOW(), 0
WHERE NOT EXISTS (SELECT 1 FROM `membership_provider` WHERE `id` = 195004 OR (`code` = 'bilibili' AND `is_deleted` = 0));
INSERT INTO `membership_provider` (`id`, `name`, `code`, `category`, `icon_key`, `sort_order`, `is_enabled`, `create_time`, `update_time`, `is_deleted`)
SELECT 195005, 'Netflix', 'netflix', 'video', 'netflix', 50, 1, NOW(), NOW(), 0
WHERE NOT EXISTS (SELECT 1 FROM `membership_provider` WHERE `id` = 195005 OR (`code` = 'netflix' AND `is_deleted` = 0));
INSERT INTO `membership_provider` (`id`, `name`, `code`, `category`, `icon_key`, `sort_order`, `is_enabled`, `create_time`, `update_time`, `is_deleted`)
SELECT 195006, 'YouTube', 'youtube', 'video', 'youtube', 60, 1, NOW(), NOW(), 0
WHERE NOT EXISTS (SELECT 1 FROM `membership_provider` WHERE `id` = 195006 OR (`code` = 'youtube' AND `is_deleted` = 0));
INSERT INTO `membership_provider` (`id`, `name`, `code`, `category`, `icon_key`, `sort_order`, `is_enabled`, `create_time`, `update_time`, `is_deleted`)
SELECT 195007, 'QQ音乐', 'qq_music', 'music', 'qq_music', 70, 1, NOW(), NOW(), 0
WHERE NOT EXISTS (SELECT 1 FROM `membership_provider` WHERE `id` = 195007 OR (`code` = 'qq_music' AND `is_deleted` = 0));
INSERT INTO `membership_provider` (`id`, `name`, `code`, `category`, `icon_key`, `sort_order`, `is_enabled`, `create_time`, `update_time`, `is_deleted`)
SELECT 195008, '网易云音乐', 'netease_music', 'music', 'netease_music', 80, 1, NOW(), NOW(), 0
WHERE NOT EXISTS (SELECT 1 FROM `membership_provider` WHERE `id` = 195008 OR (`code` = 'netease_music' AND `is_deleted` = 0));
INSERT INTO `membership_provider` (`id`, `name`, `code`, `category`, `icon_key`, `sort_order`, `is_enabled`, `create_time`, `update_time`, `is_deleted`)
SELECT 195009, '酷狗音乐', 'kugou', 'music', 'kugou', 90, 1, NOW(), NOW(), 0
WHERE NOT EXISTS (SELECT 1 FROM `membership_provider` WHERE `id` = 195009 OR (`code` = 'kugou' AND `is_deleted` = 0));
INSERT INTO `membership_provider` (`id`, `name`, `code`, `category`, `icon_key`, `sort_order`, `is_enabled`, `create_time`, `update_time`, `is_deleted`)
SELECT 195010, 'Spotify', 'spotify', 'music', 'spotify', 100, 1, NOW(), NOW(), 0
WHERE NOT EXISTS (SELECT 1 FROM `membership_provider` WHERE `id` = 195010 OR (`code` = 'spotify' AND `is_deleted` = 0));
INSERT INTO `membership_provider` (`id`, `name`, `code`, `category`, `icon_key`, `sort_order`, `is_enabled`, `create_time`, `update_time`, `is_deleted`)
SELECT 195011, 'Apple Music', 'apple_music', 'music', 'apple_music', 110, 1, NOW(), NOW(), 0
WHERE NOT EXISTS (SELECT 1 FROM `membership_provider` WHERE `id` = 195011 OR (`code` = 'apple_music' AND `is_deleted` = 0));
INSERT INTO `membership_provider` (`id`, `name`, `code`, `category`, `icon_key`, `sort_order`, `is_enabled`, `create_time`, `update_time`, `is_deleted`)
SELECT 195012, '京东', 'jd', 'shopping', 'jd', 120, 1, NOW(), NOW(), 0
WHERE NOT EXISTS (SELECT 1 FROM `membership_provider` WHERE `id` = 195012 OR (`code` = 'jd' AND `is_deleted` = 0));
INSERT INTO `membership_provider` (`id`, `name`, `code`, `category`, `icon_key`, `sort_order`, `is_enabled`, `create_time`, `update_time`, `is_deleted`)
SELECT 195013, '淘宝', 'taobao', 'shopping', 'taobao', 130, 1, NOW(), NOW(), 0
WHERE NOT EXISTS (SELECT 1 FROM `membership_provider` WHERE `id` = 195013 OR (`code` = 'taobao' AND `is_deleted` = 0));
INSERT INTO `membership_provider` (`id`, `name`, `code`, `category`, `icon_key`, `sort_order`, `is_enabled`, `create_time`, `update_time`, `is_deleted`)
SELECT 195014, '美团', 'meituan', 'shopping', 'meituan', 140, 1, NOW(), NOW(), 0
WHERE NOT EXISTS (SELECT 1 FROM `membership_provider` WHERE `id` = 195014 OR (`code` = 'meituan' AND `is_deleted` = 0));
INSERT INTO `membership_provider` (`id`, `name`, `code`, `category`, `icon_key`, `sort_order`, `is_enabled`, `create_time`, `update_time`, `is_deleted`)
SELECT 195015, '百度网盘', 'baidu_netdisk', 'cloud', 'baidu_netdisk', 150, 1, NOW(), NOW(), 0
WHERE NOT EXISTS (SELECT 1 FROM `membership_provider` WHERE `id` = 195015 OR (`code` = 'baidu_netdisk' AND `is_deleted` = 0));
INSERT INTO `membership_provider` (`id`, `name`, `code`, `category`, `icon_key`, `sort_order`, `is_enabled`, `create_time`, `update_time`, `is_deleted`)
SELECT 195016, '夸克', 'quark', 'cloud', 'quark', 160, 1, NOW(), NOW(), 0
WHERE NOT EXISTS (SELECT 1 FROM `membership_provider` WHERE `id` = 195016 OR (`code` = 'quark' AND `is_deleted` = 0));
INSERT INTO `membership_provider` (`id`, `name`, `code`, `category`, `icon_key`, `sort_order`, `is_enabled`, `create_time`, `update_time`, `is_deleted`)
SELECT 195017, '阿里云盘', 'aliyundrive', 'cloud', 'aliyundrive', 170, 1, NOW(), NOW(), 0
WHERE NOT EXISTS (SELECT 1 FROM `membership_provider` WHERE `id` = 195017 OR (`code` = 'aliyundrive' AND `is_deleted` = 0));
INSERT INTO `membership_provider` (`id`, `name`, `code`, `category`, `icon_key`, `sort_order`, `is_enabled`, `create_time`, `update_time`, `is_deleted`)
SELECT 195018, '迅雷', 'xunlei', 'cloud', 'xunlei', 180, 1, NOW(), NOW(), 0
WHERE NOT EXISTS (SELECT 1 FROM `membership_provider` WHERE `id` = 195018 OR (`code` = 'xunlei' AND `is_deleted` = 0));
INSERT INTO `membership_provider` (`id`, `name`, `code`, `category`, `icon_key`, `sort_order`, `is_enabled`, `create_time`, `update_time`, `is_deleted`)
SELECT 195019, 'ChatGPT', 'chatgpt', 'other', 'chatgpt', 190, 1, NOW(), NOW(), 0
WHERE NOT EXISTS (SELECT 1 FROM `membership_provider` WHERE `id` = 195019 OR (`code` = 'chatgpt' AND `is_deleted` = 0));

INSERT INTO `membership_provider` (`id`, `name`, `code`, `category`, `icon_key`, `sort_order`, `is_enabled`, `create_time`, `update_time`, `is_deleted`)
SELECT 195020, 'Disney+', 'disneyplus', 'video', 'disneyplus', 65, 1, NOW(), NOW(), 0
WHERE NOT EXISTS (SELECT 1 FROM `membership_provider` WHERE `id` = 195020 OR (`code` = 'disneyplus' AND `is_deleted` = 0));

INSERT INTO `sys_menu`
    (`id`, `parent_id`, `name`, `path`, `component`, `meta`, `roles`, `sort`, `status`, `mobile_status`, `create_time`, `update_time`, `is_deleted`)
SELECT 1911, parent.id, 'MembershipProviderAdmin', '/system/membership-providers', 'system/membership-providers/index',
    JSON_OBJECT('icon', 'lucide:crown', 'title', '会员平台', 'authority', JSON_ARRAY('admin')),
    'admin', 10, 1, 1, NOW(), NOW(), 0
FROM sys_menu parent
WHERE parent.name = 'System' AND parent.parent_id = 0 AND parent.is_deleted = 0
    AND NOT EXISTS (SELECT 1 FROM sys_menu m WHERE m.id = 1911 OR m.name = 'MembershipProviderAdmin' OR m.path = '/system/membership-providers')
LIMIT 1;
