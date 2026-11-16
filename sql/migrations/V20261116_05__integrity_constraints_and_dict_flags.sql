-- 目的：补齐通知投递、采集来源和用户菜单关联的唯一约束，把已有默认值却允许 NULL 的字段改为必填，并把字典默认/只读标记统一为 0/1 smallint。
-- 前置：已执行 V20261110_01 及之后全部增量；目标 PostgreSQL 18+。
-- 影响：只清理重复行和回填空值为列默认值；设备、衣物重复采集记录保留用户维护的内容，仅清空采集来源字段；不改业务字段的其他取值。
-- 重跑：Flyway 只执行一次；失败时本文件全部 DDL 与数据修正在同一事务回滚，修复后可对同一版本重新执行。
-- 执行：通过 mvn -f tools/flyway/pom.xml compile flyway:migrate 执行，遇错停止。

-- 1. 通知投递：同一用户、渠道和去重键只允许一条有效记录。
--    历史重复只可能来自并发入队，保留最早创建的一条，其余标记为已删除，不再重试。
UPDATE "notification_delivery" AS d
   SET "is_deleted" = 1,
       "update_time" = CURRENT_TIMESTAMP
 WHERE "is_deleted" = 0
   AND "id" IN (
     SELECT "id" FROM (
       SELECT "id",
              ROW_NUMBER() OVER (PARTITION BY "user_id", "channel", "dedup_key" ORDER BY "create_time", "id") AS rn
         FROM "notification_delivery"
        WHERE "is_deleted" = 0
     ) ranked
      WHERE ranked.rn > 1
   );
CREATE UNIQUE INDEX "uk_notification_delivery_user_channel_dedup"
    ON "notification_delivery" ("user_id", "channel", "dedup_key")
 WHERE "is_deleted" = 0;
COMMENT ON INDEX "uk_notification_delivery_user_channel_dedup" IS '同一用户同一渠道同一去重键只能有一条有效投递记录';

-- 2. 采集来源：同一用户同一来源件只能对应一条有效设备或衣物记录。
--    历史重复记录保留最早一条的来源定位，其余清空采集字段并按手工记录继续保留，避免丢失用户维护的内容。
UPDATE "device" AS d
   SET "source_platform" = NULL,
       "source_order_id" = NULL,
       "source_product_id" = NULL,
       "source_item_id" = NULL,
       "source_unit_index" = NULL
 WHERE "is_deleted" = 0
   AND "source_platform" IS NOT NULL
   AND "source_order_id" IS NOT NULL
   AND "source_item_id" IS NOT NULL
   AND "source_unit_index" IS NOT NULL
   AND "id" IN (
     SELECT "id" FROM (
       SELECT "id",
              ROW_NUMBER() OVER (
                PARTITION BY "user_id", "source_platform", "source_order_id", "source_item_id", "source_unit_index"
                ORDER BY "create_time", "id") AS rn
         FROM "device"
        WHERE "is_deleted" = 0
          AND "source_platform" IS NOT NULL
          AND "source_order_id" IS NOT NULL
          AND "source_item_id" IS NOT NULL
          AND "source_unit_index" IS NOT NULL
     ) ranked
      WHERE ranked.rn > 1
   );
CREATE UNIQUE INDEX "uk_device_user_source_unit"
    ON "device" ("user_id", "source_platform", "source_order_id", "source_item_id", "source_unit_index")
 WHERE "is_deleted" = 0
   AND "source_platform" IS NOT NULL
   AND "source_order_id" IS NOT NULL
   AND "source_item_id" IS NOT NULL
   AND "source_unit_index" IS NOT NULL;
COMMENT ON INDEX "uk_device_user_source_unit" IS '同一用户同一来源件只能有一条有效设备记录，手工录入和无完整来源的记录不参与比较';

UPDATE "wardrobe_item" AS w
   SET "source_platform" = NULL,
       "source_order_id" = NULL,
       "source_product_id" = NULL,
       "source_item_id" = NULL,
       "source_unit_index" = NULL
 WHERE "is_deleted" = 0
   AND "source_platform" IS NOT NULL
   AND "source_order_id" IS NOT NULL
   AND "source_item_id" IS NOT NULL
   AND "source_unit_index" IS NOT NULL
   AND "id" IN (
     SELECT "id" FROM (
       SELECT "id",
              ROW_NUMBER() OVER (
                PARTITION BY "user_id", "source_platform", "source_order_id", "source_item_id", "source_unit_index"
                ORDER BY "create_time", "id") AS rn
         FROM "wardrobe_item"
        WHERE "is_deleted" = 0
          AND "source_platform" IS NOT NULL
          AND "source_order_id" IS NOT NULL
          AND "source_item_id" IS NOT NULL
          AND "source_unit_index" IS NOT NULL
     ) ranked
      WHERE ranked.rn > 1
   );
