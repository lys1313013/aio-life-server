USE `aio_life`;

-- 仅用于尚未完成历史修正的旧库；新库无需执行。
-- 从已归档的 AI、OpenCode Go、生活服务迁移保留原有修正规则。
-- 日常补充平台仅执行 2026-10-20_membership_providers.sql，不执行本文件。
START TRANSACTION;

-- 只调整原 ChatGPT 的 other 默认分类。
UPDATE `membership_provider`
SET `category` = 'AI', `update_time` = NOW()
WHERE `code` = 'chatgpt' AND `is_deleted` = 0 AND `category` = 'other';

UPDATE `membership_provider`
SET `category` = 'AI', `update_time` = NOW()
WHERE CAST(`category` AS BINARY) = CAST('ai' AS BINARY);

-- 仅规范分类大小写，保留会员记录审计字段。
UPDATE `membership_record`
SET `category` = 'AI'
WHERE CAST(`category` AS BINARY) = CAST('ai' AS BINARY);

-- 仅补齐空图标，保留自选非空图标、名称、排序和启停配置。
UPDATE `membership_provider`
SET `icon_key` = `code`, `update_time` = NOW()
WHERE `is_deleted` = 0
  AND (`icon_key` IS NULL OR TRIM(`icon_key`) = '')
  AND `code` IN (
    'chatgpt', 'claude', 'gemini', 'grok', 'poe', 'kimi', 'cursor',
    'github_copilot', 'glm_coding_plan', 'midjourney', 'jimeng', 'suno',
    'aliyun_bailian', 'volcengine_ark', 'opencode_go'
  );

-- 仅调整美团预设的原购物分类。
UPDATE `membership_provider`
SET `category` = 'lifestyle', `update_time` = NOW()
WHERE `id` = 195014 AND `code` = 'meituan'
  AND `category` = 'shopping' AND `is_deleted` = 0;

COMMIT;
