USE `aio_life`;

-- 前提：membership_provider 表已存在，服务及客户端已支持 ai 分类。
-- 可重复执行：按有效平台编码去重，保留既有平台名称、启停和排序配置。
-- 新增平台使用 AI 分类图标；ChatGPT 保留已有品牌图标。
START TRANSACTION;

INSERT INTO `membership_provider`
    (`id`, `name`, `code`, `category`, `icon_key`, `sort_order`, `is_enabled`, `create_time`, `update_time`, `is_deleted`)
SELECT seed.id, seed.name, seed.code, 'ai', seed.icon_key, seed.sort_order, 1, NOW(), NOW(), 0
FROM (
    SELECT 195019 AS id, 'ChatGPT' AS name, 'chatgpt' AS code, 'chatgpt' AS icon_key, 190 AS sort_order
    UNION ALL SELECT 195021, 'Claude', 'claude', NULL, 210
    UNION ALL SELECT 195022, 'Gemini / Google AI', 'gemini', NULL, 220
    UNION ALL SELECT 195023, 'Grok', 'grok', NULL, 230
    UNION ALL SELECT 195024, 'Poe', 'poe', NULL, 240
    UNION ALL SELECT 195025, 'Kimi', 'kimi', NULL, 250
    UNION ALL SELECT 195026, 'Cursor', 'cursor', NULL, 260
    UNION ALL SELECT 195027, 'GitHub Copilot', 'github_copilot', NULL, 270
    UNION ALL SELECT 195028, '智谱 GLM Coding Plan', 'glm_coding_plan', NULL, 280
    UNION ALL SELECT 195029, 'Midjourney', 'midjourney', NULL, 290
    UNION ALL SELECT 195030, '即梦 AI', 'jimeng', NULL, 300
    UNION ALL SELECT 195031, 'Suno', 'suno', NULL, 310
    UNION ALL SELECT 195032, '阿里云百炼', 'aliyun_bailian', NULL, 320
    UNION ALL SELECT 195033, '火山引擎·火山方舟', 'volcengine_ark', NULL, 330
) AS seed
WHERE NOT EXISTS (
    SELECT 1 FROM `membership_provider` existing
    WHERE existing.id = seed.id OR (existing.code = seed.code AND existing.is_deleted = 0)
);

-- 只调整原目录中的 ChatGPT 默认分类，不改写用户已有会员记录。
UPDATE `membership_provider`
SET `category` = 'ai', `update_time` = NOW()
WHERE `code` = 'chatgpt' AND `is_deleted` = 0 AND `category` = 'other';

COMMIT;

SELECT `id`, `name`, `code`, `category`, `is_enabled`
FROM `membership_provider`
WHERE `category` = 'ai' AND `is_deleted` = 0
ORDER BY `sort_order`, `id`;
