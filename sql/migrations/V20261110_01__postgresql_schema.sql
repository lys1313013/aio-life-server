-- PostgreSQL 18 当前结构；新 PostgreSQL 库使用独立 Flyway 版本链。
-- 来源为冻结 MySQL 迁移在隔离库完整执行后的结构，不包含业务账号或业务数据。
-- 数字状态保持 smallint/integer；JSON 使用 jsonb，二进制使用 bytea，时间保持本地时间语义。
-- 索引名加表名前缀，避免 PostgreSQL schema 内重名；保留唯一键、检查、生成列和外键。
-- 文本列使用数据库默认排序规则（确定性、精确比较）；大小写不敏感只在查询层用 ILIKE 或 lower() 显式表达。

CREATE TABLE "anniversary_record" (
    "id" bigint NOT NULL,
    "user_id" bigint NOT NULL,
    "title" varchar(200) NOT NULL,
    "target_date" date NOT NULL,
    "type" varchar(20) NOT NULL,
    "remark" varchar(500),
    "color" varchar(50),
    "icon" varchar(20),
    "is_pinned" smallint DEFAULT '0' NOT NULL,
    "pinned_sort_order" integer DEFAULT '0' NOT NULL,
    "is_deleted" smallint DEFAULT '0' NOT NULL,
    "create_time" timestamp(0) without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "update_time" timestamp(0) without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "create_user" bigint,
    "update_user" bigint,
    PRIMARY KEY ("id")
 );
COMMENT ON TABLE "anniversary_record" IS '纪念日记录表';
COMMENT ON COLUMN "anniversary_record"."id" IS '记录ID';
COMMENT ON COLUMN "anniversary_record"."user_id" IS '用户ID';
COMMENT ON COLUMN "anniversary_record"."title" IS '标题';
COMMENT ON COLUMN "anniversary_record"."target_date" IS '目标日期';
COMMENT ON COLUMN "anniversary_record"."type" IS '类型：anniversary-纪念日(正数), countdown-倒数日(倒数)';
COMMENT ON COLUMN "anniversary_record"."remark" IS '备注，未填写时为空';
COMMENT ON COLUMN "anniversary_record"."color" IS '渐变色class';
COMMENT ON COLUMN "anniversary_record"."icon" IS 'Emoji图标';
COMMENT ON COLUMN "anniversary_record"."is_pinned" IS '是否固定到首页：0-否，1-是';
COMMENT ON COLUMN "anniversary_record"."pinned_sort_order" IS '首页固定排序，数值越小越靠前';
COMMENT ON COLUMN "anniversary_record"."is_deleted" IS '逻辑删除标记：0=未删除，1=已删除';
COMMENT ON COLUMN "anniversary_record"."create_time" IS '创建时间';
COMMENT ON COLUMN "anniversary_record"."update_time" IS '更新时间';
COMMENT ON COLUMN "anniversary_record"."create_user" IS '创建人ID';
COMMENT ON COLUMN "anniversary_record"."update_user" IS '更新人ID';
CREATE INDEX "anniversary_record_idx_home_pinned" ON "anniversary_record" ("user_id", "is_deleted", "is_pinned", "pinned_sort_order", "id");
COMMENT ON INDEX "anniversary_record_idx_home_pinned" IS '业务检索索引：idx_home_pinned';
CREATE INDEX "anniversary_record_idx_target_date" ON "anniversary_record" ("target_date");
COMMENT ON INDEX "anniversary_record_idx_target_date" IS '业务检索索引：idx_target_date';
CREATE INDEX "anniversary_record_idx_user_id" ON "anniversary_record" ("user_id");
COMMENT ON INDEX "anniversary_record_idx_user_id" IS '业务检索索引：idx_user_id';

CREATE TABLE "api_key" (
    "id" bigint NOT NULL,
    "user_id" bigint NOT NULL,
    "api_key" varchar(128) COLLATE "C" NOT NULL,
    "remark" varchar(255),
    "expired_at" timestamp(0) without time zone,
    "create_user" bigint,
    "create_time" timestamp(0) without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "update_user" bigint,
    "update_time" timestamp(0) without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "is_deleted" smallint DEFAULT '0' NOT NULL,
    PRIMARY KEY ("id")
 );
COMMENT ON TABLE "api_key" IS 'API Key 表';
COMMENT ON COLUMN "api_key"."id" IS '主键ID';
COMMENT ON COLUMN "api_key"."user_id" IS '用户ID';
COMMENT ON COLUMN "api_key"."api_key" IS 'API Key';
COMMENT ON COLUMN "api_key"."remark" IS '备注';
COMMENT ON COLUMN "api_key"."expired_at" IS '过期时间';
COMMENT ON COLUMN "api_key"."create_user" IS '创建人';
COMMENT ON COLUMN "api_key"."create_time" IS '创建时间';
COMMENT ON COLUMN "api_key"."update_user" IS '更新人';
COMMENT ON COLUMN "api_key"."update_time" IS '更新时间';
COMMENT ON COLUMN "api_key"."is_deleted" IS '是否删除';
CREATE INDEX "api_key_idx_user_id" ON "api_key" ("user_id");
COMMENT ON INDEX "api_key_idx_user_id" IS '业务检索索引：idx_user_id';

CREATE TABLE "api_key_log" (
    "id" bigint NOT NULL,
    "api_key_id" bigint NOT NULL,
    "request_path" varchar(255) NOT NULL,
    "request_method" varchar(10) NOT NULL,
    "response_status" integer,
    "client_ip" varchar(45),
    "create_time" timestamp(0) without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    PRIMARY KEY ("id")
 );
COMMENT ON TABLE "api_key_log" IS 'API Key 调用日志表';
COMMENT ON COLUMN "api_key_log"."id" IS '主键ID';
COMMENT ON COLUMN "api_key_log"."api_key_id" IS 'API Key ID';
COMMENT ON COLUMN "api_key_log"."request_path" IS '请求路径';
COMMENT ON COLUMN "api_key_log"."request_method" IS '请求方法';
COMMENT ON COLUMN "api_key_log"."response_status" IS '响应状态码';
COMMENT ON COLUMN "api_key_log"."client_ip" IS '客户端IP';
COMMENT ON COLUMN "api_key_log"."create_time" IS '记录时间';
CREATE INDEX "api_key_log_idx_api_key_id" ON "api_key_log" ("api_key_id");
COMMENT ON INDEX "api_key_log_idx_api_key_id" IS '业务检索索引：idx_api_key_id';

CREATE TABLE "b_video" (
    "cover_file_id" varchar(32),
    "cover_state" varchar(16) DEFAULT 'NONE' NOT NULL,
    "cover_version" bigint DEFAULT '0' NOT NULL,
    "id" bigint NOT NULL,
    "title" varchar(500) NOT NULL,
    "url" varchar(1000) COLLATE "C" NOT NULL,
    "cover" varchar(1000),
    "duration_seconds" integer NOT NULL,
    "watched_duration_seconds" integer DEFAULT '0' NOT NULL,
    "episodes" integer DEFAULT '1',
    "current_episode" integer DEFAULT '1',
    "status" varchar(32) DEFAULT 'in_progress' NOT NULL,
    "last_watched" timestamp(0) without time zone,
    "notes" text,
    "bvid" varchar(20) COLLATE "C",
    "aid" varchar(20),
    "description" text,
    "owner_name" varchar(100),
    "pages_info" jsonb,
    "user_id" bigint NOT NULL,
    "create_user" bigint,
    "create_time" timestamp(0) without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "update_user" bigint,
    "update_time" timestamp(0) without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "is_deleted" smallint DEFAULT '0' NOT NULL,
    PRIMARY KEY ("id")
 );
COMMENT ON TABLE "b_video" IS 'B 站视频记录表';
COMMENT ON COLUMN "b_video"."cover_file_id" IS '当前封面附件ID，关联file.id；尚无内部封面时为空';
COMMENT ON COLUMN "b_video"."cover_state" IS '封面状态：NONE=无来源，PENDING=导入中，READY=可用，FAILED=失败';
COMMENT ON COLUMN "b_video"."cover_version" IS '封面导入版本，从0开始，每次重新入队递增，防止旧任务覆盖新封面';
COMMENT ON COLUMN "b_video"."id" IS '主键ID';
COMMENT ON COLUMN "b_video"."title" IS '视频标题';
COMMENT ON COLUMN "b_video"."url" IS 'B站视频URL';
COMMENT ON COLUMN "b_video"."cover" IS '视频封面URL';
COMMENT ON COLUMN "b_video"."duration_seconds" IS '视频总时长（秒）';
COMMENT ON COLUMN "b_video"."watched_duration_seconds" IS '已观看时长（秒）';
COMMENT ON COLUMN "b_video"."episodes" IS '总集数';
COMMENT ON COLUMN "b_video"."current_episode" IS '当前观看集数';
COMMENT ON COLUMN "b_video"."status" IS '学习状态：not_started/in_progress/on_hold/completed';
COMMENT ON COLUMN "b_video"."last_watched" IS '最后观看时间（syncProgress 同步时写入）';
COMMENT ON COLUMN "b_video"."notes" IS '学习笔记';
COMMENT ON COLUMN "b_video"."bvid" IS 'BV号';
COMMENT ON COLUMN "b_video"."aid" IS 'AV号';
COMMENT ON COLUMN "b_video"."description" IS '视频描述';
COMMENT ON COLUMN "b_video"."owner_name" IS '视频UP主名称，未获取时为空';
COMMENT ON COLUMN "b_video"."pages_info" IS '分集信息：cid-分集ID, page-页码, part-分集标题, durationSeconds-分集时长（秒）';
COMMENT ON COLUMN "b_video"."user_id" IS '所属用户ID，用于用户数据隔离';
COMMENT ON COLUMN "b_video"."create_user" IS '创建人';
COMMENT ON COLUMN "b_video"."create_time" IS '创建时间';
COMMENT ON COLUMN "b_video"."update_user" IS '修改人';
COMMENT ON COLUMN "b_video"."update_time" IS '更新时间';
COMMENT ON COLUMN "b_video"."is_deleted" IS '逻辑删除标记：0=未删除，1=已删除';
CREATE INDEX "b_video_idx_aid" ON "b_video" ("aid");
COMMENT ON INDEX "b_video_idx_aid" IS '业务检索索引：idx_aid';
CREATE INDEX "b_video_idx_bvid" ON "b_video" ("bvid");
COMMENT ON INDEX "b_video_idx_bvid" IS '业务检索索引：idx_bvid';
CREATE INDEX "b_video_idx_status" ON "b_video" ("status");
COMMENT ON INDEX "b_video_idx_status" IS '业务检索索引：idx_status';
CREATE INDEX "b_video_idx_title" ON "b_video" ("title");
COMMENT ON INDEX "b_video_idx_title" IS '业务检索索引：idx_title';

CREATE TABLE "bank_card" (
    "id" bigint NOT NULL,
    "user_id" bigint NOT NULL,
    "bank_id" bigint,
    "custom_bank_name" varchar(100),
    "card_name" varchar(100),
    "alias" varchar(50),
    "card_type" varchar(20) NOT NULL,
    "card_no_ciphertext" varchar(512) COLLATE "C",
    "card_no_fingerprint" bytea,
    "card_no_last4" char(4),
    "branch_name" varchar(200),
    "status" varchar(20) DEFAULT 'normal' NOT NULL,
    "opened_date" date,
    "expiry_month" date,
    "balance" numeric(18,2),
    "credit_limit" numeric(18,2),
    "statement_day" smallint,
    "repayment_day" smallint,
    "cover_template_id" bigint,
    "cover_color" char(7),
    "cover_source_url" varchar(1000),
    "sort_order" integer DEFAULT '0' NOT NULL,
    "remark" varchar(1000),
    "is_deleted" smallint DEFAULT '0' NOT NULL,
    "create_user" bigint NOT NULL,
    "create_time" timestamp(0) without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "update_user" bigint NOT NULL,
    "update_time" timestamp(0) without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    PRIMARY KEY ("id"),
    CONSTRAINT "chk_bank_card_bank_source" CHECK (((("bank_id" is not null) and ("custom_bank_name" is null)) or (("bank_id" is null) and ("custom_bank_name" is not null) and (char_length(trim("custom_bank_name")) > 0))))
 );
COMMENT ON TABLE "bank_card" IS '银行卡档案';
COMMENT ON COLUMN "bank_card"."id" IS '雪花ID';
COMMENT ON COLUMN "bank_card"."user_id" IS '所属用户';
COMMENT ON COLUMN "bank_card"."bank_id" IS 'sys_dict_data.dict_code，类型bank；自定义银行时为空';
COMMENT ON COLUMN "bank_card"."custom_bank_name" IS '用户填写的银行名称，仅bank_id为空时使用';
COMMENT ON COLUMN "bank_card"."card_name" IS '卡片产品名称';
COMMENT ON COLUMN "bank_card"."alias" IS '个人别名';
COMMENT ON COLUMN "bank_card"."card_type" IS 'debit储蓄卡 credit信用卡';
COMMENT ON COLUMN "bank_card"."card_no_ciphertext" IS '版本化SM4-GCM卡号密文';
COMMENT ON COLUMN "bank_card"."card_no_fingerprint" IS '主密钥派生HMAC-SM3判重指纹';
COMMENT ON COLUMN "bank_card"."card_no_last4" IS '卡号末四位';
COMMENT ON COLUMN "bank_card"."branch_name" IS '开户支行';
COMMENT ON COLUMN "bank_card"."status" IS 'normal frozen lost closed';
COMMENT ON COLUMN "bank_card"."opened_date" IS '开卡日期';
COMMENT ON COLUMN "bank_card"."expiry_month" IS '有效期月份，存当月第一天';
COMMENT ON COLUMN "bank_card"."balance" IS '储蓄卡余额，人民币元；NULL未记录';
COMMENT ON COLUMN "bank_card"."credit_limit" IS '信用额度，人民币元；NULL未记录';
COMMENT ON COLUMN "bank_card"."statement_day" IS '每月账单日1至31';
COMMENT ON COLUMN "bank_card"."repayment_day" IS '每月固定还款日1至31';
COMMENT ON COLUMN "bank_card"."cover_template_id" IS '公共卡面ID';
COMMENT ON COLUMN "bank_card"."cover_color" IS '默认卡面Hex颜色';
COMMENT ON COLUMN "bank_card"."cover_source_url" IS '当前卡面出处，不用于远程抓取';
COMMENT ON COLUMN "bank_card"."sort_order" IS '排序值，小值在前';
COMMENT ON COLUMN "bank_card"."remark" IS '备注';
COMMENT ON COLUMN "bank_card"."is_deleted" IS '逻辑删除标记：0=未删除，1=已删除';
COMMENT ON COLUMN "bank_card"."create_user" IS '创建人用户ID';
COMMENT ON COLUMN "bank_card"."create_time" IS '创建时间';
COMMENT ON COLUMN "bank_card"."update_user" IS '最后更新人用户ID';
COMMENT ON COLUMN "bank_card"."update_time" IS '最后更新时间';
CREATE INDEX "bank_card_idx_bank_reference" ON "bank_card" ("bank_id", "is_deleted");
COMMENT ON INDEX "bank_card_idx_bank_reference" IS '业务检索索引：idx_bank_reference';
CREATE INDEX "bank_card_idx_cover_template_reference" ON "bank_card" ("cover_template_id", "is_deleted");
COMMENT ON INDEX "bank_card_idx_cover_template_reference" IS '业务检索索引：idx_cover_template_reference';
CREATE INDEX "bank_card_idx_user_bank" ON "bank_card" ("user_id", "bank_id", "is_deleted");
COMMENT ON INDEX "bank_card_idx_user_bank" IS '业务检索索引：idx_user_bank';
CREATE INDEX "bank_card_idx_user_fingerprint" ON "bank_card" ("user_id", "card_no_fingerprint", "is_deleted");
COMMENT ON INDEX "bank_card_idx_user_fingerprint" IS '业务检索索引：idx_user_fingerprint';
CREATE INDEX "bank_card_idx_user_last4" ON "bank_card" ("user_id", "card_no_last4", "is_deleted");
COMMENT ON INDEX "bank_card_idx_user_last4" IS '业务检索索引：idx_user_last4';
CREATE INDEX "bank_card_idx_user_sort" ON "bank_card" ("user_id", "is_deleted", "sort_order", "id");
COMMENT ON INDEX "bank_card_idx_user_sort" IS '业务检索索引：idx_user_sort';

CREATE TABLE "bank_card_cover_template" (
    "id" bigint NOT NULL,
    "name" varchar(100) NOT NULL,
    "bank_id" bigint NOT NULL,
    "card_type" varchar(20) NOT NULL,
    "source_url" varchar(1000),
    "is_enabled" smallint DEFAULT '1' NOT NULL,
    "sort_order" integer DEFAULT '0' NOT NULL,
    "is_deleted" smallint DEFAULT '0' NOT NULL,
    "create_user" bigint NOT NULL,
    "create_time" timestamp(0) without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "update_user" bigint NOT NULL,
    "update_time" timestamp(0) without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    PRIMARY KEY ("id"),
    CONSTRAINT "chk_cover_template_type" CHECK (("card_type" in ('debit','credit'))),
    CONSTRAINT "chk_cover_template_enabled" CHECK (("is_enabled" in (0,1)))
 );
COMMENT ON TABLE "bank_card_cover_template" IS '公共银行卡卡面';
COMMENT ON COLUMN "bank_card_cover_template"."id" IS '雪花ID';
COMMENT ON COLUMN "bank_card_cover_template"."name" IS '卡面名称';
COMMENT ON COLUMN "bank_card_cover_template"."bank_id" IS 'sys_dict_data.dict_code，类型bank';
COMMENT ON COLUMN "bank_card_cover_template"."card_type" IS 'debit借记卡 credit信用卡';
COMMENT ON COLUMN "bank_card_cover_template"."source_url" IS '图片出处';
COMMENT ON COLUMN "bank_card_cover_template"."is_enabled" IS '是否启用：1-启用，0-停用';
COMMENT ON COLUMN "bank_card_cover_template"."sort_order" IS '排序值，数值越小越靠前';
COMMENT ON COLUMN "bank_card_cover_template"."is_deleted" IS '逻辑删除标记：0=未删除，1=已删除';
COMMENT ON COLUMN "bank_card_cover_template"."create_user" IS '创建人用户ID';
COMMENT ON COLUMN "bank_card_cover_template"."create_time" IS '创建时间';
COMMENT ON COLUMN "bank_card_cover_template"."update_user" IS '最后更新人用户ID';
COMMENT ON COLUMN "bank_card_cover_template"."update_time" IS '最后更新时间';
CREATE INDEX "bank_card_cover_template_idx_bank_type_enabled" ON "bank_card_cover_template" ("bank_id", "card_type", "is_deleted", "is_enabled", "sort_order", "id");
COMMENT ON INDEX "bank_card_cover_template_idx_bank_type_enabled" IS '业务检索索引：idx_bank_type_enabled';

CREATE TABLE "bank_card_tag_rel" (
    "id" bigint NOT NULL,
    "user_id" bigint NOT NULL,
    "bank_card_id" bigint NOT NULL,
    "tag_id" bigint NOT NULL,
    "is_deleted" smallint DEFAULT '0' NOT NULL,
    "create_user" bigint NOT NULL,
    "create_time" timestamp(0) without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "update_user" bigint NOT NULL,
    "update_time" timestamp(0) without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    PRIMARY KEY ("id")
 );
COMMENT ON TABLE "bank_card_tag_rel" IS '银行卡标签关联';
COMMENT ON COLUMN "bank_card_tag_rel"."id" IS '主键ID';
COMMENT ON COLUMN "bank_card_tag_rel"."user_id" IS '与银行卡和标签的所属用户一致';
COMMENT ON COLUMN "bank_card_tag_rel"."bank_card_id" IS '关联银行卡ID，关联bank_card.id';
COMMENT ON COLUMN "bank_card_tag_rel"."tag_id" IS 'user_dict_data.id，类型bank_card_tag';
COMMENT ON COLUMN "bank_card_tag_rel"."is_deleted" IS '逻辑删除标记：0=未删除，1=已删除';
COMMENT ON COLUMN "bank_card_tag_rel"."create_user" IS '创建人用户ID';
COMMENT ON COLUMN "bank_card_tag_rel"."create_time" IS '创建时间';
COMMENT ON COLUMN "bank_card_tag_rel"."update_user" IS '最后更新人用户ID';
COMMENT ON COLUMN "bank_card_tag_rel"."update_time" IS '最后更新时间';
CREATE INDEX "bank_card_tag_rel_idx_user_card_tag" ON "bank_card_tag_rel" ("user_id", "bank_card_id", "tag_id", "is_deleted");
COMMENT ON INDEX "bank_card_tag_rel_idx_user_card_tag" IS '业务检索索引：idx_user_card_tag';
CREATE INDEX "bank_card_tag_rel_idx_user_tag_card" ON "bank_card_tag_rel" ("user_id", "tag_id", "is_deleted", "bank_card_id");
COMMENT ON INDEX "bank_card_tag_rel_idx_user_tag_card" IS '业务检索索引：idx_user_tag_card';

CREATE TABLE "cbti_personality" (
    "id" bigint NOT NULL,
    "code" varchar(20) COLLATE "C" NOT NULL,
    "name" varchar(64) NOT NULL,
    "motto" varchar(255),
    "color" varchar(20),
    "vector" jsonb,
    "description" text,
    "strengths" jsonb,
    "weaknesses" jsonb,
    "tech_stack" varchar(255),
    "spirit" text,
    "image_object" varchar(255),
    "is_special" smallint DEFAULT '0' NOT NULL,
    "create_user" bigint,
    "create_time" timestamp(0) without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "update_user" bigint,
    "update_time" timestamp(0) without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "is_deleted" smallint DEFAULT '0' NOT NULL,
    PRIMARY KEY ("id")
 );
COMMENT ON TABLE "cbti_personality" IS 'CBTI 人格类型表';
COMMENT ON COLUMN "cbti_personality"."id" IS '主键ID';
COMMENT ON COLUMN "cbti_personality"."code" IS '人格代码（唯一）';
COMMENT ON COLUMN "cbti_personality"."name" IS '人格名称';
COMMENT ON COLUMN "cbti_personality"."motto" IS '座右铭';
COMMENT ON COLUMN "cbti_personality"."color" IS '主题色（HEX）';
COMMENT ON COLUMN "cbti_personality"."vector" IS '人格向量（长度15，数值为-1/0/1/2）';
COMMENT ON COLUMN "cbti_personality"."description" IS '人格描述';
COMMENT ON COLUMN "cbti_personality"."strengths" IS '优势（字符串数组）';
COMMENT ON COLUMN "cbti_personality"."weaknesses" IS '弱点/注意（字符串数组）';
COMMENT ON COLUMN "cbti_personality"."tech_stack" IS '技术栈';
COMMENT ON COLUMN "cbti_personality"."spirit" IS '灵魂格言';
COMMENT ON COLUMN "cbti_personality"."image_object" IS 'MinIO对象路径（如 images/cbti/characters/SUDO.png）';
COMMENT ON COLUMN "cbti_personality"."is_special" IS '是否隐藏人格（0-否，1-是）';
COMMENT ON COLUMN "cbti_personality"."create_user" IS '创建人';
COMMENT ON COLUMN "cbti_personality"."create_time" IS '创建时间';
COMMENT ON COLUMN "cbti_personality"."update_user" IS '更新人';
COMMENT ON COLUMN "cbti_personality"."update_time" IS '更新时间';
COMMENT ON COLUMN "cbti_personality"."is_deleted" IS '逻辑删除标记：0=未删除，1=已删除';
CREATE INDEX "cbti_personality_idx_cbti_personality_special" ON "cbti_personality" ("is_special");
COMMENT ON INDEX "cbti_personality_idx_cbti_personality_special" IS '业务检索索引：idx_cbti_personality_special';
CREATE UNIQUE INDEX "cbti_personality_uk_cbti_personality_code" ON "cbti_personality" ("code") WHERE "is_deleted" = 0;
COMMENT ON INDEX "cbti_personality_uk_cbti_personality_code" IS '有效人格代码唯一，已删除人格不占用';

