-- 目的：把 wardrobe_item.season 由逗号拼接的 varchar(20) 改为 jsonb 字符串数组，接口按数组收发，并由数据库约束取值。
-- 前置：PostgreSQL 18+；已执行 V20261117_06；wardrobe_item.season 仍为 varchar(20)，内容为春/夏/秋/冬的逗号拼接值。
-- 影响：只改一列类型、注释并新增 CHECK。历史值按逗号拆分，去掉空格与空项并保持顺序，空串转 NULL；雪花 ID、索引、唯一约束和审计字段不变。
-- 重跑：Flyway 只执行一次；全部 DDL 在同一事务执行，失败整体回滚，修正后对同一版本重新执行。
-- 执行：mvn -f tools/flyway/pom.xml compile flyway:migrate flyway:validate；遇错停止。

ALTER TABLE wardrobe_item
    ALTER COLUMN season TYPE jsonb
    USING (
        CASE
            WHEN season IS NULL OR btrim(season) = '' THEN NULL
            ELSE NULLIF(
                to_jsonb(array_remove(string_to_array(replace(btrim(season), ' ', ''), ','), '')),
                '[]'::jsonb
            )
        END
    );
COMMENT ON COLUMN wardrobe_item.season IS '适用季节数组，元素为春/夏/秋/冬；NULL表示未填写';

-- 转换后先确认已有元素，存在非法季节时整体失败，不静默丢弃数据。
DO $$
DECLARE
    invalid_count bigint;
BEGIN
    SELECT count(*) INTO invalid_count
      FROM wardrobe_item
     WHERE season IS NOT NULL
       AND EXISTS (
           SELECT 1
             FROM jsonb_array_elements_text(season) AS element
            WHERE element NOT IN ('春', '夏', '秋', '冬')
       );
    IF invalid_count > 0 THEN
        RAISE EXCEPTION 'wardrobe_item.season 有 % 条记录包含春/夏/秋/冬以外的季节值，请先人工核对后再迁移', invalid_count;
    END IF;
END $$;

ALTER TABLE wardrobe_item ADD CONSTRAINT ck_wardrobe_item_season CHECK (
    season IS NULL
    OR (
        jsonb_typeof(season) = 'array'
        AND jsonb_array_length(
            CASE WHEN jsonb_typeof(season) = 'array' THEN season ELSE '[]'::jsonb END
        ) BETWEEN 1 AND 4
        AND season <@ '["春","夏","秋","冬"]'::jsonb
    )
);
COMMENT ON CONSTRAINT ck_wardrobe_item_season ON wardrobe_item IS '适用季节只允许春/夏/秋/冬组成的非空数组，最多四项';