CREATE UNIQUE INDEX "uk_wardrobe_item_user_source_unit"
    ON "wardrobe_item" ("user_id", "source_platform", "source_order_id", "source_item_id", "source_unit_index")
 WHERE "is_deleted" = 0
   AND "source_platform" IS NOT NULL
   AND "source_order_id" IS NOT NULL
   AND "source_item_id" IS NOT NULL
   AND "source_unit_index" IS NOT NULL;
COMMENT ON INDEX "uk_wardrobe_item_user_source_unit" IS '同一用户同一来源件只能有一条有效衣物记录，手工录入和无完整来源的记录不参与比较';

-- 3. 存储对象：唯一键只约束有效对象，软删除后同一份字节可以重新入库。
DROP INDEX IF EXISTS "storage_object_uk_storage_hash";
CREATE UNIQUE INDEX "storage_object_uk_storage_hash"
    ON "storage_object" ("bucket", "sha256")
 WHERE "is_deleted" = 0;
COMMENT ON INDEX "storage_object_uk_storage_hash" IS '同一桶内有效对象的字节摘要唯一，已删除对象不占用';
DROP INDEX IF EXISTS "storage_object_uk_storage_key";
CREATE UNIQUE INDEX "storage_object_uk_storage_key"
    ON "storage_object" ("bucket", "object_key")
 WHERE "is_deleted" = 0;
COMMENT ON INDEX "storage_object_uk_storage_key" IS '同一桶内有效对象的键唯一，已删除对象不占用';

-- 4. 行政区划：明确目录只保留一版统计区划快照，地区编码在当前库内唯一。
COMMENT ON TABLE "sys_region" IS '省市区县乡镇街道基础目录，当前库只保留一版统计区划快照';
COMMENT ON COLUMN "sys_region"."name" IS '地区名称，取自当前保留版本的原始名称';
COMMENT ON COLUMN "sys_region"."data_version" IS '来源版本，NBS-2023表示2023年统计区划快照，不表示当前实时区划；同一地区编码同时只保留一版';
COMMENT ON INDEX "sys_region_uk_sys_region_code" IS '当前库内有效地区编码唯一，已删除记录不占用；切换快照版本需先替换旧版本数据';

-- 5. 用户快捷导航：配置表按“物理删除后重建”维护，同一用户同一菜单只能有一行。
--    历史重复保留有效行和最早一行，其余物理删除。
DELETE FROM "user_quick_nav" q
 WHERE q."id" IN (
   SELECT "id" FROM (
     SELECT "id",
            ROW_NUMBER() OVER (PARTITION BY "user_id", "menu_id" ORDER BY "is_deleted", "id") AS rn
       FROM "user_quick_nav"
   ) ranked
    WHERE ranked.rn > 1
 );
CREATE UNIQUE INDEX "uk_user_quick_nav_user_menu" ON "user_quick_nav" ("user_id", "menu_id");
COMMENT ON INDEX "uk_user_quick_nav_user_menu" IS '同一用户同一菜单只能有一行快捷导航配置';

-- 6. 用户二级锁菜单：同一用户同一菜单只允许一条有效关联。
UPDATE "user_secondary_lock_menu" AS m
   SET "is_deleted" = 1,
       "update_time" = CURRENT_TIMESTAMP
 WHERE "is_deleted" = 0
   AND "id" IN (
     SELECT "id" FROM (
       SELECT "id",
              ROW_NUMBER() OVER (PARTITION BY "user_id", "menu_id" ORDER BY "create_time", "id") AS rn
         FROM "user_secondary_lock_menu"
        WHERE "is_deleted" = 0
     ) ranked
      WHERE ranked.rn > 1
   );
CREATE UNIQUE INDEX "uk_user_secondary_lock_menu_user_menu"
    ON "user_secondary_lock_menu" ("user_id", "menu_id")
 WHERE "is_deleted" = 0;
COMMENT ON INDEX "uk_user_secondary_lock_menu_user_menu" IS '同一用户同一菜单只能有一条有效二级锁关联';

-- 7. 字典标记统一为 0/1 smallint，与全库状态列一致；空值按默认值回填后改为必填。
ALTER TABLE "sys_dict_data"
    ALTER COLUMN "is_default" DROP DEFAULT,
    ALTER COLUMN "is_default" TYPE smallint USING (CASE WHEN "is_default" = 'Y' THEN 1 ELSE 0 END),
    ALTER COLUMN "is_default" SET DEFAULT 0,
    ALTER COLUMN "is_default" SET NOT NULL;
COMMENT ON COLUMN "sys_dict_data"."is_default" IS '是否默认值：1=是，0=否';