CREATE TABLE "cbti_result" (
    "id" bigint NOT NULL,
    "user_id" bigint NOT NULL,
    "personality_code" varchar(20) NOT NULL,
    "similarity" integer DEFAULT '0' NOT NULL,
    "dimensions" jsonb,
    "answers" jsonb,
    "hidden_answers" jsonb,
    "create_user" bigint,
    "create_time" timestamp(0) without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "update_user" bigint,
    "update_time" timestamp(0) without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "is_deleted" smallint DEFAULT '0' NOT NULL,
    PRIMARY KEY ("id")
 );
COMMENT ON TABLE "cbti_result" IS 'CBTI 测试历史表';
COMMENT ON COLUMN "cbti_result"."id" IS '主键ID';
COMMENT ON COLUMN "cbti_result"."user_id" IS '用户ID';
COMMENT ON COLUMN "cbti_result"."personality_code" IS '人格代码';
COMMENT ON COLUMN "cbti_result"."similarity" IS '匹配度（0-100）';
COMMENT ON COLUMN "cbti_result"."dimensions" IS '15维度结果(JSON)';
COMMENT ON COLUMN "cbti_result"."answers" IS '答题结果(JSON，题号->选项值)';
COMMENT ON COLUMN "cbti_result"."hidden_answers" IS '彩蛋答题结果(JSON)';
COMMENT ON COLUMN "cbti_result"."create_user" IS '创建人';
COMMENT ON COLUMN "cbti_result"."create_time" IS '创建时间';
COMMENT ON COLUMN "cbti_result"."update_user" IS '更新人';
COMMENT ON COLUMN "cbti_result"."update_time" IS '更新时间';
COMMENT ON COLUMN "cbti_result"."is_deleted" IS '逻辑删除标记：0=未删除，1=已删除';
CREATE INDEX "cbti_result_idx_cbti_result_create_time" ON "cbti_result" ("create_time");
COMMENT ON INDEX "cbti_result_idx_cbti_result_create_time" IS '业务检索索引：idx_cbti_result_create_time';
CREATE INDEX "cbti_result_idx_cbti_result_personality_code" ON "cbti_result" ("personality_code");
COMMENT ON INDEX "cbti_result_idx_cbti_result_personality_code" IS '业务检索索引：idx_cbti_result_personality_code';
CREATE INDEX "cbti_result_idx_cbti_result_user_id" ON "cbti_result" ("user_id");
COMMENT ON INDEX "cbti_result_idx_cbti_result_user_id" IS '业务检索索引：idx_cbti_result_user_id';

CREATE TABLE "data_export_audit_event" (
    "id" bigint NOT NULL,
    "job_id" bigint,
    "user_id" bigint,
    "event_type" varchar(128) NOT NULL,
    "result_code" varchar(128) NOT NULL,
    "request_id" varchar(64) NOT NULL,
    "client_ip" varchar(128),
    "trace_id" varchar(64),
    "details" text,
    "event_time" timestamp(3) without time zone NOT NULL,
    "create_user" bigint,
    "create_time" timestamp(3) without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "update_user" bigint,
    "update_time" timestamp(3) without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "is_deleted" smallint DEFAULT '0' NOT NULL,
    PRIMARY KEY ("id")
 );
COMMENT ON TABLE "data_export_audit_event" IS '个人数据导出追加审计事件';
COMMENT ON COLUMN "data_export_audit_event"."id" IS '记录ID，由应用生成雪花ID';
COMMENT ON COLUMN "data_export_audit_event"."job_id" IS '关联任务ID，建单前或无有效任务时为空';
COMMENT ON COLUMN "data_export_audit_event"."user_id" IS '所属用户ID，用于个人数据隔离';
COMMENT ON COLUMN "data_export_audit_event"."event_type" IS '审计事件类型';
COMMENT ON COLUMN "data_export_audit_event"."result_code" IS '安全结果码，不含业务内容';
COMMENT ON COLUMN "data_export_audit_event"."request_id" IS '服务端事件请求标识';
COMMENT ON COLUMN "data_export_audit_event"."client_ip" IS '可信代理处理后的来源IP，后台任务为空';
COMMENT ON COLUMN "data_export_audit_event"."trace_id" IS '链路追踪标识，无上下文为空';
COMMENT ON COLUMN "data_export_audit_event"."details" IS '白名单事件元数据，不含正文或凭据';
COMMENT ON COLUMN "data_export_audit_event"."event_time" IS '审计事件时间，上海时区';
COMMENT ON COLUMN "data_export_audit_event"."create_user" IS '创建人用户ID，系统操作为空';
COMMENT ON COLUMN "data_export_audit_event"."create_time" IS '记录创建时间';
COMMENT ON COLUMN "data_export_audit_event"."update_user" IS '最后更新人用户ID，系统操作为空';
COMMENT ON COLUMN "data_export_audit_event"."update_time" IS '最后更新时间';
COMMENT ON COLUMN "data_export_audit_event"."is_deleted" IS '逻辑删除：0未删除、1已删除';
CREATE INDEX "data_export_audit_event_idx_data_export_audit_event_job" ON "data_export_audit_event" ("job_id", "event_time", "id");
COMMENT ON INDEX "data_export_audit_event_idx_data_export_audit_event_job" IS '业务检索索引：idx_data_export_audit_event_job';
CREATE INDEX "data_export_audit_event_idx_data_export_audit_event_user" ON "data_export_audit_event" ("user_id", "event_time", "id");
COMMENT ON INDEX "data_export_audit_event_idx_data_export_audit_event_user" IS '业务检索索引：idx_data_export_audit_event_user';

CREATE TABLE "data_export_coordinator" (
    "id" bigint NOT NULL,
    "name" varchar(128) NOT NULL,
    "create_user" bigint,
    "create_time" timestamp(3) without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "update_user" bigint,
    "update_time" timestamp(3) without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "is_deleted" smallint DEFAULT '0' NOT NULL,
    PRIMARY KEY ("id")
 );
COMMENT ON TABLE "data_export_coordinator" IS '个人数据导出全局队列控制';
COMMENT ON COLUMN "data_export_coordinator"."id" IS '记录ID，由应用生成雪花ID，控制行固定为1';
COMMENT ON COLUMN "data_export_coordinator"."name" IS '控制行名称，用于串行化领取和月额度结算';
COMMENT ON COLUMN "data_export_coordinator"."create_user" IS '创建人用户ID，系统操作为空';
COMMENT ON COLUMN "data_export_coordinator"."create_time" IS '记录创建时间';
COMMENT ON COLUMN "data_export_coordinator"."update_user" IS '最后更新人用户ID，系统操作为空';
COMMENT ON COLUMN "data_export_coordinator"."update_time" IS '最后更新时间';
COMMENT ON COLUMN "data_export_coordinator"."is_deleted" IS '逻辑删除：0未删除、1已删除';
CREATE UNIQUE INDEX "data_export_coordinator_uk_data_export_coordinator_name" ON "data_export_coordinator" ("name");
COMMENT ON INDEX "data_export_coordinator_uk_data_export_coordinator_name" IS '唯一性约束：uk_data_export_coordinator_name';

CREATE TABLE "data_export_grant" (
    "id" bigint NOT NULL,
    "user_id" bigint NOT NULL,
    "token_hash" varchar(64) NOT NULL,
    "session_hash" varchar(64) NOT NULL,
    "security_hash" varchar(64) NOT NULL,
    "scope_hash" varchar(64) NOT NULL,
    "purpose" varchar(128) NOT NULL,
    "job_id" bigint,
    "expires_at" timestamp(3) without time zone NOT NULL,
    "consumed" integer NOT NULL,
    "create_user" bigint,
    "create_time" timestamp(3) without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "update_user" bigint,
    "update_time" timestamp(3) without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "is_deleted" smallint DEFAULT '0' NOT NULL,
    PRIMARY KEY ("id")
 );
COMMENT ON TABLE "data_export_grant" IS '个人数据导出一次性身份核验授权';
COMMENT ON COLUMN "data_export_grant"."id" IS '记录ID，由应用生成雪花ID';
COMMENT ON COLUMN "data_export_grant"."user_id" IS '所属用户ID，用于个人数据隔离';
COMMENT ON COLUMN "data_export_grant"."token_hash" IS '一次性核验票据SHA256摘要';
COMMENT ON COLUMN "data_export_grant"."session_hash" IS '申请会话Token摘要，不保存原值';
COMMENT ON COLUMN "data_export_grant"."security_hash" IS '账户凭据状态摘要，用于凭据变更后撤销授权';
COMMENT ON COLUMN "data_export_grant"."scope_hash" IS '获核验范围和目录版本的SHA256摘要';
COMMENT ON COLUMN "data_export_grant"."purpose" IS '授权用途：CREATE创建、DOWNLOAD下载';
COMMENT ON COLUMN "data_export_grant"."job_id" IS '关联任务ID，建单前或无有效任务时为空';
COMMENT ON COLUMN "data_export_grant"."expires_at" IS '一次性授权到期时间';
COMMENT ON COLUMN "data_export_grant"."consumed" IS '核验票据状态：0=未消费，1=已消费';
COMMENT ON COLUMN "data_export_grant"."create_user" IS '创建人用户ID，系统操作为空';
COMMENT ON COLUMN "data_export_grant"."create_time" IS '记录创建时间';
COMMENT ON COLUMN "data_export_grant"."update_user" IS '最后更新人用户ID，系统操作为空';
COMMENT ON COLUMN "data_export_grant"."update_time" IS '最后更新时间';
COMMENT ON COLUMN "data_export_grant"."is_deleted" IS '逻辑删除：0未删除、1已删除';
CREATE INDEX "data_export_grant_idx_data_export_grant_expiry" ON "data_export_grant" ("expires_at");
COMMENT ON INDEX "data_export_grant_idx_data_export_grant_expiry" IS '业务检索索引：idx_data_export_grant_expiry';
CREATE UNIQUE INDEX "data_export_grant_uk_data_export_grant_token" ON "data_export_grant" ("token_hash");
COMMENT ON INDEX "data_export_grant_uk_data_export_grant_token" IS '唯一性约束：uk_data_export_grant_token';

CREATE TABLE "data_export_job" (
    "id" bigint NOT NULL,
    "user_id" bigint NOT NULL,
    "request_key" varchar(64) COLLATE "C" NOT NULL,
    "request_hash" varchar(64) NOT NULL,
    "scope_json" text NOT NULL,
    "session_hash" varchar(64) NOT NULL,
    "security_hash" varchar(64) NOT NULL,
    "quota_month" varchar(7) NOT NULL,
    "quota_charged" integer NOT NULL,
    "status" varchar(128) NOT NULL,
    "phase" varchar(128) NOT NULL,
    "attempt" integer NOT NULL,
    "lease_token" varchar(128),
    "lease_until" timestamp(3) without time zone,
    "deadline" timestamp(3) without time zone NOT NULL,
    "started_at" timestamp(3) without time zone,
    "finished_at" timestamp(3) without time zone,
    "cancel_requested" integer NOT NULL,
    "error_code" varchar(128),
    "summary_json" jsonb,
    "object_key" varchar(255) COLLATE "C",
    "file_name" varchar(128),
    "content_type" varchar(128),
    "sha256" varchar(64),
    "file_bytes" bigint,
    "row_count" integer NOT NULL,
    "artifact_status" varchar(128) NOT NULL,
    "expires_at" timestamp(3) without time zone,
    "download_count" integer NOT NULL,
    "create_user" bigint,
    "create_time" timestamp(3) without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "update_user" bigint,
    "update_time" timestamp(3) without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "is_deleted" smallint DEFAULT '0' NOT NULL,
    PRIMARY KEY ("id")
 );
COMMENT ON TABLE "data_export_job" IS '个人数据导出任务与月额度账本';
COMMENT ON COLUMN "data_export_job"."id" IS '记录ID，由应用生成雪花ID';
COMMENT ON COLUMN "data_export_job"."user_id" IS '所属用户ID，用于个人数据隔离';
COMMENT ON COLUMN "data_export_job"."request_key" IS '客户端幂等键，同一用户内唯一';
COMMENT ON COLUMN "data_export_job"."request_hash" IS '归一化申请范围的SHA256摘要';
COMMENT ON COLUMN "data_export_job"."scope_json" IS '已授权数据集键数组（JSON 原文）；按写入字节参与导出请求幂等哈希，必须保持 text，不能改为 jsonb';
COMMENT ON COLUMN "data_export_job"."session_hash" IS '申请会话Token摘要，不保存原值';
COMMENT ON COLUMN "data_export_job"."security_hash" IS '账户凭据状态摘要，用于凭据变更后撤销授权';
COMMENT ON COLUMN "data_export_job"."quota_month" IS '申请所属自然月，上海时区yyyy-MM';
COMMENT ON COLUMN "data_export_job"."quota_charged" IS '月额度标记：1=预占或已扣除，0=已退还';
COMMENT ON COLUMN "data_export_job"."status" IS '任务状态：QUEUED排队、RUNNING执行、SUCCEEDED成功、FAILED失败、CANCELLED取消、REVOKED撤销';
COMMENT ON COLUMN "data_export_job"."phase" IS '执行阶段：QUEUED排队、CAPTURING读取、DONE终态';
COMMENT ON COLUMN "data_export_job"."attempt" IS '尝试次数，初始0，每次领取加1';
COMMENT ON COLUMN "data_export_job"."lease_token" IS '当前执行租约随机标识，防止迟到执行者发布';
COMMENT ON COLUMN "data_export_job"."lease_until" IS '当前租约到期时间，未领取为空';
COMMENT ON COLUMN "data_export_job"."deadline" IS '任务最迟结束时间，上海时区';
COMMENT ON COLUMN "data_export_job"."started_at" IS '首次开始读取时间，尚未执行为空';
COMMENT ON COLUMN "data_export_job"."finished_at" IS '终态时间，尚未结束为空';
COMMENT ON COLUMN "data_export_job"."cancel_requested" IS '取消请求：0=未请求，1=用户已请求';
COMMENT ON COLUMN "data_export_job"."error_code" IS '安全错误码，成功或尚未失败为空';
COMMENT ON COLUMN "data_export_job"."summary_json" IS '数据集行数及文件摘要清单（JSON 对象），完成前为空';
COMMENT ON COLUMN "data_export_job"."object_key" IS '专用私有桶内部对象键，不返回客户端';
COMMENT ON COLUMN "data_export_job"."file_name" IS '用户下载文件名，不含个人信息';
COMMENT ON COLUMN "data_export_job"."content_type" IS '生成文件MIME类型，完成前为空';
COMMENT ON COLUMN "data_export_job"."sha256" IS '生成文件SHA256摘要，完成前为空';
COMMENT ON COLUMN "data_export_job"."file_bytes" IS '文件字节数，完成前为空，0表示空文件';
COMMENT ON COLUMN "data_export_job"."row_count" IS '业务数据总行数，0表示空数据';
COMMENT ON COLUMN "data_export_job"."artifact_status" IS '文件状态：NONE未生成、AVAILABLE可用、EXPIRED过期、DELETED删除、REVOKED撤销';
COMMENT ON COLUMN "data_export_job"."expires_at" IS '文件或授权到期时间，文件完成前为空';
COMMENT ON COLUMN "data_export_job"."download_count" IS '已授权开始传输次数，中断也计次';
COMMENT ON COLUMN "data_export_job"."create_user" IS '创建人用户ID，系统操作为空';
COMMENT ON COLUMN "data_export_job"."create_time" IS '记录创建时间';
COMMENT ON COLUMN "data_export_job"."update_user" IS '最后更新人用户ID，系统操作为空';
COMMENT ON COLUMN "data_export_job"."update_time" IS '最后更新时间';
COMMENT ON COLUMN "data_export_job"."is_deleted" IS '逻辑删除：0未删除、1已删除，额度有效期内禁止删除任务';
CREATE INDEX "data_export_job_idx_data_export_job_expiry" ON "data_export_job" ("artifact_status", "expires_at");
COMMENT ON INDEX "data_export_job_idx_data_export_job_expiry" IS '业务检索索引：idx_data_export_job_expiry';
CREATE INDEX "data_export_job_idx_data_export_job_history" ON "data_export_job" ("user_id", "create_time", "id");
COMMENT ON INDEX "data_export_job_idx_data_export_job_history" IS '业务检索索引：idx_data_export_job_history';
CREATE INDEX "data_export_job_idx_data_export_job_month" ON "data_export_job" ("user_id", "quota_month", "quota_charged");
COMMENT ON INDEX "data_export_job_idx_data_export_job_month" IS '业务检索索引：idx_data_export_job_month';
CREATE INDEX "data_export_job_idx_data_export_job_queue" ON "data_export_job" ("status", "create_time", "id");
COMMENT ON INDEX "data_export_job_idx_data_export_job_queue" IS '业务检索索引：idx_data_export_job_queue';
CREATE UNIQUE INDEX "data_export_job_uk_data_export_job_request" ON "data_export_job" ("user_id", "request_key") WHERE "is_deleted" = 0;
COMMENT ON INDEX "data_export_job_uk_data_export_job_request" IS '同一用户有效导出任务的幂等键唯一，已删除任务不占用';

CREATE TABLE "device" (
    "id" bigint NOT NULL,
    "user_id" bigint NOT NULL,
    "name" varchar(255) NOT NULL,
    "spec" varchar(255),
    "type" varchar(255),
    "status" varchar(255),
    "remark" varchar(255),
    "purchase_date" date,
    "purchase_price" numeric(10,2),
    "purchase_place" varchar(255),
    "end_date" date,
    "file_id" varchar(32),
    "source_platform" varchar(16),
    "source_order_id" varchar(100) COLLATE "C",
    "source_product_id" varchar(100) COLLATE "C",
    "source_item_id" varchar(128) COLLATE "C",
    "source_unit_index" integer,
    "create_user" bigint,
    "create_time" timestamp(0) without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "update_user" bigint,
    "update_time" timestamp(0) without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "is_deleted" smallint DEFAULT '0' NOT NULL,
    PRIMARY KEY ("id")
 );
COMMENT ON TABLE "device" IS '设备表';
COMMENT ON COLUMN "device"."id" IS '主键ID';
COMMENT ON COLUMN "device"."user_id" IS '用户ID';
COMMENT ON COLUMN "device"."name" IS '设备名称';
COMMENT ON COLUMN "device"."spec" IS '设备规格';
COMMENT ON COLUMN "device"."type" IS '设备类型';
COMMENT ON COLUMN "device"."status" IS '设备状态';
COMMENT ON COLUMN "device"."remark" IS '备注';
COMMENT ON COLUMN "device"."purchase_date" IS '购买日期';
COMMENT ON COLUMN "device"."purchase_price" IS '购买价格';
COMMENT ON COLUMN "device"."purchase_place" IS '购买地点';
COMMENT ON COLUMN "device"."end_date" IS '结束日期（用于计算日均费用）';
COMMENT ON COLUMN "device"."file_id" IS '图片文件ID(UUID)';
COMMENT ON COLUMN "device"."source_platform" IS '采集平台：jd=京东、taobao=淘宝；手工录入或历史记录为NULL';
COMMENT ON COLUMN "device"."source_order_id" IS '来源订单ID，原始字符串；无采集来源时为NULL';
COMMENT ON COLUMN "device"."source_product_id" IS '来源商品ID，采集行sku原始字符串；无采集来源时为NULL';
COMMENT ON COLUMN "device"."source_item_id" IS '订单内稳定商品明细标识，区分同商品的规格或多行；无采集来源时为NULL';
COMMENT ON COLUMN "device"."source_unit_index" IS '来源商品行内件序号，从1开始；无采集来源时为NULL';
COMMENT ON COLUMN "device"."create_user" IS '创建人';
COMMENT ON COLUMN "device"."create_time" IS '创建时间';
COMMENT ON COLUMN "device"."update_user" IS '更新人';
COMMENT ON COLUMN "device"."update_time" IS '更新时间';
COMMENT ON COLUMN "device"."is_deleted" IS '是否删除: 0-未删除, 1-已删除';
CREATE INDEX "device_idx_device_capture_order" ON "device" ("user_id", "source_platform", "source_order_id", "is_deleted");
COMMENT ON INDEX "device_idx_device_capture_order" IS '业务检索索引：idx_device_capture_order';

CREATE TABLE "device_expense" (
    "id" bigint NOT NULL,
    "user_id" bigint NOT NULL,
    "device_id" bigint NOT NULL,
    "expense_id" bigint NOT NULL,
    "create_user" bigint,
    "create_time" timestamp(0) without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "update_user" bigint,
    "update_time" timestamp(0) without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "is_deleted" smallint DEFAULT '0' NOT NULL,
    PRIMARY KEY ("id")
 );
COMMENT ON TABLE "device_expense" IS '设备支出关联表';
COMMENT ON COLUMN "device_expense"."id" IS '关联ID，由应用生成雪花ID';
COMMENT ON COLUMN "device_expense"."user_id" IS '所属用户ID，用于数据隔离';
COMMENT ON COLUMN "device_expense"."device_id" IS '关联设备ID';
COMMENT ON COLUMN "device_expense"."expense_id" IS '关联支出ID，解绑后复用同一关联记录';
COMMENT ON COLUMN "device_expense"."create_user" IS '首次创建人用户ID，未记录时为空';
COMMENT ON COLUMN "device_expense"."create_time" IS '首次创建时间';
COMMENT ON COLUMN "device_expense"."update_user" IS '最后更新人用户ID，未记录时为空';
COMMENT ON COLUMN "device_expense"."update_time" IS '最后更新时间';
COMMENT ON COLUMN "device_expense"."is_deleted" IS '逻辑删除标记：0=已绑定，1=已解绑';
CREATE INDEX "device_expense_idx_device_expense_user_device" ON "device_expense" ("user_id", "device_id", "is_deleted");
COMMENT ON INDEX "device_expense_idx_device_expense_user_device" IS '业务检索索引：idx_device_expense_user_device';
CREATE UNIQUE INDEX "device_expense_uk_device_expense_expense" ON "device_expense" ("expense_id");
COMMENT ON INDEX "device_expense_uk_device_expense_expense" IS '唯一性约束：uk_device_expense_expense';

CREATE TABLE "exercise_record" (
    "id" bigint NOT NULL,
    "user_id" bigint NOT NULL,
    "exercise_type_id" bigint,
    "exercise_date" date NOT NULL,
    "exercise_count" integer DEFAULT '0' NOT NULL,
    "description" text,
    "create_user" bigint,
    "create_time" timestamp(0) without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "update_time" timestamp(0) without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "update_user" bigint,
    "is_deleted" smallint DEFAULT '0' NOT NULL,
    "time_id" varchar(32),
    PRIMARY KEY ("id")
 );
