
-- 会员平台预设首次接入种子；执行后冻结，后续平台变更新增 V 迁移。
-- 前提：membership_provider 表已存在，Web、Mobile 和后端支持 AI、lifestyle、reading 分类。
-- 只补缺失平台：任一相同 ID 或编码已存在即跳过（含软删除记录）。
-- 重复执行不会覆盖名称、分类、图标、排序、启停、审计字段，也不会恢复已删除平台。
-- 不修改或自动关联用户会员记录；微信读书未指定 Logo 时使用阅读分类图标。
START TRANSACTION;

INSERT INTO `membership_provider`
    (`id`, `name`, `code`, `category`, `icon_key`, `sort_order`, `is_enabled`, `create_time`, `update_time`, `is_deleted`)
SELECT seed.id, seed.name, seed.code, seed.category, seed.icon_key, seed.sort_order, 1, NOW(), NOW(), 0
FROM (
    SELECT 195001 AS id, '腾讯视频' AS name, 'tencent_video' AS code, 'video' AS category, 'tencent_video' AS icon_key, 10 AS sort_order
    UNION ALL SELECT 195002, '爱奇艺', 'iqiyi', 'video', 'iqiyi', 20
    UNION ALL SELECT 195003, '优酷', 'youku', 'video', 'youku', 30
    UNION ALL SELECT 195004, '哔哩哔哩', 'bilibili', 'video', 'bilibili', 40
    UNION ALL SELECT 195005, 'Netflix', 'netflix', 'video', 'netflix', 50
    UNION ALL SELECT 195006, 'YouTube', 'youtube', 'video', 'youtube', 60
    UNION ALL SELECT 195007, 'QQ音乐', 'qq_music', 'music', 'qq_music', 70
    UNION ALL SELECT 195008, '网易云音乐', 'netease_music', 'music', 'netease_music', 80
    UNION ALL SELECT 195009, '酷狗音乐', 'kugou', 'music', 'kugou', 90
    UNION ALL SELECT 195010, 'Spotify', 'spotify', 'music', 'spotify', 100
    UNION ALL SELECT 195011, 'Apple Music', 'apple_music', 'music', 'apple_music', 110
    UNION ALL SELECT 195012, '京东', 'jd', 'shopping', 'jd', 120
    UNION ALL SELECT 195013, '淘宝', 'taobao', 'shopping', 'taobao', 130
    UNION ALL SELECT 195014, '美团', 'meituan', 'lifestyle', 'meituan', 140
    UNION ALL SELECT 195015, '百度网盘', 'baidu_netdisk', 'cloud', 'baidu_netdisk', 150
    UNION ALL SELECT 195016, '夸克', 'quark', 'cloud', 'quark', 160
    UNION ALL SELECT 195017, '阿里云盘', 'aliyundrive', 'cloud', 'aliyundrive', 170
    UNION ALL SELECT 195018, '迅雷', 'xunlei', 'cloud', 'xunlei', 180
    UNION ALL SELECT 195019, 'ChatGPT', 'chatgpt', 'AI', 'chatgpt', 190
    UNION ALL SELECT 195020, 'Disney+', 'disneyplus', 'video', 'disneyplus', 65
    UNION ALL SELECT 195021, 'Claude', 'claude', 'AI', 'claude', 210
    UNION ALL SELECT 195022, 'Gemini / Google AI', 'gemini', 'AI', 'gemini', 220
    UNION ALL SELECT 195023, 'Grok', 'grok', 'AI', 'grok', 230
    UNION ALL SELECT 195024, 'Poe', 'poe', 'AI', 'poe', 240
    UNION ALL SELECT 195025, 'Kimi', 'kimi', 'AI', 'kimi', 250
    UNION ALL SELECT 195026, 'Cursor', 'cursor', 'AI', 'cursor', 260
    UNION ALL SELECT 195027, 'GitHub Copilot', 'github_copilot', 'AI', 'github_copilot', 270
    UNION ALL SELECT 195028, '智谱 GLM Coding Plan', 'glm_coding_plan', 'AI', 'glm_coding_plan', 280
    UNION ALL SELECT 195029, 'Midjourney', 'midjourney', 'AI', 'midjourney', 290
    UNION ALL SELECT 195030, '即梦 AI', 'jimeng', 'AI', 'jimeng', 300
    UNION ALL SELECT 195031, 'Suno', 'suno', 'AI', 'suno', 310
    UNION ALL SELECT 195032, '阿里云百炼', 'aliyun_bailian', 'AI', 'aliyun_bailian', 320
    UNION ALL SELECT 195033, '火山引擎·火山方舟', 'volcengine_ark', 'AI', 'volcengine_ark', 330
    UNION ALL SELECT 195034, 'OpenCode Go', 'opencode_go', 'AI', 'opencode_go', 340
    UNION ALL SELECT 195035, 'iCloud+', 'icloud', 'cloud', 'icloud', 181
    UNION ALL SELECT 195036, '小米云服务', 'xiaomi_cloud', 'cloud', 'xiaomi_cloud', 182
    UNION ALL SELECT 195037, '肯德基', 'kfc', 'lifestyle', 'kfc', 141
    UNION ALL SELECT 195038, '微信读书', 'weread', 'reading', NULL, 183
) AS seed
WHERE NOT EXISTS (
    SELECT 1 FROM `membership_provider` existing
    WHERE existing.id = seed.id OR existing.code = seed.code
);

COMMIT;
