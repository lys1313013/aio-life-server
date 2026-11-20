# 数据库脚本入口

仅支持 PostgreSQL 18+。执行方式见[数据库初始化与验证](../docs/数据库初始化与验证.md)，编写规则见[SQL 脚本规范](../docs/SQL脚本规范.md)。

## 当前执行链

[建表脚本](migrations/V20261110_01__postgresql_schema.sql)按业务模块定义截至 `20261120.01` 的 74 张表及其约束、索引和注释。新库按 Flyway 版本依次执行建表和以下公共种子。

| 种子 | 版本 |
| --- | --- |
| CBTI 人格 | [20261110.02.001](migrations/V20261110_02_001__cbti_personalities.sql) |
| 数据导出协调记录 | [20261110.02.002](migrations/V20261110_02_002__data_export_coordinator.sql) |
| 荣誉分类 | [20261110.02.003](migrations/V20261110_02_003__honor_categories.sql) |
| 会员平台 | [20261110.02.004](migrations/V20261110_02_004__membership_providers.sql) |
| 银行字典 | [20261110.02.005](migrations/V20261110_02_005__bank_dictionary.sql) |
| 系统菜单 | [20261110.02.006](migrations/V20261110_02_006__menus.sql) |
| 工作日历 | [20261110.02.007](migrations/V20261110_02_007__work_calendar_2026.sql) |
| 系统配置 | [20261110.02.008](migrations/V20261110_02_008__sys_config.sql) |
| 行政区划 | [20261110.02.009](migrations/V20261110_02_009__regions.sql) |
| 公共支付方式 | [20261110.02.010](migrations/V20261110_02_010__payment_methods.sql) |
| 衣橱分类 | [20261110.02.011](migrations/V20261110_02_011__wardrobe_categories.sql) |
| 数据库图谱关系 | [20261118.12](migrations/V20261118_12__query_schema_relation.sql) |
| 豆瓣电影和读书 Top 250 | [20261118.14](migrations/V20261118_14__douban_top250.sql) |
| 高校目录 | [20261119.04](migrations/V20261119_04__seed_universities.sql) |

已有数据库按[合并前版本升级与登记](../docs/数据库初始化与验证.md#已有库升级与登记)执行。后续增量使用高于 `20261120.01` 的新版本。

## 公共来源与后续维护

`reference/banks/` 等目录保存公共来源快照与许可，不是可执行脚本。生成器只输出待审阅草稿，不覆盖已执行迁移。后续 SQL 按同类合并、异类分开维护，并遵守 Flyway 版本不可改写的约定。

## 高校目录

[高校目录种子](migrations/V20261119_04__seed_universities.sql) 导入教育部《全国高等学校名单》截至 2026-06-17 的完整中国大陆名录，共 3,196 所。教育部于 2026-06-18 发布名单，范围覆盖普通高校 2,952 所（本科 1,412 所、高职专科 1,540 所）及成人高校 244 所。[教育部公告](https://www.moe.gov.cn/jyb_xxgk/s5743/s5744/202606/t20260618_1441074.html)

| 原始附件 | SHA-256 |
| --- | --- |
| [全国普通高等学校名单 XLS](https://hudong.moe.gov.cn/jyb_xxgk/s5743/s5744/202606/W020260618416094865984.xls) | `29c40f083b639888e429cf40b68f9a75782d1ce81131c99aabca65b65b36eaea` |
| [全国成人高等学校名单 XLS](https://hudong.moe.gov.cn/jyb_xxgk/s5743/s5744/202606/W020260618307096079727.xls) | `34765edba0ceda935ca7d4455bc2eb105b94eee8d8d4d7c9150fba327cf11bbf` |

种子保留附件中的学校名称及备注，按附件的省级分组名称匹配 `sys_region` 的省级编码，覆盖 31 个省级地区。种子 ID 按两份附件的行序预分配，后续更新名单时应保留现有高校 ID。迁移使用仓库内固定数据，可离线执行，若有省级编码无法关联则整份种子迁移回滚。

## 豆瓣榜单

[豆瓣榜单种子](migrations/V20261118_14__douban_top250.sql)各导入 250 个电影和读书条目。观影和阅读的外部系统编码及条目 ID 由当前表生成列从 URL 提取。普通页面只读本地榜单和图片缓存。

在后端仓库根目录运行以下命令，路径参数使用下一个尚未占用的 Flyway 版本。Python 环境需安装 `beautifulsoup4`。生成器只读取官方榜单分页，校验成功后创建新文件。审阅名次变化后按项目的独立 Flyway 入口执行迁移。

```bash
python3 sql/reference/refresh_douban_rankings.py sql/migrations/VYYYYMMDD_NN__refresh_douban_rankings.sql
```
