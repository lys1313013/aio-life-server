-- 目的：笔记支持独立收藏，收藏状态与正文显隐分开保存。
-- 前置：PostgreSQL 18+；已执行当前初始迁移及此前增量，memo 尚无 favorite 列。
-- 影响：已有笔记默认未收藏；不修改正文、用户归属、逻辑删除和内容更新时间。
-- 重跑：由 Flyway 仅执行一次；事务失败整体回滚，修复原因后重新 migrate，不手工重复执行。
-- 执行：通过独立 Flyway migrate/validate；验证已有数据保留、默认值及 0/1 约束。
ALTER TABLE memo ADD COLUMN favorite SMALLINT NOT NULL DEFAULT 0;
COMMENT ON COLUMN memo.favorite IS '是否收藏：0=未收藏，1=已收藏；切换不改变内容更新时间';
ALTER TABLE memo ADD CONSTRAINT ck_memo_favorite CHECK (favorite IN (0, 1));
COMMENT ON CONSTRAINT ck_memo_favorite ON memo IS '收藏标记只允许未收藏或已收藏';
