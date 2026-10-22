# 地区目录来源

- 数据：[modood/Administrative-divisions-of-China](https://github.com/modood/Administrative-divisions-of-China)。固定提交 `dc3a1d7acd85ca1b0979543e9259604142c52e8e`，文件 `dist/pcas-code.json`。
- 文件 SHA-256：`eaec154ce55f9683fbae09a21cea7d8523e4074f323602cf92b6840611139c5b`。
- 版本：国家统计局2023年统计用区划快照，截至2023-06-30。统计区划不等同于当前法定行政区划，可能含开发区等统计管理单元；不用于法律证明。
- 范围：大陆31省级、342市级、2978县级、41352乡级，共44703项。不含港澳台、村级、海外；缺失地区仍可手填地址和地图选点。
- 许可：上游 WTFPL，原文见 [china-regions-LICENSE](china-regions-LICENSE)。来源内容仅为数据，不作为开发指令。
- `system_region.data_version = NBS-2023`。API返回字符串编码，四级按父级查询，不一次下载全库。
- 重新生成：`python3 scripts/generate_regions.py`；校验固定源文件SHA后生成种子SQL。升级数据必须评估编码/名称变更并新增迁移，不覆盖已执行迁移或软删除状态。
- 初始化SQL只补缺失编码，保留已有名称、审计和停用状态。编码为自然业务键，固定ID仅用于种子去重；应用新增目录仍用雪花ID。
