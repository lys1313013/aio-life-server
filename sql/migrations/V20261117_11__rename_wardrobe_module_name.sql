-- 目的：模块中文名由「衣柜」改为「衣橱」，同步菜单标题与表、索引注释；表名、字段名与接口保持不变。
-- 前置：PostgreSQL 18+；已执行 V20261117_10。
-- 影响：只更新显示名称与注释，不改变表结构、业务数据、权限与接口契约。
-- 重跑：由 Flyway 版本历史保证只执行一次。

UPDATE sys_menu
SET meta = jsonb_set(meta, '{title}', '"衣橱"'::jsonb),
    update_time = CURRENT_TIMESTAMP
WHERE id = 1702
  AND is_deleted = 0;

COMMENT ON TABLE wardrobe_category IS '衣橱分类表';
COMMENT ON TABLE wardrobe_item IS '衣橱衣物表';
COMMENT ON INDEX idx_wardrobe_category_preset_sort IS '系统预设衣橱分类按排序检索';
COMMENT ON INDEX idx_wardrobe_category_user_sort IS '用户自建衣橱分类按用户与排序检索';