COMMENT ON TABLE "exercise_record" IS '运动记录表';
COMMENT ON COLUMN "exercise_record"."id" IS '主键ID';
COMMENT ON COLUMN "exercise_record"."user_id" IS '用户ID';
COMMENT ON COLUMN "exercise_record"."exercise_type_id" IS '运动类型ID(关联user_dict_data.id)';
COMMENT ON COLUMN "exercise_record"."exercise_date" IS '运动日期';
COMMENT ON COLUMN "exercise_record"."exercise_count" IS '运动次数，单位次；0表示本条未记录有效次数';
COMMENT ON COLUMN "exercise_record"."description" IS '运动描述';
COMMENT ON COLUMN "exercise_record"."create_user" IS '创建用户';
COMMENT ON COLUMN "exercise_record"."create_time" IS '创建时间';
COMMENT ON COLUMN "exercise_record"."update_time" IS '更新时间';
COMMENT ON COLUMN "exercise_record"."update_user" IS '更新用户';
COMMENT ON COLUMN "exercise_record"."is_deleted" IS '是否删除（0：未删除，1：已删除）';
COMMENT ON COLUMN "exercise_record"."time_id" IS '时间ID';

CREATE TABLE "expense" (
    "id" bigint NOT NULL,
    "amt" numeric(10,2) NOT NULL,
    "transaction_amt" numeric(10,2),
    "exp_type_id" bigint NOT NULL,
    "pay_type_id" bigint,
    "remark" varchar(255),
    "user_id" bigint NOT NULL,
    "exp_time" timestamp(0) without time zone NOT NULL,
    "is_deleted" smallint DEFAULT '0' NOT NULL,
    "create_user" bigint,
    "create_time" timestamp(0) without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "update_user" bigint,
    "update_time" timestamp(0) without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "exp_desc" varchar(200),
    "transaction_id" varchar(100) COLLATE "C",
    "counterparty" varchar(100),
    "counterparty_acct" varchar(100) COLLATE "C",
    "merchant_order_no" varchar(100) COLLATE "C",
    "transaction_status" varchar(20),
    PRIMARY KEY ("id")
 );
COMMENT ON TABLE "expense" IS '支出表';
COMMENT ON COLUMN "expense"."id" IS '账单ID';
COMMENT ON COLUMN "expense"."amt" IS '花费金额';
COMMENT ON COLUMN "expense"."transaction_amt" IS '交易金额';
COMMENT ON COLUMN "expense"."exp_type_id" IS '支出类型ID';
COMMENT ON COLUMN "expense"."pay_type_id" IS '支付方式ID(关联user_dict_data)';
COMMENT ON COLUMN "expense"."remark" IS '备注';
COMMENT ON COLUMN "expense"."user_id" IS '用户id';
COMMENT ON COLUMN "expense"."exp_time" IS '支出时间';
COMMENT ON COLUMN "expense"."is_deleted" IS '逻辑删除标记：0=未删除，1=已删除';
COMMENT ON COLUMN "expense"."create_user" IS '创建人';
COMMENT ON COLUMN "expense"."create_time" IS '创建时间';
COMMENT ON COLUMN "expense"."update_user" IS '修改人';
COMMENT ON COLUMN "expense"."update_time" IS '更新时间';
COMMENT ON COLUMN "expense"."exp_desc" IS '交易描述，未记录时为空';
COMMENT ON COLUMN "expense"."transaction_id" IS '支付平台交易号，保持原始字符串；未记录时为空';
COMMENT ON COLUMN "expense"."counterparty" IS '交易对方名称，未记录时为空';
COMMENT ON COLUMN "expense"."counterparty_acct" IS '交易对方账号，保持原始字符串；未记录时为空';
COMMENT ON COLUMN "expense"."merchant_order_no" IS '商家订单号';
COMMENT ON COLUMN "expense"."transaction_status" IS '交易状态';

CREATE TABLE "feedback" (
    "id" bigint NOT NULL,
    "user_id" bigint NOT NULL,
    "title" varchar(200) NOT NULL,
    "content" text NOT NULL,
    "feedback_type" varchar(32) NOT NULL,
    "status" varchar(32) DEFAULT 'PENDING' NOT NULL,
    "priority" varchar(16) DEFAULT 'MEDIUM',
    "is_deleted" smallint DEFAULT '0' NOT NULL,
    "create_time" timestamp(0) without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "update_time" timestamp(0) without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "create_user" bigint,
    "update_user" bigint,
    PRIMARY KEY ("id")
 );
COMMENT ON TABLE "feedback" IS '用户反馈表';
COMMENT ON COLUMN "feedback"."id" IS '反馈ID（雪花）';
COMMENT ON COLUMN "feedback"."user_id" IS '反馈人ID';
COMMENT ON COLUMN "feedback"."title" IS '标题';
COMMENT ON COLUMN "feedback"."content" IS '内容（Markdown）';
COMMENT ON COLUMN "feedback"."feedback_type" IS '类型：BUG/SUGGESTION/QUESTION/OTHER';
COMMENT ON COLUMN "feedback"."status" IS '状态：PENDING/PROCESSING/RESOLVED/CLOSED/REJECTED';
COMMENT ON COLUMN "feedback"."priority" IS '优先级：LOW/MEDIUM/HIGH（预留）';
COMMENT ON COLUMN "feedback"."is_deleted" IS '逻辑删除标记：0=未删除，1=已删除';
COMMENT ON COLUMN "feedback"."create_time" IS '创建时间';
COMMENT ON COLUMN "feedback"."update_time" IS '更新时间';
COMMENT ON COLUMN "feedback"."create_user" IS '创建人ID';
COMMENT ON COLUMN "feedback"."update_user" IS '更新人ID';
CREATE INDEX "feedback_idx_status_create_time" ON "feedback" ("status", "create_time");
COMMENT ON INDEX "feedback_idx_status_create_time" IS '业务检索索引：idx_status_create_time';
CREATE INDEX "feedback_idx_user_id_create_time" ON "feedback" ("user_id", "create_time");
COMMENT ON INDEX "feedback_idx_user_id_create_time" IS '业务检索索引：idx_user_id_create_time';

CREATE TABLE "feedback_comment" (
    "id" bigint NOT NULL,
    "feedback_id" bigint NOT NULL,
    "user_id" bigint NOT NULL,
    "role_type" varchar(16) NOT NULL,
    "content" text NOT NULL,
    "is_deleted" smallint DEFAULT '0' NOT NULL,
    "create_time" timestamp(0) without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "update_time" timestamp(0) without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "create_user" bigint,
    "update_user" bigint,
    PRIMARY KEY ("id")
 );
COMMENT ON TABLE "feedback_comment" IS '反馈评论表';
COMMENT ON COLUMN "feedback_comment"."id" IS '评论ID（雪花）';
COMMENT ON COLUMN "feedback_comment"."feedback_id" IS '所属反馈ID';
COMMENT ON COLUMN "feedback_comment"."user_id" IS '评论人ID';
COMMENT ON COLUMN "feedback_comment"."role_type" IS '角色：USER/ADMIN';
COMMENT ON COLUMN "feedback_comment"."content" IS '评论内容（Markdown）';
COMMENT ON COLUMN "feedback_comment"."is_deleted" IS '逻辑删除标记：0=未删除，1=已删除';
COMMENT ON COLUMN "feedback_comment"."create_time" IS '创建时间';
COMMENT ON COLUMN "feedback_comment"."update_time" IS '更新时间';
COMMENT ON COLUMN "feedback_comment"."create_user" IS '创建人ID';
COMMENT ON COLUMN "feedback_comment"."update_user" IS '更新人ID';
CREATE INDEX "feedback_comment_idx_feedback_id_create_time" ON "feedback_comment" ("feedback_id", "create_time");
COMMENT ON INDEX "feedback_comment_idx_feedback_id_create_time" IS '业务检索索引：idx_feedback_id_create_time';

CREATE TABLE "file" (
    "storage_object_id" bigint,
    "id" varchar(32) NOT NULL,
    "file_name" varchar(255) COLLATE "C" NOT NULL,
    "file_size" bigint,
    "file_type" varchar(100),
    "hash_value" varchar(128),
    "biz_type" varchar(50) NOT NULL,
    "biz_id" bigint,
    "is_public" smallint DEFAULT '0' NOT NULL,
    "create_user" bigint,
    "create_time" timestamp(0) without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "update_user" bigint,
    "update_time" timestamp(0) without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "is_deleted" smallint DEFAULT '0' NOT NULL,
    PRIMARY KEY ("id")
 );
COMMENT ON TABLE "file" IS '系统文件表';
COMMENT ON COLUMN "file"."storage_object_id" IS '关联不可变图片对象ID；普通上传未接入对象表时为空';
COMMENT ON COLUMN "file"."id" IS '主键UUID';
COMMENT ON COLUMN "file"."file_name" IS 'MinIO完整对象键';
COMMENT ON COLUMN "file"."file_size" IS '文件大小(字节)';
COMMENT ON COLUMN "file"."file_type" IS '文件MIME类型';
COMMENT ON COLUMN "file"."hash_value" IS '文件哈希值(如 MD5，用于防重)';
COMMENT ON COLUMN "file"."biz_type" IS '业务类型(如 movie, device, honor, wardrobe_item)';
COMMENT ON COLUMN "file"."biz_id" IS '业务记录ID(用于反向关联，单附件时也可为空)';
COMMENT ON COLUMN "file"."is_public" IS '是否公开：0-否，1-是';
COMMENT ON COLUMN "file"."create_user" IS '创建人';
COMMENT ON COLUMN "file"."create_time" IS '创建时间';
COMMENT ON COLUMN "file"."update_user" IS '更新人';
COMMENT ON COLUMN "file"."update_time" IS '更新时间';
COMMENT ON COLUMN "file"."is_deleted" IS '是否删除';
CREATE INDEX "file_idx_biz" ON "file" ("biz_type", "biz_id");
COMMENT ON INDEX "file_idx_biz" IS '业务检索索引：idx_biz';
CREATE INDEX "file_idx_file_storage_object" ON "file" ("storage_object_id");
COMMENT ON INDEX "file_idx_file_storage_object" IS '业务检索索引：idx_file_storage_object';
CREATE INDEX "file_idx_hash" ON "file" ("hash_value");
COMMENT ON INDEX "file_idx_hash" IS '业务检索索引：idx_hash';

CREATE TABLE "goal" (
    "id" bigint NOT NULL,
    "user_id" bigint NOT NULL,
    "type" smallint,
    "title" varchar(255),
    "description" text,
    "content" text,
    "status" varchar(32) DEFAULT 'not_started',
    "target_value" numeric(14,2),
    "current_value" numeric(14,2),
    "year" integer,
    "month" integer,
    "day" integer,
    "parent_id" bigint,
    "start_date" timestamp(0) without time zone,
    "end_date" timestamp(0) without time zone,
    "completed_at" timestamp(0) without time zone,
    "tags" varchar(1000),
    "is_pinned" smallint DEFAULT '0' NOT NULL,
    "pinned_sort_order" integer DEFAULT '0' NOT NULL,
    "is_deleted" smallint DEFAULT '0' NOT NULL,
    "create_time" timestamp(0) without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "update_time" timestamp(0) without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "create_user" bigint,
    "update_user" bigint,
    "progress_mode" varchar(16) DEFAULT 'manual' NOT NULL,
    "progress_query" text,
    "progress_variables" jsonb,
    "progress_variable_bindings" jsonb,
    "progress_value_path" varchar(256),
    "target_operator" varchar(8) DEFAULT 'gte' NOT NULL,
    "progress_checked_at" timestamp(0) without time zone,
    "progress_error" varchar(255),
    "progress_refresh_id" varchar(36),
    "progress_version" bigint DEFAULT '0' NOT NULL,
    PRIMARY KEY ("id"),
    CONSTRAINT "ck_goal_progress_mode" CHECK (("progress_mode" in ('manual','graphql'))),
    CONSTRAINT "ck_goal_target_operator" CHECK (("target_operator" in ('gte','gt'))),
    CONSTRAINT "ck_goal_progress_values" CHECK (((("target_value" is null) or ("target_value" >= 0)) and (("current_value" is null) or ("current_value" >= 0))))
 );
COMMENT ON TABLE "goal" IS '目标管理表';
COMMENT ON COLUMN "goal"."id" IS '目标ID';
COMMENT ON COLUMN "goal"."user_id" IS '所属用户ID，用于用户数据隔离';
COMMENT ON COLUMN "goal"."type" IS '目标类型：1=日目标，2=周目标，3=月度目标，4=季度目标，5=半年目标，6=年度目标，7=三年目标，8=五年目标，9=十年目标，10=人生目标';
COMMENT ON COLUMN "goal"."title" IS '目标标题';
COMMENT ON COLUMN "goal"."description" IS '目标描述';
COMMENT ON COLUMN "goal"."content" IS '目标详细内容/行动计划';
COMMENT ON COLUMN "goal"."status" IS '目标状态：not_started/in_progress/completed/on_hold';
COMMENT ON COLUMN "goal"."target_value" IS '目标值，非负数最多两位小数；NULL表示无量化目标';
COMMENT ON COLUMN "goal"."current_value" IS '当前值，非负数最多两位小数；自动模式为最近成功计算值，NULL表示未计算';
COMMENT ON COLUMN "goal"."year" IS '年份（用于年度目标筛选）';
COMMENT ON COLUMN "goal"."month" IS '月份（用于月度目标筛选）';
COMMENT ON COLUMN "goal"."day" IS '日期（用于日目标筛选）';
COMMENT ON COLUMN "goal"."parent_id" IS '父目标ID';
COMMENT ON COLUMN "goal"."start_date" IS '开始时间';
COMMENT ON COLUMN "goal"."end_date" IS '结束时间';
COMMENT ON COLUMN "goal"."completed_at" IS '完成时间';
COMMENT ON COLUMN "goal"."tags" IS '目标标签（JSON格式存储）';
COMMENT ON COLUMN "goal"."is_pinned" IS '是否固定到首页：0-否，1-是';
COMMENT ON COLUMN "goal"."pinned_sort_order" IS '首页固定排序，数值越小越靠前';
COMMENT ON COLUMN "goal"."is_deleted" IS '逻辑删除标记：0=未删除，1=已删除';
COMMENT ON COLUMN "goal"."create_time" IS '创建时间';
COMMENT ON COLUMN "goal"."update_time" IS '更新时间';
COMMENT ON COLUMN "goal"."create_user" IS '创建人ID';
COMMENT ON COLUMN "goal"."update_user" IS '更新人ID';
COMMENT ON COLUMN "goal"."progress_mode" IS '进度来源：manual=手动，graphql=自定义只读查询';
COMMENT ON COLUMN "goal"."progress_query" IS '自定义GraphQL查询文本，NULL表示未配置';
COMMENT ON COLUMN "goal"."progress_variables" IS '固定查询变量（JSON 对象）；NULL 表示空对象';
COMMENT ON COLUMN "goal"."progress_variable_bindings" IS '变量到目标日期上下文的绑定（JSON 对象）；NULL 表示不绑定';
COMMENT ON COLUMN "goal"."progress_value_path" IS '相对于GraphQL data的数值路径，支持字段及数组下标；NULL表示未配置';
COMMENT ON COLUMN "goal"."target_operator" IS '达标比较：gte=达到目标值，gt=严格超过目标值';
COMMENT ON COLUMN "goal"."progress_checked_at" IS '最近成功计算的本地时间，NULL表示尚未成功计算';
COMMENT ON COLUMN "goal"."progress_error" IS '最近计算错误，NULL表示无错误；失败保留成功快照';
COMMENT ON COLUMN "goal"."progress_refresh_id" IS '最近刷新请求UUID，仅内部使用，阻止同一配置下旧查询覆盖较新刷新；NULL表示未刷新';
COMMENT ON COLUMN "goal"."progress_version" IS '目标配置修订号，从0开始，每次编辑递增以拒绝过期计算';
CREATE INDEX "goal_idx_home_pinned" ON "goal" ("user_id", "is_deleted", "is_pinned", "pinned_sort_order", "id");
COMMENT ON INDEX "goal_idx_home_pinned" IS '业务检索索引：idx_home_pinned';
CREATE INDEX "goal_idx_user_id" ON "goal" ("user_id");
COMMENT ON INDEX "goal_idx_user_id" IS '业务检索索引：idx_user_id';

CREATE TABLE "honor_category" (
    "id" bigint NOT NULL,
    "user_id" bigint,
    "name" varchar(50) NOT NULL,
    "icon" varchar(255),
    "color" varchar(20),
    "sort_order" integer DEFAULT '0',
    "is_deleted" smallint DEFAULT '0' NOT NULL,
    "create_user" bigint,
    "create_time" timestamp(0) without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "update_user" bigint,
    "update_time" timestamp(0) without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    PRIMARY KEY ("id")
 );
COMMENT ON TABLE "honor_category" IS '荣誉分类表';
COMMENT ON COLUMN "honor_category"."id" IS '分类ID';
COMMENT ON COLUMN "honor_category"."user_id" IS '用户ID（NULL表示系统预设分类）';
COMMENT ON COLUMN "honor_category"."name" IS '分类名称';
COMMENT ON COLUMN "honor_category"."icon" IS '分类图标';
COMMENT ON COLUMN "honor_category"."color" IS '分类颜色';
COMMENT ON COLUMN "honor_category"."sort_order" IS '排序顺序';
COMMENT ON COLUMN "honor_category"."is_deleted" IS '逻辑删除标记：0=未删除，1=已删除';
COMMENT ON COLUMN "honor_category"."create_user" IS '创建人ID';
COMMENT ON COLUMN "honor_category"."create_time" IS '创建时间';
COMMENT ON COLUMN "honor_category"."update_user" IS '更新人ID';
COMMENT ON COLUMN "honor_category"."update_time" IS '更新时间';
CREATE INDEX "honor_category_idx_user_id" ON "honor_category" ("user_id");
COMMENT ON INDEX "honor_category_idx_user_id" IS '业务检索索引：idx_user_id';

CREATE TABLE "honor_record" (
    "id" bigint NOT NULL,
    "user_id" bigint NOT NULL,
    "title" varchar(200) NOT NULL,
    "description" text,
    "honor_date" date NOT NULL,
    "issuer" varchar(200),
    "level" varchar(20),
    "category_id" bigint,
    "custom_category" varchar(50),
    "tags" varchar(500),
    "is_top" smallint DEFAULT '0',
    "is_public" smallint DEFAULT '1',
    "sort_order" integer DEFAULT '0',
    "is_deleted" smallint DEFAULT '0' NOT NULL,
    "create_time" timestamp(0) without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "update_time" timestamp(0) without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "create_user" bigint,
    "update_user" bigint,
    PRIMARY KEY ("id")
 );
COMMENT ON TABLE "honor_record" IS '荣誉记录表';
COMMENT ON COLUMN "honor_record"."id" IS '记录ID';
COMMENT ON COLUMN "honor_record"."user_id" IS '用户ID';
COMMENT ON COLUMN "honor_record"."title" IS '荣誉标题';
COMMENT ON COLUMN "honor_record"."description" IS '荣誉描述';
COMMENT ON COLUMN "honor_record"."honor_date" IS '获得日期';
COMMENT ON COLUMN "honor_record"."issuer" IS '颁发机构/组织';
COMMENT ON COLUMN "honor_record"."level" IS '荣誉级别：1-校级，2-市级，3-省级，4-国家级，5-国际级';
COMMENT ON COLUMN "honor_record"."category_id" IS '所属分类ID（可为空）';
COMMENT ON COLUMN "honor_record"."custom_category" IS '自定义分类名称（当不选择预设分类时使用）';
COMMENT ON COLUMN "honor_record"."tags" IS '标签（JSON格式存储）';
COMMENT ON COLUMN "honor_record"."is_top" IS '是否置顶：0-否，1-是';
COMMENT ON COLUMN "honor_record"."is_public" IS '是否公开：0-私密，1-公开';
COMMENT ON COLUMN "honor_record"."sort_order" IS '排序顺序';
COMMENT ON COLUMN "honor_record"."is_deleted" IS '逻辑删除标记：0=未删除，1=已删除';
COMMENT ON COLUMN "honor_record"."create_time" IS '创建时间';
COMMENT ON COLUMN "honor_record"."update_time" IS '更新时间';
COMMENT ON COLUMN "honor_record"."create_user" IS '创建人ID';
COMMENT ON COLUMN "honor_record"."update_user" IS '更新人ID';
CREATE INDEX "honor_record_idx_category_id" ON "honor_record" ("category_id");
COMMENT ON INDEX "honor_record_idx_category_id" IS '业务检索索引：idx_category_id';
CREATE INDEX "honor_record_idx_honor_date" ON "honor_record" ("honor_date");
COMMENT ON INDEX "honor_record_idx_honor_date" IS '业务检索索引：idx_honor_date';
CREATE INDEX "honor_record_idx_user_id" ON "honor_record" ("user_id");
COMMENT ON INDEX "honor_record_idx_user_id" IS '业务检索索引：idx_user_id';

CREATE TABLE "image_import_task" (
    "id" bigint NOT NULL,
    "video_id" bigint NOT NULL,
    "cover_version" bigint NOT NULL,
    "source_url" varchar(1000) NOT NULL,
    "state" varchar(16) NOT NULL,
    "attempts" integer DEFAULT '0' NOT NULL,
    "next_attempt_at" timestamp(0) without time zone NOT NULL,
    "lease_token" varchar(32),
    "lease_until" timestamp(0) without time zone,
    "error_code" varchar(64),
    "create_user" bigint,
    "create_time" timestamp(0) without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "update_user" bigint,
    "update_time" timestamp(0) without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "is_deleted" smallint DEFAULT '0' NOT NULL,
    PRIMARY KEY ("id")
 );
COMMENT ON TABLE "image_import_task" IS '视频封面持久化导入任务';
COMMENT ON COLUMN "image_import_task"."id" IS '主键ID';
COMMENT ON COLUMN "image_import_task"."video_id" IS '关联视频ID，关联b_video.id';
COMMENT ON COLUMN "image_import_task"."cover_version" IS '入队时的封面版本，须与视频当前版本一致才可发布';
COMMENT ON COLUMN "image_import_task"."source_url" IS '本次任务抓取的原始封面URL';
COMMENT ON COLUMN "image_import_task"."state" IS '任务状态：PENDING=待执行，RUNNING=执行中，READY=完成，FAILED=失败，CANCELLED=已取消';
COMMENT ON COLUMN "image_import_task"."attempts" IS '领取执行次数，初始0，每次领取递增至最多5';
COMMENT ON COLUMN "image_import_task"."next_attempt_at" IS '首次可执行或退避后的下次重试时间';
COMMENT ON COLUMN "image_import_task"."lease_token" IS '本次执行租约令牌，未领取或已释放时为空';
COMMENT ON COLUMN "image_import_task"."lease_until" IS '执行租约到期时间，未领取或已释放时为空';
COMMENT ON COLUMN "image_import_task"."error_code" IS '最近一次失败码，重试成功后保留；尚未失败时为空';
COMMENT ON COLUMN "image_import_task"."create_user" IS '创建人用户ID';
COMMENT ON COLUMN "image_import_task"."create_time" IS '创建时间';
COMMENT ON COLUMN "image_import_task"."update_user" IS '最后更新人用户ID';
COMMENT ON COLUMN "image_import_task"."update_time" IS '最后更新时间';
COMMENT ON COLUMN "image_import_task"."is_deleted" IS '逻辑删除标记：0=未删除，1=已删除';
CREATE INDEX "image_import_task_idx_image_import_due" ON "image_import_task" ("state", "next_attempt_at", "lease_until");
COMMENT ON INDEX "image_import_task_idx_image_import_due" IS '业务检索索引：idx_image_import_due';
CREATE UNIQUE INDEX "image_import_task_uk_image_import_version" ON "image_import_task" ("video_id", "cover_version");
COMMENT ON INDEX "image_import_task_uk_image_import_version" IS '唯一性约束：uk_image_import_version';

