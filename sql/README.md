# 数据库脚本入口

仅支持 PostgreSQL 18+。执行方式见[数据库初始化与验证](../docs/数据库初始化与验证.md)，编写规则见[SQL 脚本规范](../docs/SQL脚本规范.md)。

## 当前执行链

- [V20261110_01__postgresql_schema.sql](migrations/V20261110_01__postgresql_schema.sql)：完整结构，包含 68 张业务表、索引、约束和中文注释。

> 2026-11-13 一次性调整：把 `time_record.id`、`exercise_record.time_id` 从 `varchar(32)` 改为 `bigint`（与时迹雪花 ID 和其他业务主键一致）。已有库先重编非数字 ID 并同步 `exercise_record`，再执行 `ALTER COLUMN ... TYPE bigint`，随后 `flyway repair` 同步基线校验和；新库直接按本文件创建。
- [V20261110_02_001__cbti_personalities.sql](migrations/V20261110_02_001__cbti_personalities.sql)：CBTI 人格定义。
- [V20261110_02_002__data_export_coordinator.sql](migrations/V20261110_02_002__data_export_coordinator.sql)：数据导出协调记录。
- [V20261110_02_003__honor_categories.sql](migrations/V20261110_02_003__honor_categories.sql)：荣誉分类。
- [V20261110_02_004__membership_providers.sql](migrations/V20261110_02_004__membership_providers.sql)：会员平台。
- [V20261110_02_005__bank_dictionary.sql](migrations/V20261110_02_005__bank_dictionary.sql)：银行字典。
- [V20261110_02_006__menus.sql](migrations/V20261110_02_006__menus.sql)：系统菜单，含 Web 查询工作台与数据模型入口，移动端不启用。
- [V20261110_02_007__work_calendar_2026.sql](migrations/V20261110_02_007__work_calendar_2026.sql)：2026 年工作日历。
- [V20261110_02_008__sys_config.sql](migrations/V20261110_02_008__sys_config.sql)：系统配置。
- [V20261110_02_009__regions.sql](migrations/V20261110_02_009__regions.sql)：行政区划。
- [V20261110_02_010__payment_methods.sql](migrations/V20261110_02_010__payment_methods.sql)：公共支付方式。
- [V20261110_02_011__wardrobe_categories.sql](migrations/V20261110_02_011__wardrobe_categories.sql)：衣柜分类。
- [V20261112_13__align_check_constraint_expressions.sql](migrations/V20261112_13__align_check_constraint_expressions.sql)：把已有库的 7 个 CHECK 约束表达式对齐到基线写法，语义不变。

- [V20261112_14__drop_time_category_time_type.sql](migrations/V20261112_14__drop_time_category_time_type.sql)：删除时迹分类时间类型，保留分类、覆盖、层级与时迹记录。

- [V20261112_15__relocate_category_menus.sql](migrations/V20261112_15__relocate_category_menus.sql)：个人分类归入个人中心分类管理，管理员时迹分类移至系统管理，保留菜单 ID 和页面路径。
- [V20261112_16__menu_visible_clients.sql](migrations/V20261112_16__menu_visible_clients.sql)：将 Web、移动端启用列合并为展示端位掩码并删除旧列，保留各端设置，时迹取消移动端菜单重复入口。

- [V20261113_01__record_images_use_file_binding.sql](migrations/V20261113_01__record_images_use_file_binding.sql)：迁移设备、观影、阅读、衣物图片至 `file.biz_type/biz_id`，删除四表 `file_id`，保留文件和存储对象并约束单图唯一。

- [V20261115_01__user_avatar_use_file_binding.sql](migrations/V20261115_01__user_avatar_use_file_binding.sql)：迁移用户头像至 `file.biz_type='avatar'`、`file.biz_id=sys_user.id`，删除 `sys_user.avatar_file_id` 及索引，保留文件和存储对象并约束每用户单头像。

- [V20261115_02__memo_favorite.sql](migrations/V20261115_02__memo_favorite.sql)：笔记增加收藏标记，已有笔记默认未收藏，不改正文及内容更新时间。

- [V20261116_01__goal_sleep_query_minute_fields.sql](migrations/V20261116_01__goal_sleep_query_minute_fields.sql)：将已保存的旧标准休息天数查询改为 `startMinute/endMinute`，保留变量和进度快照，递增修订号以拒绝在途旧结果；不修改其他自定义查询。

- [V20261116_03__password_vault_username_comment.sql](migrations/V20261116_03__password_vault_username_comment.sql)：把密码库账号明确为明文存储，仅更新表和列注释，不改结构、数据与回填。

