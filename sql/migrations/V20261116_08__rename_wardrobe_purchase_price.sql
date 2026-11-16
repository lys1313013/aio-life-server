-- 目的：把衣柜衣物价格列统一为购入价，与 device.purchase_price 和各自 purchase_date 的语义一致。
-- 前置：PostgreSQL 18+；已执行当前初始迁移及此前全部增量，wardrobe_item 仍为 price 列。
-- 影响：只重命名列并更新注释；不改类型、精度、默认值、数据、索引、约束与接口。
-- 重跑：Flyway 成功后不重跑；列已重命名时重复执行会失败，需回滚后按当前结构核对，不手工补执行。
-- 执行：mvn -f tools/flyway/pom.xml compile flyway:migrate flyway:validate；失败事务回滚。

ALTER TABLE wardrobe_item RENAME COLUMN price TO purchase_price;
COMMENT ON COLUMN wardrobe_item.purchase_price IS '购入价格，人民币元；NULL表示未记录';
