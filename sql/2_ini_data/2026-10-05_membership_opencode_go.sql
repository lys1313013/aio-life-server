USE `aio_life`;

-- 前提：会员平台表已存在，已部署包含 AI 品牌图标的后端。
-- 可重复执行：增加 OpenCode Go，并补齐已有 AI 平台的空图标；保留自选图标和平台配置。
START TRANSACTION;

INSERT INTO `membership_provider`
    (`id`, `name`, `code`, `category`, `icon_key`, `sort_order`, `is_enabled`, `create_time`, `update_time`, `is_deleted`)
SELECT 195034, 'OpenCode Go', 'opencode_go', 'AI', 'opencode_go', 340, 1, NOW(), NOW(), 0
WHERE NOT EXISTS (
    SELECT 1 FROM `membership_provider`
    WHERE `id` = 195034 OR (`code` = 'opencode_go' AND `is_deleted` = 0)
);

UPDATE `membership_provider`
SET `category` = 'AI', `update_time` = NOW()
WHERE CAST(`category` AS BINARY) = CAST('ai' AS BINARY);

UPDATE `membership_record`
SET `category` = 'AI'
WHERE CAST(`category` AS BINARY) = CAST('ai' AS BINARY);

UPDATE `membership_provider`
SET `icon_key` = `code`, `update_time` = NOW()
WHERE `is_deleted` = 0
  AND (`icon_key` IS NULL OR TRIM(`icon_key`) = '')
  AND `code` IN (
    'chatgpt', 'claude', 'gemini', 'grok', 'poe', 'kimi', 'cursor',
    'github_copilot', 'glm_coding_plan', 'midjourney', 'jimeng', 'suno',
    'aliyun_bailian', 'volcengine_ark', 'opencode_go'
  );

COMMIT;

SELECT `id`, `name`, `code`, `category`, `icon_key`, `is_enabled`
FROM `membership_provider`
WHERE `category` = 'AI' AND `is_deleted` = 0
ORDER BY `sort_order`, `id`;
