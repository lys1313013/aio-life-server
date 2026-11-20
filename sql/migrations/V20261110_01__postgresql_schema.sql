-- 目的：为 PostgreSQL 18+ 空库创建截至 20261120.01 的完整业务结构。
-- 前置：目标库为空，由独立 Flyway 入口执行。
-- 影响：创建 74 张表及其约束、索引、注释和派生列函数；公共数据由后续种子导入。
-- 重跑：由 Flyway 版本历史管理，失败整体回滚。
-- 执行：mvn -f tools/flyway/pom.xml compile flyway:migrate flyway:validate

-- 系统、权限、认证与公共字典

CREATE TABLE "api_key" (
  "id" BIGINT NOT NULL,
  "user_id" BIGINT NOT NULL,
  "remark" VARCHAR(255),
  "expired_at" TIMESTAMP(0) WITHOUT TIME ZONE,
  "create_user" BIGINT,
  "create_time" TIMESTAMP(0) WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
  "update_user" BIGINT,
  "update_time" TIMESTAMP(0) WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
  "is_deleted" SMALLINT DEFAULT '0'::SMALLINT NOT NULL,
  "api_key_hash" CHARACTER(64) NOT NULL COLLATE "pg_catalog"."C",
  "key_prefix" VARCHAR(16) NOT NULL COLLATE "pg_catalog"."C",
  CONSTRAINT "ck_api_key_is_deleted" CHECK (("is_deleted" = ANY (ARRAY[0, 1]))),
  CONSTRAINT "api_key_pkey" PRIMARY KEY ("id")
);

COMMENT ON TABLE "api_key" IS 'API Key 表';
COMMENT ON COLUMN "api_key"."id" IS '主键ID';
COMMENT ON COLUMN "api_key"."user_id" IS '用户ID';
COMMENT ON COLUMN "api_key"."remark" IS '备注';
COMMENT ON COLUMN "api_key"."expired_at" IS '过期时间';
COMMENT ON COLUMN "api_key"."create_user" IS '创建人';
COMMENT ON COLUMN "api_key"."create_time" IS '创建时间';
COMMENT ON COLUMN "api_key"."update_user" IS '更新人';
COMMENT ON COLUMN "api_key"."update_time" IS '更新时间';
COMMENT ON COLUMN "api_key"."is_deleted" IS '是否删除';
COMMENT ON COLUMN "api_key"."api_key_hash" IS 'API Key的SHA-256摘要，不保存原文';
COMMENT ON COLUMN "api_key"."key_prefix" IS 'API Key前12位，仅用于列表识别';

CREATE INDEX "api_key_idx_user_id" ON "api_key" ("user_id");
COMMENT ON INDEX "api_key_idx_user_id" IS '业务检索索引：idx_user_id';
CREATE UNIQUE INDEX "uk_api_key_hash" ON "api_key" ("api_key_hash") WHERE ("is_deleted" = 0);
COMMENT ON INDEX "uk_api_key_hash" IS '有效API Key摘要唯一，已删除Key可重新生成';

