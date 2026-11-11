# 数据库脚本入口

仅支持 PostgreSQL 18+。执行方式见[数据库初始化与验证](../docs/数据库初始化与验证.md)，编写规则见[SQL 脚本规范](../docs/SQL脚本规范.md)。

## 当前执行链

- [V20261110_01__postgresql_schema.sql](migrations/V20261110_01__postgresql_schema.sql)：完整结构，包含 72 张业务表、索引、约束和中文注释。
- [V20261110_02_001__cbti_personalities.sql](migrations/V20261110_02_001__cbti_personalities.sql)：CBTI 人格定义。
- [V20261110_02_002__data_export_coordinator.sql](migrations/V20261110_02_002__data_export_coordinator.sql)：数据导出协调记录。
- [V20261110_02_003__honor_categories.sql](migrations/V20261110_02_003__honor_categories.sql)：荣誉分类。
- [V20261110_02_004__membership_providers.sql](migrations/V20261110_02_004__membership_providers.sql)：会员平台。
- [V20261110_02_005__bank_dictionary.sql](migrations/V20261110_02_005__bank_dictionary.sql)：银行字典。
- [V20261110_02_006__menus.sql](migrations/V20261110_02_006__menus.sql)：系统菜单。
- [V20261110_02_007__work_calendar_2026.sql](migrations/V20261110_02_007__work_calendar_2026.sql)：2026 年工作日历。
- [V20261110_02_008__system_config.sql](migrations/V20261110_02_008__system_config.sql)：系统配置。
- [V20261110_02_009__regions.sql](migrations/V20261110_02_009__regions.sql)：行政区划。
- [V20261110_02_010__payment_methods.sql](migrations/V20261110_02_010__payment_methods.sql)：公共支付方式。
- [V20261110_02_011__wardrobe_categories.sql](migrations/V20261110_02_011__wardrobe_categories.sql)：衣柜分类。
- [V20261111_01__query_workspace_menus.sql](migrations/V20261111_01__query_workspace_menus.sql)：在 AI 接入分组补充 Web 查询工作台与数据模型菜单，移动端不启用。
- 后续结构、菜单和数据变更新增更高版本 `V`。日常不改写已执行的 PostgreSQL 文件，已有库升级所需的增量继续保留；本次种子分类拆分使用下述显式登记转换。

新库先用独立 Flyway 入口迁移，再启动应用；应用启动不自动建表或导入种子。公共种子已按用途从原 `V20261110_02` 拆为 `.02.001` 至 `.02.011`；结构迁移 `.01` 保持原文。已执行旧种子的库先按[一次性拆分登记流程](../docs/数据库初始化与验证.md#已执行旧公共种子的库一次性拆分登记)转换历史，再使用日常迁移入口。

## 历史资料

旧 MySQL 结构、数据及历史修正 SQL 不再保留在工作树中，原文件仅通过 Git 历史追溯；当前 SQL 只维护上述 PostgreSQL 分类迁移。一次性数据搬迁工具继续从源库或离线快照读取，不依赖旧 SQL 文件。

`reference/banks/` 等目录继续保存公共来源快照与许可，`reference/seed-split/` 保存种子拆分登记所需证据；这些不是重复的 MySQL 执行脚本。生成器只输出待审阅草稿，不覆盖已执行迁移。后续 SQL 按同类合并、异类分开维护，并遵守 Flyway 版本不可改写的约定。
