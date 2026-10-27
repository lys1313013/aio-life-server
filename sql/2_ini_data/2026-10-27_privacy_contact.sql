-- 当前全量初始化的隐私联系配置；只补缺，不覆盖管理员填写的信息。
-- 兼容：MySQL 5.7.44、8.0。
-- 前置：system_config 表已存在；已有库先执行 sql/migrations/2026-10-27_01_user_privacy_erasure.sql。
-- 影响：不回填用户数据；配置值公开给未登录用户，不得填写密钥或私人凭据。
-- 重跑：只补缺失配置键，保留既有值和软删除状态；部分失败修正原因后可重跑。
-- 执行：由调用方指定目标库，遇错停止；固定 ID 冲突需核对现有记录，不覆盖或忽略错误。
INSERT INTO system_config (id, config_key, config_value, config_type, description, is_deleted)
SELECT 2026102701, 'privacy.operator', '', 'STRING', '隐私政策公开的实际运营主体名称，发布前填写真实个人或组织名称', 0
WHERE NOT EXISTS (SELECT 1 FROM system_config WHERE config_key = 'privacy.operator');
INSERT INTO system_config (id, config_key, config_value, config_type, description, is_deleted)
SELECT 2026102702, 'privacy.contact-email', '', 'STRING', '隐私政策公开的联系邮箱，发布前填写可收信地址', 0
WHERE NOT EXISTS (SELECT 1 FROM system_config WHERE config_key = 'privacy.contact-email');
