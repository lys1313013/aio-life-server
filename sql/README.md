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
- [V20261112_01__p1_unique_constraints_and_collations.sql](migrations/V20261112_01__p1_unique_constraints_and_collations.sql)：补齐业务唯一键，把 API Key、BV 号、对象键和外部订单号等标识符改为确定性排序，并将使用逻辑删除的业务键改为部分唯一索引。
- [V20261112_02__drop_user_avatar_file_fk.sql](migrations/V20261112_02__drop_user_avatar_file_fk.sql)：移除 `user.avatar_file_id` 到 `file.id` 的外键，头像引用改由应用层校验。
- [V20261112_03__rename_user_table.sql](migrations/V20261112_03__rename_user_table.sql)：将用户主表 `user` 重命名为 `sys_user`，同步重命名主键、检查约束与索引，避免 PostgreSQL 保留字。
- [V20261112_04__remaining_constraints_and_cleanup.sql](migrations/V20261112_04__remaining_constraints_and_cleanup.sql)：补齐字典、银行卡和分类覆盖唯一键，补时迹时间 CHECK，并删除无引用的死表死列及对应导出配置。
- [V20261112_05__rename_duration_units.sql](migrations/V20261112_05__rename_duration_units.sql)：把时迹、视频和演出的同名时长列改为 `<量>_<单位>`，时迹起止改为 `start_minute/end_minute`，并改写 `b_video.pages_info` 内的分集时长键。
- [V20261112_06__normalize_is_deleted_and_wardrobe_file_id.sql](migrations/V20261112_06__normalize_is_deleted_and_wardrobe_file_id.sql)：把基线中仍为 `integer` 的 `is_deleted` 统一为 `smallint`，并把 `wardrobe_item.file_id` 收窄到 `varchar(32)`，执行前检查非法删除标记与超长文件ID。
- [V20261112_07__hot_query_indexes.sql](migrations/V20261112_07__hot_query_indexes.sql)：补齐用户维度的收支、时迹、视频、备忘录、闪念、任务明细、分类、登录日志、设备和演出列表索引。
- [V20261112_08__audit_and_dict_contract.sql](migrations/V20261112_08__audit_and_dict_contract.sql)：回填并收紧 `create_time/update_time`，把系统字典 `is_enabled` 设为 `NOT NULL DEFAULT 1`，保留 `user_dict_data` 的 NULL 继承语义并增加 0/1/NULL 检查。
- 后续结构、菜单和数据变更新增更高版本 `V`。日常不改写已执行的 PostgreSQL 文件，已有库升级所需的增量继续保留；本次种子分类拆分使用下述显式登记转换。

新库先用独立 Flyway 入口迁移，再启动应用；应用启动不自动建表或导入种子。公共种子已按用途从原 `V20261110_02` 拆为 `.02.001` 至 `.02.011`；结构迁移 `.01` 未随种子拆分改写。已执行旧种子的库先按[一次性拆分登记流程](../docs/数据库初始化与验证.md#已执行旧公共种子的库一次性拆分登记)转换历史，再使用日常迁移入口。

## 历史资料

旧库结构、数据及历史修正 SQL 不再保留在工作树中，原文件仅通过 Git 历史追溯；当前 SQL 只维护上述 PostgreSQL 分类迁移。

`reference/banks/` 等目录继续保存公共来源快照与许可，`reference/seed-split/` 保存种子拆分登记所需证据；这些不是重复的旧库执行脚本。生成器只输出待审阅草稿，不覆盖已执行迁移。后续 SQL 按同类合并、异类分开维护，并遵守 Flyway 版本不可改写的约定。