CREATE TABLE "income" (
    "id" bigint NOT NULL,
    "amt" numeric(10,2) NOT NULL,
    "inc_date" date NOT NULL,
    "remark" varchar(255),
    "user_id" bigint NOT NULL,
    "create_user" bigint,
    "create_time" timestamp(0) without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "update_user" bigint,
    "update_time" timestamp(0) without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "is_deleted" smallint DEFAULT '0' NOT NULL,
    "inc_type_id" bigint NOT NULL,
    "tax" numeric(10,2),
    PRIMARY KEY ("id")
 );
COMMENT ON TABLE "income" IS '收入表';
COMMENT ON COLUMN "income"."id" IS '主键ID';
COMMENT ON COLUMN "income"."amt" IS '收入';
COMMENT ON COLUMN "income"."inc_date" IS '收入时间';
COMMENT ON COLUMN "income"."remark" IS '备注';
COMMENT ON COLUMN "income"."user_id" IS '用户id';
COMMENT ON COLUMN "income"."create_user" IS '创建人';
COMMENT ON COLUMN "income"."create_time" IS '创建时间';
COMMENT ON COLUMN "income"."update_user" IS '修改人';
COMMENT ON COLUMN "income"."update_time" IS '更新时间';
COMMENT ON COLUMN "income"."is_deleted" IS '逻辑删除标记：0=未删除，1=已删除';
COMMENT ON COLUMN "income"."inc_type_id" IS '收入类型';
COMMENT ON COLUMN "income"."tax" IS '收入税额，单位元；NULL表示未记录，0表示零税额';

CREATE TABLE "life_footprint" (
    "id" bigint NOT NULL,
    "user_id" bigint NOT NULL,
    "start_date" date NOT NULL,
    "end_date" date NOT NULL,
    "place_name" varchar(100) NOT NULL,
    "address" varchar(500),
    "city" varchar(100),
    "region_code" varchar(12),
    "longitude" numeric(10,6) NOT NULL,
    "latitude" numeric(9,6) NOT NULL,
    "coordinate_system" varchar(16) DEFAULT 'GCJ02' NOT NULL,
    "provider_uid" varchar(128),
    "remark" text,
    "create_user" bigint,
    "create_time" timestamp(0) without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "update_user" bigint,
    "update_time" timestamp(0) without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "is_deleted" smallint DEFAULT '0' NOT NULL,
    PRIMARY KEY ("id"),
    CONSTRAINT "ck_footprint_coordinate" CHECK (("coordinate_system" = 'GCJ02')),
    CONSTRAINT "ck_footprint_end" CHECK (("end_date" >= "start_date")),
    CONSTRAINT "ck_footprint_lat" CHECK (("latitude" between -(90) and 90)),
    CONSTRAINT "ck_footprint_lng" CHECK (("longitude" between -(180) and 180))
 );
COMMENT ON TABLE "life_footprint" IS '生活足迹，记录长期居住地点';
COMMENT ON COLUMN "life_footprint"."id" IS '足迹ID，由应用生成雪花ID';
COMMENT ON COLUMN "life_footprint"."user_id" IS '所属用户ID，用于用户数据隔离';
COMMENT ON COLUMN "life_footprint"."start_date" IS '开始日期，精确到日，格式YYYY-MM-DD';
COMMENT ON COLUMN "life_footprint"."end_date" IS '结束日期，9999-12-31表示至今';
COMMENT ON COLUMN "life_footprint"."place_name" IS '地点名称，可填写住所名称或自定义名称';
COMMENT ON COLUMN "life_footprint"."address" IS '详细地址，可补充楼栋和房号，未填写时为空';
COMMENT ON COLUMN "life_footprint"."city" IS '地点所属城市名称，未获取时为空';
COMMENT ON COLUMN "life_footprint"."region_code" IS '所选最深层地区编码，可停在省市区县或乡镇，未选时为空';
COMMENT ON COLUMN "life_footprint"."longitude" IS '经度，GCJ02坐标，单位度，范围-180至180';
COMMENT ON COLUMN "life_footprint"."latitude" IS '纬度，GCJ02坐标，单位度，范围-90至90';
COMMENT ON COLUMN "life_footprint"."coordinate_system" IS '坐标系，固定为GCJ02，写入前须完成坐标转换';
COMMENT ON COLUMN "life_footprint"."provider_uid" IS '百度地点UID，手动选点或未获取时为空';
COMMENT ON COLUMN "life_footprint"."remark" IS '备注，未填写时为空，应用限制5000字符';
COMMENT ON COLUMN "life_footprint"."create_user" IS '创建人用户ID，未记录时为空';
COMMENT ON COLUMN "life_footprint"."create_time" IS '创建时间，由应用写入，未记录时为空';
COMMENT ON COLUMN "life_footprint"."update_user" IS '最后更新人用户ID，未记录时为空';
COMMENT ON COLUMN "life_footprint"."update_time" IS '最后更新时间，由应用写入，未记录时为空';
COMMENT ON COLUMN "life_footprint"."is_deleted" IS '逻辑删除标记：0=未删除，1=已删除';
CREATE INDEX "life_footprint_idx_footprint_user_start" ON "life_footprint" ("user_id", "is_deleted", "start_date");
COMMENT ON INDEX "life_footprint_idx_footprint_user_start" IS '业务检索索引：idx_footprint_user_start';

CREATE TABLE "login_log" (
    "id" bigint NOT NULL,
    "user_id" bigint,
    "username" varchar(50) NOT NULL,
    "created_at" timestamp(0) without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "ip_address" varchar(45) NOT NULL,
    PRIMARY KEY ("id")
 );
COMMENT ON TABLE "login_log" IS '登录日志';
COMMENT ON COLUMN "login_log"."id" IS '登录日志ID';
COMMENT ON COLUMN "login_log"."user_id" IS '用户ID';
COMMENT ON COLUMN "login_log"."username" IS '用户名';
COMMENT ON COLUMN "login_log"."created_at" IS '创建时间';
COMMENT ON COLUMN "login_log"."ip_address" IS 'IP地址';

CREATE TABLE "mail_log" (
    "id" bigint NOT NULL,
    "send_to" varchar(100) NOT NULL,
    "subject" varchar(255),
    "biz_type" varchar(50),
    "status" smallint DEFAULT '1' NOT NULL,
    "error_msg" varchar(255),
    "ip_address" varchar(45),
    "create_time" timestamp(0) without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    PRIMARY KEY ("id")
 );
COMMENT ON TABLE "mail_log" IS '邮件发送记录表';
COMMENT ON COLUMN "mail_log"."id" IS '主键ID';
COMMENT ON COLUMN "mail_log"."send_to" IS '接收者邮箱';
COMMENT ON COLUMN "mail_log"."subject" IS '邮件标题';
COMMENT ON COLUMN "mail_log"."biz_type" IS '业务类型：register-注册, login-登录, reset_pwd-重置密码, system_notice-系统通知等';
COMMENT ON COLUMN "mail_log"."status" IS '发送状态：1-成功，0-失败';
COMMENT ON COLUMN "mail_log"."error_msg" IS '失败原因';
COMMENT ON COLUMN "mail_log"."ip_address" IS '请求IP';
COMMENT ON COLUMN "mail_log"."create_time" IS '创建时间';
CREATE INDEX "mail_log_idx_create_time" ON "mail_log" ("create_time");
COMMENT ON INDEX "mail_log_idx_create_time" IS '业务检索索引：idx_create_time';
CREATE INDEX "mail_log_idx_send_to_biz_type" ON "mail_log" ("send_to", "biz_type");
COMMENT ON INDEX "mail_log_idx_send_to_biz_type" IS '业务检索索引：idx_send_to_biz_type';

CREATE TABLE "mbti_result" (
    "id" bigint NOT NULL,
    "user_id" bigint NOT NULL,
    "test_id" varchar(255),
    "mbti_type" varchar(10),
    "raw_result" text,
    "results_page" varchar(500),
    "create_user" bigint,
    "create_time" timestamp(0) without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "update_user" bigint,
    "update_time" timestamp(0) without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "is_deleted" smallint DEFAULT '0' NOT NULL,
    PRIMARY KEY ("id")
 );
COMMENT ON TABLE "mbti_result" IS 'MBTI测试结果表';
COMMENT ON COLUMN "mbti_result"."id" IS '主键ID';
COMMENT ON COLUMN "mbti_result"."user_id" IS '用户ID';
COMMENT ON COLUMN "mbti_result"."test_id" IS '测试ID';
COMMENT ON COLUMN "mbti_result"."mbti_type" IS 'MBTI类型';
COMMENT ON COLUMN "mbti_result"."raw_result" IS '原始结果数据(JSON)';
COMMENT ON COLUMN "mbti_result"."results_page" IS '官方结果页面URL';
COMMENT ON COLUMN "mbti_result"."create_user" IS '创建人';
COMMENT ON COLUMN "mbti_result"."create_time" IS '创建时间';
COMMENT ON COLUMN "mbti_result"."update_user" IS '更新人';
COMMENT ON COLUMN "mbti_result"."update_time" IS '更新时间';
COMMENT ON COLUMN "mbti_result"."is_deleted" IS '逻辑删除标记：0=未删除，1=已删除';
CREATE INDEX "mbti_result_idx_mbti_type" ON "mbti_result" ("mbti_type");
COMMENT ON INDEX "mbti_result_idx_mbti_type" IS '业务检索索引：idx_mbti_type';
CREATE INDEX "mbti_result_idx_test_id" ON "mbti_result" ("test_id");
COMMENT ON INDEX "mbti_result_idx_test_id" IS '业务检索索引：idx_test_id';
CREATE INDEX "mbti_result_idx_user_id" ON "mbti_result" ("user_id");
COMMENT ON INDEX "mbti_result_idx_user_id" IS '业务检索索引：idx_user_id';

CREATE TABLE "membership_provider" (
    "id" bigint NOT NULL,
    "name" varchar(100) NOT NULL,
    "code" varchar(50) COLLATE "C" NOT NULL,
    "category" varchar(50) DEFAULT 'other' NOT NULL,
    "icon_key" varchar(64),
    "sort_order" integer DEFAULT '0' NOT NULL,
    "is_enabled" smallint DEFAULT '1' NOT NULL,
    "create_user" bigint,
    "create_time" timestamp(0) without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "update_user" bigint,
    "update_time" timestamp(0) without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "is_deleted" smallint DEFAULT '0' NOT NULL,
    "active_flag" smallint GENERATED ALWAYS AS ((case when ("is_deleted" = 0) then 1 else NULL end)) STORED,
    PRIMARY KEY ("id")
 );
COMMENT ON TABLE "membership_provider" IS '会员平台表';
COMMENT ON COLUMN "membership_provider"."id" IS '主键ID';
COMMENT ON COLUMN "membership_provider"."name" IS '平台名称';
COMMENT ON COLUMN "membership_provider"."code" IS '稳定编码';
COMMENT ON COLUMN "membership_provider"."category" IS '默认分类:video/music/shopping/lifestyle/cloud/reading/study/game/AI/other';
COMMENT ON COLUMN "membership_provider"."icon_key" IS '内置图标标识，不支持上传';
COMMENT ON COLUMN "membership_provider"."sort_order" IS '展示顺序';
COMMENT ON COLUMN "membership_provider"."is_enabled" IS '是否启用:1-启用 0-停用';
COMMENT ON COLUMN "membership_provider"."create_user" IS '创建人';
COMMENT ON COLUMN "membership_provider"."create_time" IS '创建时间';
COMMENT ON COLUMN "membership_provider"."update_user" IS '更新人';
COMMENT ON COLUMN "membership_provider"."update_time" IS '更新时间';
COMMENT ON COLUMN "membership_provider"."is_deleted" IS '是否删除';
COMMENT ON COLUMN "membership_provider"."active_flag" IS '仅有效平台参与编码唯一约束';
CREATE INDEX "membership_provider_idx_membership_provider_enabled_sort" ON "membership_provider" ("is_enabled", "sort_order");
COMMENT ON INDEX "membership_provider_idx_membership_provider_enabled_sort" IS '业务检索索引：idx_membership_provider_enabled_sort';
CREATE UNIQUE INDEX "membership_provider_uk_membership_provider_code" ON "membership_provider" ("code", "active_flag");
COMMENT ON INDEX "membership_provider_uk_membership_provider_code" IS '唯一性约束：uk_membership_provider_code';

CREATE TABLE "membership_record" (
    "id" bigint NOT NULL,
    "user_id" bigint NOT NULL,
    "name" varchar(100) NOT NULL,
    "category" varchar(50) DEFAULT 'other' NOT NULL,
    "provider_id" bigint,
    "provider" varchar(100),
    "icon" varchar(50),
    "color" varchar(50),
    "start_date" date,
    "expiry_date" date NOT NULL,
    "price" numeric(10,2),
    "billing_cycle" varchar(20) DEFAULT 'month' NOT NULL,
    "monthly_amount" numeric(10,2) DEFAULT '0.00' NOT NULL,
    "auto_renew" smallint DEFAULT '0',
    "remark" varchar(500),
    "create_user" bigint,
    "create_time" timestamp(0) without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "update_user" bigint,
    "update_time" timestamp(0) without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "is_deleted" smallint DEFAULT '0' NOT NULL,
    PRIMARY KEY ("id")
 );
COMMENT ON TABLE "membership_record" IS '会员记录表';
COMMENT ON COLUMN "membership_record"."id" IS '主键ID';
COMMENT ON COLUMN "membership_record"."user_id" IS '用户ID';
COMMENT ON COLUMN "membership_record"."name" IS '会员名称';
COMMENT ON COLUMN "membership_record"."category" IS '分类:video/music/shopping/lifestyle/cloud/reading/study/game/AI/other';
COMMENT ON COLUMN "membership_record"."provider_id" IS '会员平台ID，未关联平台时可为空';
COMMENT ON COLUMN "membership_record"."provider" IS '平台/服务商';
COMMENT ON COLUMN "membership_record"."icon" IS 'emoji图标';
COMMENT ON COLUMN "membership_record"."color" IS '卡片背景色class';
COMMENT ON COLUMN "membership_record"."start_date" IS '开通日期';
COMMENT ON COLUMN "membership_record"."expiry_date" IS '到期日期';
COMMENT ON COLUMN "membership_record"."price" IS '支付金额';
COMMENT ON COLUMN "membership_record"."billing_cycle" IS '计费周期:week/two_weeks/month/quarter/half_year/year';
COMMENT ON COLUMN "membership_record"."monthly_amount" IS '折算后的月均成本，可手动修改';
COMMENT ON COLUMN "membership_record"."auto_renew" IS '是否自动续费:0-否 1-是';
COMMENT ON COLUMN "membership_record"."remark" IS '备注，未填写时为空';
COMMENT ON COLUMN "membership_record"."create_user" IS '创建人';
COMMENT ON COLUMN "membership_record"."create_time" IS '创建时间';
COMMENT ON COLUMN "membership_record"."update_user" IS '更新人';
COMMENT ON COLUMN "membership_record"."update_time" IS '更新时间';
COMMENT ON COLUMN "membership_record"."is_deleted" IS '逻辑删除标记：0=未删除，1=已删除';
CREATE INDEX "membership_record_idx_expiry_date" ON "membership_record" ("expiry_date");
COMMENT ON INDEX "membership_record_idx_expiry_date" IS '业务检索索引：idx_expiry_date';
CREATE INDEX "membership_record_idx_membership_provider_id" ON "membership_record" ("provider_id");
COMMENT ON INDEX "membership_record_idx_membership_provider_id" IS '业务检索索引：idx_membership_provider_id';
CREATE INDEX "membership_record_idx_user_id" ON "membership_record" ("user_id");
COMMENT ON INDEX "membership_record_idx_user_id" IS '业务检索索引：idx_user_id';

CREATE TABLE "memo" (
    "id" bigint NOT NULL,
    "user_id" bigint NOT NULL,
    "title" varchar(100),
    "content" text,
    "hidden_content" smallint DEFAULT '0',
    "create_user" bigint,
    "create_time" timestamp(0) without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "update_user" bigint,
    "update_time" timestamp(0) without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "is_deleted" smallint DEFAULT '0' NOT NULL,
    PRIMARY KEY ("id")
 );
COMMENT ON TABLE "memo" IS '备忘录表';
COMMENT ON COLUMN "memo"."id" IS '主键ID';
COMMENT ON COLUMN "memo"."user_id" IS '用户ID';
COMMENT ON COLUMN "memo"."title" IS '备忘录标题，未填写时为空';
COMMENT ON COLUMN "memo"."content" IS '备忘录内容';
COMMENT ON COLUMN "memo"."hidden_content" IS '是否隐藏内容';
COMMENT ON COLUMN "memo"."create_user" IS '创建人';
COMMENT ON COLUMN "memo"."create_time" IS '创建时间';
COMMENT ON COLUMN "memo"."update_user" IS '更新人';
COMMENT ON COLUMN "memo"."update_time" IS '更新时间';
COMMENT ON COLUMN "memo"."is_deleted" IS '逻辑删除标记：0=未删除，1=已删除';

CREATE TABLE "message" (
    "id" bigint NOT NULL,
    "sender_id" bigint NOT NULL,
    "receiver_id" bigint NOT NULL,
    "title" varchar(255) NOT NULL,
    "content" text,
    "type" integer DEFAULT '0' NOT NULL,
    "is_read" smallint DEFAULT '0' NOT NULL,
    "create_user" bigint,
    "create_time" timestamp(0) without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "update_user" bigint,
    "update_time" timestamp(0) without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "is_deleted" smallint DEFAULT '0' NOT NULL,
    PRIMARY KEY ("id")
 );
COMMENT ON TABLE "message" IS '消息表';
COMMENT ON COLUMN "message"."id" IS '主键ID';
COMMENT ON COLUMN "message"."sender_id" IS '发送用户ID';
COMMENT ON COLUMN "message"."receiver_id" IS '接收用户ID';
COMMENT ON COLUMN "message"."title" IS '消息标题';
COMMENT ON COLUMN "message"."content" IS '消息内容';
COMMENT ON COLUMN "message"."type" IS '消息类型: 0-系统通知, 1-用户消息';
COMMENT ON COLUMN "message"."is_read" IS '是否已读: 0-未读, 1-已读';
COMMENT ON COLUMN "message"."create_user" IS '创建人';
COMMENT ON COLUMN "message"."create_time" IS '创建时间';
COMMENT ON COLUMN "message"."update_user" IS '更新人';
COMMENT ON COLUMN "message"."update_time" IS '更新时间';
COMMENT ON COLUMN "message"."is_deleted" IS '是否删除: 0-未删除, 1-已删除';
CREATE INDEX "message_idx_create_time" ON "message" ("create_time");
COMMENT ON INDEX "message_idx_create_time" IS '业务检索索引：idx_create_time';
CREATE INDEX "message_idx_receiver_is_read" ON "message" ("receiver_id", "is_read");
COMMENT ON INDEX "message_idx_receiver_is_read" IS '业务检索索引：idx_receiver_is_read';

CREATE TABLE "milestone" (
    "id" bigint NOT NULL,
    "user_id" bigint NOT NULL,
    "title" varchar(255) NOT NULL,
    "description" text,
    "date" date NOT NULL,
    "end_date" date,
    "type" varchar(50) DEFAULT 'other' NOT NULL,
    "tags" jsonb,
    "create_user" bigint,
    "create_time" timestamp(0) without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "update_user" bigint,
    "update_time" timestamp(0) without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "is_deleted" smallint DEFAULT '0' NOT NULL,
    PRIMARY KEY ("id")
 );
COMMENT ON TABLE "milestone" IS '里程碑表';
COMMENT ON COLUMN "milestone"."id" IS '主键ID';
COMMENT ON COLUMN "milestone"."user_id" IS '用户ID';
COMMENT ON COLUMN "milestone"."title" IS '标题';
COMMENT ON COLUMN "milestone"."description" IS '详细描述';
COMMENT ON COLUMN "milestone"."date" IS '开始日期';
COMMENT ON COLUMN "milestone"."end_date" IS '结束日期';
COMMENT ON COLUMN "milestone"."type" IS '类型: work, study, life, other';
COMMENT ON COLUMN "milestone"."tags" IS '标签数组';
COMMENT ON COLUMN "milestone"."create_user" IS '创建人';
COMMENT ON COLUMN "milestone"."create_time" IS '创建时间';
COMMENT ON COLUMN "milestone"."update_user" IS '更新人';
COMMENT ON COLUMN "milestone"."update_time" IS '更新时间';
COMMENT ON COLUMN "milestone"."is_deleted" IS '是否删除';
CREATE INDEX "milestone_idx_user_date" ON "milestone" ("user_id", "date");
COMMENT ON INDEX "milestone_idx_user_date" IS '业务检索索引：idx_user_date';

CREATE TABLE "movie" (
    "id" bigint NOT NULL,
    "title" varchar(255) NOT NULL,
    "type" smallint NOT NULL,
    "director" varchar(100),
    "url" varchar(500),
    "status" varchar(32) DEFAULT 'not_started',
    "total_progress" integer DEFAULT '0',
    "current_progress" integer DEFAULT '0',
    "start_time" timestamp(0) without time zone,
    "finish_time" timestamp(0) without time zone,
    "rating" smallint,
    "remark" varchar(1000),
    "user_id" bigint NOT NULL,
    "create_time" timestamp(0) without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "update_time" timestamp(0) without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "create_user" bigint,
    "update_user" bigint,
    "is_deleted" smallint DEFAULT '0' NOT NULL,
    "file_id" varchar(32),
    PRIMARY KEY ("id")
 );
COMMENT ON TABLE "movie" IS '影视记录表';
COMMENT ON COLUMN "movie"."id" IS '主键';
COMMENT ON COLUMN "movie"."title" IS '影视名称';
COMMENT ON COLUMN "movie"."type" IS '类型：1-电影，2-剧集，3-动漫，4-纪录片，5-其他';
COMMENT ON COLUMN "movie"."director" IS '导演/演员';
COMMENT ON COLUMN "movie"."url" IS '链接（主要针对豆瓣等外部链接）';
COMMENT ON COLUMN "movie"."status" IS '状态：not_started/in_progress/completed/on_hold';
COMMENT ON COLUMN "movie"."total_progress" IS '总进度（总集数或时长）';
COMMENT ON COLUMN "movie"."current_progress" IS '当前进度（当前集数或观看时长）';
COMMENT ON COLUMN "movie"."start_time" IS '开始观看时间';
COMMENT ON COLUMN "movie"."finish_time" IS '看完时间';
COMMENT ON COLUMN "movie"."rating" IS '个人评分：1-5';
COMMENT ON COLUMN "movie"."remark" IS '短评/备注';
COMMENT ON COLUMN "movie"."user_id" IS '归属用户ID';
COMMENT ON COLUMN "movie"."create_time" IS '创建时间';
COMMENT ON COLUMN "movie"."update_time" IS '更新时间';
COMMENT ON COLUMN "movie"."create_user" IS '创建人';
COMMENT ON COLUMN "movie"."update_user" IS '更新人';
COMMENT ON COLUMN "movie"."is_deleted" IS '逻辑删除标记：0=未删除，1=已删除';
COMMENT ON COLUMN "movie"."file_id" IS '封面文件ID(UUID)';
CREATE INDEX "movie_idx_user_id" ON "movie" ("user_id");
COMMENT ON INDEX "movie_idx_user_id" IS '业务检索索引：idx_user_id';

