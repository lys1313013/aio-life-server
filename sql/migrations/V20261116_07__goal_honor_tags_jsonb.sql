-- 目的：把 goal.tags 与 honor_record.tags 由存 JSON 文本的 varchar 改为 jsonb，与 milestone.tags 保持一致，写入时由数据库校验 JSON 合法性。
-- 前置：已执行 V20261110_01 及之后全部增量；目标 PostgreSQL 18+。
-- 影响：只改两列类型与注释。历史值按 JSON 解析：null 与空串保持无标签，合法数组原样保留，其余旧文本（逗号分隔或单标签）包成单元素数组，不丢失标签文字。
-- 重跑：Flyway 只执行一次；失败时本文件全部 DDL 在同一事务回滚，修复后可对同一版本重新执行。
-- 执行：通过 mvn -f tools/flyway/pom.xml compile flyway:migrate 执行，遇错停止。

ALTER TABLE "goal"
    ALTER COLUMN "tags" TYPE jsonb
    USING (
        CASE
            WHEN "tags" IS NULL OR btrim("tags") = '' THEN NULL
            WHEN pg_input_is_valid("tags", 'jsonb') THEN
                CASE
                    WHEN jsonb_typeof("tags"::jsonb) = 'array' THEN "tags"::jsonb
                    ELSE jsonb_build_array("tags"::jsonb)
                END
            ELSE jsonb_build_array(btrim("tags"))
        END
    );

ALTER TABLE "honor_record"
    ALTER COLUMN "tags" TYPE jsonb
    USING (
        CASE
            WHEN "tags" IS NULL OR btrim("tags") = '' THEN NULL
            WHEN pg_input_is_valid("tags", 'jsonb') THEN
                CASE
                    WHEN jsonb_typeof("tags"::jsonb) = 'array' THEN "tags"::jsonb
                    ELSE jsonb_build_array("tags"::jsonb)
                END
            ELSE jsonb_build_array(btrim("tags"))
        END
    );

COMMENT ON COLUMN "goal"."tags" IS '目标标签数组';
COMMENT ON COLUMN "honor_record"."tags" IS '标签数组';