ALTER TABLE "user_dict_data"
    ALTER COLUMN "is_default" DROP DEFAULT,
    ALTER COLUMN "is_default" TYPE smallint USING (CASE WHEN "is_default" = 'Y' THEN 1 ELSE 0 END),
    ALTER COLUMN "is_default" SET DEFAULT 0,
    ALTER COLUMN "is_default" SET NOT NULL,
    ALTER COLUMN "is_readonly" DROP DEFAULT,
    ALTER COLUMN "is_readonly" TYPE smallint USING (CASE WHEN "is_readonly" = 'Y' THEN 1 ELSE 0 END),
    ALTER COLUMN "is_readonly" SET DEFAULT 0,
    ALTER COLUMN "is_readonly" SET NOT NULL;
COMMENT ON COLUMN "user_dict_data"."is_default" IS '是否默认值：1=是，0=否';
COMMENT ON COLUMN "user_dict_data"."is_readonly" IS '是否只读：1=是，0=否；只读项用户仅可修改启用状态';

-- 8. 有默认值的状态和排序列补齐必填约束，避免显式 NULL 产生“未设置”第三态。
UPDATE "honor_record" SET "is_top" = 0 WHERE "is_top" IS NULL;
UPDATE "honor_record" SET "is_public" = 1 WHERE "is_public" IS NULL;
UPDATE "honor_record" SET "sort_order" = 0 WHERE "sort_order" IS NULL;
ALTER TABLE "honor_record"
    ALTER COLUMN "is_top" SET NOT NULL,
    ALTER COLUMN "is_public" SET NOT NULL,
    ALTER COLUMN "sort_order" SET NOT NULL;

UPDATE "membership_record" SET "auto_renew" = 0 WHERE "auto_renew" IS NULL;
ALTER TABLE "membership_record"
    ALTER COLUMN "auto_renew" SET NOT NULL;

UPDATE "memo" SET "hidden_content" = 0 WHERE "hidden_content" IS NULL;
ALTER TABLE "memo"
    ALTER COLUMN "hidden_content" SET NOT NULL;

UPDATE "thought" SET "hidden_content" = 0 WHERE "hidden_content" IS NULL;
ALTER TABLE "thought"
    ALTER COLUMN "hidden_content" SET NOT NULL;

UPDATE "movie" SET "status" = 'not_started' WHERE "status" IS NULL;
UPDATE "movie" SET "total_progress" = 0 WHERE "total_progress" IS NULL;
UPDATE "movie" SET "current_progress" = 0 WHERE "current_progress" IS NULL;
ALTER TABLE "movie"
    ALTER COLUMN "status" SET NOT NULL,
    ALTER COLUMN "total_progress" SET NOT NULL,
    ALTER COLUMN "current_progress" SET NOT NULL;

UPDATE "read_record" SET "status" = 'not_started' WHERE "status" IS NULL;
UPDATE "read_record" SET "total_progress" = 0 WHERE "total_progress" IS NULL;
UPDATE "read_record" SET "current_progress" = 0 WHERE "current_progress" IS NULL;
ALTER TABLE "read_record"
    ALTER COLUMN "status" SET NOT NULL,
    ALTER COLUMN "total_progress" SET NOT NULL,
    ALTER COLUMN "current_progress" SET NOT NULL;

UPDATE "goal" SET "status" = 'not_started' WHERE "status" IS NULL;
ALTER TABLE "goal"
    ALTER COLUMN "status" SET NOT NULL;

UPDATE "honor_category" SET "sort_order" = 0 WHERE "sort_order" IS NULL;
ALTER TABLE "honor_category"
    ALTER COLUMN "sort_order" SET NOT NULL;

UPDATE "wardrobe_category" SET "sort_order" = 0 WHERE "sort_order" IS NULL;
UPDATE "wardrobe_category" SET "category_type" = 0 WHERE "category_type" IS NULL;
ALTER TABLE "wardrobe_category"
    ALTER COLUMN "sort_order" SET NOT NULL,
    ALTER COLUMN "category_type" SET NOT NULL;

UPDATE "user_dict_data" SET "sort_order" = 0 WHERE "sort_order" IS NULL;
ALTER TABLE "user_dict_data"
    ALTER COLUMN "sort_order" SET NOT NULL;

UPDATE "sys_dict_data" SET "sort_order" = 0 WHERE "sort_order" IS NULL;
ALTER TABLE "sys_dict_data"
    ALTER COLUMN "sort_order" SET NOT NULL;

UPDATE "task" SET "sort_order" = 0 WHERE "sort_order" IS NULL;
ALTER TABLE "task"
    ALTER COLUMN "sort_order" SET DEFAULT 0,
    ALTER COLUMN "sort_order" SET NOT NULL;