CREATE TABLE "notification_channel_config" (
    "id" bigint NOT NULL,
    "user_id" bigint NOT NULL,
    "channel" varchar(32) NOT NULL,
    "is_enabled" smallint DEFAULT '0' NOT NULL,
    "app_id" varchar(128) NOT NULL,
    "app_secret_ciphertext" text NOT NULL,
    "receiver_open_id" varchar(128),
    "receiver_name" varchar(128),
    "create_user" bigint,
    "create_time" timestamp(0) without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "update_user" bigint,
    "update_time" timestamp(0) without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "is_deleted" smallint DEFAULT '0' NOT NULL,
    PRIMARY KEY ("id")
 );
COMMENT ON TABLE "notification_channel_config" IS '用户通知渠道配置';
COMMENT ON COLUMN "notification_channel_config"."id" IS 'ID';
COMMENT ON COLUMN "notification_channel_config"."user_id" IS 'AIO Life 用户 ID';
COMMENT ON COLUMN "notification_channel_config"."channel" IS '通知渠道：FEISHU';
COMMENT ON COLUMN "notification_channel_config"."is_enabled" IS '是否启用：1=启用，0=停用';
COMMENT ON COLUMN "notification_channel_config"."app_id" IS '用户自己的飞书应用 App ID';
COMMENT ON COLUMN "notification_channel_config"."app_secret_ciphertext" IS '加密的飞书应用 App Secret';
COMMENT ON COLUMN "notification_channel_config"."receiver_open_id" IS '接收用户在该应用下的 open_id';
COMMENT ON COLUMN "notification_channel_config"."receiver_name" IS '接收用户名称';
COMMENT ON COLUMN "notification_channel_config"."create_user" IS '创建人用户ID';
COMMENT ON COLUMN "notification_channel_config"."create_time" IS '创建时间';
COMMENT ON COLUMN "notification_channel_config"."update_user" IS '最后更新人用户ID';
COMMENT ON COLUMN "notification_channel_config"."update_time" IS '最后更新时间';
COMMENT ON COLUMN "notification_channel_config"."is_deleted" IS '逻辑删除标记：0=未删除，1=已删除';

CREATE TABLE "notification_delivery" (
    "id" bigint NOT NULL,
    "dedup_key" varchar(128) NOT NULL,
    "user_id" bigint NOT NULL,
    "biz_type" varchar(64) NOT NULL,
    "channel" varchar(32) NOT NULL,
    "status" varchar(16) NOT NULL,
    "payload_ciphertext" text NOT NULL,
    "retry_count" integer DEFAULT '0' NOT NULL,
    "next_retry_time" timestamp(0) without time zone,
    "provider_code" varchar(64),
    "error_message" varchar(512),
    "create_user" bigint,
    "create_time" timestamp(0) without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "update_user" bigint,
    "update_time" timestamp(0) without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "is_deleted" smallint DEFAULT '0' NOT NULL,
    PRIMARY KEY ("id")
 );
COMMENT ON TABLE "notification_delivery" IS '通知投递记录';
COMMENT ON COLUMN "notification_delivery"."id" IS 'ID';
COMMENT ON COLUMN "notification_delivery"."dedup_key" IS '通知事件去重标识，与用户ID和渠道共同确定同一次投递';
COMMENT ON COLUMN "notification_delivery"."user_id" IS '所属用户ID，用于用户数据隔离';
COMMENT ON COLUMN "notification_delivery"."biz_type" IS '通知业务类型：LEETCODE_REMINDER=未打卡提醒，LEETCODE_DAILY=每日一题，FEEDBACK_ADMIN=新反馈，FEEDBACK_REPLY=反馈回复';
COMMENT ON COLUMN "notification_delivery"."channel" IS '投递渠道，当前持久化重试由FEISHU飞书渠道使用';
COMMENT ON COLUMN "notification_delivery"."status" IS 'PENDING/SUCCESS/RETRY/FAILED';
COMMENT ON COLUMN "notification_delivery"."payload_ciphertext" IS '重试所需的加密消息载荷';
COMMENT ON COLUMN "notification_delivery"."retry_count" IS '累计发送失败次数，初始0，每次失败递增';
COMMENT ON COLUMN "notification_delivery"."next_retry_time" IS '下次重试时间，未安排重试时为空';
COMMENT ON COLUMN "notification_delivery"."provider_code" IS '最近渠道返回码或内部失败码，尚未返回时为空';
COMMENT ON COLUMN "notification_delivery"."error_message" IS '最近投递失败原因，成功或尚未失败时为空';
COMMENT ON COLUMN "notification_delivery"."create_user" IS '创建人用户ID';
COMMENT ON COLUMN "notification_delivery"."create_time" IS '创建时间';
COMMENT ON COLUMN "notification_delivery"."update_user" IS '最后更新人用户ID';
COMMENT ON COLUMN "notification_delivery"."update_time" IS '最后更新时间';
COMMENT ON COLUMN "notification_delivery"."is_deleted" IS '逻辑删除标记：0=未删除，1=已删除';
CREATE INDEX "notification_delivery_idx_retry" ON "notification_delivery" ("status", "next_retry_time");
COMMENT ON INDEX "notification_delivery_idx_retry" IS '业务检索索引：idx_retry';

CREATE TABLE "notification_preference" (
    "id" bigint NOT NULL,
    "user_id" bigint NOT NULL,
    "biz_type" varchar(64) NOT NULL,
    "channel" varchar(32) NOT NULL,
    "is_enabled" smallint DEFAULT '1' NOT NULL,
    "create_user" bigint,
    "create_time" timestamp(0) without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "update_user" bigint,
    "update_time" timestamp(0) without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "is_deleted" smallint DEFAULT '0' NOT NULL,
    PRIMARY KEY ("id")
 );
COMMENT ON TABLE "notification_preference" IS '用户通知偏好';
COMMENT ON COLUMN "notification_preference"."id" IS 'ID';
COMMENT ON COLUMN "notification_preference"."user_id" IS '所属用户ID，用于用户数据隔离';
COMMENT ON COLUMN "notification_preference"."biz_type" IS '通知业务类型：LEETCODE_REMINDER=未打卡提醒，LEETCODE_DAILY=每日一题，FEEDBACK_ADMIN=新反馈，FEEDBACK_REPLY=反馈回复';
COMMENT ON COLUMN "notification_preference"."channel" IS '通知渠道：FEISHU';
COMMENT ON COLUMN "notification_preference"."is_enabled" IS '是否启用：1=启用，0=停用';
COMMENT ON COLUMN "notification_preference"."create_user" IS '创建人用户ID';
COMMENT ON COLUMN "notification_preference"."create_time" IS '创建时间';
COMMENT ON COLUMN "notification_preference"."update_user" IS '最后更新人用户ID';
COMMENT ON COLUMN "notification_preference"."update_time" IS '最后更新时间';
COMMENT ON COLUMN "notification_preference"."is_deleted" IS '逻辑删除标记：0=未删除，1=已删除';

CREATE TABLE "password_vault" (
    "id" bigint NOT NULL,
    "user_id" bigint NOT NULL,
    "title" varchar(100) NOT NULL,
    "website" varchar(255),
    "category" varchar(50) DEFAULT '其他',
    "username" text,
    "password" text,
    "salt" varchar(64) NOT NULL,
    "remark" text,
    "favorite" smallint DEFAULT '0',
    "is_deleted" smallint DEFAULT '0' NOT NULL,
    "create_time" timestamp(0) without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "update_time" timestamp(0) without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "create_user" bigint,
    "update_user" bigint,
    PRIMARY KEY ("id")
 );
COMMENT ON TABLE "password_vault" IS '密码库表（密码及备注由客户端加密，服务端不进行加解密）';
COMMENT ON COLUMN "password_vault"."id" IS '记录ID';
COMMENT ON COLUMN "password_vault"."user_id" IS '所属用户ID';
COMMENT ON COLUMN "password_vault"."title" IS '标题，如 GitHub';
COMMENT ON COLUMN "password_vault"."website" IS '网站/应用名';
COMMENT ON COLUMN "password_vault"."category" IS '分类：工作/生活/金融/社交/其他';
COMMENT ON COLUMN "password_vault"."username" IS '账号内容，由客户端提交，服务端不进行加解密';
COMMENT ON COLUMN "password_vault"."password" IS '客户端加密的密码密文；服务端不进行加解密';
COMMENT ON COLUMN "password_vault"."salt" IS 'PBKDF2盐值，每条记录唯一（前端生成）';
COMMENT ON COLUMN "password_vault"."remark" IS '客户端加密的备注密文；服务端不进行加解密';
COMMENT ON COLUMN "password_vault"."favorite" IS '是否收藏';
COMMENT ON COLUMN "password_vault"."is_deleted" IS '逻辑删除标记：0=未删除，1=已删除';
COMMENT ON COLUMN "password_vault"."create_time" IS '创建时间';
COMMENT ON COLUMN "password_vault"."update_time" IS '更新时间';
COMMENT ON COLUMN "password_vault"."create_user" IS '创建人ID';
COMMENT ON COLUMN "password_vault"."update_user" IS '更新人ID';
CREATE INDEX "password_vault_idx_user_id" ON "password_vault" ("user_id");
COMMENT ON INDEX "password_vault_idx_user_id" IS '业务检索索引：idx_user_id';

CREATE TABLE "performance" (
    "id" bigint NOT NULL,
    "user_id" bigint NOT NULL,
    "performance_name" varchar(100) NOT NULL,
    "performer" varchar(50),
    "performance_type" varchar(50) NOT NULL,
    "performance_date" timestamp(0) without time zone NOT NULL,
    "city" varchar(50) NOT NULL,
    "venue" varchar(100) NOT NULL,
    "ticket_price" numeric(10,2) NOT NULL,
    "seat_info" varchar(100),
    "duration_minutes" integer,
    "rating" smallint,
    "review" text,
    "purchase_platform" varchar(50),
    "order_number" varchar(50) COLLATE "C",
    "create_user" bigint,
    "create_time" timestamp(0) without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "update_user" bigint,
    "update_time" timestamp(0) without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "is_deleted" smallint DEFAULT '0' NOT NULL,
    PRIMARY KEY ("id")
 );
COMMENT ON TABLE "performance" IS '演出表';
COMMENT ON COLUMN "performance"."id" IS '唯一标识';
COMMENT ON COLUMN "performance"."user_id" IS '所属用户ID';
COMMENT ON COLUMN "performance"."performance_name" IS '演出名称';
COMMENT ON COLUMN "performance"."performer" IS '演出者或团体名称，未填写时为空';
COMMENT ON COLUMN "performance"."performance_type" IS '演出类型(演唱会/话剧/音乐会等)';
COMMENT ON COLUMN "performance"."performance_date" IS '演出日期';
COMMENT ON COLUMN "performance"."city" IS '演出城市';
COMMENT ON COLUMN "performance"."venue" IS '演出地点';
COMMENT ON COLUMN "performance"."ticket_price" IS '票价';
COMMENT ON COLUMN "performance"."seat_info" IS '座位信息';
COMMENT ON COLUMN "performance"."duration_minutes" IS '演出时长（分钟）';
COMMENT ON COLUMN "performance"."rating" IS '演出评分(1-5)';
COMMENT ON COLUMN "performance"."review" IS '演出评价';
COMMENT ON COLUMN "performance"."purchase_platform" IS '购票平台';
COMMENT ON COLUMN "performance"."order_number" IS '购票订单号';
COMMENT ON COLUMN "performance"."create_user" IS '创建人ID';
COMMENT ON COLUMN "performance"."create_time" IS '创建时间';
COMMENT ON COLUMN "performance"."update_user" IS '更新人ID';
COMMENT ON COLUMN "performance"."update_time" IS '更新时间';
COMMENT ON COLUMN "performance"."is_deleted" IS '逻辑删除标记：0=未删除，1=已删除';
CREATE INDEX "performance_idx_performance_user_deleted" ON "performance" ("user_id", "is_deleted", "id");
COMMENT ON INDEX "performance_idx_performance_user_deleted" IS '业务检索索引：idx_performance_user_deleted';

CREATE TABLE "read_record" (
    "id" bigint NOT NULL,
    "title" varchar(255) NOT NULL,
    "type" smallint NOT NULL,
    "author" varchar(100),
    "url" varchar(500),
    "status" varchar(32) DEFAULT 'not_started',
    "total_progress" integer DEFAULT '0',
    "current_progress" integer DEFAULT '0',
    "start_time" timestamp(0) without time zone,
    "finish_time" timestamp(0) without time zone,
    "remark" varchar(1000),
    "user_id" bigint NOT NULL,
    "create_time" timestamp(0) without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "update_time" timestamp(0) without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "create_user" bigint,
    "update_user" bigint,
    "is_deleted" smallint DEFAULT '0' NOT NULL,
    "file_id" varchar(32),
    PRIMARY KEY ("id")
 );
COMMENT ON TABLE "read_record" IS '阅读记录表';
COMMENT ON COLUMN "read_record"."id" IS '主键';
COMMENT ON COLUMN "read_record"."title" IS '书名或文章标题';
COMMENT ON COLUMN "read_record"."type" IS '类型：1-书籍，2-文章/网页';
COMMENT ON COLUMN "read_record"."author" IS '作者';
COMMENT ON COLUMN "read_record"."url" IS '链接（主要针对文章/网页或豆瓣链接）';
COMMENT ON COLUMN "read_record"."status" IS '状态：not_started/in_progress/completed/on_hold';
COMMENT ON COLUMN "read_record"."total_progress" IS '总进度（总页数或100表示百分比）';
COMMENT ON COLUMN "read_record"."current_progress" IS '当前进度（当前页数或当前百分比）';
COMMENT ON COLUMN "read_record"."start_time" IS '开始阅读时间';
COMMENT ON COLUMN "read_record"."finish_time" IS '完成时间';
COMMENT ON COLUMN "read_record"."remark" IS '备注/读后感';
COMMENT ON COLUMN "read_record"."user_id" IS '归属用户ID';
COMMENT ON COLUMN "read_record"."create_time" IS '创建时间';
COMMENT ON COLUMN "read_record"."update_time" IS '更新时间';
COMMENT ON COLUMN "read_record"."create_user" IS '创建人';
COMMENT ON COLUMN "read_record"."update_user" IS '更新人';
COMMENT ON COLUMN "read_record"."is_deleted" IS '逻辑删除标记：0=未删除，1=已删除';
COMMENT ON COLUMN "read_record"."file_id" IS '封面文件ID(UUID)';
CREATE INDEX "read_record_idx_user_id" ON "read_record" ("user_id");
COMMENT ON INDEX "read_record_idx_user_id" IS '业务检索索引：idx_user_id';

CREATE TABLE "storage_object" (
    "id" bigint NOT NULL,
    "bucket" varchar(63) NOT NULL,
    "object_key" varchar(255) COLLATE "C" NOT NULL,
    "sha256" char(64) NOT NULL,
    "file_size" bigint NOT NULL,
    "content_type" varchar(100) NOT NULL,
    "create_user" bigint,
    "create_time" timestamp(0) without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "update_user" bigint,
    "update_time" timestamp(0) without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "is_deleted" smallint DEFAULT '0' NOT NULL,
    PRIMARY KEY ("id")
 );
COMMENT ON TABLE "storage_object" IS '不可变图片对象';
COMMENT ON COLUMN "storage_object"."id" IS '主键ID';
COMMENT ON COLUMN "storage_object"."bucket" IS '对象存储桶名称，视频封面使用system';
COMMENT ON COLUMN "storage_object"."object_key" IS '桶内完整对象键，视频封面由bvedio前缀、SHA-256和扩展名组成';
COMMENT ON COLUMN "storage_object"."sha256" IS '最终存储文件字节的SHA-256摘要，64位小写十六进制';
COMMENT ON COLUMN "storage_object"."file_size" IS '文件大小，单位字节';
COMMENT ON COLUMN "storage_object"."content_type" IS '实际存储文件的MIME类型';
COMMENT ON COLUMN "storage_object"."create_user" IS '创建人用户ID';
COMMENT ON COLUMN "storage_object"."create_time" IS '创建时间';
COMMENT ON COLUMN "storage_object"."update_user" IS '最后更新人用户ID';
COMMENT ON COLUMN "storage_object"."update_time" IS '最后更新时间';
COMMENT ON COLUMN "storage_object"."is_deleted" IS '逻辑删除标记：0=未删除，1=已删除';
CREATE UNIQUE INDEX "storage_object_uk_storage_hash" ON "storage_object" ("bucket", "sha256");
COMMENT ON INDEX "storage_object_uk_storage_hash" IS '唯一性约束：uk_storage_hash';
CREATE UNIQUE INDEX "storage_object_uk_storage_key" ON "storage_object" ("bucket", "object_key");
COMMENT ON INDEX "storage_object_uk_storage_key" IS '唯一性约束：uk_storage_key';

CREATE TABLE "sys_activity_log" (
    "id" bigint NOT NULL,
    "log_type" varchar(16) NOT NULL,
    "user_id" bigint,
    "username" varchar(100) DEFAULT '' NOT NULL,
    "nickname" varchar(100) DEFAULT '' NOT NULL,
    "ip_address" varchar(64) DEFAULT '' NOT NULL,
    "browser" varchar(100) DEFAULT '' NOT NULL,
    "function_name" varchar(200),
    "function_item" varchar(100),
    "access_type" varchar(32),
    "request_path" varchar(255),
    "request_method" varchar(16),
    "request_params" jsonb,
    "request_time" timestamp(3) without time zone,
    "duration_ms" integer,
    "server_ip" varchar(64),
    "success" smallint DEFAULT '1' NOT NULL,
    "create_user" bigint,
    "create_time" timestamp(0) without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "update_user" bigint,
    "update_time" timestamp(0) without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "is_deleted" smallint DEFAULT '0' NOT NULL,
    PRIMARY KEY ("id")
 );
COMMENT ON TABLE "sys_activity_log" IS '操作和访问日志';
COMMENT ON COLUMN "sys_activity_log"."id" IS '主键ID';
COMMENT ON COLUMN "sys_activity_log"."log_type" IS 'OPERATION 操作 / ACCESS 访问';
COMMENT ON COLUMN "sys_activity_log"."user_id" IS '操作用户ID';
COMMENT ON COLUMN "sys_activity_log"."username" IS '用户名';
COMMENT ON COLUMN "sys_activity_log"."nickname" IS '昵称';
COMMENT ON COLUMN "sys_activity_log"."ip_address" IS 'IP 地址';
COMMENT ON COLUMN "sys_activity_log"."browser" IS '浏览器';
COMMENT ON COLUMN "sys_activity_log"."function_name" IS '功能名称';
COMMENT ON COLUMN "sys_activity_log"."function_item" IS '功能项';
COMMENT ON COLUMN "sys_activity_log"."access_type" IS '访问类型';
COMMENT ON COLUMN "sys_activity_log"."request_path" IS '接口路由模板，不含查询参数';
COMMENT ON COLUMN "sys_activity_log"."request_method" IS 'HTTP方法，未采集时为NULL';
COMMENT ON COLUMN "sys_activity_log"."request_params" IS '白名单脱敏参数，应用限制32KiB';
COMMENT ON COLUMN "sys_activity_log"."request_time" IS '服务端请求接收时间，Asia/Shanghai';
COMMENT ON COLUMN "sys_activity_log"."duration_ms" IS '处理耗时毫秒，非负，0有效，NULL未采集';
COMMENT ON COLUMN "sys_activity_log"."server_ip" IS '处理请求的后端实例IPv4或IPv6';
COMMENT ON COLUMN "sys_activity_log"."success" IS '是否成功';
COMMENT ON COLUMN "sys_activity_log"."create_user" IS '创建人';
COMMENT ON COLUMN "sys_activity_log"."create_time" IS '创建时间';
COMMENT ON COLUMN "sys_activity_log"."update_user" IS '更新人';
COMMENT ON COLUMN "sys_activity_log"."update_time" IS '更新时间';
COMMENT ON COLUMN "sys_activity_log"."is_deleted" IS '是否删除(0-否,1-是)';
CREATE INDEX "sys_activity_log_idx_activity_type_id" ON "sys_activity_log" ("log_type", "is_deleted", "id");
COMMENT ON INDEX "sys_activity_log_idx_activity_type_id" IS '业务检索索引：idx_activity_type_id';
CREATE INDEX "sys_activity_log_idx_activity_type_req_time" ON "sys_activity_log" ("log_type", "is_deleted", "request_time", "id");
COMMENT ON INDEX "sys_activity_log_idx_activity_type_req_time" IS '业务检索索引：idx_activity_type_req_time';
CREATE INDEX "sys_activity_log_idx_activity_type_time" ON "sys_activity_log" ("log_type", "is_deleted", "create_time", "id");
COMMENT ON INDEX "sys_activity_log_idx_activity_type_time" IS '业务检索索引：idx_activity_type_time';

CREATE TABLE "sys_dict_data" (
    "dict_code" bigint NOT NULL,
    "dict_id" bigint NOT NULL,
    "sort_order" integer DEFAULT '0',
    "dict_label" varchar(100) DEFAULT '',
    "dict_value" varchar(100) COLLATE "C" DEFAULT '',
    "dict_type" varchar(100) COLLATE "C" DEFAULT '',
    "css_class" varchar(100),
    "list_class" varchar(100),
    "is_default" char(1) DEFAULT 'N',
    "create_user" bigint,
    "create_time" timestamp(0) without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "update_user" bigint,
    "update_time" timestamp(0) without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "remark" varchar(500),
    "is_deleted" smallint DEFAULT '0' NOT NULL,
    "is_enabled" smallint DEFAULT 1 NOT NULL,
    PRIMARY KEY ("dict_code"),
    CONSTRAINT "ck_sys_dict_data_enabled" CHECK (("is_enabled" in (0,1)))
 );
COMMENT ON TABLE "sys_dict_data" IS '字典数据表';
COMMENT ON COLUMN "sys_dict_data"."dict_code" IS '字典编码';
COMMENT ON COLUMN "sys_dict_data"."dict_id" IS '所属字典类型ID，关联sys_dict_type.dict_id';
COMMENT ON COLUMN "sys_dict_data"."sort_order" IS '排序值，数值越小越靠前';
COMMENT ON COLUMN "sys_dict_data"."dict_label" IS '字典标签';
COMMENT ON COLUMN "sys_dict_data"."dict_value" IS '字典键值';
COMMENT ON COLUMN "sys_dict_data"."dict_type" IS '字典类型';
COMMENT ON COLUMN "sys_dict_data"."css_class" IS '样式属性（其他样式扩展）';
COMMENT ON COLUMN "sys_dict_data"."list_class" IS '表格回显样式';
COMMENT ON COLUMN "sys_dict_data"."is_default" IS '是否默认（Y是 N否）';
COMMENT ON COLUMN "sys_dict_data"."create_user" IS '创建人';
COMMENT ON COLUMN "sys_dict_data"."create_time" IS '创建时间';
COMMENT ON COLUMN "sys_dict_data"."update_user" IS '更新人';
COMMENT ON COLUMN "sys_dict_data"."update_time" IS '更新时间';
COMMENT ON COLUMN "sys_dict_data"."remark" IS '备注';
COMMENT ON COLUMN "sys_dict_data"."is_deleted" IS '是否删除: 0-未删除, 1-已删除';
COMMENT ON COLUMN "sys_dict_data"."is_enabled" IS '是否启用：1=启用，0=停用';

