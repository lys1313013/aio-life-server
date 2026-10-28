# 菜单 SQL

菜单全量结构基线集中在 `../1_init_table/2026-08-18_init_all_tables.sql` 的 `sys_menu`、`user_menu_hidden` 等表中；后续结构变更同时提供 `../migrations/` 下的增量脚本供已有库升级。

初始化先在全量基线后执行尚未合并的 `../migrations/2026-10-28_02_unify_common_fields.sql`，再执行 `2_ini_data/2_menu.sql`，再按文件名顺序执行 `2_ini_data/202*.sql`。基础首页路径为 `/`；菜单种子使用当前组件和接口，不保留旧路由转换。

菜单默认数据保留重复执行时只补缺失项的语义，不覆盖管理员配置、启停和逻辑删除状态。`web_is_enabled` 控制 Web，`mobile_is_enabled` 控制 H5、微信和 App，均为 1 启用、0 停用；操作日志默认仅 Web 显示。

修改菜单结构时提供增量脚本并同步实体和文档；全量基线在用户选定的时间统一合并；修改默认菜单时同步对应种子和两端路由。验证见 [数据库初始化与验证](../../docs/数据库初始化与验证.md)。
