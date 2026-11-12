# 数据库脚本入口

仅支持 PostgreSQL 18+。执行方式见[数据库初始化与验证](../docs/数据库初始化与验证.md)，编写规则见[SQL 脚本规范](../docs/SQL脚本规范.md)。

## 当前执行链

- [V20261110_01__postgresql_schema.sql](migrations/V20261110_01__postgresql_schema.sql)：完整结构，包含 68 张业务表、索引、约束和中文注释。
- [V20261110_02_001__cbti_personalities.sql](migrations/V20261110_02_001__cbti_personalities.sql)：CBTI 人格定义。
- [V20261110_02_002__data_export_coordinator.sql](migrations/V20261110_02_002__data_export_coordinator.sql)：数据导出协调记录。
- [V20261110_02_003__honor_categories.sql](migrations/V20261110_02_003__honor_categories.sql)：荣誉分类。
- [V20261110_02_004__membership_providers.sql](migrations/V20261110_02_004__membership_providers.sql)：会员平台。
- [V20261110_02_005__bank_dictionary.sql](migrations/V20261110_02_005__bank_dictionary.sql)：银行字典。
- [V20261110_02_006__menus.sql](migrations/V20261110_02_006__menus.sql)：系统菜单，含 Web 查询工作台与数据模型入口，移动端不启用。
- [V20261110_02_007__work_calendar_2026.sql](migrations/V20261110_02_007__work_calendar_2026.sql)：2026 年工作日历。
- [V20261110_02_008__system_config.sql](migrations/V20261110_02_008__system_config.sql)：系统配置。
- [V20261110_02_009__regions.sql](migrations/V20261110_02_009__regions.sql)：行政区划。
- [V20261110_02_010__payment_methods.sql](migrations/V20261110_02_010__payment_methods.sql)：公共支付方式。
- [V20261110_02_011__wardrobe_categories.sql](migrations/V20261110_02_011__wardrobe_categories.sql)：衣柜分类。
- [V20261112_13__align_check_constraint_expressions.sql](migrations/V20261112_13__align_check_constraint_expressions.sql)：把已有库的 7 个 CHECK 约束表达式对齐到基线写法，语义不变。

- [V20261112_14__drop_time_category_time_type.sql](migrations/V20261112_14__drop_time_category_time_type.sql)：删除时迹分类时间类型，保留分类、覆盖、层级与时迹记录。

- [V20261112_15__relocate_category_menus.sql](migrations/V20261112_15__relocate_category_menus.sql)：个人分类归入个人中心分类管理，管理员时迹分类移至系统管理，保留菜单 ID 和页面路径。
- [V20261112_16__menu_visible_clients.sql](migrations/V20261112_16__menu_visible_clients.sql)：将 Web、移动端启用列合并为展示端位掩码并删除旧列，保留各端设置，时迹取消移动端菜单重复入口。

新库先用独立 Flyway 入口迁移，再启动应用；应用启动不自动建表或导入种子。后续结构、菜单和数据变更新增更高版本 `V`，不改写已执行文件。

## 公共来源与后续维护

`reference/banks/` 等目录保存公共来源快照与许可，不是可执行脚本。生成器只输出待审阅草稿，不覆盖已执行迁移。后续 SQL 按同类合并、异类分开维护，并遵守 Flyway 版本不可改写的约定。