CREATE TABLE "sys_dict_type" (
    "dict_id" bigint NOT NULL,
    "dict_name" varchar(100) DEFAULT '',
    "dict_type" varchar(100) COLLATE "C" DEFAULT '',
    "create_user" bigint,
    "create_time" timestamp(0) without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "update_user" bigint,
    "update_time" timestamp(0) without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "remark" varchar(500),
    "is_deleted" smallint DEFAULT '0' NOT NULL,
    "is_enabled" smallint DEFAULT 1 NOT NULL,
    PRIMARY KEY ("dict_id"),
    CONSTRAINT "ck_sys_dict_type_enabled" CHECK (("is_enabled" in (0,1)))
 );
COMMENT ON TABLE "sys_dict_type" IS '字典类型表';
COMMENT ON COLUMN "sys_dict_type"."dict_id" IS '字典主键';
COMMENT ON COLUMN "sys_dict_type"."dict_name" IS '字典名称';
COMMENT ON COLUMN "sys_dict_type"."dict_type" IS '字典类型';
COMMENT ON COLUMN "sys_dict_type"."create_user" IS '创建人';
COMMENT ON COLUMN "sys_dict_type"."create_time" IS '创建时间';
COMMENT ON COLUMN "sys_dict_type"."update_user" IS '更新人';
COMMENT ON COLUMN "sys_dict_type"."update_time" IS '更新时间';
COMMENT ON COLUMN "sys_dict_type"."remark" IS '备注';
COMMENT ON COLUMN "sys_dict_type"."is_deleted" IS '是否删除: 0-未删除, 1-已删除';
COMMENT ON COLUMN "sys_dict_type"."is_enabled" IS '是否启用：1=启用，0=停用';

CREATE TABLE "sys_menu" (
    "id" bigint NOT NULL,
    "parent_id" bigint DEFAULT '0',
    "name" varchar(64) NOT NULL,
    "path" varchar(255) COLLATE "C" NOT NULL,
    "component" varchar(255),
    "redirect" varchar(255),
    "icon_color" varchar(7),
    "meta" jsonb,
    "roles" varchar(255),
    "sort_order" integer DEFAULT '0' NOT NULL,
    "web_is_enabled" smallint DEFAULT '1' NOT NULL,
    "mobile_is_enabled" smallint DEFAULT '1' NOT NULL,
    "create_user" bigint,
    "create_time" timestamp(0) without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "update_user" bigint,
    "update_time" timestamp(0) without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "is_deleted" smallint DEFAULT '0' NOT NULL,
    PRIMARY KEY ("id")
 );
COMMENT ON TABLE "sys_menu" IS '系统菜单表';
COMMENT ON COLUMN "sys_menu"."id" IS '主键ID';
COMMENT ON COLUMN "sys_menu"."parent_id" IS '父级ID（0为根）';
COMMENT ON COLUMN "sys_menu"."name" IS '路由名称';
COMMENT ON COLUMN "sys_menu"."path" IS '路由路径（唯一）';
COMMENT ON COLUMN "sys_menu"."component" IS '组件标识（BasicLayout/IFrameView 或 views 相对路径，如 system/user/index）';
COMMENT ON COLUMN "sys_menu"."redirect" IS '重定向';
COMMENT ON COLUMN "sys_menu"."icon_color" IS '菜单图标颜色（#RRGGBB，空为默认）';
COMMENT ON COLUMN "sys_menu"."meta" IS '路由 meta（title/icon/order/keepAlive/hideInMenu/link等）';
COMMENT ON COLUMN "sys_menu"."roles" IS '可访问角色（逗号分隔，空表示所有）';
COMMENT ON COLUMN "sys_menu"."sort_order" IS '排序值，数值越小越靠前';
COMMENT ON COLUMN "sys_menu"."web_is_enabled" IS 'Web端是否启用：1=启用，0=停用';
COMMENT ON COLUMN "sys_menu"."mobile_is_enabled" IS '移动端是否启用：1=启用，0=停用';
COMMENT ON COLUMN "sys_menu"."create_user" IS '创建人';
COMMENT ON COLUMN "sys_menu"."create_time" IS '创建时间';
COMMENT ON COLUMN "sys_menu"."update_user" IS '更新人';
COMMENT ON COLUMN "sys_menu"."update_time" IS '更新时间';
COMMENT ON COLUMN "sys_menu"."is_deleted" IS '逻辑删除标记：0=未删除，1=已删除';
CREATE INDEX "sys_menu_idx_sys_menu_parent_id" ON "sys_menu" ("parent_id");
COMMENT ON INDEX "sys_menu_idx_sys_menu_parent_id" IS '业务检索索引：idx_sys_menu_parent_id';
CREATE INDEX "sys_menu_idx_sys_menu_sort" ON "sys_menu" ("sort_order");
COMMENT ON INDEX "sys_menu_idx_sys_menu_sort" IS '业务检索索引：idx_sys_menu_sort';
CREATE UNIQUE INDEX "sys_menu_uk_sys_menu_path" ON "sys_menu" ("path") WHERE "is_deleted" = 0;
COMMENT ON INDEX "sys_menu_uk_sys_menu_path" IS '有效菜单路由路径唯一，已删除菜单不占用';

CREATE TABLE "sys_scheduled_job" (
    "id" bigint NOT NULL,
    "job_code" varchar(80) COLLATE "C" NOT NULL,
    "name" varchar(100) NOT NULL,
    "cron" varchar(100) NOT NULL,
    "zone_id" varchar(40) DEFAULT 'Asia/Shanghai' NOT NULL,
    "is_enabled" smallint DEFAULT '1' NOT NULL,
    "revision" integer DEFAULT '0' NOT NULL,
    "create_user" bigint,
    "create_time" timestamp(0) without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "update_user" bigint,
    "update_time" timestamp(0) without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "is_deleted" smallint DEFAULT '0' NOT NULL,
    PRIMARY KEY ("id")
 );
COMMENT ON TABLE "sys_scheduled_job" IS '可配置定时任务';
COMMENT ON COLUMN "sys_scheduled_job"."id" IS '任务ID，由应用生成雪花ID';
COMMENT ON COLUMN "sys_scheduled_job"."job_code" IS '代码注册的任务编码，不允许任意脚本或方法名';
COMMENT ON COLUMN "sys_scheduled_job"."name" IS '任务名称';
COMMENT ON COLUMN "sys_scheduled_job"."cron" IS '六段Spring Cron，秒为单个整数，每分钟最多一次';
COMMENT ON COLUMN "sys_scheduled_job"."zone_id" IS '调度时区，首版固定Asia/Shanghai';
COMMENT ON COLUMN "sys_scheduled_job"."is_enabled" IS '是否启用：1=启用，0=停用；不限制手动执行';
COMMENT ON COLUMN "sys_scheduled_job"."revision" IS '配置版本，从0开始，每次修改递增';
COMMENT ON COLUMN "sys_scheduled_job"."create_user" IS '创建人用户ID，自动任务为空';
COMMENT ON COLUMN "sys_scheduled_job"."create_time" IS '创建时间';
COMMENT ON COLUMN "sys_scheduled_job"."update_user" IS '最后更新人用户ID，自动任务为空';
COMMENT ON COLUMN "sys_scheduled_job"."update_time" IS '最后更新时间';
COMMENT ON COLUMN "sys_scheduled_job"."is_deleted" IS '逻辑删除标记：0=未删除，1=已删除';
CREATE UNIQUE INDEX "sys_scheduled_job_uk_sys_scheduled_job_code" ON "sys_scheduled_job" ("job_code") WHERE "is_deleted" = 0;
COMMENT ON INDEX "sys_scheduled_job_uk_sys_scheduled_job_code" IS '有效定时任务编码唯一，已删除任务不占用';

CREATE TABLE "sys_scheduled_job_log" (
    "id" bigint NOT NULL,
    "job_id" bigint NOT NULL,
    "trigger_key" varchar(80) NOT NULL,
    "trigger_type" varchar(10) NOT NULL,
    "status" varchar(20) NOT NULL,
    "scheduled_time" timestamp(0) without time zone,
    "start_time" timestamp(0) without time zone NOT NULL,
    "end_time" timestamp(0) without time zone,
    "duration_ms" bigint,
    "message" varchar(1000),
    "instance_id" varchar(36) NOT NULL,
    "create_user" bigint,
    "create_time" timestamp(0) without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "update_user" bigint,
    "update_time" timestamp(0) without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "is_deleted" smallint DEFAULT '0' NOT NULL,
    PRIMARY KEY ("id")
 );
COMMENT ON TABLE "sys_scheduled_job_log" IS '定时任务执行日志';
COMMENT ON COLUMN "sys_scheduled_job_log"."id" IS '执行日志ID，由应用生成雪花ID';
COMMENT ON COLUMN "sys_scheduled_job_log"."job_id" IS '所属定时任务ID';
COMMENT ON COLUMN "sys_scheduled_job_log"."trigger_key" IS '自动执行为AUTO加计划时点毫秒值，手动及跳过记录为UUID';
COMMENT ON COLUMN "sys_scheduled_job_log"."trigger_type" IS '触发类型：AUTO=定时，MANUAL=手动';
COMMENT ON COLUMN "sys_scheduled_job_log"."status" IS '状态：RUNNING=执行中，SUCCESS=方法完成，FAILED=异常，SKIPPED=跳过，INTERRUPTED=结果未知';
COMMENT ON COLUMN "sys_scheduled_job_log"."scheduled_time" IS '计划触发时间，Asia/Shanghai，手动触发为空';
COMMENT ON COLUMN "sys_scheduled_job_log"."start_time" IS '执行或跳过时间，Asia/Shanghai';
COMMENT ON COLUMN "sys_scheduled_job_log"."end_time" IS '完成或检测中断时间，Asia/Shanghai，执行中为空';
COMMENT ON COLUMN "sys_scheduled_job_log"."duration_ms" IS '执行耗时毫秒，0=不足1毫秒或跳过，中断及执行中为空';
COMMENT ON COLUMN "sys_scheduled_job_log"."message" IS '执行结果摘要，不存储外部响应或敏感参数，执行中为空';
COMMENT ON COLUMN "sys_scheduled_job_log"."instance_id" IS '执行服务实例启动时生成的UUID';
COMMENT ON COLUMN "sys_scheduled_job_log"."create_user" IS '创建人用户ID，自动任务为空';
COMMENT ON COLUMN "sys_scheduled_job_log"."create_time" IS '创建时间';
COMMENT ON COLUMN "sys_scheduled_job_log"."update_user" IS '最后更新人用户ID，自动任务为空';
COMMENT ON COLUMN "sys_scheduled_job_log"."update_time" IS '最后更新时间';
COMMENT ON COLUMN "sys_scheduled_job_log"."is_deleted" IS '逻辑删除标记：0=未删除，1=已删除';
CREATE INDEX "sys_scheduled_job_log_idx_sys_scheduled_job_log_page" ON "sys_scheduled_job_log" ("job_id", "is_deleted", "start_time", "id");
COMMENT ON INDEX "sys_scheduled_job_log_idx_sys_scheduled_job_log_page" IS '业务检索索引：idx_sys_scheduled_job_log_page';
CREATE INDEX "sys_scheduled_job_log_idx_sys_scheduled_job_log_status" ON "sys_scheduled_job_log" ("job_id", "is_deleted", "status");
COMMENT ON INDEX "sys_scheduled_job_log_idx_sys_scheduled_job_log_status" IS '业务检索索引：idx_sys_scheduled_job_log_status';
CREATE UNIQUE INDEX "sys_scheduled_job_log_uk_sys_scheduled_job_log_trigger" ON "sys_scheduled_job_log" ("job_id", "trigger_key");
COMMENT ON INDEX "sys_scheduled_job_log_uk_sys_scheduled_job_log_trigger" IS '唯一性约束：uk_sys_scheduled_job_log_trigger';

CREATE TABLE "sys_work_calendar" (
    "calendar_date" date NOT NULL,
    "day_type" smallint NOT NULL,
    "holiday_name" varchar(50),
    "source" varchar(50) NOT NULL,
    "updated_at" timestamp(0) without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    PRIMARY KEY ("calendar_date"),
    CONSTRAINT "chk_sys_work_calendar_day_type" CHECK (("day_type" in (0,1,2,3)))
 );
COMMENT ON TABLE "sys_work_calendar" IS '中国大陆工作日历表';
COMMENT ON COLUMN "sys_work_calendar"."calendar_date" IS '日历日期（中国大陆，按北京时间）';
COMMENT ON COLUMN "sys_work_calendar"."day_type" IS '日期类型：0普通工作日，1普通周末，2节假日休息，3调休补班';
COMMENT ON COLUMN "sys_work_calendar"."holiday_name" IS '关联节日名称，如春节、国庆节；普通日期为空';
COMMENT ON COLUMN "sys_work_calendar"."source" IS '数据来源，如 weekday_rule、holiday-cn、manual';
COMMENT ON COLUMN "sys_work_calendar"."updated_at" IS '最后更新时间';

CREATE TABLE "system_config" (
    "id" bigint NOT NULL,
    "config_key" varchar(128) COLLATE "C" NOT NULL,
    "config_value" text,
    "config_type" varchar(32) DEFAULT 'STRING',
    "description" varchar(512),
    "is_deleted" smallint DEFAULT '0' NOT NULL,
    "create_time" timestamp(0) without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "update_time" timestamp(0) without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "create_user" bigint,
    "update_user" bigint,
    PRIMARY KEY ("id")
 );
COMMENT ON TABLE "system_config" IS '系统配置表';
COMMENT ON COLUMN "system_config"."id" IS '主键ID（雪花）';
COMMENT ON COLUMN "system_config"."config_key" IS '配置键（唯一）';
COMMENT ON COLUMN "system_config"."config_value" IS '配置值（JSON 或纯文本）';
COMMENT ON COLUMN "system_config"."config_type" IS '类型：STRING/JSON/BOOLEAN/NUMBER（前端渲染用）';
COMMENT ON COLUMN "system_config"."description" IS '配置说明';
COMMENT ON COLUMN "system_config"."is_deleted" IS '逻辑删除标记：0=未删除，1=已删除';
COMMENT ON COLUMN "system_config"."create_time" IS '创建时间';
COMMENT ON COLUMN "system_config"."update_time" IS '更新时间';
COMMENT ON COLUMN "system_config"."create_user" IS '创建人ID';
COMMENT ON COLUMN "system_config"."update_user" IS '更新人ID';

CREATE TABLE "system_region" (
    "id" bigint NOT NULL,
    "code" varchar(12) COLLATE "C" NOT NULL,
    "parent_code" varchar(12) COLLATE "C" NOT NULL,
    "name" varchar(100) NOT NULL,
    "level" integer NOT NULL,
    "data_version" varchar(32) NOT NULL,
    "create_user" bigint,
    "create_time" timestamp(0) without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "update_user" bigint,
    "update_time" timestamp(0) without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "is_deleted" smallint DEFAULT '0' NOT NULL,
    PRIMARY KEY ("id"),
    CONSTRAINT "ck_system_region_level" CHECK (("level" between 1 and 4))
 );
COMMENT ON TABLE "system_region" IS '省市区县乡镇街道基础目录，版本化统计区划快照';
COMMENT ON COLUMN "system_region"."id" IS '目录记录ID，初始化数据使用编码映射的固定ID，应用新增使用雪花ID';
COMMENT ON COLUMN "system_region"."code" IS '地区编码，保留原始字符串，目录内唯一';
COMMENT ON COLUMN "system_region"."parent_code" IS '上级地区编码，0表示省级根节点';
COMMENT ON COLUMN "system_region"."name" IS '地区名称，按数据版本保留原名';
COMMENT ON COLUMN "system_region"."level" IS '地区层级：1=省，2=市，3=区县，4=乡镇街道';
COMMENT ON COLUMN "system_region"."data_version" IS '来源版本，NBS-2023表示2023年统计区划快照，不表示当前实时区划';
COMMENT ON COLUMN "system_region"."create_user" IS '创建人用户ID，初始化数据为空';
COMMENT ON COLUMN "system_region"."create_time" IS '创建时间，由应用写入，初始化数据为空';
COMMENT ON COLUMN "system_region"."update_user" IS '最后更新人用户ID，未记录时为空';
COMMENT ON COLUMN "system_region"."update_time" IS '最后更新时间，未记录时为空';
COMMENT ON COLUMN "system_region"."is_deleted" IS '逻辑删除标记：0=未删除，1=已删除';
CREATE INDEX "system_region_idx_system_region_parent" ON "system_region" ("parent_code", "is_deleted", "code");
COMMENT ON INDEX "system_region_idx_system_region_parent" IS '业务检索索引：idx_system_region_parent';
CREATE UNIQUE INDEX "system_region_uk_system_region_code" ON "system_region" ("code") WHERE "is_deleted" = 0;
COMMENT ON INDEX "system_region_uk_system_region_code" IS '有效地区编码唯一，已删除版本记录不占用';

CREATE TABLE "task" (
    "id" bigint NOT NULL,
    "user_id" bigint NOT NULL,
    "content" varchar(100),
    "detail" varchar(1000),
    "column_id" bigint,
    "due_date" timestamp(0) without time zone,
    "sort_order" integer,
    "is_deleted" smallint DEFAULT '0' NOT NULL,
    "create_user" bigint,
    "create_time" timestamp(0) without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "update_user" bigint,
    "update_time" timestamp(0) without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    PRIMARY KEY ("id")
 );
COMMENT ON TABLE "task" IS '任务表';
COMMENT ON COLUMN "task"."id" IS '主键ID';
COMMENT ON COLUMN "task"."user_id" IS '用户ID';
COMMENT ON COLUMN "task"."content" IS '任务标题，未填写时为空';
COMMENT ON COLUMN "task"."detail" IS '任务详细说明，未填写时为空';
COMMENT ON COLUMN "task"."column_id" IS '所属看板列ID，关联task_column.id；未分配时为空';
COMMENT ON COLUMN "task"."due_date" IS '最后时间';
COMMENT ON COLUMN "task"."sort_order" IS '排序字段';
COMMENT ON COLUMN "task"."is_deleted" IS '逻辑删除标记：0=未删除，1=已删除';
COMMENT ON COLUMN "task"."create_user" IS '创建人';
COMMENT ON COLUMN "task"."create_time" IS '创建时间';
COMMENT ON COLUMN "task"."update_user" IS '更新人';
COMMENT ON COLUMN "task"."update_time" IS '更新时间';
CREATE INDEX "task_idx_user_id" ON "task" ("user_id");
COMMENT ON INDEX "task_idx_user_id" IS '业务检索索引：idx_user_id';

CREATE TABLE "task_column" (
    "id" bigint NOT NULL,
    "user_id" bigint NOT NULL,
    "title" varchar(255) NOT NULL,
    "sort_order" integer NOT NULL,
    "bg_color" varchar(10),
    "is_deleted" smallint DEFAULT '0' NOT NULL,
    "create_user" bigint,
    "create_time" timestamp(0) without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "update_user" bigint,
    "update_time" timestamp(0) without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    PRIMARY KEY ("id")
 );
COMMENT ON TABLE "task_column" IS '任务列表';
COMMENT ON COLUMN "task_column"."id" IS '列ID';
COMMENT ON COLUMN "task_column"."user_id" IS '用户ID';
COMMENT ON COLUMN "task_column"."title" IS '列标题';
COMMENT ON COLUMN "task_column"."sort_order" IS '排序';
COMMENT ON COLUMN "task_column"."bg_color" IS '看板列背景颜色，未设置时为空';
COMMENT ON COLUMN "task_column"."is_deleted" IS '是否删除(0-未删除,1-已删除)';
COMMENT ON COLUMN "task_column"."create_user" IS '创建人';
COMMENT ON COLUMN "task_column"."create_time" IS '创建时间';
COMMENT ON COLUMN "task_column"."update_user" IS '更新人';
COMMENT ON COLUMN "task_column"."update_time" IS '更新时间';
CREATE INDEX "task_column_idx_user_id" ON "task_column" ("user_id");
COMMENT ON INDEX "task_column_idx_user_id" IS '业务检索索引：idx_user_id';

CREATE TABLE "task_detail" (
    "id" bigint NOT NULL,
    "task_id" bigint NOT NULL,
    "content" varchar(255) NOT NULL,
    "is_completed" smallint DEFAULT '0' NOT NULL,
    "sort_order" integer DEFAULT '0' NOT NULL,
    "priority" integer DEFAULT '20',
    "user_id" bigint NOT NULL,
    "create_user" bigint,
    "create_time" timestamp(0) without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "update_user" bigint,
    "update_time" timestamp(0) without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "is_deleted" smallint DEFAULT '0' NOT NULL,
    "is_starred" integer DEFAULT '0' NOT NULL,
    "start_time" timestamp(0) without time zone,
    "end_time" timestamp(0) without time zone,
    PRIMARY KEY ("id")
 );
COMMENT ON TABLE "task_detail" IS '任务明细表';
COMMENT ON COLUMN "task_detail"."id" IS '主键ID';
COMMENT ON COLUMN "task_detail"."task_id" IS '关联任务ID';
COMMENT ON COLUMN "task_detail"."content" IS '明细任务内容';
COMMENT ON COLUMN "task_detail"."is_completed" IS '是否完成 0-未完成 1-已完成';
COMMENT ON COLUMN "task_detail"."sort_order" IS '排序值，数值越小越靠前';
COMMENT ON COLUMN "task_detail"."priority" IS '优先级: 1-高, 10-中, 20-低';
COMMENT ON COLUMN "task_detail"."user_id" IS '所属用户ID';
COMMENT ON COLUMN "task_detail"."create_user" IS '创建人';
COMMENT ON COLUMN "task_detail"."create_time" IS '创建时间';
COMMENT ON COLUMN "task_detail"."update_user" IS '更新人';
COMMENT ON COLUMN "task_detail"."update_time" IS '更新时间';
COMMENT ON COLUMN "task_detail"."is_deleted" IS '是否删除 0-未删除 1-已删除';
COMMENT ON COLUMN "task_detail"."is_starred" IS '是否关注: 0-未关注, 1-已关注';
COMMENT ON COLUMN "task_detail"."start_time" IS '开始时间';
COMMENT ON COLUMN "task_detail"."end_time" IS '结束时间';

CREATE TABLE "thought" (
    "id" bigint NOT NULL,
    "content" text NOT NULL,
    "user_id" bigint NOT NULL,
    "create_time" timestamp(0) without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "create_user" bigint,
    "update_time" timestamp(0) without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "update_user" bigint,
    "is_deleted" smallint DEFAULT '0' NOT NULL,
    "is_pinned" smallint DEFAULT '0' NOT NULL,
    "hidden_content" smallint DEFAULT '0',
    PRIMARY KEY ("id")
 );