- [V20261116_04__drop_sys_dict_data_dict_type.sql](migrations/V20261116_04__drop_sys_dict_data_dict_type.sql)：删除字典项中重复保存的所属类型列，类型统一由 `dict_id` 关联 `sys_dict_type` 得出（该关联列后续在 `V20261118_01` 更名为 `dict_type_id`），保留字典项与关联。

- [V20261116_05__integrity_constraints_and_dict_flags.sql](migrations/V20261116_05__integrity_constraints_and_dict_flags.sql)：补齐通知投递、设备与衣物采集来源、用户快捷导航和二级锁菜单的唯一约束，存储对象唯一键改为只约束有效对象；把有默认值的状态、排序列改为必填；字典默认/只读标记由 `char(1)` 的 `Y/N` 改为 `smallint` 的 `1/0`。

- [V20261116_07__goal_honor_tags_jsonb.sql](migrations/V20261116_07__goal_honor_tags_jsonb.sql)：把 `goal.tags`、`honor_record.tags` 由 `varchar` 改为 `jsonb`，与 `milestone.tags` 一致；历史空串转 NULL，合法数组原样保留，其余旧文本包成单元素数组。

- [V20261116_08__rename_wardrobe_purchase_price.sql](migrations/V20261116_08__rename_wardrobe_purchase_price.sql)：把衣柜衣物 `price` 更名为 `purchase_price`，与设备购入价及接口 `purchasePrice` 统一，只改列名与注释。

- [V20261116_09__unify_preset_owner.sql](migrations/V20261116_09__unify_preset_owner.sql)：公共预设统一用 `user_id IS NULL` 表达，把时迹分类与用户字典的 0 号预设改为 NULL，新增归属检查与预设索引；衣柜分类删除冗余的 `category_type`。

- [V20261116_12__attachment_file_key.sql](migrations/V20261116_12__attachment_file_key.sql)：为全部附件新增并回填原生 UUIDv4 `file_key`，设为非空且全局唯一；内部附件主键及导入结果引用仍为 BIGINT，配套应用不接受数字附件标识。

- [V20261117_01__event_record_event_date.sql](migrations/V20261117_01__event_record_event_date.sql)：把 `event_record.event_date` 由 `timestamp(0)` 改为 `date`，与实体 `LocalDate`、接口 `yyyy-MM-dd` 及其他日期列一致；已有行带时分秒时先拒绝执行，确认可丢弃后手工截断再迁移。

- [V20261117_02__integrity_hardening.sql](migrations/V20261117_02__integrity_hardening.sql)：为全部 0/1 状态列补齐 CHECK，新增 `expense` 交易号唯一约束，清理附件表 3 个冗余或失效唯一索引并统一两个索引名；`sys_user.email`、`mail_log.send_to` 放宽到 254，`mbti_result.raw_result` 改为 `jsonb`，五个整数状态列收窄为 `smallint`。存在非 0/1 状态或重复交易号时整体拒绝执行。`user_dict_data` 唯一约束因空 `dict_value` 和现存重复值暂缓。

- [V20261117_03__bcrypt_password_hash.sql](migrations/V20261117_03__bcrypt_password_hash.sql)：登录密码和二级密码统一使用 bcrypt，删除两个独立盐列并更新哈希列注释；不转换旧 MD5 密码，已有账号需重新设密。

- [V20261117_04__rename_finance_amount_columns.sql](migrations/V20261117_04__rename_finance_amount_columns.sql)：把 `expense.amt`、`expense.transaction_amt`、`income.amt`、`income.tax` 分别更名为 `bookkeeping_amount`、`transaction_amount`、`gross_amount`、`tax_amount`，只改列名和注释，区分记账金额、原始交易金额、税前收入与税额。

- [V20261117_09__unify_client_ip_columns.sql](migrations/V20261117_09__unify_client_ip_columns.sql)：把 `login_log`、`mail_log`、`sys_activity_log` 的 `ip_address` 更名为 `client_ip`，并把客户端与服务器 IP 列统一为 `varchar(45)`（IPv6 文本最大长度）；已有值超长时整体拒绝执行。

- [V20261117_10__rename_user_bind_table.sql](migrations/V20261117_10__rename_user_bind_table.sql)：把动词短语表名 `user_bind` 改为名词短语 `user_third_party_account`，同步重命名主键、检查约束和唯一索引，只改名称与注释，不改列、数据与接口行为。

- [V20261117_11__rename_wardrobe_module_name.sql](migrations/V20261117_11__rename_wardrobe_module_name.sql)：模块中文名由「衣柜」改为「衣橱」，同步菜单标题与表、索引注释；表名、字段名、接口与数据不变。