CREATE TABLE "api_key_log" (
  "id" BIGINT NOT NULL,
  "api_key_id" BIGINT NOT NULL,
  "request_path" VARCHAR(255) NOT NULL,
  "request_method" VARCHAR(10) NOT NULL,
  "response_status" INTEGER,
  "client_ip" VARCHAR(45),
  "create_time" TIMESTAMP(0) WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
  CONSTRAINT "api_key_log_pkey" PRIMARY KEY ("id")
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

CREATE TABLE "login_log" (
  "id" BIGINT NOT NULL,
  "user_id" BIGINT,
  "username" VARCHAR(50) NOT NULL,
  "created_at" TIMESTAMP(0) WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
  "client_ip" VARCHAR(45) CONSTRAINT "login_log_ip_address_not_null" NOT NULL,
  CONSTRAINT "login_log_pkey" PRIMARY KEY ("id")
);

COMMENT ON TABLE "login_log" IS '登录日志';
COMMENT ON COLUMN "login_log"."id" IS '登录日志ID';
COMMENT ON COLUMN "login_log"."user_id" IS '用户ID';
COMMENT ON COLUMN "login_log"."username" IS '用户名';
COMMENT ON COLUMN "login_log"."created_at" IS '创建时间';
COMMENT ON COLUMN "login_log"."client_ip" IS '客户端来源IP，IPv4或IPv6';

CREATE INDEX "login_log_idx_created" ON "login_log" ("created_at");
COMMENT ON INDEX "login_log_idx_created" IS '登录日志按时间清理';
CREATE INDEX "login_log_idx_user_created" ON "login_log" ("user_id", "created_at");
COMMENT ON INDEX "login_log_idx_user_created" IS '登录日志按用户与时间清理 / 查询';

CREATE TABLE "mail_log" (
  "id" BIGINT NOT NULL,
  "send_to" VARCHAR(254) NOT NULL,
  "subject" VARCHAR(255),
  "biz_type" VARCHAR(50),
  "status" SMALLINT DEFAULT '1'::SMALLINT NOT NULL,
  "error_msg" VARCHAR(255),
  "client_ip" VARCHAR(45),
  "create_time" TIMESTAMP(0) WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
  CONSTRAINT "ck_mail_log_status" CHECK (("status" = ANY (ARRAY[0, 1]))),
  CONSTRAINT "mail_log_pkey" PRIMARY KEY ("id")
);

COMMENT ON TABLE "mail_log" IS '邮件发送记录表';
COMMENT ON COLUMN "mail_log"."id" IS '主键ID';
COMMENT ON COLUMN "mail_log"."send_to" IS '接收者邮箱，最长254字符';
COMMENT ON COLUMN "mail_log"."subject" IS '邮件标题';
COMMENT ON COLUMN "mail_log"."biz_type" IS '业务类型：register-注册, login-登录, reset_pwd-重置密码, system_notice-系统通知等';
COMMENT ON COLUMN "mail_log"."status" IS '发送状态：1-成功，0-失败';
COMMENT ON COLUMN "mail_log"."error_msg" IS '失败原因';
COMMENT ON COLUMN "mail_log"."client_ip" IS '请求客户端IP，IPv4或IPv6';
COMMENT ON COLUMN "mail_log"."create_time" IS '创建时间';

CREATE INDEX "mail_log_idx_create_time" ON "mail_log" ("create_time");
COMMENT ON INDEX "mail_log_idx_create_time" IS '业务检索索引：idx_create_time';
CREATE INDEX "mail_log_idx_send_to_biz_type" ON "mail_log" ("send_to", "biz_type");
COMMENT ON INDEX "mail_log_idx_send_to_biz_type" IS '业务检索索引：idx_send_to_biz_type';

CREATE TABLE "sys_activity_log" (
  "id" BIGINT NOT NULL,
  "log_type" VARCHAR(16) NOT NULL,
  "user_id" BIGINT,
  "username" VARCHAR(100) DEFAULT ''::character varying NOT NULL,
  "nickname" VARCHAR(100) DEFAULT ''::character varying NOT NULL,
  "client_ip" VARCHAR(45) DEFAULT ''::character varying CONSTRAINT "sys_activity_log_ip_address_not_null" NOT NULL,
  "browser" VARCHAR(100) DEFAULT ''::character varying NOT NULL,
  "function_name" VARCHAR(200),
  "function_item" VARCHAR(100),
  "access_type" VARCHAR(32),
  "request_path" VARCHAR(255),
  "request_method" VARCHAR(16),
  "request_params" JSONB,
  "request_time" TIMESTAMP(3) WITHOUT TIME ZONE,
  "duration_ms" INTEGER,
  "server_ip" VARCHAR(45),
  "success" SMALLINT DEFAULT '1'::SMALLINT NOT NULL,
  "create_user" BIGINT,
  "create_time" TIMESTAMP(0) WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
  "update_user" BIGINT,
  "update_time" TIMESTAMP(0) WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
  "is_deleted" SMALLINT DEFAULT '0'::SMALLINT NOT NULL,
  CONSTRAINT "ck_sys_activity_log_is_deleted" CHECK (("is_deleted" = ANY (ARRAY[0, 1]))),
  CONSTRAINT "ck_sys_activity_log_success" CHECK (("success" = ANY (ARRAY[0, 1]))),
  CONSTRAINT "sys_activity_log_pkey" PRIMARY KEY ("id")
);

COMMENT ON TABLE "sys_activity_log" IS '操作和访问日志';
COMMENT ON COLUMN "sys_activity_log"."id" IS '主键ID';
COMMENT ON COLUMN "sys_activity_log"."log_type" IS 'OPERATION 操作 / ACCESS 访问';
COMMENT ON COLUMN "sys_activity_log"."user_id" IS '操作用户ID';
COMMENT ON COLUMN "sys_activity_log"."username" IS '用户名';
COMMENT ON COLUMN "sys_activity_log"."nickname" IS '昵称';
COMMENT ON COLUMN "sys_activity_log"."client_ip" IS '客户端来源IP，IPv4或IPv6';
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

CREATE TABLE "sys_config" (
  "id" BIGINT NOT NULL,
  "config_key" VARCHAR(128) NOT NULL COLLATE "pg_catalog"."C",
  "config_value" TEXT,
  "config_type" VARCHAR(32) DEFAULT 'STRING'::character varying,
  "description" VARCHAR(512),
  "is_deleted" SMALLINT DEFAULT '0'::SMALLINT NOT NULL,
  "create_time" TIMESTAMP(0) WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
  "update_time" TIMESTAMP(0) WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
  "create_user" BIGINT,
  "update_user" BIGINT,
  CONSTRAINT "ck_sys_config_is_deleted" CHECK (("is_deleted" = ANY (ARRAY[0, 1]))),
  CONSTRAINT "sys_config_pkey" PRIMARY KEY ("id")
);

COMMENT ON TABLE "sys_config" IS '系统配置表';
COMMENT ON COLUMN "sys_config"."id" IS '主键ID（雪花）';
COMMENT ON COLUMN "sys_config"."config_key" IS '配置键（唯一）';
COMMENT ON COLUMN "sys_config"."config_value" IS '配置值（JSON 或纯文本），敏感字段以SM4-GCM密文保存';
COMMENT ON COLUMN "sys_config"."config_type" IS '类型：STRING/JSON/BOOLEAN/NUMBER（前端渲染用）';
COMMENT ON COLUMN "sys_config"."description" IS '配置说明';
COMMENT ON COLUMN "sys_config"."is_deleted" IS '逻辑删除标记：0=未删除，1=已删除';
COMMENT ON COLUMN "sys_config"."create_time" IS '创建时间';
COMMENT ON COLUMN "sys_config"."update_time" IS '更新时间';
COMMENT ON COLUMN "sys_config"."create_user" IS '创建人ID';
COMMENT ON COLUMN "sys_config"."update_user" IS '更新人ID';

CREATE UNIQUE INDEX "uk_sys_config_config_key" ON "sys_config" ("config_key") WHERE ("is_deleted" = 0);
COMMENT ON INDEX "uk_sys_config_config_key" IS '有效系统配置键唯一，已删除配置不占用';

CREATE TABLE "sys_dict_data" (
  "id" BIGINT CONSTRAINT "sys_dict_data_dict_code_not_null" NOT NULL,
  "dict_type_id" BIGINT CONSTRAINT "sys_dict_data_dict_id_not_null" NOT NULL,
  "sort_order" INTEGER DEFAULT 0 NOT NULL,
  "dict_label" VARCHAR(100) NOT NULL,
  "dict_value" VARCHAR(100) NOT NULL COLLATE "pg_catalog"."C",
  "css_class" VARCHAR(100),
  "list_class" VARCHAR(100),
  "is_default" SMALLINT DEFAULT 0 NOT NULL,
  "create_user" BIGINT,
  "create_time" TIMESTAMP(0) WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
  "update_user" BIGINT,
  "update_time" TIMESTAMP(0) WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
  "remark" VARCHAR(500),
  "is_deleted" SMALLINT DEFAULT '0'::SMALLINT NOT NULL,
  "is_enabled" SMALLINT DEFAULT 1 NOT NULL,
  CONSTRAINT "ck_sys_dict_data_enabled" CHECK (("is_enabled" = ANY (ARRAY[0, 1]))),
  CONSTRAINT "ck_sys_dict_data_is_default" CHECK (("is_default" = ANY (ARRAY[0, 1]))),
  CONSTRAINT "ck_sys_dict_data_is_deleted" CHECK (("is_deleted" = ANY (ARRAY[0, 1]))),
  CONSTRAINT "ck_sys_dict_data_label_not_blank" CHECK (("btrim"(("dict_label")::TEXT) <> ''::TEXT)),
  CONSTRAINT "ck_sys_dict_data_value_not_blank" CHECK (("btrim"(("dict_value")::TEXT) <> ''::TEXT)),
  CONSTRAINT "sys_dict_data_pkey" PRIMARY KEY ("id")
);

COMMENT ON TABLE "sys_dict_data" IS '字典数据表';
COMMENT ON COLUMN "sys_dict_data"."id" IS '字典项ID，由应用生成雪花ID';
COMMENT ON COLUMN "sys_dict_data"."dict_type_id" IS '所属字典类型ID，关联sys_dict_type.id';
COMMENT ON COLUMN "sys_dict_data"."sort_order" IS '排序值，数值越小越靠前';
COMMENT ON COLUMN "sys_dict_data"."dict_label" IS '字典标签，必填且不能只有空白';
COMMENT ON COLUMN "sys_dict_data"."dict_value" IS '字典键值，必填且不能只有空白';
COMMENT ON COLUMN "sys_dict_data"."css_class" IS '样式属性（其他样式扩展）';
COMMENT ON COLUMN "sys_dict_data"."list_class" IS '表格回显样式';
COMMENT ON COLUMN "sys_dict_data"."is_default" IS '是否默认值：1=是，0=否';
COMMENT ON COLUMN "sys_dict_data"."create_user" IS '创建人';
COMMENT ON COLUMN "sys_dict_data"."create_time" IS '创建时间';
COMMENT ON COLUMN "sys_dict_data"."update_user" IS '更新人';
COMMENT ON COLUMN "sys_dict_data"."update_time" IS '更新时间';
COMMENT ON COLUMN "sys_dict_data"."remark" IS '备注';
COMMENT ON COLUMN "sys_dict_data"."is_deleted" IS '是否删除: 0-未删除, 1-已删除';
COMMENT ON COLUMN "sys_dict_data"."is_enabled" IS '是否启用：1=启用，0=停用';
COMMENT ON CONSTRAINT "ck_sys_dict_data_label_not_blank" ON "sys_dict_data" IS '字典标签必填，不允许空串或纯空白';
COMMENT ON CONSTRAINT "ck_sys_dict_data_value_not_blank" ON "sys_dict_data" IS '字典键值必填，不允许空串或纯空白';

CREATE UNIQUE INDEX "uk_sys_dict_data_dict_type_id_value" ON "sys_dict_data" ("dict_type_id", "dict_value") WHERE ("is_deleted" = 0);
COMMENT ON INDEX "uk_sys_dict_data_dict_type_id_value" IS '同一字典类型下的有效字典值唯一';

CREATE TABLE "sys_dict_type" (
  "id" BIGINT CONSTRAINT "sys_dict_type_dict_id_not_null" NOT NULL,
  "dict_name" VARCHAR(100) DEFAULT ''::character varying,
  "dict_type" VARCHAR(100) DEFAULT ''::character varying COLLATE "pg_catalog"."C",
  "create_user" BIGINT,
  "create_time" TIMESTAMP(0) WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
  "update_user" BIGINT,
  "update_time" TIMESTAMP(0) WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
  "remark" VARCHAR(500),
  "is_deleted" SMALLINT DEFAULT '0'::SMALLINT NOT NULL,
  "is_enabled" SMALLINT DEFAULT 1 NOT NULL,
  CONSTRAINT "ck_sys_dict_type_enabled" CHECK (("is_enabled" = ANY (ARRAY[0, 1]))),
  CONSTRAINT "ck_sys_dict_type_is_deleted" CHECK (("is_deleted" = ANY (ARRAY[0, 1]))),
  CONSTRAINT "sys_dict_type_pkey" PRIMARY KEY ("id")
);

COMMENT ON TABLE "sys_dict_type" IS '字典类型表';
COMMENT ON COLUMN "sys_dict_type"."id" IS '字典类型ID，由应用生成雪花ID';
COMMENT ON COLUMN "sys_dict_type"."dict_name" IS '字典名称';
COMMENT ON COLUMN "sys_dict_type"."dict_type" IS '字典类型';
COMMENT ON COLUMN "sys_dict_type"."create_user" IS '创建人';
COMMENT ON COLUMN "sys_dict_type"."create_time" IS '创建时间';
COMMENT ON COLUMN "sys_dict_type"."update_user" IS '更新人';
COMMENT ON COLUMN "sys_dict_type"."update_time" IS '更新时间';
COMMENT ON COLUMN "sys_dict_type"."remark" IS '备注';
COMMENT ON COLUMN "sys_dict_type"."is_deleted" IS '是否删除: 0-未删除, 1-已删除';
COMMENT ON COLUMN "sys_dict_type"."is_enabled" IS '是否启用：1=启用，0=停用';

CREATE UNIQUE INDEX "uk_sys_dict_type_dict_type" ON "sys_dict_type" ("dict_type") WHERE ("is_deleted" = 0);
COMMENT ON INDEX "uk_sys_dict_type_dict_type" IS '有效系统字典类型编码唯一，已删除类型不占用';

CREATE TABLE "sys_menu" (
  "id" BIGINT NOT NULL,
  "parent_id" BIGINT,
  "name" VARCHAR(64) NOT NULL,
  "path" VARCHAR(255) NOT NULL COLLATE "pg_catalog"."C",
  "component" VARCHAR(255),
  "redirect" VARCHAR(255),
  "icon_color" VARCHAR(7),
  "meta" JSONB,
  "roles" VARCHAR(255),
  "sort_order" INTEGER DEFAULT 0 NOT NULL,
  "create_user" BIGINT,
  "create_time" TIMESTAMP(0) WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
  "update_user" BIGINT,
  "update_time" TIMESTAMP(0) WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
  "is_deleted" SMALLINT DEFAULT '0'::SMALLINT NOT NULL,
  "visible_clients" INTEGER DEFAULT 3 NOT NULL,
  CONSTRAINT "ck_sys_menu_is_deleted" CHECK (("is_deleted" = ANY (ARRAY[0, 1]))),
  CONSTRAINT "ck_sys_menu_parent_root" CHECK ((("parent_id" IS NULL) OR ("parent_id" > 0))),
  CONSTRAINT "ck_sys_menu_parent_self" CHECK (("parent_id" <> "id")),
  CONSTRAINT "ck_sys_menu_visible_clients" CHECK ((("visible_clients" >= 0) AND (("visible_clients" & (~ 3)) = 0))),
  CONSTRAINT "sys_menu_pkey" PRIMARY KEY ("id")
);

COMMENT ON TABLE "sys_menu" IS '系统菜单表';
COMMENT ON COLUMN "sys_menu"."id" IS '主键ID';
COMMENT ON COLUMN "sys_menu"."parent_id" IS '父级菜单ID；NULL 表示根节点';
COMMENT ON COLUMN "sys_menu"."name" IS '路由名称';
COMMENT ON COLUMN "sys_menu"."path" IS '路由路径（唯一）';
COMMENT ON COLUMN "sys_menu"."component" IS '组件标识（BasicLayout/IFrameView 或 views 相对路径，如 system/user/index）';
COMMENT ON COLUMN "sys_menu"."redirect" IS '重定向';
COMMENT ON COLUMN "sys_menu"."icon_color" IS '菜单图标颜色（#RRGGBB，空为默认）';
COMMENT ON COLUMN "sys_menu"."meta" IS '路由 meta（title/icon/order/keepAlive/hideInMenu/link等）';
COMMENT ON COLUMN "sys_menu"."roles" IS '可访问角色（逗号分隔，空表示所有）';
COMMENT ON COLUMN "sys_menu"."sort_order" IS '排序值，数值越小越靠前';
COMMENT ON COLUMN "sys_menu"."create_user" IS '创建人';
COMMENT ON COLUMN "sys_menu"."create_time" IS '创建时间';
COMMENT ON COLUMN "sys_menu"."update_user" IS '更新人';
COMMENT ON COLUMN "sys_menu"."update_time" IS '更新时间';
COMMENT ON COLUMN "sys_menu"."is_deleted" IS '逻辑删除标记：0=未删除，1=已删除';
COMMENT ON COLUMN "sys_menu"."visible_clients" IS '菜单展示端位掩码：1=Web，2=移动端，3=两端，0=均不展示；不改变访问权限';
COMMENT ON CONSTRAINT "ck_sys_menu_parent_root" ON "sys_menu" IS '根节点使用 NULL，父级ID必须为正数';
COMMENT ON CONSTRAINT "ck_sys_menu_parent_self" ON "sys_menu" IS '菜单不能以自身为父级';
COMMENT ON CONSTRAINT "ck_sys_menu_visible_clients" ON "sys_menu" IS '展示端只允许组合已定义的 Web 和移动端位';

CREATE INDEX "sys_menu_idx_sys_menu_parent_id" ON "sys_menu" ("parent_id");
COMMENT ON INDEX "sys_menu_idx_sys_menu_parent_id" IS '业务检索索引：idx_sys_menu_parent_id';
CREATE INDEX "sys_menu_idx_sys_menu_sort" ON "sys_menu" ("sort_order");
COMMENT ON INDEX "sys_menu_idx_sys_menu_sort" IS '业务检索索引：idx_sys_menu_sort';
CREATE UNIQUE INDEX "sys_menu_uk_sys_menu_path" ON "sys_menu" ("path") WHERE ("is_deleted" = 0);
COMMENT ON INDEX "sys_menu_uk_sys_menu_path" IS '有效菜单路由路径唯一，已删除菜单不占用';

CREATE TABLE "sys_region" (
  "id" BIGINT NOT NULL,
  "code" VARCHAR(12) NOT NULL COLLATE "pg_catalog"."C",
  "parent_code" VARCHAR(12) COLLATE "pg_catalog"."C",
  "name" VARCHAR(100) NOT NULL,
  "level" INTEGER NOT NULL,
  "data_version" VARCHAR(32) NOT NULL,
  "create_user" BIGINT,
  "create_time" TIMESTAMP(0) WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
  "update_user" BIGINT,
  "update_time" TIMESTAMP(0) WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
  "is_deleted" SMALLINT DEFAULT '0'::SMALLINT NOT NULL,
  CONSTRAINT "ck_sys_region_is_deleted" CHECK (("is_deleted" = ANY (ARRAY[0, 1]))),
  CONSTRAINT "ck_sys_region_level" CHECK ((("level" >= 1) AND ("level" <= 4))),
  CONSTRAINT "ck_sys_region_parent_level" CHECK ((("level" = 1) = ("parent_code" IS NULL))),
  CONSTRAINT "ck_sys_region_parent_root" CHECK ((("parent_code" IS NULL) OR (("parent_code")::TEXT <> '0'::TEXT))),
  CONSTRAINT "ck_sys_region_parent_self" CHECK ((("parent_code")::TEXT <> ("code")::TEXT)),
  CONSTRAINT "sys_region_pkey" PRIMARY KEY ("id")
);

COMMENT ON TABLE "sys_region" IS '省市区县乡镇街道基础目录，当前库只保留一版统计区划快照';
COMMENT ON COLUMN "sys_region"."id" IS '目录记录ID，初始化数据使用编码映射的固定ID，应用新增使用雪花ID';
COMMENT ON COLUMN "sys_region"."code" IS '地区编码，保留原始字符串，目录内唯一';
COMMENT ON COLUMN "sys_region"."parent_code" IS '上级地区编码；NULL 表示省级根节点';
COMMENT ON COLUMN "sys_region"."name" IS '地区名称，取自当前保留版本的原始名称';
COMMENT ON COLUMN "sys_region"."level" IS '地区层级：1=省，2=市，3=区县，4=乡镇街道';
COMMENT ON COLUMN "sys_region"."data_version" IS '来源版本，NBS-2023表示2023年统计区划快照，不表示当前实时区划；同一地区编码同时只保留一版';
COMMENT ON COLUMN "sys_region"."create_user" IS '创建人用户ID，初始化数据为空';
COMMENT ON COLUMN "sys_region"."create_time" IS '创建时间，由应用写入，初始化数据为空';
COMMENT ON COLUMN "sys_region"."update_user" IS '最后更新人用户ID，未记录时为空';
COMMENT ON COLUMN "sys_region"."update_time" IS '最后更新时间，未记录时为空';
COMMENT ON COLUMN "sys_region"."is_deleted" IS '逻辑删除标记：0=未删除，1=已删除';
COMMENT ON CONSTRAINT "ck_sys_region_parent_level" ON "sys_region" IS '省级（level=1）没有上级，其余层级必须有上级';
COMMENT ON CONSTRAINT "ck_sys_region_parent_root" ON "sys_region" IS '根节点使用 NULL，不再使用字符串 0';
COMMENT ON CONSTRAINT "ck_sys_region_parent_self" ON "sys_region" IS '地区不能以自身为上级';

CREATE INDEX "sys_region_idx_sys_region_parent" ON "sys_region" ("parent_code", "is_deleted", "code");
COMMENT ON INDEX "sys_region_idx_sys_region_parent" IS '业务检索索引：idx_sys_region_parent';
CREATE UNIQUE INDEX "sys_region_uk_sys_region_code" ON "sys_region" ("code") WHERE ("is_deleted" = 0);
COMMENT ON INDEX "sys_region_uk_sys_region_code" IS '当前库内有效地区编码唯一，已删除记录不占用；切换快照版本需先替换旧版本数据';

CREATE TABLE "sys_scheduled_job" (
  "id" BIGINT NOT NULL,
  "job_code" VARCHAR(80) NOT NULL COLLATE "pg_catalog"."C",
  "name" VARCHAR(100) NOT NULL,
  "cron" VARCHAR(100) NOT NULL,
  "is_enabled" SMALLINT DEFAULT '1'::SMALLINT NOT NULL,
  "revision" INTEGER DEFAULT 0 NOT NULL,
  "create_user" BIGINT,
  "create_time" TIMESTAMP(0) WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
  "update_user" BIGINT,
  "update_time" TIMESTAMP(0) WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
  "is_deleted" SMALLINT DEFAULT '0'::SMALLINT NOT NULL,
  CONSTRAINT "ck_sys_scheduled_job_is_deleted" CHECK (("is_deleted" = ANY (ARRAY[0, 1]))),
  CONSTRAINT "ck_sys_scheduled_job_is_enabled" CHECK (("is_enabled" = ANY (ARRAY[0, 1]))),
  CONSTRAINT "sys_scheduled_job_pkey" PRIMARY KEY ("id")
);

COMMENT ON TABLE "sys_scheduled_job" IS '可配置定时任务';
COMMENT ON COLUMN "sys_scheduled_job"."id" IS '任务ID，由应用生成雪花ID';
COMMENT ON COLUMN "sys_scheduled_job"."job_code" IS '代码注册的任务编码，不允许任意脚本或方法名';
COMMENT ON COLUMN "sys_scheduled_job"."name" IS '任务名称';
COMMENT ON COLUMN "sys_scheduled_job"."cron" IS '六段Spring Cron，秒为单个整数，每分钟最多一次';
COMMENT ON COLUMN "sys_scheduled_job"."is_enabled" IS '是否启用：1=启用，0=停用；不限制手动执行';
COMMENT ON COLUMN "sys_scheduled_job"."revision" IS '配置版本，从0开始，每次修改递增';
COMMENT ON COLUMN "sys_scheduled_job"."create_user" IS '创建人用户ID，自动任务为空';
COMMENT ON COLUMN "sys_scheduled_job"."create_time" IS '创建时间';
COMMENT ON COLUMN "sys_scheduled_job"."update_user" IS '最后更新人用户ID，自动任务为空';
COMMENT ON COLUMN "sys_scheduled_job"."update_time" IS '最后更新时间';
COMMENT ON COLUMN "sys_scheduled_job"."is_deleted" IS '逻辑删除标记：0=未删除，1=已删除';

CREATE UNIQUE INDEX "sys_scheduled_job_uk_sys_scheduled_job_code" ON "sys_scheduled_job" ("job_code") WHERE ("is_deleted" = 0);
COMMENT ON INDEX "sys_scheduled_job_uk_sys_scheduled_job_code" IS '有效定时任务编码唯一，已删除任务不占用';

CREATE TABLE "sys_scheduled_job_log" (
  "id" BIGINT NOT NULL,
  "job_id" BIGINT NOT NULL,
  "trigger_key" VARCHAR(80) NOT NULL,
  "trigger_type" VARCHAR(10) NOT NULL,
  "status" VARCHAR(20) NOT NULL,
  "scheduled_time" TIMESTAMP(0) WITHOUT TIME ZONE,
  "start_time" TIMESTAMP(0) WITHOUT TIME ZONE NOT NULL,
  "end_time" TIMESTAMP(0) WITHOUT TIME ZONE,
  "duration_ms" BIGINT,
  "message" VARCHAR(1000),
  "instance_id" VARCHAR(36) NOT NULL,
  "create_user" BIGINT,
  "create_time" TIMESTAMP(0) WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
  "update_user" BIGINT,
  "update_time" TIMESTAMP(0) WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
  "is_deleted" SMALLINT DEFAULT '0'::SMALLINT NOT NULL,
  CONSTRAINT "ck_sys_scheduled_job_log_is_deleted" CHECK (("is_deleted" = ANY (ARRAY[0, 1]))),
  CONSTRAINT "sys_scheduled_job_log_pkey" PRIMARY KEY ("id")
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

CREATE TABLE "sys_user" (
  "id" BIGINT NOT NULL,
  "username" VARCHAR(50) NOT NULL,
  "password" VARCHAR(255),
  "nickname" VARCHAR(50) NOT NULL,
  "policy_version" VARCHAR(32),
  "policy_accepted_at" TIMESTAMP(0) WITHOUT TIME ZONE,
  "erasure_requested_at" TIMESTAMP(0) WITHOUT TIME ZONE,
  "erasure_retry_at" TIMESTAMP(0) WITHOUT TIME ZONE,
  "create_user" BIGINT,
  "create_time" TIMESTAMP(0) WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
  "update_user" BIGINT,
  "update_time" TIMESTAMP(0) WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
  "last_active_at" TIMESTAMP(0) WITHOUT TIME ZONE,
  "email" VARCHAR(254),
  "role" VARCHAR(50) DEFAULT 'user'::character varying,
  "introduction" VARCHAR(255),
  "is_deleted" SMALLINT DEFAULT '0'::SMALLINT NOT NULL,
  "secondary_password" VARCHAR(128),
  "phone_country_code" VARCHAR(5),
  "phone" VARCHAR(20),
  "phone_verified_at" TIMESTAMP(0) WITHOUT TIME ZONE,
  "wechat_openid" VARCHAR(128),
  "wechat_unionid" VARCHAR(128),
  "active_flag" SMALLINT GENERATED ALWAYS AS (
CASE
    WHEN ("is_deleted" = 0) THEN 1
    ELSE NULL::integer
END) STORED,
  CONSTRAINT "ck_sys_user_is_deleted" CHECK (("is_deleted" = ANY (ARRAY[0, 1]))),
  CONSTRAINT "ck_sys_user_phone_pair" CHECK (((("phone_country_code" IS NULL) AND ("phone" IS NULL)) OR (("phone_country_code" IS NOT NULL) AND ("phone" IS NOT NULL) AND "regexp_like"((("phone_country_code")::TEXT COLLATE "C"), '^[1-9][0-9]{0,2}$'::TEXT) AND "regexp_like"((("phone")::TEXT COLLATE "C"), '^[0-9]{4,14}$'::TEXT) AND (("char_length"(("phone_country_code")::TEXT) + "char_length"(("phone")::TEXT)) <= 15)))),
  CONSTRAINT "sys_user_pkey" PRIMARY KEY ("id")
);

COMMENT ON TABLE "sys_user" IS '用户表';
COMMENT ON COLUMN "sys_user"."id" IS '用户ID';
COMMENT ON COLUMN "sys_user"."username" IS '用户名';
COMMENT ON COLUMN "sys_user"."password" IS '登录密码bcrypt哈希，包含随机盐与计算成本；NULL表示未设置';
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
COMMENT ON COLUMN "sys_user"."email" IS '邮箱，最长254字符';
COMMENT ON COLUMN "sys_user"."role" IS '角色类型';
COMMENT ON COLUMN "sys_user"."introduction" IS '个人简介';
COMMENT ON COLUMN "sys_user"."is_deleted" IS '逻辑删除标记：0=未删除，1=已删除';
COMMENT ON COLUMN "sys_user"."secondary_password" IS '二级密码bcrypt哈希，包含随机盐与计算成本；NULL表示未设置';
COMMENT ON COLUMN "sys_user"."phone_country_code" IS '国际电话区号，不含加号';
COMMENT ON COLUMN "sys_user"."phone" IS '不含国际区号的手机号';
COMMENT ON COLUMN "sys_user"."phone_verified_at" IS '最近接收可信手机号验证结果的时间';
COMMENT ON COLUMN "sys_user"."wechat_openid" IS '当前配置小程序的openid';
COMMENT ON COLUMN "sys_user"."wechat_unionid" IS '微信unionid，可为空，不用于自动合并账号';
COMMENT ON COLUMN "sys_user"."active_flag" IS '仅有效用户参与凭证唯一约束';

CREATE INDEX "sys_user_idx_user_erasure_retry" ON "sys_user" ("is_deleted", "erasure_retry_at");
COMMENT ON INDEX "sys_user_idx_user_erasure_retry" IS '业务检索索引：idx_user_erasure_retry';
CREATE UNIQUE INDEX "sys_user_uk_user_email_active" ON "sys_user" ("lower"(("email")::TEXT)) WHERE (("is_deleted" = 0) AND ("email" IS NOT NULL) AND (("email")::TEXT <> ''::TEXT));
COMMENT ON INDEX "sys_user_uk_user_email_active" IS '有效用户的邮箱忽略大小写唯一，空邮箱不参与比较，已删除用户不占用';
CREATE UNIQUE INDEX "sys_user_uk_user_phone_active" ON "sys_user" ("phone_country_code", "phone", "active_flag");
COMMENT ON INDEX "sys_user_uk_user_phone_active" IS '唯一性约束：uk_user_phone_active';
CREATE UNIQUE INDEX "sys_user_uk_user_username_active" ON "sys_user" ("lower"(("username")::TEXT)) WHERE ("is_deleted" = 0);
COMMENT ON INDEX "sys_user_uk_user_username_active" IS '有效用户的用户名忽略大小写唯一，已删除用户不占用';
CREATE UNIQUE INDEX "sys_user_uk_user_wechat_active" ON "sys_user" ("wechat_openid", "active_flag");
COMMENT ON INDEX "sys_user_uk_user_wechat_active" IS '唯一性约束：uk_user_wechat_active';

CREATE TABLE "sys_work_calendar" (
  "calendar_date" DATE NOT NULL,
  "day_type" SMALLINT NOT NULL,
  "holiday_name" VARCHAR(50),
  "source" VARCHAR(50) NOT NULL,
  "updated_at" TIMESTAMP(0) WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
  CONSTRAINT "chk_sys_work_calendar_day_type" CHECK (("day_type" = ANY (ARRAY[0, 1, 2, 3]))),
  CONSTRAINT "sys_work_calendar_pkey" PRIMARY KEY ("calendar_date")
);

COMMENT ON TABLE "sys_work_calendar" IS '中国大陆工作日历表';
COMMENT ON COLUMN "sys_work_calendar"."calendar_date" IS '日历日期（中国大陆，按北京时间）';
COMMENT ON COLUMN "sys_work_calendar"."day_type" IS '日期类型：0普通工作日，1普通周末，2节假日休息，3调休补班';
COMMENT ON COLUMN "sys_work_calendar"."holiday_name" IS '关联节日名称，如春节、国庆节；普通日期为空';
COMMENT ON COLUMN "sys_work_calendar"."source" IS '数据来源，如 weekday_rule、holiday-cn、manual';
COMMENT ON COLUMN "sys_work_calendar"."updated_at" IS '最后更新时间';

CREATE TABLE "university" (
  "id" BIGINT NOT NULL,
  "name" VARCHAR(200) NOT NULL,
  "province_code" VARCHAR(12) NOT NULL COLLATE "pg_catalog"."C",
  "remark" TEXT,
  "create_user" BIGINT,
  "create_time" TIMESTAMP(0) WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
  "update_user" BIGINT,
  "update_time" TIMESTAMP(0) WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
  "is_deleted" SMALLINT DEFAULT 0 NOT NULL,
  CONSTRAINT "ck_university_is_deleted" CHECK (("is_deleted" = ANY (ARRAY[0, 1]))),
  CONSTRAINT "ck_university_name_not_blank" CHECK (("btrim"(("name")::TEXT) <> ''::TEXT)),
  CONSTRAINT "university_pkey" PRIMARY KEY ("id")
);

COMMENT ON TABLE "university" IS '中国大陆高校公共目录';
COMMENT ON COLUMN "university"."id" IS '高校ID，应用新增使用雪花ID，初始化数据使用固定ID';
COMMENT ON COLUMN "university"."name" IS '学校正式名称';
COMMENT ON COLUMN "university"."province_code" IS '所在省级地区编码，逻辑关联 sys_region.code，目标须有效且 level = 1';
COMMENT ON COLUMN "university"."remark" IS '高校补充说明，未填写时为空';
COMMENT ON COLUMN "university"."create_user" IS '创建人用户ID，系统导入时为空';
COMMENT ON COLUMN "university"."create_time" IS '创建时间';
COMMENT ON COLUMN "university"."update_user" IS '最后更新人用户ID，系统导入时为空';
COMMENT ON COLUMN "university"."update_time" IS '最后更新时间，由应用在更新时维护';
COMMENT ON COLUMN "university"."is_deleted" IS '逻辑删除标记：0=未删除，1=已删除';
COMMENT ON CONSTRAINT "ck_university_is_deleted" ON "university" IS '逻辑删除标记仅允许 0 和 1';
COMMENT ON CONSTRAINT "ck_university_name_not_blank" ON "university" IS '学校正式名称不可为空字符串或仅含普通空格';

CREATE INDEX "idx_university_province" ON "university" ("province_code") WHERE ("is_deleted" = 0);
COMMENT ON INDEX "idx_university_province" IS '按所在省级地区筛选有效高校';

CREATE TABLE "user_home_card_preference" (
  "id" BIGINT NOT NULL,
  "user_id" BIGINT NOT NULL,
  "card_key" VARCHAR(64) NOT NULL,
  "is_enabled" SMALLINT DEFAULT '1'::SMALLINT NOT NULL,
  "sort_order" INTEGER DEFAULT 0 NOT NULL,
  "create_user" BIGINT,
  "create_time" TIMESTAMP(0) WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
  "update_user" BIGINT,
  "update_time" TIMESTAMP(0) WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
  "is_deleted" SMALLINT DEFAULT '0'::SMALLINT NOT NULL,
  CONSTRAINT "ck_user_home_card_preference_is_deleted" CHECK (("is_deleted" = ANY (ARRAY[0, 1]))),
  CONSTRAINT "ck_user_home_card_preference_is_enabled" CHECK (("is_enabled" = ANY (ARRAY[0, 1]))),
  CONSTRAINT "user_home_card_preference_pkey" PRIMARY KEY ("id")
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
  "id" BIGINT NOT NULL,
  "user_id" BIGINT NOT NULL,
  "menu_id" BIGINT NOT NULL,
  "create_user" BIGINT,
  "create_time" TIMESTAMP(0) WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
  "update_user" BIGINT,
  "update_time" TIMESTAMP(0) WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
  "is_deleted" SMALLINT DEFAULT '0'::SMALLINT NOT NULL,
  CONSTRAINT "ck_user_menu_hidden_is_deleted" CHECK (("is_deleted" = ANY (ARRAY[0, 1]))),
  CONSTRAINT "user_menu_hidden_pkey" PRIMARY KEY ("id")
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
  "id" BIGINT NOT NULL,
  "user_id" BIGINT NOT NULL,
  "menu_id" BIGINT NOT NULL,
  "sort_order" INTEGER DEFAULT 0 NOT NULL,
  "is_enabled" SMALLINT DEFAULT '1'::SMALLINT NOT NULL,
  "create_user" BIGINT,
  "create_time" TIMESTAMP(0) WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
  "update_user" BIGINT,
  "update_time" TIMESTAMP(0) WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
  "is_deleted" SMALLINT DEFAULT '0'::SMALLINT NOT NULL,
  CONSTRAINT "ck_user_quick_nav_is_deleted" CHECK (("is_deleted" = ANY (ARRAY[0, 1]))),
  CONSTRAINT "ck_user_quick_nav_is_enabled" CHECK (("is_enabled" = ANY (ARRAY[0, 1]))),
  CONSTRAINT "user_quick_nav_pkey" PRIMARY KEY ("id")
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

CREATE UNIQUE INDEX "uk_user_quick_nav_user_menu" ON "user_quick_nav" ("user_id", "menu_id");
COMMENT ON INDEX "uk_user_quick_nav_user_menu" IS '同一用户同一菜单只能有一行快捷导航配置';
CREATE INDEX "user_quick_nav_idx_user_sort" ON "user_quick_nav" ("user_id", "sort_order");
COMMENT ON INDEX "user_quick_nav_idx_user_sort" IS '业务检索索引：idx_user_sort';

CREATE TABLE "user_secondary_lock_menu" (
  "id" BIGINT NOT NULL,
  "user_id" BIGINT NOT NULL,
  "menu_id" BIGINT NOT NULL,
  "is_deleted" SMALLINT DEFAULT '0'::SMALLINT NOT NULL,
  "create_time" TIMESTAMP(0) WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
  "update_time" TIMESTAMP(0) WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
  "create_user" BIGINT,
  "update_user" BIGINT,
  CONSTRAINT "ck_user_secondary_lock_menu_is_deleted" CHECK (("is_deleted" = ANY (ARRAY[0, 1]))),
  CONSTRAINT "user_secondary_lock_menu_pkey" PRIMARY KEY ("id")
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

CREATE UNIQUE INDEX "uk_user_secondary_lock_menu_user_menu" ON "user_secondary_lock_menu" ("user_id", "menu_id") WHERE ("is_deleted" = 0);
COMMENT ON INDEX "uk_user_secondary_lock_menu_user_menu" IS '同一用户同一菜单只能有一条有效二级锁关联';
CREATE INDEX "user_secondary_lock_menu_idx_user_id" ON "user_secondary_lock_menu" ("user_id");
COMMENT ON INDEX "user_secondary_lock_menu_idx_user_id" IS '业务检索索引：idx_user_id';

CREATE TABLE "user_third_party_account" (
  "id" BIGINT CONSTRAINT "user_bind_id_not_null" NOT NULL,
  "user_id" BIGINT CONSTRAINT "user_bind_user_id_not_null" NOT NULL,
  "platform" VARCHAR(32) CONSTRAINT "user_bind_platform_not_null" NOT NULL,
  "platform_username" VARCHAR(128),
  "access_token_ciphertext" TEXT,
  "meta_fields" JSONB,
  "create_time" TIMESTAMP(0) WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP CONSTRAINT "user_bind_create_time_not_null" NOT NULL,
  "create_user" BIGINT,
  "update_time" TIMESTAMP(0) WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP CONSTRAINT "user_bind_update_time_not_null" NOT NULL,
  "update_user" BIGINT,
  "is_deleted" SMALLINT DEFAULT '0'::SMALLINT CONSTRAINT "user_bind_is_deleted_not_null" NOT NULL,
  CONSTRAINT "ck_user_third_party_account_is_deleted" CHECK (("is_deleted" = ANY (ARRAY[0, 1]))),
  CONSTRAINT "user_third_party_account_pkey" PRIMARY KEY ("id")
);

COMMENT ON TABLE "user_third_party_account" IS '用户第三方平台账号绑定表';
COMMENT ON COLUMN "user_third_party_account"."id" IS '主键';
COMMENT ON COLUMN "user_third_party_account"."user_id" IS '本系统用户ID';
COMMENT ON COLUMN "user_third_party_account"."platform" IS '平台类型：github, leetcode, csdn, shanbay, douban, weread';
COMMENT ON COLUMN "user_third_party_account"."platform_username" IS '第三方平台的用户名/账号';
COMMENT ON COLUMN "user_third_party_account"."access_token_ciphertext" IS 'SM4-GCM加密的第三方访问令牌';
COMMENT ON COLUMN "user_third_party_account"."meta_fields" IS '额外配置(JSON)';
COMMENT ON COLUMN "user_third_party_account"."create_time" IS '绑定时间';
COMMENT ON COLUMN "user_third_party_account"."create_user" IS '创建人';
COMMENT ON COLUMN "user_third_party_account"."update_time" IS '更新时间';
COMMENT ON COLUMN "user_third_party_account"."update_user" IS '更新人';
COMMENT ON COLUMN "user_third_party_account"."is_deleted" IS '是否删除';

CREATE UNIQUE INDEX "uk_user_third_party_account_user_platform" ON "user_third_party_account" ("user_id", "platform") WHERE ("is_deleted" = 0);
COMMENT ON INDEX "uk_user_third_party_account_user_platform" IS '同一用户在同一平台只能有一条有效绑定';

-- 时迹、运动、目标、待办与纪念日

CREATE TABLE "anniversary_record" (
  "id" BIGINT NOT NULL,
  "user_id" BIGINT NOT NULL,
  "title" VARCHAR(200) NOT NULL,
  "target_date" DATE NOT NULL,
  "type" VARCHAR(20) NOT NULL,
  "remark" VARCHAR(500),
  "color" VARCHAR(50),
  "icon" VARCHAR(20),
  "is_pinned" SMALLINT DEFAULT '0'::SMALLINT NOT NULL,
  "pinned_sort_order" INTEGER DEFAULT 0 NOT NULL,
  "is_deleted" SMALLINT DEFAULT '0'::SMALLINT NOT NULL,
  "create_time" TIMESTAMP(0) WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
  "update_time" TIMESTAMP(0) WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
  "create_user" BIGINT,
  "update_user" BIGINT,
  CONSTRAINT "ck_anniversary_record_is_deleted" CHECK (("is_deleted" = ANY (ARRAY[0, 1]))),
  CONSTRAINT "ck_anniversary_record_is_pinned" CHECK (("is_pinned" = ANY (ARRAY[0, 1]))),
  CONSTRAINT "anniversary_record_pkey" PRIMARY KEY ("id")
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

CREATE TABLE "event_record" (
  "id" BIGINT NOT NULL,
  "user_id" BIGINT NOT NULL,
  "title" VARCHAR(100) NOT NULL,
  "participants" VARCHAR(50),
  "event_type" VARCHAR(50) NOT NULL,
  "event_date" DATE NOT NULL,
  "city" VARCHAR(50) NOT NULL,
  "venue" VARCHAR(100) NOT NULL,
  "ticket_price" NUMERIC(10,2) NOT NULL,
  "seat_info" VARCHAR(100),
  "duration_minutes" INTEGER,
  "rating" SMALLINT,
  "review" TEXT,
  "purchase_platform" VARCHAR(50),
  "order_number" VARCHAR(50) COLLATE "pg_catalog"."C",
  "create_user" BIGINT,
  "create_time" TIMESTAMP(0) WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
  "update_user" BIGINT,
  "update_time" TIMESTAMP(0) WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
  "is_deleted" SMALLINT DEFAULT '0'::SMALLINT NOT NULL,
  CONSTRAINT "ck_event_record_is_deleted" CHECK (("is_deleted" = ANY (ARRAY[0, 1]))),
  CONSTRAINT "event_record_pkey" PRIMARY KEY ("id")
);

COMMENT ON TABLE "event_record" IS '活动记录表';
COMMENT ON COLUMN "event_record"."id" IS '唯一标识';
COMMENT ON COLUMN "event_record"."user_id" IS '所属用户ID';
COMMENT ON COLUMN "event_record"."title" IS '活动名称';
COMMENT ON COLUMN "event_record"."participants" IS '演出者、主讲人或嘉宾，未填写时为空';
COMMENT ON COLUMN "event_record"."event_type" IS '活动类型(演唱会/音乐节/技术分享/话剧/音乐会等)';
COMMENT ON COLUMN "event_record"."event_date" IS '活动日期';
COMMENT ON COLUMN "event_record"."city" IS '活动城市';
COMMENT ON COLUMN "event_record"."venue" IS '活动地点';
COMMENT ON COLUMN "event_record"."ticket_price" IS '票价或报名费';
COMMENT ON COLUMN "event_record"."seat_info" IS '座位信息';
COMMENT ON COLUMN "event_record"."duration_minutes" IS '活动时长（分钟）';
COMMENT ON COLUMN "event_record"."rating" IS '活动评分(1-5)';
COMMENT ON COLUMN "event_record"."review" IS '活动评价';
COMMENT ON COLUMN "event_record"."purchase_platform" IS '购票或报名平台';
COMMENT ON COLUMN "event_record"."order_number" IS '购票或报名订单号';
COMMENT ON COLUMN "event_record"."create_user" IS '创建人ID';
COMMENT ON COLUMN "event_record"."create_time" IS '创建时间';
COMMENT ON COLUMN "event_record"."update_user" IS '更新人ID';
COMMENT ON COLUMN "event_record"."update_time" IS '更新时间';
COMMENT ON COLUMN "event_record"."is_deleted" IS '逻辑删除标记：0=未删除，1=已删除';

CREATE INDEX "event_record_idx_event_record_user_deleted" ON "event_record" ("user_id", "is_deleted", "id");
COMMENT ON INDEX "event_record_idx_event_record_user_deleted" IS '业务检索索引：idx_event_record_user_deleted';
CREATE INDEX "event_record_idx_user_deleted_date" ON "event_record" ("user_id", "is_deleted", "event_date" DESC, "id" DESC);
COMMENT ON INDEX "event_record_idx_user_deleted_date" IS '活动列表：用户、删除标记与活动日期倒序';

CREATE TABLE "exercise_record" (
  "id" BIGINT NOT NULL,
  "user_id" BIGINT NOT NULL,
  "exercise_type_id" BIGINT,
  "exercise_date" DATE NOT NULL,
  "exercise_count" INTEGER DEFAULT 0 NOT NULL,
  "description" TEXT,
  "create_user" BIGINT,
  "create_time" TIMESTAMP(0) WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
  "update_time" TIMESTAMP(0) WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
  "update_user" BIGINT,
  "is_deleted" SMALLINT DEFAULT '0'::SMALLINT NOT NULL,
  "time_record_id" BIGINT,
  CONSTRAINT "ck_exercise_record_is_deleted" CHECK (("is_deleted" = ANY (ARRAY[0, 1]))),
  CONSTRAINT "exercise_record_pkey" PRIMARY KEY ("id")
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
COMMENT ON COLUMN "exercise_record"."time_record_id" IS '时迹记录ID（关联time_record.id）';

CREATE INDEX "exercise_record_idx_user_deleted_date" ON "exercise_record" ("user_id", "is_deleted", "exercise_date" DESC, "id" DESC);
COMMENT ON INDEX "exercise_record_idx_user_deleted_date" IS '运动记录列表：用户、删除标记与运动日期倒序';
CREATE INDEX "exercise_record_idx_user_time" ON "exercise_record" ("user_id", "time_record_id");
COMMENT ON INDEX "exercise_record_idx_user_time" IS '运动记录按时迹反查：用户与时迹ID';

CREATE TABLE "goal" (
  "id" BIGINT NOT NULL,
  "user_id" BIGINT NOT NULL,
  "type" SMALLINT,
  "title" VARCHAR(255),
  "description" TEXT,
  "content" TEXT,
  "status" VARCHAR(32) DEFAULT 'not_started'::character varying NOT NULL,
  "target_value" NUMERIC(14,2),
  "current_value" NUMERIC(14,2),
  "year" INTEGER,
  "month" INTEGER,
  "day" INTEGER,
  "parent_id" BIGINT,
  "start_date" TIMESTAMP(0) WITHOUT TIME ZONE,
  "end_date" TIMESTAMP(0) WITHOUT TIME ZONE,
  "completed_at" TIMESTAMP(0) WITHOUT TIME ZONE,
  "tags" JSONB,
  "is_pinned" SMALLINT DEFAULT '0'::SMALLINT NOT NULL,
  "pinned_sort_order" INTEGER DEFAULT 0 NOT NULL,
  "is_deleted" SMALLINT DEFAULT '0'::SMALLINT NOT NULL,
  "create_time" TIMESTAMP(0) WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
  "update_time" TIMESTAMP(0) WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
  "create_user" BIGINT,
  "update_user" BIGINT,
  "progress_mode" VARCHAR(16) DEFAULT 'manual'::character varying NOT NULL,
  "progress_query" TEXT,
  "progress_variables" JSONB,
  "progress_variable_bindings" JSONB,
  "progress_value_path" VARCHAR(256),
  "target_operator" VARCHAR(8) DEFAULT 'gte'::character varying NOT NULL,
  "progress_checked_at" TIMESTAMP(0) WITHOUT TIME ZONE,
  "progress_error" VARCHAR(255),
  "progress_refresh_id" VARCHAR(36),
  "progress_version" BIGINT DEFAULT '0'::bigint NOT NULL,
  CONSTRAINT "ck_goal_is_deleted" CHECK (("is_deleted" = ANY (ARRAY[0, 1]))),
  CONSTRAINT "ck_goal_is_pinned" CHECK (("is_pinned" = ANY (ARRAY[0, 1]))),
  CONSTRAINT "ck_goal_progress_mode" CHECK (("progress_mode" IN ('manual', 'graphql'))),
  CONSTRAINT "ck_goal_progress_values" CHECK (((("target_value" IS NULL) OR ("target_value" >= (0)::numeric)) AND (("current_value" IS NULL) OR ("current_value" >= (0)::numeric)))),
  CONSTRAINT "ck_goal_target_operator" CHECK (("target_operator" IN ('gte', 'gt'))),
  CONSTRAINT "goal_pkey" PRIMARY KEY ("id")
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
COMMENT ON COLUMN "goal"."tags" IS '目标标签数组';
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

CREATE TABLE "milestone" (
  "id" BIGINT NOT NULL,
  "user_id" BIGINT NOT NULL,
  "title" VARCHAR(255) NOT NULL,
  "description" TEXT,
  "date" DATE NOT NULL,
  "end_date" DATE,
  "type" VARCHAR(50) DEFAULT 'other'::character varying NOT NULL,
  "tags" JSONB,
  "create_user" BIGINT,
  "create_time" TIMESTAMP(0) WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
  "update_user" BIGINT,
  "update_time" TIMESTAMP(0) WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
  "is_deleted" SMALLINT DEFAULT '0'::SMALLINT NOT NULL,
  CONSTRAINT "ck_milestone_is_deleted" CHECK (("is_deleted" = ANY (ARRAY[0, 1]))),
  CONSTRAINT "milestone_pkey" PRIMARY KEY ("id")
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

CREATE TABLE "task" (
  "id" BIGINT NOT NULL,
  "user_id" BIGINT NOT NULL,
  "content" VARCHAR(100),
  "detail" VARCHAR(1000),
  "column_id" BIGINT,
  "due_date" TIMESTAMP(0) WITHOUT TIME ZONE,
  "sort_order" INTEGER DEFAULT 0 NOT NULL,
  "is_deleted" SMALLINT DEFAULT '0'::SMALLINT NOT NULL,
  "create_user" BIGINT,
  "create_time" TIMESTAMP(0) WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
  "update_user" BIGINT,
  "update_time" TIMESTAMP(0) WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
  CONSTRAINT "ck_task_is_deleted" CHECK (("is_deleted" = ANY (ARRAY[0, 1]))),
  CONSTRAINT "task_pkey" PRIMARY KEY ("id")
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
  "id" BIGINT NOT NULL,
  "user_id" BIGINT NOT NULL,
  "title" VARCHAR(255) NOT NULL,
  "sort_order" INTEGER NOT NULL,
  "bg_color" VARCHAR(10),
  "is_deleted" SMALLINT DEFAULT '0'::SMALLINT NOT NULL,
  "create_user" BIGINT,
  "create_time" TIMESTAMP(0) WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
  "update_user" BIGINT,
  "update_time" TIMESTAMP(0) WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
  CONSTRAINT "ck_task_column_is_deleted" CHECK (("is_deleted" = ANY (ARRAY[0, 1]))),
  CONSTRAINT "task_column_pkey" PRIMARY KEY ("id")
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
  "id" BIGINT NOT NULL,
  "task_id" BIGINT NOT NULL,
  "content" VARCHAR(255) NOT NULL,
  "is_completed" SMALLINT DEFAULT '0'::SMALLINT NOT NULL,
  "sort_order" INTEGER DEFAULT 0 NOT NULL,
  "priority" INTEGER DEFAULT 20,
  "user_id" BIGINT NOT NULL,
  "create_user" BIGINT,
  "create_time" TIMESTAMP(0) WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
  "update_user" BIGINT,
  "update_time" TIMESTAMP(0) WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
  "is_deleted" SMALLINT DEFAULT '0'::SMALLINT NOT NULL,
  "is_starred" SMALLINT DEFAULT 0 NOT NULL,
  "start_time" TIMESTAMP(0) WITHOUT TIME ZONE,
  "end_time" TIMESTAMP(0) WITHOUT TIME ZONE,
  CONSTRAINT "ck_task_detail_is_completed" CHECK (("is_completed" = ANY (ARRAY[0, 1]))),
  CONSTRAINT "ck_task_detail_is_deleted" CHECK (("is_deleted" = ANY (ARRAY[0, 1]))),
  CONSTRAINT "ck_task_detail_is_starred" CHECK (("is_starred" = ANY (ARRAY[0, 1]))),
  CONSTRAINT "task_detail_pkey" PRIMARY KEY ("id")
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

CREATE INDEX "task_detail_idx_task_user_status_sort" ON "task_detail" ("task_id", "user_id", "is_completed", "priority" NULLS FIRST, "sort_order", "id");
COMMENT ON INDEX "task_detail_idx_task_user_status_sort" IS '任务明细：任务、用户、完成状态与排序';
CREATE INDEX "task_detail_idx_user_starred_status" ON "task_detail" ("user_id", "is_starred", "is_completed", "priority" NULLS FIRST, "sort_order", "id");
COMMENT ON INDEX "task_detail_idx_user_starred_status" IS '关注明细：用户、关注与完成状态与排序';

CREATE TABLE "time_record" (
  "id" BIGINT NOT NULL,
  "user_id" BIGINT NOT NULL,
  "create_user" BIGINT,
  "category_id" BIGINT,
  "date" DATE NOT NULL,
  "start_minute" INTEGER NOT NULL,
  "end_minute" INTEGER NOT NULL,
  "title" VARCHAR(100),
  "description" TEXT,
  "duration_minutes" INTEGER NOT NULL,
  "is_manual" SMALLINT DEFAULT '0'::SMALLINT NOT NULL,
  "is_deleted" SMALLINT DEFAULT '0'::SMALLINT NOT NULL,
  "create_time" TIMESTAMP(0) WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
  "update_user" BIGINT,
  "update_time" TIMESTAMP(0) WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
  "relate_id" BIGINT,
  "relate_type" SMALLINT,
  CONSTRAINT "ck_time_record_is_deleted" CHECK (("is_deleted" = ANY (ARRAY[0, 1]))),
  CONSTRAINT "ck_time_record_is_manual" CHECK (("is_manual" = ANY (ARRAY[0, 1]))),
  CONSTRAINT "ck_time_record_time_range" CHECK ((("start_minute" BETWEEN 0 AND 1439) AND ("end_minute" BETWEEN 0 AND 1439) AND ("start_minute" <= "end_minute") AND ("duration_minutes" = (("end_minute" - "start_minute") + 1)))),
  CONSTRAINT "time_record_pkey" PRIMARY KEY ("id")
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
CREATE INDEX "time_record_idx_user_date_update" ON "time_record" ("user_id", "is_deleted", "date", "update_time", "id");
COMMENT ON INDEX "time_record_idx_user_date_update" IS '时迹按日查询：用户、日期与更新时间';

CREATE TABLE "time_tracker_category" (
  "id" BIGINT NOT NULL,
  "user_id" BIGINT,
  "name" VARCHAR(50),
  "color" VARCHAR(20),
  "description" VARCHAR(255),
  "is_track_time" SMALLINT DEFAULT '0'::SMALLINT NOT NULL,
  "sort_order" INTEGER DEFAULT 0 NOT NULL,
  "create_user" BIGINT,
  "create_time" TIMESTAMP(0) WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
  "update_user" BIGINT,
  "update_time" TIMESTAMP(0) WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
  "is_deleted" SMALLINT DEFAULT '0'::SMALLINT NOT NULL,
  "is_enabled" SMALLINT DEFAULT '1'::SMALLINT NOT NULL,
  "template_id" BIGINT,
  "parent_id" BIGINT,
  "icon" VARCHAR(100),
  "inherit_parent" SMALLINT DEFAULT 0 NOT NULL,
  CONSTRAINT "ck_time_tracker_category_inherit" CHECK ((("inherit_parent" = 0) OR (("template_id" IS NOT NULL) AND ("parent_id" IS NULL)))),
  CONSTRAINT "ck_time_tracker_category_inherit_parent" CHECK (("inherit_parent" = ANY (ARRAY[0, 1]))),
  CONSTRAINT "ck_time_tracker_category_is_deleted" CHECK (("is_deleted" = ANY (ARRAY[0, 1]))),
  CONSTRAINT "ck_time_tracker_category_is_enabled" CHECK (("is_enabled" = ANY (ARRAY[0, 1]))),
  CONSTRAINT "ck_time_tracker_category_is_track_time" CHECK (("is_track_time" = ANY (ARRAY[0, 1]))),
  CONSTRAINT "ck_time_tracker_category_owner" CHECK ((("user_id" IS NULL) OR ("user_id" > 0))),
  CONSTRAINT "ck_time_tracker_category_parent_root" CHECK ((("parent_id" IS NULL) OR ("parent_id" > 0))),
  CONSTRAINT "ck_time_tracker_category_parent_self" CHECK (("parent_id" <> "id")),
  CONSTRAINT "time_tracker_category_pkey" PRIMARY KEY ("id")
);

COMMENT ON TABLE "time_tracker_category" IS '时间追踪-分类配置表';
COMMENT ON COLUMN "time_tracker_category"."id" IS '主键ID';
COMMENT ON COLUMN "time_tracker_category"."user_id" IS '所属用户ID；NULL表示系统公共预设';
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
COMMENT ON COLUMN "time_tracker_category"."parent_id" IS '上级分类ID；NULL 表示根分类，覆盖记录是否继承模板父级由 inherit_parent 决定';
COMMENT ON COLUMN "time_tracker_category"."icon" IS '图标名称(Iconify格式)';
COMMENT ON COLUMN "time_tracker_category"."inherit_parent" IS '覆盖记录是否继承公共分类的父级（1 是，0 否）；非覆盖记录固定为 0';
COMMENT ON CONSTRAINT "ck_time_tracker_category_inherit" ON "time_tracker_category" IS '只有覆盖记录可以继承父级，且继承时不得同时保存本地父级';
COMMENT ON CONSTRAINT "ck_time_tracker_category_inherit_parent" ON "time_tracker_category" IS 'inherit_parent 只能为 0 或 1';
COMMENT ON CONSTRAINT "ck_time_tracker_category_owner" ON "time_tracker_category" IS '归属只允许为空（系统预设）或正数用户ID，不再使用0';
COMMENT ON CONSTRAINT "ck_time_tracker_category_parent_root" ON "time_tracker_category" IS '根分类使用 NULL，父级ID必须为正数';
COMMENT ON CONSTRAINT "ck_time_tracker_category_parent_self" ON "time_tracker_category" IS '分类不能以自身为父级';

CREATE INDEX "idx_time_tracker_category_preset" ON "time_tracker_category" ("parent_id", "sort_order", "id") WHERE (("user_id" IS NULL) AND ("is_deleted" = 0));
COMMENT ON INDEX "idx_time_tracker_category_preset" IS '系统预设时迹分类按层级与排序检索';
CREATE INDEX "time_tracker_category_idx_category_user_parent" ON "time_tracker_category" ("user_id", "parent_id");
COMMENT ON INDEX "time_tracker_category_idx_category_user_parent" IS '业务检索索引：idx_category_user_parent';
CREATE INDEX "time_tracker_category_idx_category_user_template" ON "time_tracker_category" ("user_id", "template_id");
COMMENT ON INDEX "time_tracker_category_idx_category_user_template" IS '业务检索索引：idx_category_user_template';
CREATE UNIQUE INDEX "uk_time_tracker_category_user_template" ON "time_tracker_category" ("user_id", "template_id") WHERE (("is_deleted" = 0) AND ("template_id" IS NOT NULL));
COMMENT ON INDEX "uk_time_tracker_category_user_template" IS '同一用户对同一公共分类只能有一条有效覆盖记录';

CREATE TABLE "user_body_profile" (
  "id" BIGINT NOT NULL,
  "user_id" BIGINT NOT NULL,
  "version" INTEGER NOT NULL,
  "height_cm" NUMERIC(5,1),
  "weight_kg" NUMERIC(5,1),
  "body_fat_percent" NUMERIC(5,1),
  "shoulder_width_cm" NUMERIC(5,1),
  "chest_cm" NUMERIC(5,1),
  "upper_arm_cm" NUMERIC(5,1),
  "forearm_cm" NUMERIC(5,1),
  "waist_cm" NUMERIC(5,1),
  "hip_cm" NUMERIC(5,1),
  "thigh_cm" NUMERIC(5,1),
  "calf_cm" NUMERIC(5,1),
  "inseam_cm" NUMERIC(5,1),
  "gender" VARCHAR(16),
  "measurement_side" VARCHAR(16),
  "arm_state" VARCHAR(16),
  "shoe_system" VARCHAR(8),
  "top_size" VARCHAR(32),
  "trousers_size" VARCHAR(32),
  "shoe_size" VARCHAR(32),
  "create_user" BIGINT,
  "create_time" TIMESTAMP(0) WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
  "update_user" BIGINT,
  "update_time" TIMESTAMP(0) WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
  "is_deleted" SMALLINT DEFAULT '0'::SMALLINT NOT NULL,
  CONSTRAINT "ck_body_profile_arm_state" CHECK (("arm_state" IN ('relaxed', 'flexed'))),
  CONSTRAINT "ck_body_profile_body_fat_percent" CHECK ((("body_fat_percent" > (0)::numeric) AND ("body_fat_percent" <= 99.9))),
  CONSTRAINT "ck_body_profile_calf_cm" CHECK ((("calf_cm" > (0)::numeric) AND ("calf_cm" <= 999.9))),
  CONSTRAINT "ck_body_profile_chest_cm" CHECK ((("chest_cm" > (0)::numeric) AND ("chest_cm" <= 999.9))),
  CONSTRAINT "ck_body_profile_forearm_cm" CHECK ((("forearm_cm" > (0)::numeric) AND ("forearm_cm" <= 999.9))),
  CONSTRAINT "ck_body_profile_gender" CHECK (("gender" IN ('male', 'female'))),
  CONSTRAINT "ck_body_profile_height_cm" CHECK ((("height_cm" > (0)::numeric) AND ("height_cm" <= 999.9))),
  CONSTRAINT "ck_body_profile_hip_cm" CHECK ((("hip_cm" > (0)::numeric) AND ("hip_cm" <= 999.9))),
  CONSTRAINT "ck_body_profile_inseam_cm" CHECK ((("inseam_cm" > (0)::numeric) AND ("inseam_cm" <= 999.9))),
  CONSTRAINT "ck_body_profile_shoe_system" CHECK (("shoe_system" IN ('EU', 'CN', 'US', 'UK'))),
  CONSTRAINT "ck_body_profile_shoulder_width_cm" CHECK ((("shoulder_width_cm" > (0)::numeric) AND ("shoulder_width_cm" <= 999.9))),
  CONSTRAINT "ck_body_profile_side" CHECK (("measurement_side" IN ('left', 'right'))),
  CONSTRAINT "ck_body_profile_thigh_cm" CHECK ((("thigh_cm" > (0)::numeric) AND ("thigh_cm" <= 999.9))),
  CONSTRAINT "ck_body_profile_upper_arm_cm" CHECK ((("upper_arm_cm" > (0)::numeric) AND ("upper_arm_cm" <= 999.9))),
  CONSTRAINT "ck_body_profile_version" CHECK (("version" > 0)),
  CONSTRAINT "ck_body_profile_waist_cm" CHECK ((("waist_cm" > (0)::numeric) AND ("waist_cm" <= 999.9))),
  CONSTRAINT "ck_body_profile_weight_kg" CHECK ((("weight_kg" > (0)::numeric) AND ("weight_kg" <= 999.9))),
  CONSTRAINT "ck_user_body_profile_is_deleted" CHECK (("is_deleted" = ANY (ARRAY[0, 1]))),
  CONSTRAINT "user_body_profile_pkey" PRIMARY KEY ("id")
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
  "id" BIGINT NOT NULL,
  "user_id" BIGINT,
  "template_id" BIGINT,
  "dict_type" VARCHAR(100) DEFAULT ''::character varying COLLATE "pg_catalog"."C",
  "sort_order" INTEGER DEFAULT 0 NOT NULL,
  "dict_label" VARCHAR(100),
  "dict_value" VARCHAR(100) DEFAULT ''::character varying COLLATE "pg_catalog"."C",
  "color" VARCHAR(20),
  "icon" VARCHAR(100),
  "extra_data" JSONB,
  "is_default" SMALLINT DEFAULT 0 NOT NULL,
  "is_readonly" SMALLINT DEFAULT 0 NOT NULL,
  "create_user" BIGINT,
  "create_time" TIMESTAMP(0) WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
  "update_user" BIGINT,
  "update_time" TIMESTAMP(0) WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
  "remark" VARCHAR(500),
  "is_deleted" SMALLINT DEFAULT '0'::SMALLINT NOT NULL,
  "is_enabled" SMALLINT DEFAULT '1'::SMALLINT,
  CONSTRAINT "ck_user_dict_data_enabled" CHECK ((("is_enabled" IS NULL) OR ("is_enabled" = ANY (ARRAY[0, 1])))),
  CONSTRAINT "ck_user_dict_data_is_default" CHECK (("is_default" = ANY (ARRAY[0, 1]))),
  CONSTRAINT "ck_user_dict_data_is_deleted" CHECK (("is_deleted" = ANY (ARRAY[0, 1]))),
  CONSTRAINT "ck_user_dict_data_is_readonly" CHECK (("is_readonly" = ANY (ARRAY[0, 1]))),
  CONSTRAINT "ck_user_dict_data_label_not_blank" CHECK ((("dict_label" IS NULL) OR ("btrim"(("dict_label")::TEXT) <> ''::TEXT))),
  CONSTRAINT "ck_user_dict_data_owner" CHECK ((("user_id" IS NULL) OR ("user_id" > 0))),
  CONSTRAINT "user_dict_data_pkey" PRIMARY KEY ("id")
);

COMMENT ON TABLE "user_dict_data" IS '用户字典数据表';
COMMENT ON COLUMN "user_dict_data"."id" IS '字典主键';
COMMENT ON COLUMN "user_dict_data"."user_id" IS '所属用户ID；NULL表示系统公共预设';
COMMENT ON COLUMN "user_dict_data"."template_id" IS '模板ID，指向被覆盖的公共字典ID';
COMMENT ON COLUMN "user_dict_data"."dict_type" IS '字典类型';
COMMENT ON COLUMN "user_dict_data"."sort_order" IS '排序值，数值越小越靠前';
COMMENT ON COLUMN "user_dict_data"."dict_label" IS '字典标签(分类名称)；NULL表示继承公共项标签，非空时不能只有空白';
COMMENT ON COLUMN "user_dict_data"."dict_value" IS '字典键值(分类标识)';
COMMENT ON COLUMN "user_dict_data"."color" IS '颜色值(Hex)';
COMMENT ON COLUMN "user_dict_data"."icon" IS '图标名称(Iconify格式)';
COMMENT ON COLUMN "user_dict_data"."extra_data" IS '字典项的补充属性，未配置时为空';
COMMENT ON COLUMN "user_dict_data"."is_default" IS '是否默认值：1=是，0=否';
COMMENT ON COLUMN "user_dict_data"."is_readonly" IS '是否只读：1=是，0=否；只读项用户仅可修改启用状态';
COMMENT ON COLUMN "user_dict_data"."create_user" IS '创建人ID';
COMMENT ON COLUMN "user_dict_data"."create_time" IS '创建时间';
COMMENT ON COLUMN "user_dict_data"."update_user" IS '更新人ID';
COMMENT ON COLUMN "user_dict_data"."update_time" IS '更新时间';
COMMENT ON COLUMN "user_dict_data"."remark" IS '备注';
COMMENT ON COLUMN "user_dict_data"."is_deleted" IS '是否删除 0-未删除 1-已删除';
COMMENT ON COLUMN "user_dict_data"."is_enabled" IS '是否启用：1=启用，0=停用；NULL表示未设置，个人覆盖项继承基础值';
COMMENT ON CONSTRAINT "ck_user_dict_data_label_not_blank" ON "user_dict_data" IS '标签为空表示继承公共项，非空时不允许空串或纯空白';
COMMENT ON CONSTRAINT "ck_user_dict_data_owner" ON "user_dict_data" IS '归属只允许为空（系统预设）或正数用户ID，不再使用0';

CREATE INDEX "idx_user_dict_data_preset" ON "user_dict_data" ("dict_type", "sort_order", "id") WHERE (("user_id" IS NULL) AND ("is_deleted" = 0));
COMMENT ON INDEX "idx_user_dict_data_preset" IS '系统预设字典项按类型与排序检索';
CREATE UNIQUE INDEX "uk_user_dict_data_user_template" ON "user_dict_data" ("user_id", "template_id") WHERE (("is_deleted" = 0) AND ("template_id" IS NOT NULL));
COMMENT ON INDEX "uk_user_dict_data_user_template" IS '同一用户对同一公共字典只能有一条有效覆盖记录';
CREATE INDEX "user_dict_data_idx_user_dict_template" ON "user_dict_data" ("template_id");
COMMENT ON INDEX "user_dict_data_idx_user_dict_template" IS '业务检索索引：idx_user_dict_template';
CREATE INDEX "user_dict_data_idx_user_dict_type" ON "user_dict_data" ("user_id", "dict_type");
COMMENT ON INDEX "user_dict_data_idx_user_dict_type" IS '业务检索索引：idx_user_dict_type';

-- 笔记、闪念、荣誉、性格测试、足迹与密码库

CREATE TABLE "cbti_personality" (
  "id" BIGINT NOT NULL,
  "code" VARCHAR(20) NOT NULL COLLATE "pg_catalog"."C",
  "name" VARCHAR(64) NOT NULL,
  "motto" VARCHAR(255),
  "color" VARCHAR(20),
  "vector" JSONB,
  "description" TEXT,
  "strengths" JSONB,
  "weaknesses" JSONB,
  "tech_stack" VARCHAR(255),
  "spirit" TEXT,
  "is_special" SMALLINT DEFAULT '0'::SMALLINT NOT NULL,
  "create_user" BIGINT,
  "create_time" TIMESTAMP(0) WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
  "update_user" BIGINT,
  "update_time" TIMESTAMP(0) WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
  "is_deleted" SMALLINT DEFAULT '0'::SMALLINT NOT NULL,
  CONSTRAINT "ck_cbti_personality_is_deleted" CHECK (("is_deleted" = ANY (ARRAY[0, 1]))),
  CONSTRAINT "ck_cbti_personality_is_special" CHECK (("is_special" = ANY (ARRAY[0, 1]))),
  CONSTRAINT "cbti_personality_pkey" PRIMARY KEY ("id")
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
COMMENT ON COLUMN "cbti_personality"."is_special" IS '是否隐藏人格（0-否，1-是）';
COMMENT ON COLUMN "cbti_personality"."create_user" IS '创建人';
COMMENT ON COLUMN "cbti_personality"."create_time" IS '创建时间';
COMMENT ON COLUMN "cbti_personality"."update_user" IS '更新人';
COMMENT ON COLUMN "cbti_personality"."update_time" IS '更新时间';
COMMENT ON COLUMN "cbti_personality"."is_deleted" IS '逻辑删除标记：0=未删除，1=已删除';

CREATE INDEX "cbti_personality_idx_cbti_personality_special" ON "cbti_personality" ("is_special");
COMMENT ON INDEX "cbti_personality_idx_cbti_personality_special" IS '业务检索索引：idx_cbti_personality_special';
CREATE UNIQUE INDEX "cbti_personality_uk_cbti_personality_code" ON "cbti_personality" ("code") WHERE ("is_deleted" = 0);
COMMENT ON INDEX "cbti_personality_uk_cbti_personality_code" IS '有效人格代码唯一，已删除人格不占用';

CREATE TABLE "cbti_result" (
  "id" BIGINT NOT NULL,
  "user_id" BIGINT NOT NULL,
  "personality_code" VARCHAR(20) NOT NULL,
  "similarity" INTEGER DEFAULT 0 NOT NULL,
  "dimensions" JSONB,
  "answers" JSONB,
  "hidden_answers" JSONB,
  "create_user" BIGINT,
  "create_time" TIMESTAMP(0) WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
  "update_user" BIGINT,
  "update_time" TIMESTAMP(0) WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
  "is_deleted" SMALLINT DEFAULT '0'::SMALLINT NOT NULL,
  CONSTRAINT "ck_cbti_result_is_deleted" CHECK (("is_deleted" = ANY (ARRAY[0, 1]))),
  CONSTRAINT "cbti_result_pkey" PRIMARY KEY ("id")
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

CREATE TABLE "honor_category" (
  "id" BIGINT NOT NULL,
  "user_id" BIGINT,
  "name" VARCHAR(50) NOT NULL,
  "icon" VARCHAR(255),
  "color" VARCHAR(20),
  "sort_order" INTEGER DEFAULT 0 NOT NULL,
  "is_deleted" SMALLINT DEFAULT '0'::SMALLINT NOT NULL,
  "create_user" BIGINT,
  "create_time" TIMESTAMP(0) WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
  "update_user" BIGINT,
  "update_time" TIMESTAMP(0) WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
  CONSTRAINT "ck_honor_category_is_deleted" CHECK (("is_deleted" = ANY (ARRAY[0, 1]))),
  CONSTRAINT "ck_honor_category_owner" CHECK ((("user_id" IS NULL) OR ("user_id" > 0))),
  CONSTRAINT "honor_category_pkey" PRIMARY KEY ("id")
);

COMMENT ON TABLE "honor_category" IS '荣誉分类表';
COMMENT ON COLUMN "honor_category"."id" IS '分类ID';
COMMENT ON COLUMN "honor_category"."user_id" IS '用户ID；NULL表示系统预设分类';
COMMENT ON COLUMN "honor_category"."name" IS '分类名称';
COMMENT ON COLUMN "honor_category"."icon" IS '分类图标';
COMMENT ON COLUMN "honor_category"."color" IS '分类颜色';
COMMENT ON COLUMN "honor_category"."sort_order" IS '排序顺序';
COMMENT ON COLUMN "honor_category"."is_deleted" IS '逻辑删除标记：0=未删除，1=已删除';
COMMENT ON COLUMN "honor_category"."create_user" IS '创建人ID';
COMMENT ON COLUMN "honor_category"."create_time" IS '创建时间';
COMMENT ON COLUMN "honor_category"."update_user" IS '更新人ID';
COMMENT ON COLUMN "honor_category"."update_time" IS '更新时间';
COMMENT ON CONSTRAINT "ck_honor_category_owner" ON "honor_category" IS '归属只允许为空（系统预设）或正数用户ID，不再使用0';

CREATE INDEX "honor_category_idx_user_id" ON "honor_category" ("user_id");
COMMENT ON INDEX "honor_category_idx_user_id" IS '业务检索索引：idx_user_id';

CREATE TABLE "honor_record" (
  "id" BIGINT NOT NULL,
  "user_id" BIGINT NOT NULL,
  "title" VARCHAR(200) NOT NULL,
  "description" TEXT,
  "honor_date" DATE NOT NULL,
  "issuer" VARCHAR(200),
  "level" VARCHAR(20),
  "category_id" BIGINT,
  "custom_category" VARCHAR(50),
  "tags" JSONB,
  "is_top" SMALLINT DEFAULT '0'::SMALLINT NOT NULL,
  "is_public" SMALLINT DEFAULT '1'::SMALLINT NOT NULL,
  "sort_order" INTEGER DEFAULT 0 NOT NULL,
  "is_deleted" SMALLINT DEFAULT '0'::SMALLINT NOT NULL,
  "create_time" TIMESTAMP(0) WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
  "update_time" TIMESTAMP(0) WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
  "create_user" BIGINT,
  "update_user" BIGINT,
  CONSTRAINT "ck_honor_record_is_deleted" CHECK (("is_deleted" = ANY (ARRAY[0, 1]))),
  CONSTRAINT "ck_honor_record_is_public" CHECK (("is_public" = ANY (ARRAY[0, 1]))),
  CONSTRAINT "ck_honor_record_is_top" CHECK (("is_top" = ANY (ARRAY[0, 1]))),
  CONSTRAINT "honor_record_pkey" PRIMARY KEY ("id")
);

COMMENT ON TABLE "honor_record" IS '荣誉记录表';
COMMENT ON COLUMN "honor_record"."id" IS '记录ID';
COMMENT ON COLUMN "honor_record"."user_id" IS '用户ID';
COMMENT ON COLUMN "honor_record"."title" IS '荣誉标题';
COMMENT ON COLUMN "honor_record"."description" IS '荣誉描述';
COMMENT ON COLUMN "honor_record"."honor_date" IS '获得日期';
COMMENT ON COLUMN "honor_record"."issuer" IS '颁发机构/组织';
COMMENT ON COLUMN "honor_record"."level" IS '荣誉级别：school-校级/district-区级/city-市级/province-省级/national-国家级/international-国际级/other-其他';
COMMENT ON COLUMN "honor_record"."category_id" IS '所属分类ID（可为空）';
COMMENT ON COLUMN "honor_record"."custom_category" IS '自定义分类名称（当不选择预设分类时使用）';
COMMENT ON COLUMN "honor_record"."tags" IS '标签数组';
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

CREATE TABLE "life_footprint" (
  "id" BIGINT NOT NULL,
  "user_id" BIGINT NOT NULL,
  "start_date" DATE NOT NULL,
  "end_date" DATE NOT NULL,
  "place_name" VARCHAR(100) NOT NULL,
  "address" VARCHAR(500),
  "city" VARCHAR(100),
  "region_code" VARCHAR(12),
  "longitude" NUMERIC(10,6) NOT NULL,
  "latitude" NUMERIC(9,6) NOT NULL,
  "coordinate_system" VARCHAR(16) DEFAULT 'GCJ02'::character varying NOT NULL,
  "provider_uid" VARCHAR(128),
  "remark" TEXT,
  "create_user" BIGINT,
  "create_time" TIMESTAMP(0) WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
  "update_user" BIGINT,
  "update_time" TIMESTAMP(0) WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
  "is_deleted" SMALLINT DEFAULT '0'::SMALLINT NOT NULL,
  CONSTRAINT "ck_footprint_coordinate" CHECK ((("coordinate_system")::TEXT = 'GCJ02'::TEXT)),
  CONSTRAINT "ck_footprint_end" CHECK (("end_date" >= "start_date")),
  CONSTRAINT "ck_footprint_lat" CHECK ((("latitude" >= ('-90'::integer)::numeric) AND ("latitude" <= (90)::numeric))),
  CONSTRAINT "ck_footprint_lng" CHECK ((("longitude" >= ('-180'::integer)::numeric) AND ("longitude" <= (180)::numeric))),
  CONSTRAINT "ck_life_footprint_is_deleted" CHECK (("is_deleted" = ANY (ARRAY[0, 1]))),
  CONSTRAINT "life_footprint_pkey" PRIMARY KEY ("id")
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

CREATE TABLE "mbti_result" (
  "id" BIGINT NOT NULL,
  "user_id" BIGINT NOT NULL,
  "test_id" VARCHAR(255),
  "mbti_type" VARCHAR(10),
  "raw_result" JSONB,
  "results_page" VARCHAR(500),
  "create_user" BIGINT,
  "create_time" TIMESTAMP(0) WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
  "update_user" BIGINT,
  "update_time" TIMESTAMP(0) WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
  "is_deleted" SMALLINT DEFAULT '0'::SMALLINT NOT NULL,
  CONSTRAINT "ck_mbti_result_is_deleted" CHECK (("is_deleted" = ANY (ARRAY[0, 1]))),
  CONSTRAINT "mbti_result_pkey" PRIMARY KEY ("id")
);

COMMENT ON TABLE "mbti_result" IS 'MBTI测试结果表';
COMMENT ON COLUMN "mbti_result"."id" IS '主键ID';
COMMENT ON COLUMN "mbti_result"."user_id" IS '用户ID';
COMMENT ON COLUMN "mbti_result"."test_id" IS '测试ID';
COMMENT ON COLUMN "mbti_result"."mbti_type" IS 'MBTI类型';
COMMENT ON COLUMN "mbti_result"."raw_result" IS '原始结果数据（JSON 对象）；历史空串为 NULL，非法 JSON 文本按字符串保留';
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

CREATE TABLE "memo" (
  "id" BIGINT NOT NULL,
  "user_id" BIGINT NOT NULL,
  "title" VARCHAR(100),
  "content" TEXT,
  "hidden_content" SMALLINT DEFAULT '0'::SMALLINT NOT NULL,
  "create_user" BIGINT,
  "create_time" TIMESTAMP(0) WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
  "update_user" BIGINT,
  "update_time" TIMESTAMP(0) WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
  "is_deleted" SMALLINT DEFAULT '0'::SMALLINT NOT NULL,
  "favorite" SMALLINT DEFAULT 0 NOT NULL,
  CONSTRAINT "ck_memo_favorite" CHECK (("favorite" = ANY (ARRAY[0, 1]))),
  CONSTRAINT "ck_memo_hidden_content" CHECK (("hidden_content" = ANY (ARRAY[0, 1]))),
  CONSTRAINT "ck_memo_is_deleted" CHECK (("is_deleted" = ANY (ARRAY[0, 1]))),
  CONSTRAINT "memo_pkey" PRIMARY KEY ("id")
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
COMMENT ON COLUMN "memo"."favorite" IS '是否收藏：0=未收藏，1=已收藏；切换不改变内容更新时间';
COMMENT ON CONSTRAINT "ck_memo_favorite" ON "memo" IS '收藏标记只允许未收藏或已收藏';

CREATE INDEX "memo_idx_user_deleted_update" ON "memo" ("user_id", "is_deleted", "update_time" DESC NULLS LAST, "id" DESC);
COMMENT ON INDEX "memo_idx_user_deleted_update" IS '备忘录列表：用户、删除标记与更新时间倒序';

CREATE TABLE "password_vault" (
  "id" BIGINT NOT NULL,
  "user_id" BIGINT NOT NULL,
  "title" VARCHAR(100) NOT NULL,
  "website" VARCHAR(255),
  "category" VARCHAR(50) DEFAULT '其他'::character varying,
  "username" TEXT,
  "password" TEXT,
  "salt" VARCHAR(64) NOT NULL,
  "remark" TEXT,
  "favorite" SMALLINT DEFAULT '0'::SMALLINT,
  "is_deleted" SMALLINT DEFAULT '0'::SMALLINT NOT NULL,
  "create_time" TIMESTAMP(0) WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
  "update_time" TIMESTAMP(0) WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
  "create_user" BIGINT,
  "update_user" BIGINT,
  CONSTRAINT "ck_password_vault_favorite" CHECK (("favorite" = ANY (ARRAY[0, 1]))),
  CONSTRAINT "ck_password_vault_is_deleted" CHECK (("is_deleted" = ANY (ARRAY[0, 1]))),
  CONSTRAINT "password_vault_pkey" PRIMARY KEY ("id")
);

COMMENT ON TABLE "password_vault" IS '密码库表（账号明文；密码及备注由客户端加密，服务端不进行加解密）';
COMMENT ON COLUMN "password_vault"."id" IS '记录ID';
COMMENT ON COLUMN "password_vault"."user_id" IS '所属用户ID';
COMMENT ON COLUMN "password_vault"."title" IS '标题，如 GitHub';
COMMENT ON COLUMN "password_vault"."website" IS '网站/应用名';
COMMENT ON COLUMN "password_vault"."category" IS '分类：工作/生活/金融/社交/其他';
COMMENT ON COLUMN "password_vault"."username" IS '账号明文，用于列表展示与复制；服务端不加密、不解密，客户端直接提交原值';
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

CREATE TABLE "thought" (
  "id" BIGINT NOT NULL,
  "content" TEXT NOT NULL,
  "user_id" BIGINT NOT NULL,
  "create_time" TIMESTAMP(0) WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
  "create_user" BIGINT,
  "update_time" TIMESTAMP(0) WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
  "update_user" BIGINT,
  "is_deleted" SMALLINT DEFAULT '0'::SMALLINT NOT NULL,
  "is_pinned" SMALLINT DEFAULT '0'::SMALLINT NOT NULL,
  "hidden_content" SMALLINT DEFAULT '0'::SMALLINT NOT NULL,
  CONSTRAINT "ck_thought_hidden_content" CHECK (("hidden_content" = ANY (ARRAY[0, 1]))),
  CONSTRAINT "ck_thought_is_deleted" CHECK (("is_deleted" = ANY (ARRAY[0, 1]))),
  CONSTRAINT "ck_thought_is_pinned" CHECK (("is_pinned" = ANY (ARRAY[0, 1]))),
  CONSTRAINT "thought_pkey" PRIMARY KEY ("id")
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

CREATE INDEX "thought_idx_user_deleted_update" ON "thought" ("user_id", "is_deleted", "update_time" DESC NULLS LAST, "id" DESC);
COMMENT ON INDEX "thought_idx_user_deleted_update" IS '闪念列表：用户、删除标记与更新时间倒序';

CREATE TABLE "thought_rela_event" (
  "id" BIGINT NOT NULL,
  "thought_id" BIGINT NOT NULL,
  "content" TEXT NOT NULL,
  "create_time" TIMESTAMP(0) WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
  "create_user" BIGINT,
  "update_time" TIMESTAMP(0) WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
  "update_user" BIGINT,
  "is_deleted" SMALLINT DEFAULT '0'::SMALLINT NOT NULL,
  CONSTRAINT "ck_thought_rela_event_is_deleted" CHECK (("is_deleted" = ANY (ARRAY[0, 1]))),
  CONSTRAINT "thought_rela_event_pkey" PRIMARY KEY ("id")
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

CREATE INDEX "thought_rela_event_idx_thought" ON "thought_rela_event" ("thought_id", "id");
COMMENT ON INDEX "thought_rela_event_idx_thought" IS '闪念关联事件：按闪念批量读取';

-- 收支、银行卡、设备、会员与衣橱

CREATE TABLE "bank_card" (
  "id" BIGINT NOT NULL,
  "user_id" BIGINT NOT NULL,
  "bank_id" BIGINT,
  "custom_bank_name" VARCHAR(100),
  "card_name" VARCHAR(100),
  "alias" VARCHAR(50),
  "card_type" VARCHAR(20) NOT NULL,
  "card_no_ciphertext" VARCHAR(512) COLLATE "pg_catalog"."C",
  "card_no_fingerprint" BYTEA,
  "card_no_last4" CHARACTER(4),
  "branch_name" VARCHAR(200),
  "status" VARCHAR(20) DEFAULT 'normal'::character varying NOT NULL,
  "opened_date" DATE,
  "expiry_month" DATE,
  "balance" NUMERIC(18,2),
  "credit_limit" NUMERIC(18,2),
  "statement_day" SMALLINT,
  "repayment_day" SMALLINT,
  "cover_template_id" BIGINT,
  "cover_color" CHARACTER(7),
  "cover_source_url" VARCHAR(1000),
  "sort_order" INTEGER DEFAULT 0 NOT NULL,
  "remark" VARCHAR(1000),
  "is_deleted" SMALLINT DEFAULT '0'::SMALLINT NOT NULL,
  "create_user" BIGINT NOT NULL,
  "create_time" TIMESTAMP(0) WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
  "update_user" BIGINT NOT NULL,
  "update_time" TIMESTAMP(0) WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
  CONSTRAINT "chk_bank_card_bank_source" CHECK (((("bank_id" IS NOT NULL) AND ("custom_bank_name" IS NULL)) OR (("bank_id" IS NULL) AND ("custom_bank_name" IS NOT NULL) AND ("char_length"(TRIM(BOTH FROM "custom_bank_name")) > 0)))),
  CONSTRAINT "ck_bank_card_balance" CHECK ((("balance" IS NULL) OR ("balance" >= (0)::numeric))),
  CONSTRAINT "ck_bank_card_credit_limit" CHECK ((("credit_limit" IS NULL) OR ("credit_limit" >= (0)::numeric))),
  CONSTRAINT "ck_bank_card_expiry_month" CHECK ((("expiry_month" IS NULL) OR (EXTRACT(DAY FROM "expiry_month") = (1)::numeric))),
  CONSTRAINT "ck_bank_card_is_deleted" CHECK (("is_deleted" = ANY (ARRAY[0, 1]))),
  CONSTRAINT "ck_bank_card_repayment_day" CHECK ((("repayment_day" IS NULL) OR (("repayment_day" >= 1) AND ("repayment_day" <= 31)))),
  CONSTRAINT "ck_bank_card_statement_day" CHECK ((("statement_day" IS NULL) OR (("statement_day" >= 1) AND ("statement_day" <= 31)))),
  CONSTRAINT "bank_card_pkey" PRIMARY KEY ("id")
);

COMMENT ON TABLE "bank_card" IS '银行卡档案';
COMMENT ON COLUMN "bank_card"."id" IS '雪花ID';
COMMENT ON COLUMN "bank_card"."user_id" IS '所属用户';
COMMENT ON COLUMN "bank_card"."bank_id" IS 'sys_dict_data.id，类型bank；自定义银行时为空';
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
COMMENT ON CONSTRAINT "ck_bank_card_balance" ON "bank_card" IS '储蓄卡余额为空或非负数';
COMMENT ON CONSTRAINT "ck_bank_card_credit_limit" ON "bank_card" IS '信用额度为空或非负数，0 表示明确零额度';
COMMENT ON CONSTRAINT "ck_bank_card_expiry_month" ON "bank_card" IS '有效期为空或只存当月第一天';
COMMENT ON CONSTRAINT "ck_bank_card_repayment_day" ON "bank_card" IS '固定还款日为空或 1-31；与请求校验一致';
COMMENT ON CONSTRAINT "ck_bank_card_statement_day" ON "bank_card" IS '账单日为空或 1-31；与请求校验一致';

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
CREATE UNIQUE INDEX "uk_bank_card_user_fingerprint" ON "bank_card" ("user_id", "card_no_fingerprint") WHERE (("is_deleted" = 0) AND ("card_no_fingerprint" IS NOT NULL));
COMMENT ON INDEX "uk_bank_card_user_fingerprint" IS '同一用户同一卡号指纹只能有一条有效银行卡';

CREATE TABLE "bank_card_cover_template" (
  "id" BIGINT NOT NULL,
  "name" VARCHAR(100) NOT NULL,
  "bank_id" BIGINT NOT NULL,
  "card_type" VARCHAR(20) NOT NULL,
  "source_url" VARCHAR(1000),
  "is_enabled" SMALLINT DEFAULT '1'::SMALLINT NOT NULL,
  "sort_order" INTEGER DEFAULT 0 NOT NULL,
  "is_deleted" SMALLINT DEFAULT '0'::SMALLINT NOT NULL,
  "create_user" BIGINT NOT NULL,
  "create_time" TIMESTAMP(0) WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
  "update_user" BIGINT NOT NULL,
  "update_time" TIMESTAMP(0) WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
  CONSTRAINT "chk_cover_template_enabled" CHECK (("is_enabled" = ANY (ARRAY[0, 1]))),
  CONSTRAINT "chk_cover_template_type" CHECK (("card_type" IN ('debit', 'credit'))),
  CONSTRAINT "ck_bank_card_cover_template_is_deleted" CHECK (("is_deleted" = ANY (ARRAY[0, 1]))),
  CONSTRAINT "bank_card_cover_template_pkey" PRIMARY KEY ("id")
);

COMMENT ON TABLE "bank_card_cover_template" IS '公共银行卡卡面';
COMMENT ON COLUMN "bank_card_cover_template"."id" IS '雪花ID';
COMMENT ON COLUMN "bank_card_cover_template"."name" IS '卡面名称';
COMMENT ON COLUMN "bank_card_cover_template"."bank_id" IS 'sys_dict_data.id，类型bank';
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
  "id" BIGINT NOT NULL,
  "user_id" BIGINT NOT NULL,
  "bank_card_id" BIGINT NOT NULL,
  "tag_id" BIGINT NOT NULL,
  "is_deleted" SMALLINT DEFAULT '0'::SMALLINT NOT NULL,
  "create_user" BIGINT NOT NULL,
  "create_time" TIMESTAMP(0) WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
  "update_user" BIGINT NOT NULL,
  "update_time" TIMESTAMP(0) WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
  CONSTRAINT "ck_bank_card_tag_rel_is_deleted" CHECK (("is_deleted" = ANY (ARRAY[0, 1]))),
  CONSTRAINT "bank_card_tag_rel_pkey" PRIMARY KEY ("id")
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
CREATE UNIQUE INDEX "uk_bank_card_tag_rel_user_card_tag" ON "bank_card_tag_rel" ("user_id", "bank_card_id", "tag_id") WHERE ("is_deleted" = 0);
COMMENT ON INDEX "uk_bank_card_tag_rel_user_card_tag" IS '同一银行卡同一标签只能有一条有效关联';

CREATE TABLE "device" (
  "id" BIGINT NOT NULL,
  "user_id" BIGINT NOT NULL,
  "name" VARCHAR(255) NOT NULL,
  "spec" VARCHAR(255),
  "type" VARCHAR(255),
  "status" VARCHAR(255),
  "remark" VARCHAR(255),
  "purchase_date" DATE,
  "purchase_price" NUMERIC(10,2),
  "purchase_place" VARCHAR(255),
  "end_date" DATE,
  "source_platform" VARCHAR(16),
  "source_order_id" VARCHAR(100) COLLATE "pg_catalog"."C",
  "source_product_id" VARCHAR(100) COLLATE "pg_catalog"."C",
  "source_item_id" VARCHAR(128) COLLATE "pg_catalog"."C",
  "source_unit_index" INTEGER,
  "create_user" BIGINT,
  "create_time" TIMESTAMP(0) WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
  "update_user" BIGINT,
  "update_time" TIMESTAMP(0) WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
  "is_deleted" SMALLINT DEFAULT '0'::SMALLINT NOT NULL,
  CONSTRAINT "ck_device_is_deleted" CHECK (("is_deleted" = ANY (ARRAY[0, 1]))),
  CONSTRAINT "device_pkey" PRIMARY KEY ("id")
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
CREATE INDEX "device_idx_user_deleted_purchase" ON "device" ("user_id", "is_deleted", "purchase_date" DESC NULLS LAST, "id" DESC);
COMMENT ON INDEX "device_idx_user_deleted_purchase" IS '设备列表：用户、删除标记与购买日期倒序';
CREATE UNIQUE INDEX "uk_device_user_source_unit" ON "device" ("user_id", "source_platform", "source_order_id", "source_item_id", "source_unit_index") WHERE (("is_deleted" = 0) AND ("source_platform" IS NOT NULL) AND ("source_order_id" IS NOT NULL) AND ("source_item_id" IS NOT NULL) AND ("source_unit_index" IS NOT NULL));
COMMENT ON INDEX "uk_device_user_source_unit" IS '同一用户同一来源件只能有一条有效设备记录，手工录入和无完整来源的记录不参与比较';

CREATE TABLE "device_expense" (
  "id" BIGINT NOT NULL,
  "user_id" BIGINT NOT NULL,
  "device_id" BIGINT NOT NULL,
  "expense_id" BIGINT NOT NULL,
  "create_user" BIGINT,
  "create_time" TIMESTAMP(0) WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
  "update_user" BIGINT,
  "update_time" TIMESTAMP(0) WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
  "is_deleted" SMALLINT DEFAULT '0'::SMALLINT NOT NULL,
  CONSTRAINT "ck_device_expense_is_deleted" CHECK (("is_deleted" = ANY (ARRAY[0, 1]))),
  CONSTRAINT "device_expense_pkey" PRIMARY KEY ("id")
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

CREATE TABLE "expense" (
  "id" BIGINT NOT NULL,
  "bookkeeping_amount" NUMERIC(10,2) CONSTRAINT "expense_amt_not_null" NOT NULL,
  "transaction_amount" NUMERIC(10,2),
  "expense_type_id" BIGINT CONSTRAINT "expense_exp_type_id_not_null" NOT NULL,
  "pay_type_id" BIGINT,
  "remark" VARCHAR(255),
  "user_id" BIGINT NOT NULL,
  "expense_time" TIMESTAMP(0) WITHOUT TIME ZONE CONSTRAINT "expense_exp_time_not_null" NOT NULL,
  "is_deleted" SMALLINT DEFAULT '0'::SMALLINT NOT NULL,
  "create_user" BIGINT,
  "create_time" TIMESTAMP(0) WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
  "update_user" BIGINT,
  "update_time" TIMESTAMP(0) WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
  "expense_desc" VARCHAR(200),
  "transaction_id" VARCHAR(100) COLLATE "pg_catalog"."C",
  "counterparty" VARCHAR(100),
  "counterparty_acct" VARCHAR(100) COLLATE "pg_catalog"."C",
  "merchant_order_no" VARCHAR(100) COLLATE "pg_catalog"."C",
  "transaction_status" VARCHAR(20),
  CONSTRAINT "ck_expense_is_deleted" CHECK (("is_deleted" = ANY (ARRAY[0, 1]))),
  CONSTRAINT "expense_pkey" PRIMARY KEY ("id")
);

COMMENT ON TABLE "expense" IS '支出表';
COMMENT ON COLUMN "expense"."id" IS '账单ID';
COMMENT ON COLUMN "expense"."bookkeeping_amount" IS '记账金额，计入支出统计；退款或手工调整后可与原始交易金额不同';
COMMENT ON COLUMN "expense"."transaction_amount" IS '支付平台原始交易金额；NULL表示未记录，不能假定等于记账金额';
COMMENT ON COLUMN "expense"."expense_type_id" IS '支出类型ID';
COMMENT ON COLUMN "expense"."pay_type_id" IS '支付方式ID(关联user_dict_data)';
COMMENT ON COLUMN "expense"."remark" IS '备注';
COMMENT ON COLUMN "expense"."user_id" IS '用户id';
COMMENT ON COLUMN "expense"."expense_time" IS '支出时间';
COMMENT ON COLUMN "expense"."is_deleted" IS '逻辑删除标记：0=未删除，1=已删除';
COMMENT ON COLUMN "expense"."create_user" IS '创建人';
COMMENT ON COLUMN "expense"."create_time" IS '创建时间';
COMMENT ON COLUMN "expense"."update_user" IS '修改人';
COMMENT ON COLUMN "expense"."update_time" IS '更新时间';
COMMENT ON COLUMN "expense"."expense_desc" IS '交易描述，未记录时为空';
COMMENT ON COLUMN "expense"."transaction_id" IS '支付平台交易号，保持原始字符串；未记录时为空';
COMMENT ON COLUMN "expense"."counterparty" IS '交易对方名称，未记录时为空';
COMMENT ON COLUMN "expense"."counterparty_acct" IS '交易对方账号，保持原始字符串；未记录时为空';
COMMENT ON COLUMN "expense"."merchant_order_no" IS '商家订单号';
COMMENT ON COLUMN "expense"."transaction_status" IS '交易状态';

CREATE INDEX "expense_idx_user_deleted_time" ON "expense" ("user_id", "is_deleted", "expense_time" DESC, "id" DESC);
COMMENT ON INDEX "expense_idx_user_deleted_time" IS '支出列表：用户、删除标记与支出时间倒序';
CREATE UNIQUE INDEX "uk_expense_user_transaction" ON "expense" ("user_id", "transaction_id") WHERE (("is_deleted" = 0) AND ("transaction_id" IS NOT NULL) AND ("btrim"(("transaction_id")::TEXT) <> ''::TEXT));
COMMENT ON INDEX "uk_expense_user_transaction" IS '同一用户同一支付平台交易号只能有一条有效支出；空交易号不参与比较';

CREATE TABLE "income" (
  "id" BIGINT NOT NULL,
  "gross_amount" NUMERIC(10,2) CONSTRAINT "income_amt_not_null" NOT NULL,
  "income_date" DATE CONSTRAINT "income_inc_date_not_null" NOT NULL,
  "remark" VARCHAR(255),
  "user_id" BIGINT NOT NULL,
  "create_user" BIGINT,
  "create_time" TIMESTAMP(0) WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
  "update_user" BIGINT,
  "update_time" TIMESTAMP(0) WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
  "is_deleted" SMALLINT DEFAULT '0'::SMALLINT NOT NULL,
  "income_type_id" BIGINT CONSTRAINT "income_inc_type_id_not_null" NOT NULL,
  "tax_amount" NUMERIC(10,2),
  CONSTRAINT "ck_income_is_deleted" CHECK (("is_deleted" = ANY (ARRAY[0, 1]))),
  CONSTRAINT "income_pkey" PRIMARY KEY ("id")
);

COMMENT ON TABLE "income" IS '收入表';
COMMENT ON COLUMN "income"."id" IS '主键ID';
COMMENT ON COLUMN "income"."gross_amount" IS '收入金额，未减税额；计入收入统计';
COMMENT ON COLUMN "income"."income_date" IS '收入时间';
COMMENT ON COLUMN "income"."remark" IS '备注';
COMMENT ON COLUMN "income"."user_id" IS '用户id';
COMMENT ON COLUMN "income"."create_user" IS '创建人';
COMMENT ON COLUMN "income"."create_time" IS '创建时间';
COMMENT ON COLUMN "income"."update_user" IS '修改人';
COMMENT ON COLUMN "income"."update_time" IS '更新时间';
COMMENT ON COLUMN "income"."is_deleted" IS '逻辑删除标记：0=未删除，1=已删除';
COMMENT ON COLUMN "income"."income_type_id" IS '收入类型ID';
COMMENT ON COLUMN "income"."tax_amount" IS '收入税额，单位元；NULL表示未记录，0表示零税额';

CREATE INDEX "income_idx_user_deleted_date" ON "income" ("user_id", "is_deleted", "income_date" DESC, "id" DESC);
COMMENT ON INDEX "income_idx_user_deleted_date" IS '收入列表：用户、删除标记与收入日期倒序';

CREATE TABLE "membership_provider" (
  "id" BIGINT NOT NULL,
  "name" VARCHAR(100) NOT NULL,
  "code" VARCHAR(50) NOT NULL COLLATE "pg_catalog"."C",
  "category" VARCHAR(50) DEFAULT 'other'::character varying NOT NULL,
  "icon_key" VARCHAR(64),
  "sort_order" INTEGER DEFAULT 0 NOT NULL,
  "is_enabled" SMALLINT DEFAULT '1'::SMALLINT NOT NULL,
  "create_user" BIGINT,
  "create_time" TIMESTAMP(0) WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
  "update_user" BIGINT,
  "update_time" TIMESTAMP(0) WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
  "is_deleted" SMALLINT DEFAULT '0'::SMALLINT NOT NULL,
  "active_flag" SMALLINT GENERATED ALWAYS AS (
CASE
    WHEN ("is_deleted" = 0) THEN 1
    ELSE NULL::integer
END) STORED,
  CONSTRAINT "ck_membership_provider_is_deleted" CHECK (("is_deleted" = ANY (ARRAY[0, 1]))),
  CONSTRAINT "ck_membership_provider_is_enabled" CHECK (("is_enabled" = ANY (ARRAY[0, 1]))),
  CONSTRAINT "membership_provider_pkey" PRIMARY KEY ("id")
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
  "id" BIGINT NOT NULL,
  "user_id" BIGINT NOT NULL,
  "name" VARCHAR(100) NOT NULL,
  "category" VARCHAR(50) DEFAULT 'other'::character varying NOT NULL,
  "provider_id" BIGINT,
  "provider" VARCHAR(100),
  "icon" VARCHAR(50),
  "color" VARCHAR(50),
  "start_date" DATE,
  "expiry_date" DATE NOT NULL,
  "price" NUMERIC(10,2),
  "billing_cycle" VARCHAR(20) DEFAULT 'month'::character varying NOT NULL,
  "monthly_amount" NUMERIC(10,2) DEFAULT 0.00 NOT NULL,
  "auto_renew" SMALLINT DEFAULT '0'::SMALLINT NOT NULL,
  "remark" VARCHAR(500),
  "create_user" BIGINT,
  "create_time" TIMESTAMP(0) WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
  "update_user" BIGINT,
  "update_time" TIMESTAMP(0) WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
  "is_deleted" SMALLINT DEFAULT '0'::SMALLINT NOT NULL,
  CONSTRAINT "ck_membership_record_auto_renew" CHECK (("auto_renew" = ANY (ARRAY[0, 1]))),
  CONSTRAINT "ck_membership_record_is_deleted" CHECK (("is_deleted" = ANY (ARRAY[0, 1]))),
  CONSTRAINT "ck_membership_record_monthly_amount" CHECK (("monthly_amount" >= (0)::numeric)),
  CONSTRAINT "ck_membership_record_period" CHECK ((("start_date" IS NULL) OR ("expiry_date" >= "start_date"))),
  CONSTRAINT "ck_membership_record_price" CHECK ((("price" IS NULL) OR ("price" >= (0)::numeric))),
  CONSTRAINT "membership_record_pkey" PRIMARY KEY ("id")
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
COMMENT ON CONSTRAINT "ck_membership_record_monthly_amount" ON "membership_record" IS '折算月均成本非负数';
COMMENT ON CONSTRAINT "ck_membership_record_period" ON "membership_record" IS '开通日为空或到期日不早于开通日';
COMMENT ON CONSTRAINT "ck_membership_record_price" ON "membership_record" IS '支付金额为空或非负数';

CREATE INDEX "membership_record_idx_expiry_date" ON "membership_record" ("expiry_date");
COMMENT ON INDEX "membership_record_idx_expiry_date" IS '业务检索索引：idx_expiry_date';
CREATE INDEX "membership_record_idx_membership_provider_id" ON "membership_record" ("provider_id");
COMMENT ON INDEX "membership_record_idx_membership_provider_id" IS '业务检索索引：idx_membership_provider_id';
CREATE INDEX "membership_record_idx_user_id" ON "membership_record" ("user_id");
COMMENT ON INDEX "membership_record_idx_user_id" IS '业务检索索引：idx_user_id';

CREATE TABLE "wardrobe_category" (
  "id" BIGINT NOT NULL,
  "name" VARCHAR(50) NOT NULL,
  "icon" VARCHAR(50),
  "parent_id" BIGINT,
  "sort_order" INTEGER DEFAULT 0 NOT NULL,
  "user_id" BIGINT,
  "create_user" BIGINT,
  "create_time" TIMESTAMP(0) WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
  "update_user" BIGINT,
  "update_time" TIMESTAMP(0) WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
  "is_deleted" SMALLINT DEFAULT '0'::SMALLINT NOT NULL,
  CONSTRAINT "ck_wardrobe_category_is_deleted" CHECK (("is_deleted" = ANY (ARRAY[0, 1]))),
  CONSTRAINT "ck_wardrobe_category_owner" CHECK ((("user_id" IS NULL) OR ("user_id" > 0))),
  CONSTRAINT "ck_wardrobe_category_parent_root" CHECK ((("parent_id" IS NULL) OR ("parent_id" > 0))),
  CONSTRAINT "ck_wardrobe_category_parent_self" CHECK (("parent_id" <> "id")),
  CONSTRAINT "wardrobe_category_pkey" PRIMARY KEY ("id")
);

COMMENT ON TABLE "wardrobe_category" IS '衣橱分类表';
COMMENT ON COLUMN "wardrobe_category"."id" IS '主键ID';
COMMENT ON COLUMN "wardrobe_category"."name" IS '分类名称';
COMMENT ON COLUMN "wardrobe_category"."icon" IS '图标';
COMMENT ON COLUMN "wardrobe_category"."parent_id" IS '父分类ID；NULL 表示根分类';
COMMENT ON COLUMN "wardrobe_category"."sort_order" IS '排序值，数值越小越靠前';
COMMENT ON COLUMN "wardrobe_category"."user_id" IS '所属用户ID；NULL表示系统公共预设';
COMMENT ON COLUMN "wardrobe_category"."create_user" IS '创建人';
COMMENT ON COLUMN "wardrobe_category"."create_time" IS '创建时间';
COMMENT ON COLUMN "wardrobe_category"."update_user" IS '更新人';
COMMENT ON COLUMN "wardrobe_category"."update_time" IS '更新时间';
COMMENT ON COLUMN "wardrobe_category"."is_deleted" IS '逻辑删除标记：0=未删除，1=已删除';
COMMENT ON CONSTRAINT "ck_wardrobe_category_owner" ON "wardrobe_category" IS '归属只允许为空（系统预设）或正数用户ID，不再使用0';
COMMENT ON CONSTRAINT "ck_wardrobe_category_parent_root" ON "wardrobe_category" IS '根分类使用 NULL，父级ID必须为正数';
COMMENT ON CONSTRAINT "ck_wardrobe_category_parent_self" ON "wardrobe_category" IS '分类不能以自身为父级';

CREATE INDEX "idx_wardrobe_category_preset_sort" ON "wardrobe_category" ("sort_order", "id") WHERE (("user_id" IS NULL) AND ("is_deleted" = 0));
COMMENT ON INDEX "idx_wardrobe_category_preset_sort" IS '系统预设衣橱分类按排序检索';
CREATE INDEX "idx_wardrobe_category_user_sort" ON "wardrobe_category" ("user_id", "sort_order", "id") WHERE ("is_deleted" = 0);
COMMENT ON INDEX "idx_wardrobe_category_user_sort" IS '用户自建衣橱分类按用户与排序检索';

CREATE TABLE "wardrobe_item" (
  "id" BIGINT NOT NULL,
  "name" VARCHAR(100) NOT NULL,
  "category_id" BIGINT,
  "color" VARCHAR(50),
  "brand" VARCHAR(100),
  "season" JSONB,
  "purchase_date" DATE,
  "purchase_price" NUMERIC(10,2),
  "size" VARCHAR(20),
  "remark" VARCHAR(500),
  "user_id" BIGINT NOT NULL,
  "source_platform" VARCHAR(16),
  "source_order_id" VARCHAR(100) COLLATE "pg_catalog"."C",
  "source_product_id" VARCHAR(100) COLLATE "pg_catalog"."C",
  "source_item_id" VARCHAR(128) COLLATE "pg_catalog"."C",
  "source_unit_index" INTEGER,
  "create_user" BIGINT,
  "create_time" TIMESTAMP(0) WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
  "update_user" BIGINT,
  "update_time" TIMESTAMP(0) WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
  "is_deleted" SMALLINT DEFAULT '0'::SMALLINT NOT NULL,
  CONSTRAINT "ck_wardrobe_item_is_deleted" CHECK (("is_deleted" = ANY (ARRAY[0, 1]))),
  CONSTRAINT "ck_wardrobe_item_season" CHECK ((("season" IS NULL) OR (("jsonb_typeof"("season") = 'array'::TEXT) AND (("jsonb_array_length"(
CASE
    WHEN ("jsonb_typeof"("season") = 'array'::TEXT) THEN "season"
    ELSE '[]'::"jsonb"
END) >= 1) AND ("jsonb_array_length"(
CASE
    WHEN ("jsonb_typeof"("season") = 'array'::TEXT) THEN "season"
    ELSE '[]'::"jsonb"
END) <= 4)) AND ("season" <@ '["春", "夏", "秋", "冬"]'::"jsonb")))),
  CONSTRAINT "wardrobe_item_pkey" PRIMARY KEY ("id")
);

COMMENT ON TABLE "wardrobe_item" IS '衣橱衣物表';
COMMENT ON COLUMN "wardrobe_item"."id" IS '主键ID';
COMMENT ON COLUMN "wardrobe_item"."name" IS '衣物名称';
COMMENT ON COLUMN "wardrobe_item"."category_id" IS '分类ID';
COMMENT ON COLUMN "wardrobe_item"."color" IS '颜色';
COMMENT ON COLUMN "wardrobe_item"."brand" IS '品牌';
COMMENT ON COLUMN "wardrobe_item"."season" IS '适用季节数组，元素为春/夏/秋/冬；NULL表示未填写';
COMMENT ON COLUMN "wardrobe_item"."purchase_date" IS '购买日期';
COMMENT ON COLUMN "wardrobe_item"."purchase_price" IS '购入价格，人民币元；NULL表示未记录';
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
COMMENT ON CONSTRAINT "ck_wardrobe_item_season" ON "wardrobe_item" IS '适用季节只允许春/夏/秋/冬组成的非空数组，最多四项';

CREATE UNIQUE INDEX "uk_wardrobe_item_user_source_unit" ON "wardrobe_item" ("user_id", "source_platform", "source_order_id", "source_item_id", "source_unit_index") WHERE (("is_deleted" = 0) AND ("source_platform" IS NOT NULL) AND ("source_order_id" IS NOT NULL) AND ("source_item_id" IS NOT NULL) AND ("source_unit_index" IS NOT NULL));
COMMENT ON INDEX "uk_wardrobe_item_user_source_unit" IS '同一用户同一来源件只能有一条有效衣物记录，手工录入和无完整来源的记录不参与比较';
CREATE INDEX "wardrobe_item_idx_category_id" ON "wardrobe_item" ("category_id");
COMMENT ON INDEX "wardrobe_item_idx_category_id" IS '业务检索索引：idx_category_id';
CREATE INDEX "wardrobe_item_idx_user_id" ON "wardrobe_item" ("user_id");
COMMENT ON INDEX "wardrobe_item_idx_user_id" IS '业务检索索引：idx_user_id';
CREATE INDEX "wardrobe_item_idx_wardrobe_item_capture_order" ON "wardrobe_item" ("user_id", "source_platform", "source_order_id", "is_deleted");
COMMENT ON INDEX "wardrobe_item_idx_wardrobe_item_capture_order" IS '业务检索索引：idx_wardrobe_item_capture_order';

-- 附件、外部资源、导入任务与存储对象

CREATE TABLE "attachment" (
  "storage_object_id" BIGINT,
  "file_name" VARCHAR(255) COLLATE "pg_catalog"."C",
  "biz_type" VARCHAR(50) CONSTRAINT "file_biz_type_not_null" NOT NULL,
  "biz_id" BIGINT,
  "is_public" SMALLINT DEFAULT '0'::SMALLINT CONSTRAINT "file_is_public_not_null" NOT NULL,
  "create_user" BIGINT,
  "create_time" TIMESTAMP(0) WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP CONSTRAINT "file_create_time_not_null" NOT NULL,
  "update_user" BIGINT,
  "update_time" TIMESTAMP(0) WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP CONSTRAINT "file_update_time_not_null" NOT NULL,
  "is_deleted" SMALLINT DEFAULT '0'::SMALLINT CONSTRAINT "file_is_deleted_not_null" NOT NULL,
  "id" BIGINT NOT NULL,
  "user_id" BIGINT,
  "external_resource_id" BIGINT,
  "sort_order" INTEGER DEFAULT 0 NOT NULL,
  "file_key" UUID NOT NULL,
  CONSTRAINT "ck_attachment_file_key_v4" CHECK ((NOT ("uuid_extract_version"("file_key") IS DISTINCT FROM 4))),
  CONSTRAINT "ck_attachment_is_public" CHECK (("is_public" = ANY (ARRAY[0, 1]))),
  CONSTRAINT "ck_attachment_object" CHECK ((("is_deleted" = 1) OR ("storage_object_id" IS NOT NULL))),
  CONSTRAINT "ck_attachment_owner" CHECK (((("user_id" IS NOT NULL) OR ("biz_type" IN ('bank_card_template_face', 'cbti_personality_image'))) AND ((("biz_type")::TEXT <> 'cbti_personality_image'::TEXT) OR ("user_id" IS NULL)))),
  CONSTRAINT "attachment_pkey" PRIMARY KEY ("id")
);

COMMENT ON TABLE "attachment" IS '用户在一个业务位置使用的附件，字节及物理元数据由storage_object持有';
COMMENT ON COLUMN "attachment"."storage_object_id" IS '物理对象ID，有效附件必填；历史缺失且已删除的附件可为空，应用校验存在性与回收并发';
COMMENT ON COLUMN "attachment"."file_name" IS '下载展示文件名，不是对象存储路径；未提供时为空';
COMMENT ON COLUMN "attachment"."biz_type" IS '附件用途：movie_cover/read_cover/device_cover/wardrobe_item_cover/b_video_cover/avatar/bank_card_face/bank_card_template_face/cbti_personality_image/feedback/feedback_comment/honor_record/event_record';
COMMENT ON COLUMN "attachment"."biz_id" IS '业务记录ID；上传待绑定或解绑后为空';
COMMENT ON COLUMN "attachment"."is_public" IS '0私有，1公开；服务端按当前头像、已发布公共卡面或 CBTI 人格图片设置';
COMMENT ON COLUMN "attachment"."create_user" IS '创建人';
COMMENT ON COLUMN "attachment"."create_time" IS '创建时间';
COMMENT ON COLUMN "attachment"."update_user" IS '更新人';
COMMENT ON COLUMN "attachment"."update_time" IS '更新时间';
COMMENT ON COLUMN "attachment"."is_deleted" IS '是否删除';
COMMENT ON COLUMN "attachment"."id" IS '附件内部主键，由应用生成雪花ID；业务API不对外暴露';
COMMENT ON COLUMN "attachment"."user_id" IS '归属用户ID；CBTI 人格图片为空，公共卡面模板可为空';
COMMENT ON COLUMN "attachment"."external_resource_id" IS '远程来源ID；本地上传或系统生成时为空';
COMMENT ON COLUMN "attachment"."sort_order" IS '同一业务多附件的展示顺序，从0开始';
COMMENT ON COLUMN "attachment"."file_key" IS '附件对外标识，由应用生成安全随机UUIDv4；接口为带连字符的小写字符串，不代表访问权限';
COMMENT ON CONSTRAINT "ck_attachment_file_key_v4" ON "attachment" IS '附件对外标识只允许随机UUIDv4';
COMMENT ON CONSTRAINT "ck_attachment_object" ON "attachment" IS '有效附件必须绑定物理对象，仅已删除历史缺失附件可无对象';
COMMENT ON CONSTRAINT "ck_attachment_owner" ON "attachment" IS 'CBTI 人格图片归系统所有；只有人格图片和公共卡面模板允许没有用户归属';

CREATE INDEX "idx_attachment_biz" ON "attachment" ("biz_type", "biz_id");
COMMENT ON INDEX "idx_attachment_biz" IS '按业务用途与业务记录检索附件';
CREATE INDEX "idx_attachment_owner" ON "attachment" ("user_id", "is_deleted");
COMMENT ON INDEX "idx_attachment_owner" IS '用户附件归属与删除状态检索';
CREATE INDEX "idx_attachment_public_object" ON "attachment" ("storage_object_id") WHERE (("is_public" = 1) AND ("is_deleted" = 0));
COMMENT ON INDEX "idx_attachment_public_object" IS '公开对象访问反查有效公开附件';
CREATE INDEX "idx_attachment_source" ON "attachment" ("external_resource_id");
COMMENT ON INDEX "idx_attachment_source" IS '附件来源追溯';
CREATE INDEX "idx_attachment_storage_object" ON "attachment" ("storage_object_id");
COMMENT ON INDEX "idx_attachment_storage_object" IS '按物理对象反查附件引用';
CREATE UNIQUE INDEX "uk_attachment_file_key" ON "attachment" ("file_key");
COMMENT ON INDEX "uk_attachment_file_key" IS '附件对外UUID全局唯一，包含已删除附件；用于上传绑定及预览下载定位';
CREATE UNIQUE INDEX "uk_attachment_single_slot" ON "attachment" ("biz_type", "biz_id") WHERE (("is_deleted" = 0) AND ("biz_id" IS NOT NULL) AND ("biz_type" IN ('movie_cover', 'read_cover', 'device_cover', 'wardrobe_item_cover', 'b_video_cover', 'avatar', 'bank_card_face', 'bank_card_template_face', 'cbti_personality_image')));
COMMENT ON INDEX "uk_attachment_single_slot" IS '含 CBTI 人格图片在内的单值业务位置只允许一条有效附件';

CREATE TABLE "external_resource" (
  "id" BIGINT NOT NULL,
  "platform" VARCHAR(32) NOT NULL,
  "resource_type" VARCHAR(32) NOT NULL,
  "external_key" VARCHAR(255) NOT NULL,
  "variant" VARCHAR(128) DEFAULT ''::character varying NOT NULL,
  "source_url" VARCHAR(1000) NOT NULL,
  "storage_object_id" BIGINT,
  "state" VARCHAR(16) DEFAULT 'PENDING'::character varying NOT NULL,
  "attempts" INTEGER DEFAULT 0 NOT NULL,
  "next_attempt_at" TIMESTAMP(0) WITHOUT TIME ZONE,
  "lease_token" VARCHAR(32),
  "lease_until" TIMESTAMP(0) WITHOUT TIME ZONE,
  "error_code" VARCHAR(64),
  "fetched_at" TIMESTAMP(0) WITHOUT TIME ZONE,
  "create_user" BIGINT,
  "create_time" TIMESTAMP(0) WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
  "update_user" BIGINT,
  "update_time" TIMESTAMP(0) WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
  "is_deleted" SMALLINT DEFAULT 0 NOT NULL,
  CONSTRAINT "ck_external_resource_active" CHECK (("is_deleted" = 0)),
  CONSTRAINT "ck_external_resource_attempts" CHECK ((("attempts" >= 0) AND ("attempts" <= 5))),
  CONSTRAINT "ck_external_resource_lease" CHECK ((((("state")::TEXT = 'RUNNING'::TEXT) AND ("lease_token" IS NOT NULL) AND ("lease_until" IS NOT NULL)) OR ((("state")::TEXT <> 'RUNNING'::TEXT) AND ("lease_token" IS NULL) AND ("lease_until" IS NULL)))),
  CONSTRAINT "ck_external_resource_object" CHECK (((("state")::TEXT = 'READY'::TEXT) = ("storage_object_id" IS NOT NULL))),
  CONSTRAINT "ck_external_resource_platform" CHECK (("platform" IN ('douban', 'bilibili', 'jd', 'taobao'))),
  CONSTRAINT "ck_external_resource_state" CHECK (("state" IN ('PENDING', 'RUNNING', 'READY', 'FAILED'))),
  CONSTRAINT "external_resource_pkey" PRIMARY KEY ("id")
);

COMMENT ON TABLE "external_resource" IS '跨用户共享的远程来源；成功后永久复用，不设置缓存过期或主动刷新';
COMMENT ON COLUMN "external_resource"."id" IS '来源ID，由应用生成雪花ID';
COMMENT ON COLUMN "external_resource"."platform" IS '平台：douban豆瓣、bilibili哔哩哔哩、jd京东、taobao淘宝';
COMMENT ON COLUMN "external_resource"."resource_type" IS '资源类型：movie_poster影视海报、book_cover书籍封面、video_cover视频封面、product_image商品图';
COMMENT ON COLUMN "external_resource"."external_key" IS '从已校验URL解析的平台资源稳定标识';
COMMENT ON COLUMN "external_resource"."variant" IS '平台尺寸裁剪规格，无规格时为空串';
COMMENT ON COLUMN "external_resource"."source_url" IS '已校验下载地址，仅用于首次下载、失败重试与追溯';
COMMENT ON COLUMN "external_resource"."storage_object_id" IS '成功对象ID；未成功时为空，成功后不再改变';
COMMENT ON COLUMN "external_resource"."state" IS 'PENDING待下载、RUNNING持租约下载、READY成功复用、FAILED失败可重试';
COMMENT ON COLUMN "external_resource"."attempts" IS '本轮下载尝试次数，最多5次，显式重试失败来源后归零';
COMMENT ON COLUMN "external_resource"."next_attempt_at" IS '退避后可重试时间；初次或显式重试时为空';
COMMENT ON COLUMN "external_resource"."lease_token" IS '下载租约令牌，未运行时为空';
COMMENT ON COLUMN "external_resource"."lease_until" IS '下载租约到期时间，未运行时为空';
COMMENT ON COLUMN "external_resource"."error_code" IS '受控失败码，无失败时为空';
COMMENT ON COLUMN "external_resource"."fetched_at" IS '成功下载时间；未成功时为空';
COMMENT ON COLUMN "external_resource"."create_user" IS '创建人用户ID，后台任务可为空';
COMMENT ON COLUMN "external_resource"."create_time" IS '创建时间';
COMMENT ON COLUMN "external_resource"."update_user" IS '最后更新人用户ID，后台任务可为空';
COMMENT ON COLUMN "external_resource"."update_time" IS '最后更新时间';
COMMENT ON COLUMN "external_resource"."is_deleted" IS '逻辑删除标记：0有效，1已删除';
COMMENT ON CONSTRAINT "ck_external_resource_active" ON "external_resource" IS '成功来源不按时间或远端状态失效';
COMMENT ON CONSTRAINT "ck_external_resource_attempts" ON "external_resource" IS '每轮尝试计数限定0至5';
COMMENT ON CONSTRAINT "ck_external_resource_lease" ON "external_resource" IS '运行来源必须同时持有租约令牌和到期时间';
COMMENT ON CONSTRAINT "ck_external_resource_object" ON "external_resource" IS '仅成功来源持有物理对象';
COMMENT ON CONSTRAINT "ck_external_resource_platform" ON "external_resource" IS '平台限定为已定义的白名单策略';
COMMENT ON CONSTRAINT "ck_external_resource_state" ON "external_resource" IS '来源状态只能为等待、运行、成功或失败';

CREATE INDEX "idx_external_resource_due" ON "external_resource" ("state", "next_attempt_at", "lease_until");
COMMENT ON INDEX "idx_external_resource_due" IS '扫描待下载或租约到期的来源';
CREATE INDEX "idx_external_resource_object" ON "external_resource" ("storage_object_id");
COMMENT ON INDEX "idx_external_resource_object" IS '对象回收反查成功来源引用';
CREATE UNIQUE INDEX "uk_external_resource_identity" ON "external_resource" ("platform", "resource_type", "external_key", "variant");
COMMENT ON INDEX "uk_external_resource_identity" IS '同一平台资源规格只保留一个来源，成功后不失效';

CREATE TABLE "import_job" (
  "id" BIGINT NOT NULL,
  "biz_type" VARCHAR(50) NOT NULL,
  "biz_id" BIGINT NOT NULL,
  "external_resource_id" BIGINT NOT NULL,
  "state" VARCHAR(16) DEFAULT 'PENDING'::character varying NOT NULL,
  "next_attempt_at" TIMESTAMP(0) WITHOUT TIME ZONE,
  "error_code" VARCHAR(64),
  "result_attachment_id" BIGINT,
  "create_user" BIGINT,
  "create_time" TIMESTAMP(0) WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
  "update_user" BIGINT,
  "update_time" TIMESTAMP(0) WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
  "is_deleted" SMALLINT DEFAULT 0 NOT NULL,
  CONSTRAINT "ck_import_job_deleted" CHECK (("is_deleted" = ANY (ARRAY[0, 1]))),
  CONSTRAINT "ck_import_job_result" CHECK (((("state")::TEXT = 'READY'::TEXT) = ("result_attachment_id" IS NOT NULL))),
  CONSTRAINT "ck_import_job_state" CHECK (("state" IN ('PENDING', 'RUNNING', 'READY', 'FAILED', 'CANCELLED'))),
  CONSTRAINT "import_job_pkey" PRIMARY KEY ("id")
);

COMMENT ON TABLE "import_job" IS '业务位置的一次远程导入请求，下载租约由来源持有，等待任务可重复推进';
COMMENT ON COLUMN "import_job"."id" IS '导入任务ID，由应用生成雪花ID';
COMMENT ON COLUMN "import_job"."biz_type" IS '目标附件用途，当前异步导入为b_video_cover';
COMMENT ON COLUMN "import_job"."biz_id" IS '目标业务记录ID';
COMMENT ON COLUMN "import_job"."external_resource_id" IS '等待的共享来源ID';
COMMENT ON COLUMN "import_job"."state" IS 'PENDING等待、RUNNING等待来源、READY已绑定附件、FAILED失败、CANCELLED已取消';
COMMENT ON COLUMN "import_job"."next_attempt_at" IS '下一次可检查时间，未设置时可立即检查';
COMMENT ON COLUMN "import_job"."error_code" IS '受控失败码，无失败时为空';
COMMENT ON COLUMN "import_job"."result_attachment_id" IS '成功产出的附件ID，未成功时为空';
COMMENT ON COLUMN "import_job"."create_user" IS '创建人用户ID，后台任务可为空';
COMMENT ON COLUMN "import_job"."create_time" IS '创建时间';
COMMENT ON COLUMN "import_job"."update_user" IS '最后更新人用户ID，后台任务可为空';
COMMENT ON COLUMN "import_job"."update_time" IS '最后更新时间';
COMMENT ON COLUMN "import_job"."is_deleted" IS '逻辑删除标记：0有效，1已删除';
COMMENT ON CONSTRAINT "ck_import_job_deleted" ON "import_job" IS '逻辑删除标记限定0或1';
COMMENT ON CONSTRAINT "ck_import_job_result" ON "import_job" IS '只有成功任务必须且能够保存结果附件ID';
COMMENT ON CONSTRAINT "ck_import_job_state" ON "import_job" IS '导入任务状态限定为已定义的五种状态';

CREATE INDEX "idx_import_job_slot" ON "import_job" ("biz_type", "biz_id", "create_time" DESC, "id" DESC);
COMMENT ON INDEX "idx_import_job_slot" IS '读取业务位置最新导入状态';
CREATE INDEX "idx_import_job_source" ON "import_job" ("external_resource_id", "state");
COMMENT ON INDEX "idx_import_job_source" IS '来源下载完成后推进等待任务';
CREATE UNIQUE INDEX "uk_import_job_active_slot" ON "import_job" ("biz_type", "biz_id") WHERE (("is_deleted" = 0) AND ("state" IN ('PENDING', 'RUNNING')));
COMMENT ON INDEX "uk_import_job_active_slot" IS '同一业务位置同时最多一个在途请求';

CREATE TABLE "storage_object" (
  "id" BIGINT NOT NULL,
  "bucket" VARCHAR(63) NOT NULL,
  "object_key" VARCHAR(255) NOT NULL COLLATE "pg_catalog"."C",
  "content_hash" CHARACTER(64) CONSTRAINT "storage_object_sha256_not_null" NOT NULL,
  "file_size" BIGINT NOT NULL,
  "content_type" VARCHAR(100) NOT NULL,
  "create_user" BIGINT,
  "create_time" TIMESTAMP(0) WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
  "update_user" BIGINT,
  "update_time" TIMESTAMP(0) WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
  "is_deleted" SMALLINT DEFAULT '0'::SMALLINT NOT NULL,
  "image_width" INTEGER,
  "image_height" INTEGER,
  CONSTRAINT "ck_storage_object_active" CHECK (("is_deleted" = 0)),
  CONSTRAINT "ck_storage_object_dimensions" CHECK (((("image_width" IS NULL) AND ("image_height" IS NULL)) OR (("image_width" IS NOT NULL) AND ("image_height" IS NOT NULL) AND ("image_width" > 0) AND ("image_height" > 0)))),
  CONSTRAINT "ck_storage_object_hash" CHECK (("content_hash" ~ '^[0-9a-f]{64}$'::TEXT)),
  CONSTRAINT "ck_storage_object_size" CHECK (("file_size" > 0)),
  CONSTRAINT "storage_object_pkey" PRIMARY KEY ("id")
);

COMMENT ON TABLE "storage_object" IS '不可变图片对象';
COMMENT ON COLUMN "storage_object"."id" IS '主键ID';
COMMENT ON COLUMN "storage_object"."bucket" IS '对象存储桶名称，视频封面使用system';
COMMENT ON COLUMN "storage_object"."object_key" IS '内容寻址对象键：objects/摘要前两位/摘要.服务端检测扩展名';
COMMENT ON COLUMN "storage_object"."content_hash" IS '文件字节的SHA-256摘要，64位小写十六进制，写入后不可变';
COMMENT ON COLUMN "storage_object"."file_size" IS '文件大小，单位字节';
COMMENT ON COLUMN "storage_object"."content_type" IS '实际存储文件的MIME类型';
COMMENT ON COLUMN "storage_object"."create_user" IS '创建人用户ID';
COMMENT ON COLUMN "storage_object"."create_time" IS '创建时间';
COMMENT ON COLUMN "storage_object"."update_user" IS '最后更新人用户ID';
COMMENT ON COLUMN "storage_object"."update_time" IS '最后更新时间';
COMMENT ON COLUMN "storage_object"."is_deleted" IS '恒为0；不使用逻辑删除，无引用回收时物理删除';
COMMENT ON COLUMN "storage_object"."image_width" IS '图片宽度，单位像素；非图片为空';
COMMENT ON COLUMN "storage_object"."image_height" IS '图片高度，单位像素；非图片为空';
COMMENT ON CONSTRAINT "ck_storage_object_active" ON "storage_object" IS '对象仅物理回收，不使用逻辑删除状态';
COMMENT ON CONSTRAINT "ck_storage_object_dimensions" ON "storage_object" IS '图片宽高同时为空或同时为正整数';
COMMENT ON CONSTRAINT "ck_storage_object_hash" ON "storage_object" IS '对象摘要必须为64位小写SHA-256十六进制';
COMMENT ON CONSTRAINT "ck_storage_object_size" ON "storage_object" IS '拒绝空对象及负字节数';

CREATE UNIQUE INDEX "uk_storage_object_hash" ON "storage_object" ("bucket", "content_hash");
COMMENT ON INDEX "uk_storage_object_hash" IS '同一桶相同字节唯一，上传导入与回收共用对象锁';
CREATE UNIQUE INDEX "uk_storage_object_key" ON "storage_object" ("bucket", "object_key");
COMMENT ON INDEX "uk_storage_object_key" IS '桶内物理对象键唯一';

-- 视频、观影、阅读、作品与榜单

CREATE FUNCTION record_douban_subject_id(record_url text, subject_domain text)
RETURNS text
LANGUAGE sql IMMUTABLE STRICT PARALLEL SAFE
AS $$
    SELECT substring(btrim(record_url) FROM
        ('(?i)^https?://' || subject_domain || '[.]douban[.]com/subject/([0-9]{1,255})/?(?:[?#].*)?$'))
$$;

COMMENT ON FUNCTION record_douban_subject_id(text, text)
    IS '从观影或阅读的豆瓣条目 URL 提取对应类型的外部条目 ID';

CREATE TABLE "b_video" (
  "id" BIGINT NOT NULL,
  "title" VARCHAR(500) NOT NULL,
  "url" VARCHAR(1000) NOT NULL COLLATE "pg_catalog"."C",
  "duration_seconds" INTEGER NOT NULL,
  "watched_duration_seconds" INTEGER DEFAULT 0 NOT NULL,
  "episodes" INTEGER DEFAULT 1,
  "current_episode" INTEGER DEFAULT 1,
  "status" VARCHAR(32) DEFAULT 'in_progress'::character varying NOT NULL,
  "last_watched" TIMESTAMP(0) WITHOUT TIME ZONE,
  "notes" TEXT,
  "bvid" VARCHAR(20) COLLATE "pg_catalog"."C",
  "aid" VARCHAR(20),
  "description" TEXT,
  "owner_name" VARCHAR(100),
  "pages_info" JSONB,
  "user_id" BIGINT NOT NULL,
  "create_user" BIGINT,
  "create_time" TIMESTAMP(0) WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
  "update_user" BIGINT,
  "update_time" TIMESTAMP(0) WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
  "is_deleted" SMALLINT DEFAULT '0'::SMALLINT NOT NULL,
  CONSTRAINT "ck_b_video_is_deleted" CHECK (("is_deleted" = ANY (ARRAY[0, 1]))),
  CONSTRAINT "b_video_pkey" PRIMARY KEY ("id")
);

COMMENT ON TABLE "b_video" IS 'B 站视频记录表';
COMMENT ON COLUMN "b_video"."id" IS '主键ID';
COMMENT ON COLUMN "b_video"."title" IS '视频标题';
COMMENT ON COLUMN "b_video"."url" IS 'B站视频URL';
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
CREATE INDEX "b_video_idx_user_deleted_update" ON "b_video" ("user_id", "is_deleted", "update_time" DESC NULLS LAST, "id" DESC);
COMMENT ON INDEX "b_video_idx_user_deleted_update" IS '视频列表：用户、删除标记与更新时间倒序';
CREATE UNIQUE INDEX "uk_b_video_user_bvid" ON "b_video" ("user_id", "bvid") WHERE (("is_deleted" = 0) AND ("bvid" IS NOT NULL) AND (("bvid")::TEXT <> ''::TEXT));
COMMENT ON INDEX "uk_b_video_user_bvid" IS '同一用户同一 BV 号只能有一条有效视频记录，未记录 BV 号时为空不参与比较';

CREATE TABLE "creative_work" (
  "id" BIGINT NOT NULL,
  "kind" VARCHAR(16) NOT NULL,
  "title" VARCHAR(255) NOT NULL,
  "director" VARCHAR(100),
  "cast_names" JSONB,
  "author" VARCHAR(100),
  "duration_minutes" INTEGER,
  "episode_count" INTEGER,
  "page_count" INTEGER,
  "platform" VARCHAR(32),
  "resource_type" VARCHAR(32),
  "external_key" VARCHAR(255),
  "cover_url" VARCHAR(1000),
  "metadata_ready_time" TIMESTAMP(0) WITHOUT TIME ZONE,
  "create_user" BIGINT,
  "create_time" TIMESTAMP(0) WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
  "update_user" BIGINT,
  "update_time" TIMESTAMP(0) WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
  "is_deleted" SMALLINT DEFAULT 0 NOT NULL,
  CONSTRAINT "ck_creative_work_active" CHECK (("is_deleted" = ANY (ARRAY[0, 1]))),
  CONSTRAINT "ck_creative_work_cast" CHECK (("jsonb_typeof"("cast_names") = 'array'::TEXT)),
  CONSTRAINT "ck_creative_work_duration" CHECK (("duration_minutes" > 0)),
  CONSTRAINT "ck_creative_work_episodes" CHECK (("episode_count" > 0)),
  CONSTRAINT "ck_creative_work_identity" CHECK (("num_nonnulls"("platform", "resource_type", "external_key") = ANY (ARRAY[0, 3]))),
  CONSTRAINT "ck_creative_work_kind" CHECK (("kind" IN ('film', 'series', 'book'))),
  CONSTRAINT "ck_creative_work_pages" CHECK (("page_count" > 0)),
  CONSTRAINT "creative_work_pkey" PRIMARY KEY ("id")
);

COMMENT ON TABLE "creative_work" IS '共享作品';
COMMENT ON COLUMN "creative_work"."id" IS '作品主键，由应用生成雪花 ID';
COMMENT ON COLUMN "creative_work"."kind" IS '作品类型，首期为 film、series、book';
COMMENT ON COLUMN "creative_work"."title" IS '作品显示名称，允许同名';
COMMENT ON COLUMN "creative_work"."director" IS '影视导演，优先保存来源提供的主要导演，供现有表单回填';
COMMENT ON COLUMN "creative_work"."cast_names" IS '影视演员姓名数组，按来源顺序保存，未取得时为空';
COMMENT ON COLUMN "creative_work"."author" IS '图书作者，供现有表单回填';
COMMENT ON COLUMN "creative_work"."duration_minutes" IS '电影总时长，单位为分钟';
COMMENT ON COLUMN "creative_work"."episode_count" IS '剧集总集数';
COMMENT ON COLUMN "creative_work"."page_count" IS '该图书版本的总页数';
COMMENT ON COLUMN "creative_work"."platform" IS '外部系统，例如 douban';
COMMENT ON COLUMN "creative_work"."resource_type" IS '外部条目类型，例如 movie_subject、book_subject';
COMMENT ON COLUMN "creative_work"."external_key" IS '外部作品条目 ID，按字符串保存';
COMMENT ON COLUMN "creative_work"."cover_url" IS '校验后的原始封面 URL，用于服务端推导图片缓存键';
COMMENT ON COLUMN "creative_work"."metadata_ready_time" IS '详情确认并保存的时间。仅有榜单基础资料时为空';
COMMENT ON COLUMN "creative_work"."create_user" IS '创建操作者 ID，系统任务可留空';
COMMENT ON COLUMN "creative_work"."create_time" IS '创建时间';
COMMENT ON COLUMN "creative_work"."update_user" IS '最后更新操作者 ID，系统任务可留空';
COMMENT ON COLUMN "creative_work"."update_time" IS '最后更新时间';
COMMENT ON COLUMN "creative_work"."is_deleted" IS '逻辑删除标记，0 有效，1 已删除';

-- 同一平台、资源类型和外部标识对应一部作品。
CREATE UNIQUE INDEX "uk_creative_work_identity" ON "creative_work" ("platform", "resource_type", "external_key") WHERE ("external_key" IS NOT NULL);

CREATE TABLE "movie" (
  "id" BIGINT NOT NULL,
  "title" VARCHAR(255) NOT NULL,
  "type" SMALLINT NOT NULL,
  "director" VARCHAR(100),
  "url" VARCHAR(500),
  "status" VARCHAR(32) DEFAULT 'not_started'::character varying NOT NULL,
  "total_progress" INTEGER DEFAULT 0 NOT NULL,
  "current_progress" INTEGER DEFAULT 0 NOT NULL,
  "start_date" DATE,
  "finish_date" DATE,
  "rating" SMALLINT,
  "remark" VARCHAR(1000),
  "user_id" BIGINT NOT NULL,
  "create_time" TIMESTAMP(0) WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
  "update_time" TIMESTAMP(0) WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
  "create_user" BIGINT,
  "update_user" BIGINT,
  "is_deleted" SMALLINT DEFAULT '0'::SMALLINT NOT NULL,
  "platform" VARCHAR(32) GENERATED ALWAYS AS (
CASE
    WHEN ("record_douban_subject_id"(("url")::TEXT, 'movie'::TEXT) IS NOT NULL) THEN 'douban'::TEXT
    ELSE NULL::TEXT
END) STORED,
  "external_key" VARCHAR(255) GENERATED ALWAYS AS ("record_douban_subject_id"(("url")::TEXT, 'movie'::TEXT)) STORED,
  CONSTRAINT "ck_movie_is_deleted" CHECK (("is_deleted" = ANY (ARRAY[0, 1]))),
  CONSTRAINT "movie_pkey" PRIMARY KEY ("id")
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
COMMENT ON COLUMN "movie"."start_date" IS '开始观看日期';
COMMENT ON COLUMN "movie"."finish_date" IS '看完日期';
COMMENT ON COLUMN "movie"."rating" IS '个人评分：1-5';
COMMENT ON COLUMN "movie"."remark" IS '短评/备注';
COMMENT ON COLUMN "movie"."user_id" IS '归属用户ID';
COMMENT ON COLUMN "movie"."create_time" IS '创建时间';
COMMENT ON COLUMN "movie"."update_time" IS '更新时间';
COMMENT ON COLUMN "movie"."create_user" IS '创建人';
COMMENT ON COLUMN "movie"."update_user" IS '更新人';
COMMENT ON COLUMN "movie"."is_deleted" IS '逻辑删除标记：0=未删除，1=已删除';
COMMENT ON COLUMN "movie"."platform" IS '外部系统编码，由观影链接生成，例如 douban';
COMMENT ON COLUMN "movie"."external_key" IS '外部系统条目 ID，由观影链接生成';

-- 按用户与外部条目标识检索有效观影记录。
CREATE INDEX "idx_movie_user_external" ON "movie" ("user_id", "platform", "external_key") WHERE (("is_deleted" = 0) AND ("external_key" IS NOT NULL));
CREATE INDEX "movie_idx_user_id" ON "movie" ("user_id");
COMMENT ON INDEX "movie_idx_user_id" IS '业务检索索引：idx_user_id';

CREATE TABLE "ranking_list" (
  "id" BIGINT NOT NULL,
  "name" VARCHAR(120) NOT NULL,
  "publisher_code" VARCHAR(32) NOT NULL COLLATE "pg_catalog"."C",
  "list_key" VARCHAR(64) NOT NULL COLLATE "pg_catalog"."C",
  "edition_key" VARCHAR(32) NOT NULL COLLATE "pg_catalog"."C",
  "source_url" VARCHAR(1000),
  "last_sync_time" TIMESTAMP(0) WITHOUT TIME ZONE,
  "create_user" BIGINT,
  "create_time" TIMESTAMP(0) WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
  "update_user" BIGINT,
  "update_time" TIMESTAMP(0) WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
  "is_deleted" SMALLINT DEFAULT 0 NOT NULL,
  CONSTRAINT "ck_ranking_list_active" CHECK (("is_deleted" = ANY (ARRAY[0, 1]))),
  CONSTRAINT "ranking_list_pkey" PRIMARY KEY ("id")
);

COMMENT ON TABLE "ranking_list" IS '榜单期次';
COMMENT ON COLUMN "ranking_list"."id" IS '榜单主键，由应用生成雪花 ID';
COMMENT ON COLUMN "ranking_list"."name" IS '前端直接显示的榜单名称，可以改名';
COMMENT ON COLUMN "ranking_list"."publisher_code" IS '发布方编码，例如 douban';
COMMENT ON COLUMN "ranking_list"."list_key" IS '发布方下的稳定榜单标识，例如 movie_top250、book_top250';
COMMENT ON COLUMN "ranking_list"."edition_key" IS '榜单期次，滚动榜显式填 current，年度榜可填 2026';
COMMENT ON COLUMN "ranking_list"."source_url" IS '对应期次的原始榜单页面';
COMMENT ON COLUMN "ranking_list"."last_sync_time" IS '最近一次完整同步成功的时间，尚未同步时为空';
COMMENT ON COLUMN "ranking_list"."create_user" IS '创建操作者 ID，系统任务可留空';
COMMENT ON COLUMN "ranking_list"."create_time" IS '创建时间';
COMMENT ON COLUMN "ranking_list"."update_user" IS '最后更新操作者 ID，系统任务可留空';
COMMENT ON COLUMN "ranking_list"."update_time" IS '最后更新时间';
COMMENT ON COLUMN "ranking_list"."is_deleted" IS '逻辑删除标记，0 有效，1 已删除';

-- 发布方、榜单标识与期次共同标识一份榜单。
CREATE UNIQUE INDEX "uk_ranking_list_edition" ON "ranking_list" ("publisher_code", "list_key", "edition_key");

CREATE TABLE "ranking_list_item" (
  "id" BIGINT NOT NULL,
  "ranking_list_id" BIGINT NOT NULL,
  "creative_work_id" BIGINT NOT NULL,
  "rank" INTEGER,
  "sort_order" INTEGER NOT NULL,
  "create_user" BIGINT,
  "create_time" TIMESTAMP(0) WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
  "update_user" BIGINT,
  "update_time" TIMESTAMP(0) WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
  "is_deleted" SMALLINT DEFAULT 0 NOT NULL,
  CONSTRAINT "ck_ranking_list_item_active" CHECK (("is_deleted" = ANY (ARRAY[0, 1]))),
  CONSTRAINT "ck_ranking_list_item_rank" CHECK (("rank" > 0)),
  CONSTRAINT "ck_ranking_list_item_sort_order" CHECK (("sort_order" >= 0)),
  CONSTRAINT "ranking_list_item_pkey" PRIMARY KEY ("id")
);

COMMENT ON TABLE "ranking_list_item" IS '作品入榜名次';
COMMENT ON COLUMN "ranking_list_item"."id" IS '榜单条目主键，由应用生成雪花 ID';
COMMENT ON COLUMN "ranking_list_item"."ranking_list_id" IS '关联 ranking_list.id';
COMMENT ON COLUMN "ranking_list_item"."creative_work_id" IS '关联入榜作品 creative_work.id';
COMMENT ON COLUMN "ranking_list_item"."rank" IS '正整数名次，允许并列。空值表示只入选';
COMMENT ON COLUMN "ranking_list_item"."sort_order" IS '从 0 开始的来源顺序，用于并列和无名次条目';
COMMENT ON COLUMN "ranking_list_item"."create_user" IS '创建操作者 ID，系统任务可留空';
COMMENT ON COLUMN "ranking_list_item"."create_time" IS '创建时间';
COMMENT ON COLUMN "ranking_list_item"."update_user" IS '最后更新操作者 ID，系统任务可留空';
COMMENT ON COLUMN "ranking_list_item"."update_time" IS '最后更新时间';
COMMENT ON COLUMN "ranking_list_item"."is_deleted" IS '逻辑删除标记，0 有效，1 已掉榜或已移出';

-- 按名次及来源顺序读取榜单的有效条目。
CREATE INDEX "idx_ranking_list_item_order" ON "ranking_list_item" ("ranking_list_id", "rank", "sort_order", "id") WHERE ("is_deleted" = 0);
-- 按作品查询其所在的有效榜单。
CREATE INDEX "idx_ranking_list_item_work" ON "ranking_list_item" ("creative_work_id", "ranking_list_id") WHERE ("is_deleted" = 0);
-- 同一作品在同一榜单内只有一条记录。
CREATE UNIQUE INDEX "uk_ranking_list_item_work" ON "ranking_list_item" ("ranking_list_id", "creative_work_id");

CREATE TABLE "read_record" (
  "id" BIGINT NOT NULL,
  "title" VARCHAR(255) NOT NULL,
  "type" SMALLINT NOT NULL,
  "author" VARCHAR(100),
  "url" VARCHAR(500),
  "status" VARCHAR(32) DEFAULT 'not_started'::character varying NOT NULL,
  "total_progress" INTEGER DEFAULT 0 NOT NULL,
  "current_progress" INTEGER DEFAULT 0 NOT NULL,
  "start_date" DATE,
  "finish_date" DATE,
  "remark" VARCHAR(1000),
  "user_id" BIGINT NOT NULL,
  "create_time" TIMESTAMP(0) WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
  "update_time" TIMESTAMP(0) WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
  "create_user" BIGINT,
  "update_user" BIGINT,
  "is_deleted" SMALLINT DEFAULT '0'::SMALLINT NOT NULL,
  "platform" VARCHAR(32) GENERATED ALWAYS AS (
CASE
    WHEN ("record_douban_subject_id"(("url")::TEXT, 'book'::TEXT) IS NOT NULL) THEN 'douban'::TEXT
    ELSE NULL::TEXT
END) STORED,
  "external_key" VARCHAR(255) GENERATED ALWAYS AS ("record_douban_subject_id"(("url")::TEXT, 'book'::TEXT)) STORED,
  CONSTRAINT "ck_read_record_is_deleted" CHECK (("is_deleted" = ANY (ARRAY[0, 1]))),
  CONSTRAINT "read_record_pkey" PRIMARY KEY ("id")
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
COMMENT ON COLUMN "read_record"."start_date" IS '开始阅读日期';
COMMENT ON COLUMN "read_record"."finish_date" IS '读完日期';
COMMENT ON COLUMN "read_record"."remark" IS '备注/读后感';
COMMENT ON COLUMN "read_record"."user_id" IS '归属用户ID';
COMMENT ON COLUMN "read_record"."create_time" IS '创建时间';
COMMENT ON COLUMN "read_record"."update_time" IS '更新时间';
COMMENT ON COLUMN "read_record"."create_user" IS '创建人';
COMMENT ON COLUMN "read_record"."update_user" IS '更新人';
COMMENT ON COLUMN "read_record"."is_deleted" IS '逻辑删除标记：0=未删除，1=已删除';
COMMENT ON COLUMN "read_record"."platform" IS '外部系统编码，由阅读链接生成，例如 douban';
COMMENT ON COLUMN "read_record"."external_key" IS '外部系统条目 ID，由阅读链接生成';

-- 按用户与外部条目标识检索有效阅读记录。
CREATE INDEX "idx_read_record_user_external" ON "read_record" ("user_id", "platform", "external_key") WHERE (("is_deleted" = 0) AND ("external_key" IS NOT NULL));
CREATE INDEX "read_record_idx_user_id" ON "read_record" ("user_id");
COMMENT ON INDEX "read_record_idx_user_id" IS '业务检索索引：idx_user_id';

-- 消息、通知与反馈

CREATE TABLE "feedback" (
  "id" BIGINT NOT NULL,
  "user_id" BIGINT NOT NULL,
  "title" VARCHAR(200) NOT NULL,
  "content" TEXT NOT NULL,
  "feedback_type" VARCHAR(32) NOT NULL,
  "status" VARCHAR(32) DEFAULT 'PENDING'::character varying NOT NULL,
  "priority" VARCHAR(16) DEFAULT 'MEDIUM'::character varying,
  "is_deleted" SMALLINT DEFAULT '0'::SMALLINT NOT NULL,
  "create_time" TIMESTAMP(0) WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
  "update_time" TIMESTAMP(0) WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
  "create_user" BIGINT,
  "update_user" BIGINT,
  CONSTRAINT "ck_feedback_is_deleted" CHECK (("is_deleted" = ANY (ARRAY[0, 1]))),
  CONSTRAINT "feedback_pkey" PRIMARY KEY ("id")
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
  "id" BIGINT NOT NULL,
  "feedback_id" BIGINT NOT NULL,
  "user_id" BIGINT NOT NULL,
  "role_type" VARCHAR(16) NOT NULL,
  "content" TEXT NOT NULL,
  "is_deleted" SMALLINT DEFAULT '0'::SMALLINT NOT NULL,
  "create_time" TIMESTAMP(0) WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
  "update_time" TIMESTAMP(0) WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
  "create_user" BIGINT,
  "update_user" BIGINT,
  CONSTRAINT "ck_feedback_comment_is_deleted" CHECK (("is_deleted" = ANY (ARRAY[0, 1]))),
  CONSTRAINT "feedback_comment_pkey" PRIMARY KEY ("id")
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

CREATE TABLE "message" (
  "id" BIGINT NOT NULL,
  "sender_id" BIGINT,
  "receiver_id" BIGINT NOT NULL,
  "title" VARCHAR(255) NOT NULL,
  "content" TEXT,
  "type" SMALLINT DEFAULT 0 NOT NULL,
  "is_read" SMALLINT DEFAULT '0'::SMALLINT NOT NULL,
  "create_user" BIGINT,
  "create_time" TIMESTAMP(0) WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
  "update_user" BIGINT,
  "update_time" TIMESTAMP(0) WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
  "is_deleted" SMALLINT DEFAULT '0'::SMALLINT NOT NULL,
  CONSTRAINT "ck_message_is_deleted" CHECK (("is_deleted" = ANY (ARRAY[0, 1]))),
  CONSTRAINT "ck_message_is_read" CHECK (("is_read" = ANY (ARRAY[0, 1]))),
  CONSTRAINT "ck_message_sender" CHECK (((("type" = 0) AND ("sender_id" IS NULL)) OR (("type" = 1) AND ("sender_id" IS NOT NULL)))),
  CONSTRAINT "ck_message_type" CHECK (("type" = ANY (ARRAY[0, 1]))),
  CONSTRAINT "message_pkey" PRIMARY KEY ("id")
);

COMMENT ON TABLE "message" IS '消息表';
COMMENT ON COLUMN "message"."id" IS '主键ID';
COMMENT ON COLUMN "message"."sender_id" IS '发送用户ID；系统通知（type=0）为空，用户消息（type=1）必填';
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
COMMENT ON CONSTRAINT "ck_message_sender" ON "message" IS '系统通知没有发送用户，用户消息必须有发送用户';

CREATE INDEX "message_idx_create_time" ON "message" ("create_time");
COMMENT ON INDEX "message_idx_create_time" IS '业务检索索引：idx_create_time';
CREATE INDEX "message_idx_receiver_is_read" ON "message" ("receiver_id", "is_read");
COMMENT ON INDEX "message_idx_receiver_is_read" IS '业务检索索引：idx_receiver_is_read';

CREATE TABLE "notification_channel_config" (
  "id" BIGINT NOT NULL,
  "user_id" BIGINT NOT NULL,
  "channel" VARCHAR(32) NOT NULL,
  "is_enabled" SMALLINT DEFAULT '0'::SMALLINT NOT NULL,
  "app_id" VARCHAR(128) NOT NULL,
  "app_secret_ciphertext" TEXT NOT NULL,
  "receiver_open_id" VARCHAR(128),
  "receiver_name" VARCHAR(128),
  "create_user" BIGINT,
  "create_time" TIMESTAMP(0) WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
  "update_user" BIGINT,
  "update_time" TIMESTAMP(0) WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
  "is_deleted" SMALLINT DEFAULT '0'::SMALLINT NOT NULL,
  CONSTRAINT "ck_notification_channel_config_is_deleted" CHECK (("is_deleted" = ANY (ARRAY[0, 1]))),
  CONSTRAINT "ck_notification_channel_config_is_enabled" CHECK (("is_enabled" = ANY (ARRAY[0, 1]))),
  CONSTRAINT "notification_channel_config_pkey" PRIMARY KEY ("id")
);

COMMENT ON TABLE "notification_channel_config" IS '用户通知渠道配置';
COMMENT ON COLUMN "notification_channel_config"."id" IS 'ID';
COMMENT ON COLUMN "notification_channel_config"."user_id" IS 'AIO Life 用户 ID';
COMMENT ON COLUMN "notification_channel_config"."channel" IS '通知渠道：FEISHU';
COMMENT ON COLUMN "notification_channel_config"."is_enabled" IS '是否启用：1=启用，0=停用';
COMMENT ON COLUMN "notification_channel_config"."app_id" IS '用户自己的飞书应用 App ID';
COMMENT ON COLUMN "notification_channel_config"."app_secret_ciphertext" IS 'SM4-GCM加密的飞书应用 App Secret';
COMMENT ON COLUMN "notification_channel_config"."receiver_open_id" IS '接收用户在该应用下的 open_id';
COMMENT ON COLUMN "notification_channel_config"."receiver_name" IS '接收用户名称';
COMMENT ON COLUMN "notification_channel_config"."create_user" IS '创建人用户ID';
COMMENT ON COLUMN "notification_channel_config"."create_time" IS '创建时间';
COMMENT ON COLUMN "notification_channel_config"."update_user" IS '最后更新人用户ID';
COMMENT ON COLUMN "notification_channel_config"."update_time" IS '最后更新时间';
COMMENT ON COLUMN "notification_channel_config"."is_deleted" IS '逻辑删除标记：0=未删除，1=已删除';

CREATE UNIQUE INDEX "uk_notification_channel_config_user_channel" ON "notification_channel_config" ("user_id", "channel") WHERE ("is_deleted" = 0);
COMMENT ON INDEX "uk_notification_channel_config_user_channel" IS '同一用户同一通知渠道只能有一条有效配置';

CREATE TABLE "notification_delivery" (
  "id" BIGINT NOT NULL,
  "dedup_key" VARCHAR(128) NOT NULL,
  "user_id" BIGINT NOT NULL,
  "biz_type" VARCHAR(64) NOT NULL,
  "channel" VARCHAR(32) NOT NULL,
  "status" VARCHAR(16) NOT NULL,
  "payload_ciphertext" TEXT NOT NULL,
  "retry_count" INTEGER DEFAULT 0 NOT NULL,
  "next_retry_time" TIMESTAMP(0) WITHOUT TIME ZONE,
  "provider_code" VARCHAR(64),
  "error_message" VARCHAR(512),
  "create_user" BIGINT,
  "create_time" TIMESTAMP(0) WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
  "update_user" BIGINT,
  "update_time" TIMESTAMP(0) WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
  "is_deleted" SMALLINT DEFAULT '0'::SMALLINT NOT NULL,
  CONSTRAINT "ck_notification_delivery_is_deleted" CHECK (("is_deleted" = ANY (ARRAY[0, 1]))),
  CONSTRAINT "notification_delivery_pkey" PRIMARY KEY ("id")
);

COMMENT ON TABLE "notification_delivery" IS '通知投递记录';
COMMENT ON COLUMN "notification_delivery"."id" IS 'ID';
COMMENT ON COLUMN "notification_delivery"."dedup_key" IS '通知事件去重标识，与用户ID和渠道共同确定同一次投递';
COMMENT ON COLUMN "notification_delivery"."user_id" IS '所属用户ID，用于用户数据隔离';
COMMENT ON COLUMN "notification_delivery"."biz_type" IS '通知业务类型：LEETCODE_REMINDER=未打卡提醒，LEETCODE_DAILY=每日一题，FEEDBACK_ADMIN=新反馈，FEEDBACK_REPLY=反馈回复';
COMMENT ON COLUMN "notification_delivery"."channel" IS '投递渠道，当前持久化重试由FEISHU飞书渠道使用';
COMMENT ON COLUMN "notification_delivery"."status" IS 'PENDING/SUCCESS/RETRY/FAILED';
COMMENT ON COLUMN "notification_delivery"."payload_ciphertext" IS '重试所需的SM4-GCM加密消息载荷';
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
CREATE UNIQUE INDEX "uk_notification_delivery_user_channel_dedup" ON "notification_delivery" ("user_id", "channel", "dedup_key") WHERE ("is_deleted" = 0);
COMMENT ON INDEX "uk_notification_delivery_user_channel_dedup" IS '同一用户同一渠道同一去重键只能有一条有效投递记录';

CREATE TABLE "notification_preference" (
  "id" BIGINT NOT NULL,
  "user_id" BIGINT NOT NULL,
  "biz_type" VARCHAR(64) NOT NULL,
  "channel" VARCHAR(32) NOT NULL,
  "is_enabled" SMALLINT DEFAULT '1'::SMALLINT NOT NULL,
  "create_user" BIGINT,
  "create_time" TIMESTAMP(0) WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
  "update_user" BIGINT,
  "update_time" TIMESTAMP(0) WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
  "is_deleted" SMALLINT DEFAULT '0'::SMALLINT NOT NULL,
  CONSTRAINT "ck_notification_preference_is_deleted" CHECK (("is_deleted" = ANY (ARRAY[0, 1]))),
  CONSTRAINT "ck_notification_preference_is_enabled" CHECK (("is_enabled" = ANY (ARRAY[0, 1]))),
  CONSTRAINT "notification_preference_pkey" PRIMARY KEY ("id")
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

CREATE UNIQUE INDEX "uk_notification_preference_user_biz_channel" ON "notification_preference" ("user_id", "biz_type", "channel") WHERE ("is_deleted" = 0);
COMMENT ON INDEX "uk_notification_preference_user_biz_channel" IS '同一用户同一业务类型和渠道只能有一条有效偏好';

-- 数据查询配置与个人数据导出

CREATE TABLE "data_export_audit_event" (
  "id" BIGINT NOT NULL,
  "job_id" BIGINT,
  "user_id" BIGINT,
  "event_type" VARCHAR(128) NOT NULL,
  "result_code" VARCHAR(128) NOT NULL,
  "request_id" VARCHAR(64) NOT NULL,
  "client_ip" VARCHAR(45),
  "trace_id" VARCHAR(64),
  "details" TEXT,
  "event_time" TIMESTAMP(3) WITHOUT TIME ZONE NOT NULL,
  "create_user" BIGINT,
  "create_time" TIMESTAMP(3) WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
  "update_user" BIGINT,
  "update_time" TIMESTAMP(3) WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
  "is_deleted" SMALLINT DEFAULT '0'::SMALLINT NOT NULL,
  CONSTRAINT "ck_data_export_audit_event_is_deleted" CHECK (("is_deleted" = ANY (ARRAY[0, 1]))),
  CONSTRAINT "data_export_audit_event_pkey" PRIMARY KEY ("id")
);

COMMENT ON TABLE "data_export_audit_event" IS '个人数据导出追加审计事件';
COMMENT ON COLUMN "data_export_audit_event"."id" IS '记录ID，由应用生成雪花ID';
COMMENT ON COLUMN "data_export_audit_event"."job_id" IS '关联任务ID，建单前或无有效任务时为空';
COMMENT ON COLUMN "data_export_audit_event"."user_id" IS '所属用户ID，用于个人数据隔离';
COMMENT ON COLUMN "data_export_audit_event"."event_type" IS '审计事件类型';
COMMENT ON COLUMN "data_export_audit_event"."result_code" IS '安全结果码，不含业务内容';
COMMENT ON COLUMN "data_export_audit_event"."request_id" IS '服务端事件请求标识';
COMMENT ON COLUMN "data_export_audit_event"."client_ip" IS '可信代理处理后的客户端来源IP，后台任务为空';
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
  "id" BIGINT NOT NULL,
  "name" VARCHAR(128) NOT NULL,
  "create_user" BIGINT,
  "create_time" TIMESTAMP(3) WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
  "update_user" BIGINT,
  "update_time" TIMESTAMP(3) WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
  "is_deleted" SMALLINT DEFAULT '0'::SMALLINT NOT NULL,
  CONSTRAINT "ck_data_export_coordinator_is_deleted" CHECK (("is_deleted" = ANY (ARRAY[0, 1]))),
  CONSTRAINT "data_export_coordinator_pkey" PRIMARY KEY ("id")
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
  "id" BIGINT NOT NULL,
  "user_id" BIGINT NOT NULL,
  "token_hash" VARCHAR(64) NOT NULL,
  "session_hash" VARCHAR(64) NOT NULL,
  "security_hash" VARCHAR(64) NOT NULL,
  "scope_hash" VARCHAR(64) NOT NULL,
  "purpose" VARCHAR(128) NOT NULL,
  "job_id" BIGINT,
  "expires_at" TIMESTAMP(3) WITHOUT TIME ZONE NOT NULL,
  "consumed" SMALLINT NOT NULL,
  "create_user" BIGINT,
  "create_time" TIMESTAMP(3) WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
  "update_user" BIGINT,
  "update_time" TIMESTAMP(3) WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
  "is_deleted" SMALLINT DEFAULT '0'::SMALLINT NOT NULL,
  CONSTRAINT "ck_data_export_grant_consumed" CHECK (("consumed" = ANY (ARRAY[0, 1]))),
  CONSTRAINT "ck_data_export_grant_is_deleted" CHECK (("is_deleted" = ANY (ARRAY[0, 1]))),
  CONSTRAINT "data_export_grant_pkey" PRIMARY KEY ("id")
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
  "id" BIGINT NOT NULL,
  "user_id" BIGINT NOT NULL,
  "request_key" VARCHAR(64) NOT NULL COLLATE "pg_catalog"."C",
  "request_hash" VARCHAR(64) NOT NULL,
  "scope_json" TEXT NOT NULL,
  "session_hash" VARCHAR(64) NOT NULL,
  "security_hash" VARCHAR(64) NOT NULL,
  "quota_month" VARCHAR(7) NOT NULL,
  "quota_charged" SMALLINT NOT NULL,
  "status" VARCHAR(128) NOT NULL,
  "phase" VARCHAR(128) NOT NULL,
  "attempt" INTEGER NOT NULL,
  "lease_token" VARCHAR(128),
  "lease_until" TIMESTAMP(3) WITHOUT TIME ZONE,
  "deadline" TIMESTAMP(3) WITHOUT TIME ZONE NOT NULL,
  "started_at" TIMESTAMP(3) WITHOUT TIME ZONE,
  "finished_at" TIMESTAMP(3) WITHOUT TIME ZONE,
  "cancel_requested" SMALLINT NOT NULL,
  "error_code" VARCHAR(128),
  "summary_json" JSONB,
  "object_key" VARCHAR(255) COLLATE "pg_catalog"."C",
  "file_name" VARCHAR(128),
  "content_type" VARCHAR(128),
  "sha256" VARCHAR(64),
  "file_bytes" BIGINT,
  "row_count" INTEGER NOT NULL,
  "artifact_status" VARCHAR(128) NOT NULL,
  "expires_at" TIMESTAMP(3) WITHOUT TIME ZONE,
  "download_count" INTEGER NOT NULL,
  "create_user" BIGINT,
  "create_time" TIMESTAMP(3) WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
  "update_user" BIGINT,
  "update_time" TIMESTAMP(3) WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
  "is_deleted" SMALLINT DEFAULT '0'::SMALLINT NOT NULL,
  CONSTRAINT "ck_data_export_job_cancel_requested" CHECK (("cancel_requested" = ANY (ARRAY[0, 1]))),
  CONSTRAINT "ck_data_export_job_is_deleted" CHECK (("is_deleted" = ANY (ARRAY[0, 1]))),
  CONSTRAINT "ck_data_export_job_quota_charged" CHECK (("quota_charged" = ANY (ARRAY[0, 1]))),
  CONSTRAINT "data_export_job_pkey" PRIMARY KEY ("id")
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
CREATE UNIQUE INDEX "data_export_job_uk_data_export_job_request" ON "data_export_job" ("user_id", "request_key") WHERE ("is_deleted" = 0);
COMMENT ON INDEX "data_export_job_uk_data_export_job_request" IS '同一用户有效导出任务的幂等键唯一，已删除任务不占用';

CREATE TABLE "query_schema_relation" (
  "id" BIGINT NOT NULL,
  "source_table" VARCHAR(63) NOT NULL COLLATE "pg_catalog"."C",
  "target_table" VARCHAR(63) NOT NULL COLLATE "pg_catalog"."C",
  "relation_key" VARCHAR(120) NOT NULL COLLATE "pg_catalog"."C",
  "display_name" VARCHAR(120) NOT NULL,
  "relation_kind" VARCHAR(16) NOT NULL COLLATE "pg_catalog"."C",
  "cardinality" VARCHAR(16) NOT NULL COLLATE "pg_catalog"."C",
  "column_mapping" JSONB NOT NULL,
  "conditions" JSONB DEFAULT '[]'::"jsonb" NOT NULL,
  "description" VARCHAR(800) DEFAULT ''::character varying NOT NULL,
  "create_user" BIGINT,
  "create_time" TIMESTAMP(0) WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
  "update_user" BIGINT,
  "update_time" TIMESTAMP(0) WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
  "is_deleted" SMALLINT DEFAULT 0 NOT NULL,
  CONSTRAINT "ck_query_schema_relation_cardinality" CHECK ((("cardinality")::TEXT ~ '^(1|0[.][.]1|N|0[.][.]N):(1|0[.][.]1|N|0[.][.]N)$'::TEXT)),
  CONSTRAINT "ck_query_schema_relation_conditions" CHECK (("jsonb_typeof"("conditions") = 'array'::TEXT)),
  CONSTRAINT "ck_query_schema_relation_is_deleted" CHECK (("is_deleted" = ANY (ARRAY[0, 1]))),
  CONSTRAINT "ck_query_schema_relation_kind" CHECK (("relation_kind" IN ('reference', 'owner', 'polymorphic', 'hierarchy', 'dictionary', 'scoped'))),
  CONSTRAINT "ck_query_schema_relation_mapping" CHECK ((("jsonb_typeof"("column_mapping") = 'array'::TEXT) AND ("column_mapping" <> '[]'::"jsonb"))),
  CONSTRAINT "query_schema_relation_pkey" PRIMARY KEY ("id")
);

COMMENT ON TABLE "query_schema_relation" IS '数据库图谱的逻辑表关系配置';
COMMENT ON COLUMN "query_schema_relation"."id" IS '逻辑关系ID，由应用生成雪花ID';
COMMENT ON COLUMN "query_schema_relation"."source_table" IS 'public 下的来源物理表名';
COMMENT ON COLUMN "query_schema_relation"."target_table" IS 'public 下的目标物理表名';
COMMENT ON COLUMN "query_schema_relation"."relation_key" IS '来源表内稳定的关系标识';
COMMENT ON COLUMN "query_schema_relation"."display_name" IS '页面显示的关系名称';
COMMENT ON COLUMN "query_schema_relation"."relation_kind" IS '关系类型：reference 普通引用，owner 用户归属，polymorphic 多态，hierarchy 树形，dictionary 字典，scoped 按范围匹配';
COMMENT ON COLUMN "query_schema_relation"."cardinality" IS '来源到目标基数，格式为来源:目标，例如 N:0..1';
COMMENT ON COLUMN "query_schema_relation"."column_mapping" IS '等值字段映射数组，每项含 sourceColumn 和 targetColumn';
COMMENT ON COLUMN "query_schema_relation"."conditions" IS '匹配条件数组，每项含 side、column、operator 及可选 value，条件之间为且';
COMMENT ON COLUMN "query_schema_relation"."description" IS '关系含义及补充业务规则';
COMMENT ON COLUMN "query_schema_relation"."create_user" IS '创建人用户ID，系统初始关系为空';
COMMENT ON COLUMN "query_schema_relation"."create_time" IS '创建时间';
COMMENT ON COLUMN "query_schema_relation"."update_user" IS '最后更新人用户ID，系统初始关系为空';
COMMENT ON COLUMN "query_schema_relation"."update_time" IS '最后更新时间';
COMMENT ON COLUMN "query_schema_relation"."is_deleted" IS '逻辑删除标记：0 有效，1 删除';

-- 同一来源表的有效关系键唯一。
CREATE UNIQUE INDEX "uk_query_schema_relation_source_key" ON "query_schema_relation" ("source_table", "relation_key") WHERE ("is_deleted" = 0);
