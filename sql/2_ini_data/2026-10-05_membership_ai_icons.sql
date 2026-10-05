USE `aio_life`;

-- 前提：已执行会员平台及 AI 平台初始化，已部署包含以下内置 Logo 的后端。
-- 可重复执行：只补齐空 Logo，保留已有自选图标、名称、排序及启停配置。
START TRANSACTION;

UPDATE `membership_provider`
SET `category` = 'AI', `update_time` = NOW()
WHERE CAST(`category` AS BINARY) = CAST('ai' AS BINARY);

-- 仅规范分类大小写，保留会员记录审计字段。
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
    'aliyun_bailian', 'volcengine_ark'
  );

COMMIT;

SELECT `id`, `name`, `code`, `category`, `icon_key`, `is_enabled`
FROM `membership_provider`
WHERE `category` = 'AI' AND `is_deleted` = 0
ORDER BY `sort_order`, `id`;