- [V20261117_12__message_sender_nullable.sql](migrations/V20261117_12__message_sender_nullable.sql)：允许 `message.sender_id` 为空并清空已有系统通知的发送人，新增 `ck_message_sender` 约束系统通知（type=0）无发送人、用户消息（type=1）必须有发送人。

- [V20261118_01__unify_dict_key_names.sql](migrations/V20261118_01__unify_dict_key_names.sql)：把 `sys_dict_type.dict_id` 更名为 `id`，`sys_dict_data.dict_code` 更名为 `id`、`dict_id` 更名为 `dict_type_id`，并同步重命名唯一索引；只改名称与注释，不改类型、数据、关联取值和接口业务语义。

- [V20261118_02__rename_exercise_record_time_record_id.sql](migrations/V20261118_02__rename_exercise_record_time_record_id.sql)：把 `exercise_record.time_id` 更名为 `time_record_id`，关联列统一使用被引用对象全名，接口字段同步为 `timeRecordId`；只改列名与注释，不改类型、数据、索引和约束。

- [V20261118_03__rename_finance_columns.sql](migrations/V20261118_03__rename_finance_columns.sql)：把收支列 `expense.exp_type_id`、`exp_time`、`exp_desc` 和 `income.inc_type_id` 更名为 `expense_type_id`、`expense_time`、`expense_desc`、`income_type_id`，接口字段同步为 `expenseTypeId`、`expenseTime`、`expenseDesc`、`incomeTypeId`；只改列名与注释，不改类型、数据、索引和约束。
- [V20261118_04__parent_root_null.sql](migrations/V20261118_04__parent_root_null.sql)：把 `sys_menu`、`wardrobe_category`、`sys_region` 的根节点统一为 NULL，时迹分类新增 `inherit_parent` 区分继承与根，并补充自引用和层级一致性约束。
- [V20261118_05__drop_scheduled_job_zone_id.sql](migrations/V20261118_05__drop_scheduled_job_zone_id.sql)：删除 `sys_scheduled_job.zone_id`，调度时区固定为 `Asia/Shanghai` 并只由代码常量维护；保留任务配置、cron、启用状态和执行日志。
- [V20261118_06__time_tracker_category_inherit_smallint.sql](migrations/V20261118_06__time_tracker_category_inherit_smallint.sql)：把 `time_tracker_category.inherit_parent` 由 `boolean` 改为 `smallint` 的 0/1，与全局 `NumericBooleanTypeHandler` 和其他布尔状态列一致；按新类型重建继承约束，数据语义与行数不变。
- [V20261118_07__drop_cbti_personality_image_object.sql](migrations/V20261118_07__drop_cbti_personality_image_object.sql)：删除 `cbti_personality.image_object`，人格图片统一由 `attachment` 的 `cbti_personality_image` 关联表达；只删除已无读写方的历史对象路径，不改行数、其他列和索引。

本次按明确要求同步修改历史初始脚本：新库不再创建上述四列；已有库仍保留可执行的迁移。另按明确要求不为 B 站视频封面新增增量脚本：`b_video.cover_file_id` 已直接在目标库迁移并从初始脚本移除，新库改由 `uk_file_b_video_cover` 约束 `file.biz_type='b_video_cover'` 的单封面关联；执行与校验记录见[数据库初始化与验证](../docs/数据库初始化与验证.md)。历史校验和调整需先备份并完成实际数据、结构迁移，再执行 `flyway:repair`；`repair` 本身不迁移业务数据。

新库先用独立 Flyway 入口迁移，再启动应用；应用启动不自动建表或导入种子。后续结构、菜单和数据变更新增更高版本 `V`，不改写已执行文件。

## 公共来源与后续维护

`reference/banks/` 等目录保存公共来源快照与许可，不是可执行脚本。生成器只输出待审阅草稿，不覆盖已执行迁移。后续 SQL 按同类合并、异类分开维护，并遵守 Flyway 版本不可改写的约定。

## 附件四表切换

- [V20261116_10__prepare_attachment_objects.sql](migrations/V20261116_10__prepare_attachment_objects.sql)：已有库准备列与图片尺寸；补齐仍保留视频封面指针的升级路径。
- [V20261116_11__attachment_sources_and_jobs.sql](migrations/V20261116_11__attachment_sources_and_jobs.sql)：切换 `attachment/external_resource/import_job/storage_object`，成功来源持续复用，删除文件重复元数据及视频来源/状态/版本列。

已有库停写并先迁移到 `20261116.10`，执行 `tools/attachments/prepare.py --stopped-writes`，完成真实字节校验后再继续迁移。空库可直接 migrate；完整说明见[附件四表升级](../docs/数据库初始化与验证.md#附件四表升级)。本次未执行实际环境升级。