COMMENT ON TABLE "thought" IS '思考表';
COMMENT ON COLUMN "thought"."id" IS '主键ID';
COMMENT ON COLUMN "thought"."content" IS '内容';
COMMENT ON COLUMN "thought"."user_id" IS '用户 ID';
COMMENT ON COLUMN "thought"."create_time" IS '创建时间';
COMMENT ON COLUMN "thought"."create_user" IS '创建人用户ID';
COMMENT ON COLUMN "thought"."update_time" IS '修改时间';
COMMENT ON COLUMN "thought"."update_user" IS '最后更新人用户ID';
COMMENT ON COLUMN "thought"."is_deleted" IS '逻辑删除标记：0=未删除，1=已删除';
COMMENT ON COLUMN "thought"."is_pinned" IS '是否固定(用于在看板等重要位置展示): 0-否, 1-是';
COMMENT ON COLUMN "thought"."hidden_content" IS '是否隐藏内容';

CREATE TABLE "thought_rela_event" (
    "id" bigint NOT NULL,
    "thought_id" bigint NOT NULL,
    "content" text NOT NULL,
    "create_time" timestamp(0) without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "create_user" bigint,
    "update_time" timestamp(0) without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "update_user" bigint,
    "is_deleted" smallint DEFAULT '0' NOT NULL,
    PRIMARY KEY ("id")
 );
COMMENT ON TABLE "thought_rela_event" IS '思考关联事件表';
COMMENT ON COLUMN "thought_rela_event"."id" IS '主键ID';
COMMENT ON COLUMN "thought_rela_event"."thought_id" IS '所属闪念ID，关联thought.id';
COMMENT ON COLUMN "thought_rela_event"."content" IS '闪念关联事件内容';
COMMENT ON COLUMN "thought_rela_event"."create_time" IS '创建时间';
COMMENT ON COLUMN "thought_rela_event"."create_user" IS '创建人用户ID';
COMMENT ON COLUMN "thought_rela_event"."update_time" IS '修改时间';
COMMENT ON COLUMN "thought_rela_event"."update_user" IS '最后更新人用户ID';
COMMENT ON COLUMN "thought_rela_event"."is_deleted" IS '逻辑删除标记：0=未删除，1=已删除';

CREATE TABLE "time_record" (
    "id" varchar(32) NOT NULL,
    "user_id" bigint NOT NULL,
    "create_user" bigint,
    "category_id" bigint,
    "date" date NOT NULL,
    "start_minute" integer NOT NULL,
    "end_minute" integer NOT NULL,
    "title" varchar(100),
    "description" text,
    "duration_minutes" integer NOT NULL,
    "is_manual" smallint DEFAULT '0' NOT NULL,
    "is_deleted" smallint DEFAULT '0' NOT NULL,
    "create_time" timestamp(0) without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "update_user" bigint,
    "update_time" timestamp(0) without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "relate_id" bigint,
    "relate_type" smallint,
    PRIMARY KEY ("id"),
    CONSTRAINT "ck_time_record_time_range" CHECK (("start_minute" between 0 and 1439) and ("end_minute" between 0 and 1439) and ("start_minute" <= "end_minute") and ("duration_minutes" = ("end_minute" - "start_minute") + 1))
 );
COMMENT ON TABLE "time_record" IS '时间段记录表';
COMMENT ON COLUMN "time_record"."id" IS '记录ID';
COMMENT ON COLUMN "time_record"."user_id" IS '用户ID';
COMMENT ON COLUMN "time_record"."create_user" IS '创建人';
COMMENT ON COLUMN "time_record"."category_id" IS '分类ID(关联time_tracker_category.id)';
COMMENT ON COLUMN "time_record"."date" IS '日期（YYYY-MM-DD格式）';
COMMENT ON COLUMN "time_record"."start_minute" IS '开始分钟（当天 0:00 起的分钟数，0–1439，包含该分钟）';
COMMENT ON COLUMN "time_record"."end_minute" IS '结束分钟（当天 0:00 起的分钟数，0–1439，包含该分钟）';
COMMENT ON COLUMN "time_record"."title" IS '标题';
COMMENT ON COLUMN "time_record"."description" IS '详细描述';
COMMENT ON COLUMN "time_record"."duration_minutes" IS '时长（分钟），闭区间 end_minute - start_minute + 1，由服务端计算';
COMMENT ON COLUMN "time_record"."is_manual" IS '是否手动创建：0-系统，1-手动';
COMMENT ON COLUMN "time_record"."is_deleted" IS '逻辑删除标记：0=未删除，1=已删除';
COMMENT ON COLUMN "time_record"."create_time" IS '创建时间';
COMMENT ON COLUMN "time_record"."update_user" IS '更新人';
COMMENT ON COLUMN "time_record"."update_time" IS '更新时间';
COMMENT ON COLUMN "time_record"."relate_id" IS '关联业务ID（阅读记录/观影记录等的主键ID）';
COMMENT ON COLUMN "time_record"."relate_type" IS '关联业务类型：1-阅读，2-观影';
CREATE INDEX "time_record_idx_time_record_date" ON "time_record" ("date");
COMMENT ON INDEX "time_record_idx_time_record_date" IS '业务检索索引：idx_time_record_date';

CREATE TABLE "time_tracker_category" (
    "id" bigint NOT NULL,
    "user_id" bigint NOT NULL,
    "name" varchar(50),
    "color" varchar(20),
    "description" varchar(255),
    "is_track_time" smallint DEFAULT '0' NOT NULL,
    "sort_order" integer DEFAULT '0' NOT NULL,
    "create_user" bigint,
    "create_time" timestamp(0) without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "update_user" bigint,
    "update_time" timestamp(0) without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "is_deleted" smallint DEFAULT '0' NOT NULL,
    "is_enabled" smallint DEFAULT '1' NOT NULL,
    "template_id" bigint,
    "parent_id" bigint,
    "icon" varchar(100),
    "time_type" smallint DEFAULT '1' NOT NULL,
    PRIMARY KEY ("id")
 );
COMMENT ON TABLE "time_tracker_category" IS '时间追踪-分类配置表';
COMMENT ON COLUMN "time_tracker_category"."id" IS '主键ID';
COMMENT ON COLUMN "time_tracker_category"."user_id" IS '用户ID';
COMMENT ON COLUMN "time_tracker_category"."name" IS '分类名称';
COMMENT ON COLUMN "time_tracker_category"."color" IS '颜色值(Hex)';
COMMENT ON COLUMN "time_tracker_category"."description" IS '描述';
COMMENT ON COLUMN "time_tracker_category"."is_track_time" IS '是否记录时间';
COMMENT ON COLUMN "time_tracker_category"."sort_order" IS '排序值，数值越小越靠前';
COMMENT ON COLUMN "time_tracker_category"."create_user" IS '创建人';
COMMENT ON COLUMN "time_tracker_category"."create_time" IS '创建时间';
COMMENT ON COLUMN "time_tracker_category"."update_user" IS '更新人';
COMMENT ON COLUMN "time_tracker_category"."update_time" IS '更新时间';
COMMENT ON COLUMN "time_tracker_category"."is_deleted" IS '是否删除';
COMMENT ON COLUMN "time_tracker_category"."is_enabled" IS '是否启用：1-启用，0-禁用';
COMMENT ON COLUMN "time_tracker_category"."template_id" IS '模板ID，指向被覆盖的公共分类ID';
COMMENT ON COLUMN "time_tracker_category"."parent_id" IS '上级分类ID：0为一级；覆盖记录NULL表示继承公共分类';
COMMENT ON COLUMN "time_tracker_category"."icon" IS '图标名称(Iconify格式)';
COMMENT ON COLUMN "time_tracker_category"."time_type" IS '时间类型: 1-必须, 2-积极, 3-休闲';
CREATE INDEX "time_tracker_category_idx_category_user_parent" ON "time_tracker_category" ("user_id", "parent_id");
COMMENT ON INDEX "time_tracker_category_idx_category_user_parent" IS '业务检索索引：idx_category_user_parent';
CREATE INDEX "time_tracker_category_idx_category_user_template" ON "time_tracker_category" ("user_id", "template_id");
COMMENT ON INDEX "time_tracker_category_idx_category_user_template" IS '业务检索索引：idx_category_user_template';

CREATE TABLE "sys_user" (
    "id" bigint NOT NULL,
    "username" varchar(50) NOT NULL,
    "password" varchar(255),
    "nickname" varchar(50) NOT NULL,
    "policy_version" varchar(32),
    "policy_accepted_at" timestamp(0) without time zone,
    "erasure_requested_at" timestamp(0) without time zone,
    "erasure_retry_at" timestamp(0) without time zone,
    "create_user" bigint,
    "create_time" timestamp(0) without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "update_user" bigint,
    "update_time" timestamp(0) without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "last_active_at" timestamp(0) without time zone,
    "email" varchar(50),
    "avatar_file_id" varchar(32),
    "password_salt" varchar(32),
    "role" varchar(50) DEFAULT 'user',
    "introduction" varchar(255),
    "is_deleted" smallint DEFAULT '0' NOT NULL,
    "secondary_password" varchar(128),
    "secondary_password_salt" varchar(64),
    "phone_country_code" varchar(5),
    "phone" varchar(20),
    "phone_verified_at" timestamp(0) without time zone,
    "wechat_openid" varchar(128),
    "wechat_unionid" varchar(128),
    "active_flag" smallint GENERATED ALWAYS AS ((case when ("is_deleted" = 0) then 1 else NULL end)) STORED,
    PRIMARY KEY ("id"),
    CONSTRAINT "ck_sys_user_phone_pair" CHECK (((("phone_country_code" is null) and ("phone" is null)) or (("phone_country_code" is not null) and ("phone" is not null) and regexp_like("phone_country_code" COLLATE "C",'^[1-9][0-9]{0,2}$') and regexp_like("phone" COLLATE "C",'^[0-9]{4,14}$') and ((char_length("phone_country_code") + char_length("phone")) <= 15))))
 );
COMMENT ON TABLE "sys_user" IS '用户表';
COMMENT ON COLUMN "sys_user"."id" IS '用户ID';
COMMENT ON COLUMN "sys_user"."username" IS '用户名';
COMMENT ON COLUMN "sys_user"."password" IS '密码，NULL表示未设置';
COMMENT ON COLUMN "sys_user"."nickname" IS '昵称';
COMMENT ON COLUMN "sys_user"."policy_version" IS '注册时明确同意的隐私政策与服务条款版本';
COMMENT ON COLUMN "sys_user"."policy_accepted_at" IS '注册协议同意时间，上海时区';
COMMENT ON COLUMN "sys_user"."erasure_requested_at" IS '自助注销受理时间，非空表示等待不可撤销的数据清理';
COMMENT ON COLUMN "sys_user"."erasure_retry_at" IS '注销后台清理下次重试时间，上海时区';
COMMENT ON COLUMN "sys_user"."create_user" IS '创建人';
COMMENT ON COLUMN "sys_user"."create_time" IS '创建时间';
COMMENT ON COLUMN "sys_user"."update_user" IS '更新人';
COMMENT ON COLUMN "sys_user"."update_time" IS '更新时间';
COMMENT ON COLUMN "sys_user"."last_active_at" IS '最后活跃时间';
COMMENT ON COLUMN "sys_user"."email" IS '邮箱';
COMMENT ON COLUMN "sys_user"."avatar_file_id" IS '头像文件ID';
COMMENT ON COLUMN "sys_user"."password_salt" IS '密码盐值';
COMMENT ON COLUMN "sys_user"."role" IS '角色类型';
COMMENT ON COLUMN "sys_user"."introduction" IS '个人简介';
COMMENT ON COLUMN "sys_user"."is_deleted" IS '逻辑删除标记：0=未删除，1=已删除';
COMMENT ON COLUMN "sys_user"."secondary_password" IS '二级密码（加盐哈希）';
COMMENT ON COLUMN "sys_user"."secondary_password_salt" IS '二级密码盐值';
COMMENT ON COLUMN "sys_user"."phone_country_code" IS '国际电话区号，不含加号';
COMMENT ON COLUMN "sys_user"."phone" IS '不含国际区号的手机号';
COMMENT ON COLUMN "sys_user"."phone_verified_at" IS '最近接收可信手机号验证结果的时间';
COMMENT ON COLUMN "sys_user"."wechat_openid" IS '当前配置小程序的openid';
COMMENT ON COLUMN "sys_user"."wechat_unionid" IS '微信unionid，可为空，不用于自动合并账号';
COMMENT ON COLUMN "sys_user"."active_flag" IS '仅有效用户参与凭证唯一约束';
CREATE INDEX "sys_user_idx_user_avatar_file" ON "sys_user" ("avatar_file_id");
COMMENT ON INDEX "sys_user_idx_user_avatar_file" IS '业务检索索引：idx_user_avatar_file';
CREATE INDEX "sys_user_idx_user_erasure_retry" ON "sys_user" ("is_deleted", "erasure_retry_at");
COMMENT ON INDEX "sys_user_idx_user_erasure_retry" IS '业务检索索引：idx_user_erasure_retry';
CREATE UNIQUE INDEX "sys_user_uk_user_phone_active" ON "sys_user" ("phone_country_code", "phone", "active_flag");
COMMENT ON INDEX "sys_user_uk_user_phone_active" IS '唯一性约束：uk_user_phone_active';
CREATE UNIQUE INDEX "sys_user_uk_user_wechat_active" ON "sys_user" ("wechat_openid", "active_flag");
COMMENT ON INDEX "sys_user_uk_user_wechat_active" IS '唯一性约束：uk_user_wechat_active';

CREATE TABLE "user_bind" (
    "id" bigint NOT NULL,
    "user_id" bigint NOT NULL,
    "platform" varchar(32) NOT NULL,
    "platform_username" varchar(128),
    "access_token" text,
    "meta_fields" jsonb,
    "create_time" timestamp(0) without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "create_user" bigint,
    "update_time" timestamp(0) without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "update_user" bigint,
    "is_deleted" smallint DEFAULT '0' NOT NULL,
    PRIMARY KEY ("id")
 );
COMMENT ON TABLE "user_bind" IS '用户第三方账号绑定表';
COMMENT ON COLUMN "user_bind"."id" IS '主键';
COMMENT ON COLUMN "user_bind"."user_id" IS '本系统用户ID';
COMMENT ON COLUMN "user_bind"."platform" IS '平台类型：github, leetcode, csdn, shanbay, douban, weread';
COMMENT ON COLUMN "user_bind"."platform_username" IS '第三方平台的用户名/账号';
COMMENT ON COLUMN "user_bind"."access_token" IS '访问令牌';
COMMENT ON COLUMN "user_bind"."meta_fields" IS '额外配置(JSON)';
COMMENT ON COLUMN "user_bind"."create_time" IS '绑定时间';
COMMENT ON COLUMN "user_bind"."create_user" IS '创建人';
COMMENT ON COLUMN "user_bind"."update_time" IS '更新时间';
COMMENT ON COLUMN "user_bind"."update_user" IS '更新人';
COMMENT ON COLUMN "user_bind"."is_deleted" IS '是否删除';

CREATE TABLE "user_body_profile" (
    "id" bigint NOT NULL,
    "user_id" bigint NOT NULL,
    "version" integer NOT NULL,
    "height_cm" numeric(5,1),
    "weight_kg" numeric(5,1),
    "body_fat_percent" numeric(5,1),
    "shoulder_width_cm" numeric(5,1),
    "chest_cm" numeric(5,1),
    "upper_arm_cm" numeric(5,1),
    "forearm_cm" numeric(5,1),
    "waist_cm" numeric(5,1),
    "hip_cm" numeric(5,1),
    "thigh_cm" numeric(5,1),
    "calf_cm" numeric(5,1),
    "inseam_cm" numeric(5,1),
    "gender" varchar(16),
    "measurement_side" varchar(16),
    "arm_state" varchar(16),
    "shoe_system" varchar(8),
    "top_size" varchar(32),
    "trousers_size" varchar(32),
    "shoe_size" varchar(32),
    "create_user" bigint,
    "create_time" timestamp(0) without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "update_user" bigint,
    "update_time" timestamp(0) without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "is_deleted" smallint DEFAULT '0' NOT NULL,
    PRIMARY KEY ("id"),
    CONSTRAINT "ck_body_profile_version" CHECK (("version" > 0)),
    CONSTRAINT "ck_body_profile_gender" CHECK (("gender" in ('male','female'))),
    CONSTRAINT "ck_body_profile_side" CHECK (("measurement_side" in ('left','right'))),
    CONSTRAINT "ck_body_profile_arm_state" CHECK (("arm_state" in ('relaxed','flexed'))),
    CONSTRAINT "ck_body_profile_shoe_system" CHECK (("shoe_system" in ('EU','CN','US','UK'))),
    CONSTRAINT "ck_body_profile_height_cm" CHECK ((("height_cm" > 0) and ("height_cm" <= 999.9))),
    CONSTRAINT "ck_body_profile_weight_kg" CHECK ((("weight_kg" > 0) and ("weight_kg" <= 999.9))),
    CONSTRAINT "ck_body_profile_body_fat_percent" CHECK ((("body_fat_percent" > 0) and ("body_fat_percent" <= 99.9))),
    CONSTRAINT "ck_body_profile_shoulder_width_cm" CHECK ((("shoulder_width_cm" > 0) and ("shoulder_width_cm" <= 999.9))),
    CONSTRAINT "ck_body_profile_chest_cm" CHECK ((("chest_cm" > 0) and ("chest_cm" <= 999.9))),
    CONSTRAINT "ck_body_profile_upper_arm_cm" CHECK ((("upper_arm_cm" > 0) and ("upper_arm_cm" <= 999.9))),
    CONSTRAINT "ck_body_profile_forearm_cm" CHECK ((("forearm_cm" > 0) and ("forearm_cm" <= 999.9))),
    CONSTRAINT "ck_body_profile_waist_cm" CHECK ((("waist_cm" > 0) and ("waist_cm" <= 999.9))),
    CONSTRAINT "ck_body_profile_hip_cm" CHECK ((("hip_cm" > 0) and ("hip_cm" <= 999.9))),
    CONSTRAINT "ck_body_profile_thigh_cm" CHECK ((("thigh_cm" > 0) and ("thigh_cm" <= 999.9))),
    CONSTRAINT "ck_body_profile_calf_cm" CHECK ((("calf_cm" > 0) and ("calf_cm" <= 999.9))),
    CONSTRAINT "ck_body_profile_inseam_cm" CHECK ((("inseam_cm" > 0) and ("inseam_cm" <= 999.9)))
 );
COMMENT ON TABLE "user_body_profile" IS '身体档案';
COMMENT ON COLUMN "user_body_profile"."id" IS '主键ID';
COMMENT ON COLUMN "user_body_profile"."user_id" IS '所属用户ID，用于用户数据隔离';
COMMENT ON COLUMN "user_body_profile"."version" IS '乐观锁版本，首次保存为1，每次成功修改递增';
COMMENT ON COLUMN "user_body_profile"."height_cm" IS '身高（cm）';
COMMENT ON COLUMN "user_body_profile"."weight_kg" IS '体重（kg）';
COMMENT ON COLUMN "user_body_profile"."body_fat_percent" IS '体脂率（%）';
COMMENT ON COLUMN "user_body_profile"."shoulder_width_cm" IS '肩宽';
COMMENT ON COLUMN "user_body_profile"."chest_cm" IS '胸围';
COMMENT ON COLUMN "user_body_profile"."upper_arm_cm" IS '上臂围';
COMMENT ON COLUMN "user_body_profile"."forearm_cm" IS '前臂围';
COMMENT ON COLUMN "user_body_profile"."waist_cm" IS '腰围';
COMMENT ON COLUMN "user_body_profile"."hip_cm" IS '臀围';
COMMENT ON COLUMN "user_body_profile"."thigh_cm" IS '大腿围';
COMMENT ON COLUMN "user_body_profile"."calf_cm" IS '小腿围';
COMMENT ON COLUMN "user_body_profile"."inseam_cm" IS '腿内侧长';
COMMENT ON COLUMN "user_body_profile"."gender" IS '性别：male=男，female=女；未填写时为空';
COMMENT ON COLUMN "user_body_profile"."measurement_side" IS '四肢围度测量侧别：left=左侧，right=右侧；未填写时为空';
COMMENT ON COLUMN "user_body_profile"."arm_state" IS '上臂测量状态：relaxed=放松，flexed=屈臂；未填写时为空';
COMMENT ON COLUMN "user_body_profile"."shoe_system" IS '鞋码制式：EU=欧码，CN=中国码，US=美码，UK=英码；未填写时为空';
COMMENT ON COLUMN "user_body_profile"."top_size" IS '上装尺码，自由文本；未填写时为空';
COMMENT ON COLUMN "user_body_profile"."trousers_size" IS '裤装尺码，自由文本；未填写时为空';
COMMENT ON COLUMN "user_body_profile"."shoe_size" IS '鞋码文本，按shoe_system解释；未填写时为空';
COMMENT ON COLUMN "user_body_profile"."create_user" IS '创建人用户ID';
COMMENT ON COLUMN "user_body_profile"."create_time" IS '创建时间';
COMMENT ON COLUMN "user_body_profile"."update_user" IS '最后更新人用户ID';
COMMENT ON COLUMN "user_body_profile"."update_time" IS '最后更新时间';
COMMENT ON COLUMN "user_body_profile"."is_deleted" IS '逻辑删除标记：0=未删除，1=已删除';
CREATE UNIQUE INDEX "user_body_profile_uk_body_profile_user" ON "user_body_profile" ("user_id");
COMMENT ON INDEX "user_body_profile_uk_body_profile_user" IS '唯一性约束：uk_body_profile_user';

CREATE TABLE "user_dict_data" (
    "id" bigint NOT NULL,
    "user_id" bigint NOT NULL,
    "template_id" bigint,
    "dict_type" varchar(100) COLLATE "C" DEFAULT '',
    "sort_order" integer DEFAULT '0',
    "dict_label" varchar(100) DEFAULT '',
    "dict_value" varchar(100) COLLATE "C" DEFAULT '',
    "color" varchar(20),
    "icon" varchar(100),
    "ext_data" jsonb,
    "is_default" char(1) DEFAULT 'N',
    "is_readonly" char(1) DEFAULT 'N',
    "create_user" bigint,
    "create_time" timestamp(0) without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "update_user" bigint,
    "update_time" timestamp(0) without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "remark" varchar(500),
    "is_deleted" smallint DEFAULT '0' NOT NULL,
    "is_enabled" smallint DEFAULT '1',
    PRIMARY KEY ("id"),
    CONSTRAINT "ck_user_dict_data_enabled" CHECK ((("is_enabled" is null) or ("is_enabled" in (0,1))))
 );
COMMENT ON TABLE "user_dict_data" IS '用户字典数据表';
COMMENT ON COLUMN "user_dict_data"."id" IS '字典主键';
COMMENT ON COLUMN "user_dict_data"."user_id" IS '所属用户ID';
COMMENT ON COLUMN "user_dict_data"."template_id" IS '模板ID，指向被覆盖的公共字典ID';
COMMENT ON COLUMN "user_dict_data"."dict_type" IS '字典类型';
COMMENT ON COLUMN "user_dict_data"."sort_order" IS '排序值，数值越小越靠前';
COMMENT ON COLUMN "user_dict_data"."dict_label" IS '字典标签(分类名称)';
COMMENT ON COLUMN "user_dict_data"."dict_value" IS '字典键值(分类标识)';
COMMENT ON COLUMN "user_dict_data"."color" IS '颜色值(Hex)';
COMMENT ON COLUMN "user_dict_data"."icon" IS '图标名称(Iconify格式)';
COMMENT ON COLUMN "user_dict_data"."ext_data" IS '特定分类所需的额外扩展字段(JSON)';
COMMENT ON COLUMN "user_dict_data"."is_default" IS '是否默认（Y是 N否）';
COMMENT ON COLUMN "user_dict_data"."is_readonly" IS '是否只读（Y是 N否），当为Y时用户无法修改除状态外的其他属性';
COMMENT ON COLUMN "user_dict_data"."create_user" IS '创建人ID';
COMMENT ON COLUMN "user_dict_data"."create_time" IS '创建时间';
COMMENT ON COLUMN "user_dict_data"."update_user" IS '更新人ID';
COMMENT ON COLUMN "user_dict_data"."update_time" IS '更新时间';
COMMENT ON COLUMN "user_dict_data"."remark" IS '备注';
COMMENT ON COLUMN "user_dict_data"."is_deleted" IS '是否删除 0-未删除 1-已删除';
COMMENT ON COLUMN "user_dict_data"."is_enabled" IS '是否启用：1=启用，0=停用；NULL表示未设置，个人覆盖项继承基础值';
CREATE INDEX "user_dict_data_idx_user_dict_template" ON "user_dict_data" ("template_id");
COMMENT ON INDEX "user_dict_data_idx_user_dict_template" IS '业务检索索引：idx_user_dict_template';
CREATE INDEX "user_dict_data_idx_user_dict_type" ON "user_dict_data" ("user_id", "dict_type");
COMMENT ON INDEX "user_dict_data_idx_user_dict_type" IS '业务检索索引：idx_user_dict_type';

CREATE TABLE "user_home_card_preference" (
    "id" bigint NOT NULL,
    "user_id" bigint NOT NULL,
    "card_key" varchar(64) NOT NULL,
    "is_enabled" smallint DEFAULT '1' NOT NULL,
    "sort_order" integer DEFAULT '0' NOT NULL,
    "create_user" bigint,
    "create_time" timestamp(0) without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "update_user" bigint,
    "update_time" timestamp(0) without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "is_deleted" smallint DEFAULT '0' NOT NULL,
    PRIMARY KEY ("id")
 );
COMMENT ON TABLE "user_home_card_preference" IS '用户首页卡片偏好';
COMMENT ON COLUMN "user_home_card_preference"."id" IS '主键ID';
COMMENT ON COLUMN "user_home_card_preference"."user_id" IS '所属用户ID，用于用户数据隔离';
COMMENT ON COLUMN "user_home_card_preference"."card_key" IS '首页卡片注册清单中的稳定标识';
COMMENT ON COLUMN "user_home_card_preference"."is_enabled" IS '是否启用：1=启用，0=停用';
COMMENT ON COLUMN "user_home_card_preference"."sort_order" IS '首页卡片分组内排序，数值越小越靠前';
COMMENT ON COLUMN "user_home_card_preference"."create_user" IS '创建人用户ID';
COMMENT ON COLUMN "user_home_card_preference"."create_time" IS '创建时间';
COMMENT ON COLUMN "user_home_card_preference"."update_user" IS '最后更新人用户ID';
COMMENT ON COLUMN "user_home_card_preference"."update_time" IS '最后更新时间';
COMMENT ON COLUMN "user_home_card_preference"."is_deleted" IS '统一保留的删除标记，固定为0；恢复默认使用物理删除';
CREATE UNIQUE INDEX "user_home_card_preference_uk_user_home_card" ON "user_home_card_preference" ("user_id", "card_key");
COMMENT ON INDEX "user_home_card_preference_uk_user_home_card" IS '唯一性约束：uk_user_home_card';

CREATE TABLE "user_menu_hidden" (
    "id" bigint NOT NULL,
    "user_id" bigint NOT NULL,
    "menu_id" bigint NOT NULL,
    "create_user" bigint,
    "create_time" timestamp(0) without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "update_user" bigint,
    "update_time" timestamp(0) without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "is_deleted" smallint DEFAULT '0' NOT NULL,
    PRIMARY KEY ("id")
 );
COMMENT ON TABLE "user_menu_hidden" IS '用户隐藏菜单';
COMMENT ON COLUMN "user_menu_hidden"."id" IS '主键（雪花）';
COMMENT ON COLUMN "user_menu_hidden"."user_id" IS '用户ID';
COMMENT ON COLUMN "user_menu_hidden"."menu_id" IS '关联 sys_menu.id';
COMMENT ON COLUMN "user_menu_hidden"."create_user" IS '创建人ID';
COMMENT ON COLUMN "user_menu_hidden"."create_time" IS '创建时间';
COMMENT ON COLUMN "user_menu_hidden"."update_user" IS '更新人ID';
COMMENT ON COLUMN "user_menu_hidden"."update_time" IS '更新时间';
COMMENT ON COLUMN "user_menu_hidden"."is_deleted" IS '统一保留字段，固定为0，删除使用物理删除';
CREATE UNIQUE INDEX "user_menu_hidden_uk_user_menu" ON "user_menu_hidden" ("user_id", "menu_id");
COMMENT ON INDEX "user_menu_hidden_uk_user_menu" IS '唯一性约束：uk_user_menu';

CREATE TABLE "user_quick_nav" (
    "id" bigint NOT NULL,
    "user_id" bigint NOT NULL,
    "menu_id" bigint NOT NULL,
    "sort_order" integer DEFAULT '0' NOT NULL,
    "is_enabled" smallint DEFAULT '1' NOT NULL,
    "create_user" bigint,
    "create_time" timestamp(0) without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "update_user" bigint,
    "update_time" timestamp(0) without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "is_deleted" smallint DEFAULT '0' NOT NULL,
    PRIMARY KEY ("id")
 );
COMMENT ON TABLE "user_quick_nav" IS '用户首页快捷导航布局';
COMMENT ON COLUMN "user_quick_nav"."id" IS '主键ID';
COMMENT ON COLUMN "user_quick_nav"."user_id" IS '用户ID';
COMMENT ON COLUMN "user_quick_nav"."menu_id" IS '关联 sys_menu.id';
COMMENT ON COLUMN "user_quick_nav"."sort_order" IS '排序号（越小越前）';
COMMENT ON COLUMN "user_quick_nav"."is_enabled" IS '是否启用：1=启用，0=停用';
COMMENT ON COLUMN "user_quick_nav"."create_user" IS '创建人';
COMMENT ON COLUMN "user_quick_nav"."create_time" IS '创建时间';
COMMENT ON COLUMN "user_quick_nav"."update_user" IS '更新人';
COMMENT ON COLUMN "user_quick_nav"."update_time" IS '更新时间';
COMMENT ON COLUMN "user_quick_nav"."is_deleted" IS '统一保留字段，固定为0，替换布局使用物理删除';
CREATE INDEX "user_quick_nav_idx_user_sort" ON "user_quick_nav" ("user_id", "sort_order");
COMMENT ON INDEX "user_quick_nav_idx_user_sort" IS '业务检索索引：idx_user_sort';

CREATE TABLE "user_secondary_lock_menu" (
    "id" bigint NOT NULL,
    "user_id" bigint NOT NULL,
    "menu_id" bigint NOT NULL,
    "is_deleted" smallint DEFAULT '0' NOT NULL,
    "create_time" timestamp(0) without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "update_time" timestamp(0) without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "create_user" bigint,
    "update_user" bigint,
    PRIMARY KEY ("id")
 );
COMMENT ON TABLE "user_secondary_lock_menu" IS '用户二级锁菜单关联';
COMMENT ON COLUMN "user_secondary_lock_menu"."id" IS '主键（雪花）';
COMMENT ON COLUMN "user_secondary_lock_menu"."user_id" IS '用户ID';
COMMENT ON COLUMN "user_secondary_lock_menu"."menu_id" IS '菜单ID';
COMMENT ON COLUMN "user_secondary_lock_menu"."is_deleted" IS '逻辑删除标记：0=未删除，1=已删除';
COMMENT ON COLUMN "user_secondary_lock_menu"."create_time" IS '创建时间';
COMMENT ON COLUMN "user_secondary_lock_menu"."update_time" IS '更新时间';
COMMENT ON COLUMN "user_secondary_lock_menu"."create_user" IS '创建人ID';
COMMENT ON COLUMN "user_secondary_lock_menu"."update_user" IS '更新人ID';
CREATE INDEX "user_secondary_lock_menu_idx_user_id" ON "user_secondary_lock_menu" ("user_id");
COMMENT ON INDEX "user_secondary_lock_menu_idx_user_id" IS '业务检索索引：idx_user_id';

CREATE TABLE "wardrobe_category" (
    "id" bigint NOT NULL,
    "name" varchar(50) NOT NULL,
    "icon" varchar(50),
    "parent_id" bigint,
    "sort_order" integer DEFAULT '0',
    "category_type" smallint DEFAULT '0',
    "user_id" bigint,
    "create_user" bigint,
    "create_time" timestamp(0) without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "update_user" bigint,
    "update_time" timestamp(0) without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "is_deleted" smallint DEFAULT '0' NOT NULL,
    PRIMARY KEY ("id")
 );
COMMENT ON TABLE "wardrobe_category" IS '衣柜分类表';
COMMENT ON COLUMN "wardrobe_category"."id" IS '主键ID';
COMMENT ON COLUMN "wardrobe_category"."name" IS '分类名称';
COMMENT ON COLUMN "wardrobe_category"."icon" IS '图标';
COMMENT ON COLUMN "wardrobe_category"."parent_id" IS '父分类ID';
COMMENT ON COLUMN "wardrobe_category"."sort_order" IS '排序值，数值越小越靠前';
COMMENT ON COLUMN "wardrobe_category"."category_type" IS '0=系统预设 1=用户自定义';
COMMENT ON COLUMN "wardrobe_category"."user_id" IS '用户ID(用户自定义分类时)';
COMMENT ON COLUMN "wardrobe_category"."create_user" IS '创建人';
COMMENT ON COLUMN "wardrobe_category"."create_time" IS '创建时间';
COMMENT ON COLUMN "wardrobe_category"."update_user" IS '更新人';
COMMENT ON COLUMN "wardrobe_category"."update_time" IS '更新时间';
COMMENT ON COLUMN "wardrobe_category"."is_deleted" IS '逻辑删除标记：0=未删除，1=已删除';

CREATE TABLE "wardrobe_item" (
    "id" bigint NOT NULL,
    "name" varchar(100) NOT NULL,
    "category_id" bigint,
    "color" varchar(50),
    "brand" varchar(100),
    "season" varchar(20),
    "purchase_date" date,
    "price" numeric(10,2),
    "file_id" varchar(32),
    "size" varchar(20),
    "remark" varchar(500),
    "user_id" bigint NOT NULL,
    "source_platform" varchar(16),
    "source_order_id" varchar(100) COLLATE "C",
    "source_product_id" varchar(100) COLLATE "C",
    "source_item_id" varchar(128) COLLATE "C",
    "source_unit_index" integer,
    "create_user" bigint,
    "create_time" timestamp(0) without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "update_user" bigint,
    "update_time" timestamp(0) without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "is_deleted" smallint DEFAULT '0' NOT NULL,
    PRIMARY KEY ("id")
 );
COMMENT ON TABLE "wardrobe_item" IS '衣柜衣物表';
COMMENT ON COLUMN "wardrobe_item"."id" IS '主键ID';
COMMENT ON COLUMN "wardrobe_item"."name" IS '衣物名称';
COMMENT ON COLUMN "wardrobe_item"."category_id" IS '分类ID';
COMMENT ON COLUMN "wardrobe_item"."color" IS '颜色';
COMMENT ON COLUMN "wardrobe_item"."brand" IS '品牌';
COMMENT ON COLUMN "wardrobe_item"."season" IS '适用季节:春,夏,秋,冬';
COMMENT ON COLUMN "wardrobe_item"."purchase_date" IS '购买日期';
COMMENT ON COLUMN "wardrobe_item"."price" IS '价格';
COMMENT ON COLUMN "wardrobe_item"."file_id" IS '图片文件ID(UUID)';
COMMENT ON COLUMN "wardrobe_item"."size" IS '尺码';
COMMENT ON COLUMN "wardrobe_item"."remark" IS '备注，未填写时为空';
COMMENT ON COLUMN "wardrobe_item"."user_id" IS '用户ID';
COMMENT ON COLUMN "wardrobe_item"."source_platform" IS '采集平台：jd=京东、taobao=淘宝；手工录入或历史记录为NULL';
COMMENT ON COLUMN "wardrobe_item"."source_order_id" IS '来源订单ID，原始字符串；无采集来源时为NULL';
COMMENT ON COLUMN "wardrobe_item"."source_product_id" IS '来源商品ID，采集行sku原始字符串；无采集来源时为NULL';
COMMENT ON COLUMN "wardrobe_item"."source_item_id" IS '订单内稳定商品明细标识，区分同商品的规格或多行；无采集来源时为NULL';
COMMENT ON COLUMN "wardrobe_item"."source_unit_index" IS '来源商品行内件序号，从1开始；无采集来源时为NULL';
COMMENT ON COLUMN "wardrobe_item"."create_user" IS '创建人';
COMMENT ON COLUMN "wardrobe_item"."create_time" IS '创建时间';
COMMENT ON COLUMN "wardrobe_item"."update_user" IS '更新人';
COMMENT ON COLUMN "wardrobe_item"."update_time" IS '更新时间';
COMMENT ON COLUMN "wardrobe_item"."is_deleted" IS '逻辑删除标记：0=未删除，1=已删除';
CREATE INDEX "wardrobe_item_idx_category_id" ON "wardrobe_item" ("category_id");
COMMENT ON INDEX "wardrobe_item_idx_category_id" IS '业务检索索引：idx_category_id';
CREATE INDEX "wardrobe_item_idx_user_id" ON "wardrobe_item" ("user_id");
COMMENT ON INDEX "wardrobe_item_idx_user_id" IS '业务检索索引：idx_user_id';
CREATE INDEX "wardrobe_item_idx_wardrobe_item_capture_order" ON "wardrobe_item" ("user_id", "source_platform", "source_order_id", "is_deleted");
COMMENT ON INDEX "wardrobe_item_idx_wardrobe_item_capture_order" IS '业务检索索引：idx_wardrobe_item_capture_order';

-- 有效记录部分唯一索引；已逻辑删除的记录不占用业务键。
CREATE UNIQUE INDEX "sys_user_uk_user_username_active" ON "sys_user" (lower("username")) WHERE "is_deleted" = 0;
COMMENT ON INDEX "sys_user_uk_user_username_active" IS '有效用户的用户名忽略大小写唯一，已删除用户不占用';
CREATE UNIQUE INDEX "sys_user_uk_user_email_active" ON "sys_user" (lower("email")) WHERE "is_deleted" = 0 AND "email" IS NOT NULL AND "email" <> '';
COMMENT ON INDEX "sys_user_uk_user_email_active" IS '有效用户的邮箱忽略大小写唯一，空邮箱不参与比较，已删除用户不占用';
CREATE UNIQUE INDEX "uk_user_bind_user_platform" ON "user_bind" ("user_id", "platform") WHERE "is_deleted" = 0;
COMMENT ON INDEX "uk_user_bind_user_platform" IS '同一用户在同一平台只能有一条有效绑定';
CREATE UNIQUE INDEX "uk_notification_channel_config_user_channel" ON "notification_channel_config" ("user_id", "channel") WHERE "is_deleted" = 0;
COMMENT ON INDEX "uk_notification_channel_config_user_channel" IS '同一用户同一通知渠道只能有一条有效配置';
CREATE UNIQUE INDEX "uk_notification_preference_user_biz_channel" ON "notification_preference" ("user_id", "biz_type", "channel") WHERE "is_deleted" = 0;
COMMENT ON INDEX "uk_notification_preference_user_biz_channel" IS '同一用户同一业务类型和渠道只能有一条有效偏好';
CREATE UNIQUE INDEX "uk_api_key_api_key" ON "api_key" ("api_key") WHERE "is_deleted" = 0;
COMMENT ON INDEX "uk_api_key_api_key" IS '有效 API Key 精确唯一，已删除 Key 可重新使用';
CREATE UNIQUE INDEX "uk_system_config_config_key" ON "system_config" ("config_key") WHERE "is_deleted" = 0;
COMMENT ON INDEX "uk_system_config_config_key" IS '有效系统配置键唯一，已删除配置不占用';
CREATE UNIQUE INDEX "uk_b_video_user_bvid" ON "b_video" ("user_id", "bvid") WHERE "is_deleted" = 0 AND "bvid" IS NOT NULL AND "bvid" <> '';
COMMENT ON INDEX "uk_b_video_user_bvid" IS '同一用户同一 BV 号只能有一条有效视频记录，未记录 BV 号时为空不参与比较';
CREATE UNIQUE INDEX "uk_user_dict_data_user_template" ON "user_dict_data" ("user_id", "template_id") WHERE "is_deleted" = 0 AND "template_id" IS NOT NULL;
COMMENT ON INDEX "uk_user_dict_data_user_template" IS '同一用户对同一公共字典只能有一条有效覆盖记录';
CREATE UNIQUE INDEX "uk_time_tracker_category_user_template" ON "time_tracker_category" ("user_id", "template_id") WHERE "is_deleted" = 0 AND "template_id" IS NOT NULL;
COMMENT ON INDEX "uk_time_tracker_category_user_template" IS '同一用户对同一公共分类只能有一条有效覆盖记录';
CREATE UNIQUE INDEX "uk_bank_card_user_fingerprint" ON "bank_card" ("user_id", "card_no_fingerprint") WHERE "is_deleted" = 0 AND "card_no_fingerprint" IS NOT NULL;
COMMENT ON INDEX "uk_bank_card_user_fingerprint" IS '同一用户同一卡号指纹只能有一条有效银行卡';
CREATE UNIQUE INDEX "uk_bank_card_tag_rel_user_card_tag" ON "bank_card_tag_rel" ("user_id", "bank_card_id", "tag_id") WHERE "is_deleted" = 0;
COMMENT ON INDEX "uk_bank_card_tag_rel_user_card_tag" IS '同一银行卡同一标签只能有一条有效关联';
CREATE UNIQUE INDEX "uk_sys_dict_type_dict_type" ON "sys_dict_type" ("dict_type") WHERE "is_deleted" = 0;
COMMENT ON INDEX "uk_sys_dict_type_dict_type" IS '有效系统字典类型编码唯一，已删除类型不占用';
CREATE UNIQUE INDEX "uk_sys_dict_data_dict_id_value" ON "sys_dict_data" ("dict_id", "dict_value") WHERE "is_deleted" = 0;
COMMENT ON INDEX "uk_sys_dict_data_dict_id_value" IS '同一字典类型下的有效字典值唯一';

-- 用户维度热点查询索引。
CREATE INDEX "expense_idx_user_deleted_time" ON "expense" ("user_id", "is_deleted", "exp_time" DESC, "id" DESC);
COMMENT ON INDEX "expense_idx_user_deleted_time" IS '支出列表：用户、删除标记与支出时间倒序';
CREATE INDEX "income_idx_user_deleted_date" ON "income" ("user_id", "is_deleted", "inc_date" DESC, "id" DESC);
COMMENT ON INDEX "income_idx_user_deleted_date" IS '收入列表：用户、删除标记与收入日期倒序';
CREATE INDEX "time_record_idx_user_date_update" ON "time_record" ("user_id", "is_deleted", "date", "update_time", "id");
COMMENT ON INDEX "time_record_idx_user_date_update" IS '时迹按日查询：用户、日期与更新时间';
CREATE INDEX "b_video_idx_user_deleted_update" ON "b_video" ("user_id", "is_deleted", "update_time" DESC NULLS LAST, "id" DESC);
COMMENT ON INDEX "b_video_idx_user_deleted_update" IS '视频列表：用户、删除标记与更新时间倒序';
CREATE INDEX "exercise_record_idx_user_deleted_date" ON "exercise_record" ("user_id", "is_deleted", "exercise_date" DESC, "id" DESC);
COMMENT ON INDEX "exercise_record_idx_user_deleted_date" IS '运动记录列表：用户、删除标记与运动日期倒序';
CREATE INDEX "exercise_record_idx_user_time" ON "exercise_record" ("user_id", "time_id");
COMMENT ON INDEX "exercise_record_idx_user_time" IS '运动记录按时迹反查：用户与时迹ID';
CREATE INDEX "memo_idx_user_deleted_update" ON "memo" ("user_id", "is_deleted", "update_time" DESC NULLS LAST, "id" DESC);
COMMENT ON INDEX "memo_idx_user_deleted_update" IS '备忘录列表：用户、删除标记与更新时间倒序';
CREATE INDEX "thought_idx_user_deleted_update" ON "thought" ("user_id", "is_deleted", "update_time" DESC NULLS LAST, "id" DESC);
COMMENT ON INDEX "thought_idx_user_deleted_update" IS '闪念列表：用户、删除标记与更新时间倒序';
CREATE INDEX "thought_rela_event_idx_thought" ON "thought_rela_event" ("thought_id", "id");
COMMENT ON INDEX "thought_rela_event_idx_thought" IS '闪念关联事件：按闪念批量读取';
CREATE INDEX "task_detail_idx_task_user_status_sort" ON "task_detail" ("task_id", "user_id", "is_completed", "priority" ASC NULLS FIRST, "sort_order", "id");
COMMENT ON INDEX "task_detail_idx_task_user_status_sort" IS '任务明细：任务、用户、完成状态与排序';
CREATE INDEX "task_detail_idx_user_starred_status" ON "task_detail" ("user_id", "is_starred", "is_completed", "priority" ASC NULLS FIRST, "sort_order", "id");
COMMENT ON INDEX "task_detail_idx_user_starred_status" IS '关注明细：用户、关注与完成状态与排序';
CREATE INDEX "wardrobe_category_idx_type_user_sort" ON "wardrobe_category" ("category_type", "user_id", "sort_order" ASC NULLS FIRST);
COMMENT ON INDEX "wardrobe_category_idx_type_user_sort" IS '衣柜分类：系统 / 用户分类与排序';
CREATE INDEX "login_log_idx_user_created" ON "login_log" ("user_id", "created_at");
COMMENT ON INDEX "login_log_idx_user_created" IS '登录日志按用户与时间清理 / 查询';
CREATE INDEX "login_log_idx_created" ON "login_log" ("created_at");
COMMENT ON INDEX "login_log_idx_created" IS '登录日志按时间清理';
CREATE INDEX "device_idx_user_deleted_purchase" ON "device" ("user_id", "is_deleted", "purchase_date" DESC NULLS LAST, "id" DESC);
COMMENT ON INDEX "device_idx_user_deleted_purchase" IS '设备列表：用户、删除标记与购买日期倒序';
CREATE INDEX "performance_idx_user_deleted_date" ON "performance" ("user_id", "is_deleted", "performance_date" DESC, "id" DESC);
COMMENT ON INDEX "performance_idx_user_deleted_date" IS '演出列表：用户、删除标记与演出日期倒序';
