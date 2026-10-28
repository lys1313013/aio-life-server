-- =====================================================
-- AIO Life 数据库初始化脚本（全量建表）
-- 创建时间: 2026-08-18
-- 说明: Flyway 空库基线 20261028.00；后续 V 迁移共同定义当前结构，不代表已合并全部增量。
-- 已包含隐私字段和空值/注释变更；已有未知结构库接入见 sql/legacy/ 和 docs/数据库初始化与验证.md。
-- 前置：空库，MySQL 8.0.16+；目标库由 Flyway 连接指定。
-- 影响：创建业务表与必要协调记录，不插入用户账号；执行后由 Flyway 记录校验和。
-- 重跑：由 Flyway 历史决定执行；已有库不得用本文件代替增量升级。
-- 统一约定: InnoDB + utf8mb4 + utf8mb4_unicode_ci
-- =====================================================



-- =====================================================
-- 用户与认证
-- =====================================================

CREATE TABLE IF NOT EXISTS `user` (
    `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '用户ID',
    `username` varchar(50) NOT NULL COMMENT '用户名',
    `password` varchar(255) DEFAULT NULL COMMENT '密码，NULL表示未设置',
    `nickname` varchar(50) NOT NULL COMMENT '昵称',
    `policy_version` varchar(32) DEFAULT NULL COMMENT '注册时明确同意的隐私政策与服务条款版本',
    `policy_accepted_at` datetime DEFAULT NULL COMMENT '注册协议同意时间，上海时区',
    `erasure_requested_at` datetime DEFAULT NULL COMMENT '自助注销受理时间，非空表示等待不可撤销的数据清理',
    `erasure_retry_at` datetime DEFAULT NULL COMMENT '注销后台清理下次重试时间，上海时区',
    -- 后台只扫描已停用且到期的注销任务，避免遍历全部账号。
    KEY `idx_user_erasure_retry` (`is_deleted`, `erasure_retry_at`),
    `create_user` bigint(20) DEFAULT NULL COMMENT '创建人',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_user` bigint(20) DEFAULT NULL COMMENT '更新人',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `last_active_at` timestamp NULL DEFAULT NULL COMMENT '最后活跃时间',
    `email` varchar(50) DEFAULT NULL COMMENT '邮箱',
    `avatar_file_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '头像文件ID',
    KEY `idx_user_avatar_file` (`avatar_file_id`),
    `password_salt` varchar(32) DEFAULT NULL COMMENT '密码盐值',
    `role` varchar(50) DEFAULT 'user' COMMENT '角色类型',
    `introduction` varchar(255) DEFAULT NULL COMMENT '个人简介',
    `is_deleted` tinyint(4) NOT NULL DEFAULT '0' COMMENT '逻辑删除标记：0=未删除，1=已删除',
    `secondary_password` varchar(128) DEFAULT NULL COMMENT '二级密码（加盐哈希）',
    `secondary_password_salt` varchar(64) DEFAULT NULL COMMENT '二级密码盐值',
    `phone_country_code` varchar(5) DEFAULT NULL COMMENT '国际电话区号，不含加号',
    `phone` varchar(20) DEFAULT NULL COMMENT '不含国际区号的手机号',
    `phone_verified_at` datetime DEFAULT NULL COMMENT '最近接收可信手机号验证结果的时间',
    `wechat_openid` varchar(128) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '当前配置小程序的openid',
    `wechat_unionid` varchar(128) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '微信unionid，可为空，不用于自动合并账号',
    `active_flag` tinyint GENERATED ALWAYS AS (CASE WHEN `is_deleted` = 0 THEN 1 ELSE NULL END) STORED COMMENT '仅有效用户参与凭证唯一约束',
    UNIQUE KEY `uk_user_phone_active` (`phone_country_code`, `phone`, `active_flag`),
    UNIQUE KEY `uk_user_wechat_active` (`wechat_openid`, `active_flag`),
    CONSTRAINT `ck_user_phone_pair` CHECK (
        (`phone_country_code` IS NULL AND `phone` IS NULL) OR
        (`phone_country_code` IS NOT NULL AND `phone` IS NOT NULL
         AND `phone_country_code` REGEXP '^[1-9][0-9]{0,2}$'
         AND `phone` REGEXP '^[0-9]{4,14}$'
         AND CHAR_LENGTH(`phone_country_code`) + CHAR_LENGTH(`phone`) <= 15)
    ),
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

CREATE TABLE IF NOT EXISTS `user_bind` (
    `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id` bigint(20) NOT NULL COMMENT '本系统用户ID',
    `platform` varchar(32) NOT NULL COMMENT '平台类型：github, leetcode, csdn, shanbay, douban, weread',
    `platform_username` varchar(128) DEFAULT NULL COMMENT '第三方平台的用户名/账号',
    `access_token` text COMMENT '访问令牌',
    `meta_fields` json DEFAULT NULL COMMENT '额外配置(JSON)',
    `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '绑定时间',
    `create_user` bigint(20) DEFAULT NULL COMMENT '创建人',
    `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_user` bigint(20) DEFAULT NULL COMMENT '更新人',
    `is_deleted` tinyint(4) NOT NULL DEFAULT '0' COMMENT '是否删除',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户第三方账号绑定表';

CREATE TABLE IF NOT EXISTS `password_vault` (
    `id` bigint NOT NULL COMMENT '记录ID',
    `user_id` bigint NOT NULL COMMENT '所属用户ID',
    `title` varchar(100) NOT NULL COMMENT '标题，如 GitHub',
    `website` varchar(255) DEFAULT NULL COMMENT '网站/应用名',
    `category` varchar(50) DEFAULT '其他' COMMENT '分类：工作/生活/金融/社交/其他',
    `username` text COMMENT '账号（明文存储，便于展示）',
    `password` text COMMENT '密码密文（前端SM4-GCM加密，密钥由用户主密码PBKDF2派生，后端仅存密文）',
    `salt` varchar(64) NOT NULL COMMENT 'PBKDF2盐值，每条记录唯一（前端生成）',
    `remark` text COMMENT '备注密文（前端SM4-GCM加密，同password）',
    `favorite` boolean DEFAULT FALSE COMMENT '是否收藏',
    `is_deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记：0=未删除，1=已删除',
    `create_time` datetime DEFAULT NULL COMMENT '创建时间',
    `update_time` datetime DEFAULT NULL COMMENT '更新时间',
    `create_user` bigint DEFAULT NULL COMMENT '创建人ID',
    `update_user` bigint DEFAULT NULL COMMENT '更新人ID',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='密码库表（加解密在前端完成，后端仅透传存储密文）';

CREATE TABLE IF NOT EXISTS `user_secondary_lock_menu` (
    `id` bigint NOT NULL COMMENT '主键（雪花）',
    `user_id` bigint NOT NULL COMMENT '用户ID',
    `menu_id` bigint NOT NULL COMMENT '菜单ID',
    `is_deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记：0=未删除，1=已删除',
    `create_time` datetime DEFAULT NULL COMMENT '创建时间',
    `update_time` datetime DEFAULT NULL COMMENT '更新时间',
    `create_user` bigint DEFAULT NULL COMMENT '创建人ID',
    `update_user` bigint DEFAULT NULL COMMENT '更新人ID',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户二级锁菜单关联';

-- 个人菜单显示偏好：存在记录即隐藏，不影响角色权限和业务数据。
-- 保留统一 BaseEntity 字段；恢复显示/默认设置使用物理 DELETE。
CREATE TABLE IF NOT EXISTS `user_menu_hidden` (
    `id` bigint NOT NULL COMMENT '主键（雪花）',
    `user_id` bigint NOT NULL COMMENT '用户ID',
    `menu_id` bigint NOT NULL COMMENT '关联 sys_menu.id',
    `create_user` bigint DEFAULT NULL COMMENT '创建人ID',
    `create_time` datetime DEFAULT NULL COMMENT '创建时间',
    `update_user` bigint DEFAULT NULL COMMENT '更新人ID',
    `update_time` datetime DEFAULT NULL COMMENT '更新时间',
    `is_deleted` int NOT NULL DEFAULT 0 COMMENT '统一保留字段，固定为0，删除使用物理删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_menu` (`user_id`, `menu_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户隐藏菜单';

CREATE TABLE IF NOT EXISTS `user_quick_nav` (
    `id` bigint NOT NULL COMMENT '主键ID',
    `user_id` bigint NOT NULL COMMENT '用户ID',
    `menu_id` bigint NOT NULL COMMENT '关联 sys_menu.id',
    `sort_order` int NOT NULL DEFAULT 0 COMMENT '排序号（越小越前）',
    `enabled` tinyint NOT NULL DEFAULT 1 COMMENT '是否启用（1启用 / 0隐藏）',
    `create_user` bigint DEFAULT NULL COMMENT '创建人',
    `create_time` datetime DEFAULT NULL COMMENT '创建时间',
    `update_user` bigint DEFAULT NULL COMMENT '更新人',
    `update_time` datetime DEFAULT NULL COMMENT '更新时间',
    `is_deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记：0=未删除，1=已删除',
    PRIMARY KEY (`id`),
    KEY `idx_user_sort` (`user_id`, `sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户首页快捷导航布局';

CREATE TABLE IF NOT EXISTS `api_key` (
    `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `user_id` bigint(20) NOT NULL COMMENT '用户ID',
    `api_key` varchar(128) NOT NULL COMMENT 'API Key',
    `remark` varchar(255) DEFAULT NULL COMMENT '备注',
    `expired_at` datetime DEFAULT NULL COMMENT '过期时间',
    `create_user` bigint(20) DEFAULT NULL COMMENT '创建人',
    `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_user` bigint(20) DEFAULT NULL COMMENT '更新人',
    `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `is_deleted` tinyint(4) NOT NULL DEFAULT '0' COMMENT '是否删除',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='API Key 表';

CREATE TABLE IF NOT EXISTS `api_key_log` (
    `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `api_key_id` bigint(20) NOT NULL COMMENT 'API Key ID',
    `request_path` varchar(255) NOT NULL COMMENT '请求路径',
    `request_method` varchar(10) NOT NULL COMMENT '请求方法',
    `response_status` int(11) DEFAULT NULL COMMENT '响应状态码',
    `client_ip` varchar(45) DEFAULT NULL COMMENT '客户端IP',
    `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '记录时间',
    PRIMARY KEY (`id`),
    KEY `idx_api_key_id` (`api_key_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='API Key 调用日志表';

CREATE TABLE IF NOT EXISTS `login_log` (
    `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '登录日志ID',
    `user_id` bigint(20) DEFAULT NULL COMMENT '用户ID',
    `username` varchar(50) NOT NULL COMMENT '用户名',
    `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `ip_address` varchar(45) NOT NULL COMMENT 'IP地址',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='登录日志';

CREATE TABLE IF NOT EXISTS `mail_log` (
    `id` bigint(20) NOT NULL COMMENT '主键ID',
    `send_to` varchar(100) NOT NULL COMMENT '接收者邮箱',
    `subject` varchar(255) DEFAULT NULL COMMENT '邮件标题',
    `biz_type` varchar(50) DEFAULT NULL COMMENT '业务类型：register-注册, login-登录, reset_pwd-重置密码, system_notice-系统通知等',
    `status` tinyint(1) NOT NULL DEFAULT '1' COMMENT '发送状态：1-成功，0-失败',
    `error_msg` varchar(255) DEFAULT NULL COMMENT '失败原因',
    `ip_address` varchar(45) DEFAULT NULL COMMENT '请求IP',
    `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_send_to_biz_type` (`send_to`, `biz_type`),
    KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='邮件发送记录表';

-- =====================================================
-- 系统管理
-- =====================================================

CREATE TABLE IF NOT EXISTS `sys_work_calendar` (
    `calendar_date` date NOT NULL COMMENT '日历日期（中国大陆，按北京时间）',
    `day_type` tinyint NOT NULL COMMENT '日期类型：0普通工作日，1普通周末，2节假日休息，3调休补班',
    `holiday_name` varchar(50) DEFAULT NULL COMMENT '关联节日名称，如春节、国庆节；普通日期为空',
    `source` varchar(50) NOT NULL COMMENT '数据来源，如 weekday_rule、holiday-cn、manual',
    `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    PRIMARY KEY (`calendar_date`),
    CONSTRAINT `chk_sys_work_calendar_day_type` CHECK (`day_type` IN (0, 1, 2, 3))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='中国大陆工作日历表';

CREATE TABLE IF NOT EXISTS `sys_activity_log` (
    `id` bigint NOT NULL COMMENT '主键ID',
    `log_type` varchar(16) NOT NULL COMMENT 'OPERATION 操作 / ACCESS 访问',
    `user_id` bigint DEFAULT NULL COMMENT '操作用户ID',
    `username` varchar(100) NOT NULL DEFAULT '' COMMENT '用户名',
    `nickname` varchar(100) NOT NULL DEFAULT '' COMMENT '昵称',
    `ip_address` varchar(64) NOT NULL DEFAULT '' COMMENT 'IP 地址',
    `browser` varchar(100) NOT NULL DEFAULT '' COMMENT '浏览器',
    `function_name` varchar(200) DEFAULT NULL COMMENT '功能名称',
    `function_item` varchar(100) DEFAULT NULL COMMENT '功能项',
    `access_type` varchar(32) DEFAULT NULL COMMENT '访问类型',
    `request_path` varchar(255) DEFAULT NULL COMMENT '接口路由模板，不含查询参数',
    `request_method` varchar(16) DEFAULT NULL COMMENT 'HTTP方法，未采集时为NULL',
    `request_params` json DEFAULT NULL COMMENT '白名单脱敏参数，应用限制32KiB',
    `request_time` datetime(3) DEFAULT NULL COMMENT '服务端请求接收时间，Asia/Shanghai',
    `duration_ms` int DEFAULT NULL COMMENT '处理耗时毫秒，非负，0有效，NULL未采集',
    `server_ip` varchar(64) DEFAULT NULL COMMENT '处理请求的后端实例IPv4或IPv6',
    `success` tinyint(1) NOT NULL DEFAULT 1 COMMENT '是否成功',
    `create_user` bigint DEFAULT NULL COMMENT '创建人',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_user` bigint DEFAULT NULL COMMENT '更新人',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `is_deleted` tinyint NOT NULL DEFAULT 0 COMMENT '是否删除(0-否,1-是)',
    PRIMARY KEY (`id`),
    KEY `idx_activity_type_time` (`log_type`, `is_deleted`, `create_time`, `id`),
    KEY `idx_activity_type_id` (`log_type`, `is_deleted`, `id`),
    KEY `idx_activity_type_req_time` (`log_type`, `is_deleted`, `request_time`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='操作和访问日志';

CREATE TABLE IF NOT EXISTS `sys_menu` (
    `id` bigint NOT NULL COMMENT '主键ID',
    `parent_id` bigint DEFAULT 0 COMMENT '父级ID（0为根）',
    `name` varchar(64) NOT NULL COMMENT '路由名称',
    `path` varchar(255) NOT NULL COMMENT '路由路径（唯一）',
    `component` varchar(255) DEFAULT NULL COMMENT '组件标识（BasicLayout/IFrameView 或 views 相对路径，如 system/user/index）',
    `redirect` varchar(255) DEFAULT NULL COMMENT '重定向',
    `icon_color` varchar(7) DEFAULT NULL COMMENT '菜单图标颜色（#RRGGBB，空为默认）',
    `meta` json DEFAULT NULL COMMENT '路由 meta（title/icon/order/keepAlive/hideInMenu/link等）',
    `roles` varchar(255) DEFAULT NULL COMMENT '可访问角色（逗号分隔，空表示所有）',
    `sort` int NOT NULL DEFAULT 0 COMMENT '排序',
    `status` tinyint NOT NULL DEFAULT 1 COMMENT 'Web端状态（1启用，0禁用）',
    `mobile_status` tinyint NOT NULL DEFAULT 1 COMMENT '移动端状态（1启用，0禁用）',
    `create_user` bigint DEFAULT NULL COMMENT '创建人',
    `create_time` datetime DEFAULT NULL COMMENT '创建时间',
    `update_user` bigint DEFAULT NULL COMMENT '更新人',
    `update_time` datetime DEFAULT NULL COMMENT '更新时间',
    `is_deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记：0=未删除，1=已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_sys_menu_path` (`path`),
    KEY `idx_sys_menu_parent_id` (`parent_id`),
    KEY `idx_sys_menu_sort` (`sort`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统菜单表';

CREATE TABLE IF NOT EXISTS `sys_dict_type` (
    `dict_id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '字典主键',
    `dict_name` varchar(100) DEFAULT '' COMMENT '字典名称',
    `dict_type` varchar(100) DEFAULT '' COMMENT '字典类型',
    `status` char(1) DEFAULT '0' COMMENT '状态（0正常 1停用）',
    `create_user` bigint(20) DEFAULT NULL COMMENT '创建人',
    `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_user` bigint(20) DEFAULT NULL COMMENT '更新人',
    `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `remark` varchar(500) DEFAULT NULL COMMENT '备注',
    `is_deleted` tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否删除: 0-未删除, 1-已删除',
    PRIMARY KEY (`dict_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='字典类型表';

CREATE TABLE IF NOT EXISTS `sys_dict_data` (
    `dict_code` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '字典编码',
    `dict_id` bigint(20) NOT NULL COMMENT '所属字典类型ID，关联sys_dict_type.dict_id',
    `dict_sort` int(4) DEFAULT '0' COMMENT '字典排序',
    `dict_label` varchar(100) DEFAULT '' COMMENT '字典标签',
    `dict_value` varchar(100) DEFAULT '' COMMENT '字典键值',
    `dict_type` varchar(100) DEFAULT '' COMMENT '字典类型',
    `css_class` varchar(100) DEFAULT NULL COMMENT '样式属性（其他样式扩展）',
    `list_class` varchar(100) DEFAULT NULL COMMENT '表格回显样式',
    `is_default` char(1) DEFAULT 'N' COMMENT '是否默认（Y是 N否）',
    `status` char(1) DEFAULT '0' COMMENT '状态（0正常 1停用）',
    `create_user` bigint(20) DEFAULT NULL COMMENT '创建人',
    `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_user` bigint(20) DEFAULT NULL COMMENT '更新人',
    `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `remark` varchar(500) DEFAULT NULL COMMENT '备注',
    `is_deleted` tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否删除: 0-未删除, 1-已删除',
    PRIMARY KEY (`dict_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='字典数据表';

CREATE TABLE IF NOT EXISTS `user_dict_data` (
    `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '字典主键',
    `user_id` bigint(20) NOT NULL COMMENT '所属用户ID',
    `template_id` bigint(20) DEFAULT NULL COMMENT '模板ID，指向被覆盖的公共字典ID',
    `dict_type` varchar(100) DEFAULT '' COMMENT '字典类型',
    `dict_sort` int(4) DEFAULT '0' COMMENT '字典排序',
    `dict_label` varchar(100) DEFAULT '' COMMENT '字典标签(分类名称)',
    `dict_value` varchar(100) DEFAULT '' COMMENT '字典键值(分类标识)',
    `color` varchar(20) DEFAULT NULL COMMENT '颜色值(Hex)',
    `icon` varchar(100) DEFAULT NULL COMMENT '图标名称(Iconify格式)',
    `ext_data` json DEFAULT NULL COMMENT '特定分类所需的额外扩展字段(JSON)',
    `is_default` char(1) DEFAULT 'N' COMMENT '是否默认（Y是 N否）',
    `status` char(1) DEFAULT '0' COMMENT '状态（0正常 1停用）',
    `is_readonly` char(1) DEFAULT 'N' COMMENT '是否只读（Y是 N否），当为Y时用户无法修改除状态外的其他属性',
    `create_user` bigint(20) DEFAULT NULL COMMENT '创建人ID',
    `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_user` bigint(20) DEFAULT NULL COMMENT '更新人ID',
    `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `remark` varchar(500) DEFAULT NULL COMMENT '备注',
    `is_deleted` tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否删除 0-未删除 1-已删除',
    PRIMARY KEY (`id`),
    KEY `idx_user_dict_type` (`user_id`, `dict_type`),
    KEY `idx_user_dict_template` (`template_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户字典数据表';

CREATE TABLE IF NOT EXISTS `user_dict_type` (
    `id` bigint(20) NOT NULL COMMENT '主键ID',
    `user_id` bigint(20) NOT NULL COMMENT '所属用户ID',
    `sys_dict_id` bigint(20) DEFAULT NULL COMMENT '关联的系统字典类型ID',
    `dict_name` varchar(100) DEFAULT NULL COMMENT '字典名称',
    `dict_type` varchar(100) DEFAULT NULL COMMENT '字典类型',
    `icon` varchar(100) DEFAULT NULL COMMENT '图标',
    `color` varchar(20) DEFAULT NULL COMMENT '颜色',
    `status` char(1) DEFAULT '0' COMMENT '状态（0正常 1停用）',
    `create_user` bigint(20) DEFAULT NULL COMMENT '创建人',
    `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_user` bigint(20) DEFAULT NULL COMMENT '更新人',
    `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `remark` varchar(500) DEFAULT NULL COMMENT '备注',
    `is_deleted` tinyint(4) NOT NULL DEFAULT '0' COMMENT '是否删除',
    PRIMARY KEY (`id`),
    KEY `idx_user_dict_type_user` (`user_id`, `dict_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户字典类型表';

CREATE TABLE IF NOT EXISTS `enum_type` (
    `type_id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '类型ID',
    `type_name` varchar(50) NOT NULL COMMENT '类型名称（英文唯一标识）',
    `description` varchar(255) DEFAULT NULL COMMENT '类型描述',
    `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
    PRIMARY KEY (`type_id`),
    UNIQUE KEY `type_name` (`type_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='枚举类型表';

CREATE TABLE IF NOT EXISTS `system_config` (
    `id` bigint NOT NULL COMMENT '主键ID（雪花）',
    `config_key` varchar(128) NOT NULL COMMENT '配置键（唯一）',
    `config_value` text COMMENT '配置值（JSON 或纯文本）',
    `config_type` varchar(32) DEFAULT 'STRING' COMMENT '类型：STRING/JSON/BOOLEAN/NUMBER（前端渲染用）',
    `description` varchar(512) DEFAULT NULL COMMENT '配置说明',
    `is_deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记：0=未删除，1=已删除',
    `create_time` datetime DEFAULT NULL COMMENT '创建时间',
    `update_time` datetime DEFAULT NULL COMMENT '更新时间',
    `create_user` bigint DEFAULT NULL COMMENT '创建人ID',
    `update_user` bigint DEFAULT NULL COMMENT '更新人ID',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统配置表';

CREATE TABLE IF NOT EXISTS `feedback` (
    `id` bigint NOT NULL COMMENT '反馈ID（雪花）',
    `user_id` bigint NOT NULL COMMENT '反馈人ID',
    `title` varchar(200) NOT NULL COMMENT '标题',
    `content` text NOT NULL COMMENT '内容（Markdown）',
    `feedback_type` varchar(32) NOT NULL COMMENT '类型：BUG/SUGGESTION/QUESTION/OTHER',
    `status` varchar(32) NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING/PROCESSING/RESOLVED/CLOSED/REJECTED',
    `priority` varchar(16) DEFAULT 'MEDIUM' COMMENT '优先级：LOW/MEDIUM/HIGH（预留）',
    `is_deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记：0=未删除，1=已删除',
    `create_time` datetime DEFAULT NULL COMMENT '创建时间',
    `update_time` datetime DEFAULT NULL COMMENT '更新时间',
    `create_user` bigint DEFAULT NULL COMMENT '创建人ID',
    `update_user` bigint DEFAULT NULL COMMENT '更新人ID',
    PRIMARY KEY (`id`),
    KEY `idx_user_id_create_time` (`user_id`, `create_time`),
    KEY `idx_status_create_time` (`status`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户反馈表';

CREATE TABLE IF NOT EXISTS `feedback_comment` (
    `id` bigint NOT NULL COMMENT '评论ID（雪花）',
    `feedback_id` bigint NOT NULL COMMENT '所属反馈ID',
    `user_id` bigint NOT NULL COMMENT '评论人ID',
    `role_type` varchar(16) NOT NULL COMMENT '角色：USER/ADMIN',
    `content` text NOT NULL COMMENT '评论内容（Markdown）',
    `is_deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记：0=未删除，1=已删除',
    `create_time` datetime DEFAULT NULL COMMENT '创建时间',
    `update_time` datetime DEFAULT NULL COMMENT '更新时间',
    `create_user` bigint DEFAULT NULL COMMENT '创建人ID',
    `update_user` bigint DEFAULT NULL COMMENT '更新人ID',
    PRIMARY KEY (`id`),
    KEY `idx_feedback_id_create_time` (`feedback_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='反馈评论表';

-- =====================================================
-- 记录模块（record）—— 时间追踪
-- =====================================================

CREATE TABLE IF NOT EXISTS `time_tracker_category` (
    `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `user_id` bigint(20) NOT NULL COMMENT '用户ID',
    `code` varchar(50) DEFAULT NULL COMMENT '分类标识(如: rest, work)',
    `name` varchar(50) DEFAULT NULL COMMENT '分类名称',
    `color` varchar(20) DEFAULT NULL COMMENT '颜色值(Hex)',
    `description` varchar(255) DEFAULT NULL COMMENT '描述',
    `is_track_time` tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否记录时间',
    `sort` int(11) NOT NULL DEFAULT '0' COMMENT '排序权重',
    `create_user` bigint(20) DEFAULT NULL COMMENT '创建人',
    `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_user` bigint(20) DEFAULT NULL COMMENT '更新人',
    `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `is_deleted` int(11) NOT NULL DEFAULT '0' COMMENT '是否删除',
    `is_enabled` tinyint NOT NULL DEFAULT 1 COMMENT '是否启用：1-启用，0-禁用',
    `template_id` bigint DEFAULT NULL COMMENT '模板ID，指向被覆盖的公共分类ID',
    `parent_id` bigint DEFAULT NULL COMMENT '上级分类ID：0为一级；覆盖记录NULL表示继承公共分类',
    `icon` varchar(100) DEFAULT NULL COMMENT '图标名称(Iconify格式)',
    `time_type` tinyint NOT NULL DEFAULT 1 COMMENT '时间类型: 1-必须, 2-积极, 3-休闲',
    PRIMARY KEY (`id`),
    KEY `idx_user_code` (`user_id`, `code`),
    KEY `idx_category_user_parent` (`user_id`, `parent_id`),
    KEY `idx_category_user_template` (`user_id`, `template_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='时间追踪-分类配置表';

CREATE TABLE IF NOT EXISTS `time_record` (
    `id` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '记录ID',
    `user_id` bigint(20) NOT NULL COMMENT '用户ID',
    `create_user` bigint(20) DEFAULT NULL COMMENT '创建人',
    `category_id` bigint DEFAULT NULL COMMENT '分类ID(关联time_tracker_category.id)',
    `date` date NOT NULL COMMENT '日期（YYYY-MM-DD格式）',
    `start_time` smallint(5) unsigned NOT NULL COMMENT '开始时间（分钟，0-1440）',
    `end_time` smallint(5) unsigned NOT NULL COMMENT '结束时间（分钟，0-1440）',
    `title` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '标题',
    `description` text COLLATE utf8mb4_unicode_ci COMMENT '详细描述',
    `duration` smallint(5) unsigned NOT NULL COMMENT '时长（分钟）',
    `is_manual` tinyint(4) NOT NULL DEFAULT '0' COMMENT '是否手动创建：0-系统，1-手动',
    `is_deleted` tinyint(4) NOT NULL DEFAULT '0' COMMENT '逻辑删除标记：0=未删除，1=已删除',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_user` bigint(20) DEFAULT NULL COMMENT '更新人',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `relate_id` bigint DEFAULT NULL COMMENT '关联业务ID（阅读记录/观影记录等的主键ID）',
    `relate_type` tinyint DEFAULT NULL COMMENT '关联业务类型：1-阅读，2-观影',
    PRIMARY KEY (`id`),
    KEY `idx_time_record_date` (`date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='时间段记录表';

-- =====================================================
-- 记录模块（record）—— 任务（待办）
-- =====================================================

CREATE TABLE IF NOT EXISTS `task` (
    `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `user_id` bigint(20) NOT NULL COMMENT '用户ID',
    `content` varchar(100) DEFAULT NULL COMMENT '任务标题，未填写时为空',
    `detail` varchar(1000) DEFAULT NULL COMMENT '任务详细说明，未填写时为空',
    `column_id` bigint DEFAULT NULL COMMENT '所属看板列ID，关联task_column.id；未分配时为空',
    `due_date` datetime DEFAULT NULL COMMENT '最后时间',
    `sort_order` int(11) DEFAULT NULL COMMENT '排序字段',
    `is_deleted` int(11) NOT NULL DEFAULT '0' COMMENT '逻辑删除标记：0=未删除，1=已删除',
    `create_user` bigint(20) DEFAULT NULL COMMENT '创建人',
    `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_user` bigint(20) DEFAULT NULL COMMENT '更新人',
    `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='任务表';

CREATE TABLE IF NOT EXISTS `task_column` (
    `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '列ID',
    `user_id` bigint(20) NOT NULL COMMENT '用户ID',
    `title` varchar(255) NOT NULL COMMENT '列标题',
    `sort_order` int(11) NOT NULL COMMENT '排序',
    `bg_color` varchar(10) DEFAULT NULL COMMENT '看板列背景颜色，未设置时为空',
    `is_deleted` tinyint(4) NOT NULL DEFAULT '0' COMMENT '是否删除(0-未删除,1-已删除)',
    `create_user` bigint(20) DEFAULT NULL COMMENT '创建人',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_user` bigint(20) DEFAULT NULL COMMENT '更新人',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='任务列表';

CREATE TABLE IF NOT EXISTS `task_detail` (
    `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `task_id` bigint(20) NOT NULL COMMENT '关联任务ID',
    `content` varchar(255) NOT NULL COMMENT '明细任务内容',
    `is_completed` tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否完成 0-未完成 1-已完成',
    `sort` int(11) NOT NULL DEFAULT '0' COMMENT '排序权重',
    `priority` int(11) DEFAULT '20' COMMENT '优先级: 1-高, 10-中, 20-低',
    `user_id` bigint(20) NOT NULL COMMENT '所属用户ID',
    `create_user` bigint(20) DEFAULT NULL COMMENT '创建人',
    `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_user` bigint(20) DEFAULT NULL COMMENT '更新人',
    `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `is_deleted` tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否删除 0-未删除 1-已删除',
    `is_starred` int(1) NOT NULL DEFAULT 0 COMMENT '是否关注: 0-未关注, 1-已关注',
    `start_time` datetime DEFAULT NULL COMMENT '开始时间',
    `end_time` datetime DEFAULT NULL COMMENT '结束时间',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='任务明细表';

-- =====================================================
-- 记录模块（record）—— 目标 / 荣誉 / 纪念日 / 里程碑
-- =====================================================

CREATE TABLE IF NOT EXISTS `goal` (
    `id` bigint NOT NULL COMMENT '目标ID',
    `user_id` bigint NOT NULL COMMENT '所属用户ID，用于用户数据隔离',
    `type` tinyint DEFAULT NULL COMMENT '目标类型：1=年度目标，2=月度目标，3=日目标',
    `title` varchar(255) DEFAULT NULL COMMENT '目标标题',
    `description` text COMMENT '目标描述',
    `content` text COMMENT '目标详细内容/行动计划',
    `status` varchar(32) DEFAULT 'not_started' COMMENT '目标状态：not_started/in_progress/completed/on_hold',
    `target_value` int DEFAULT NULL COMMENT '目标值',
    `current_value` int DEFAULT 0 COMMENT '当前值',
    `year` int DEFAULT NULL COMMENT '年份（用于年度目标筛选）',
    `month` int DEFAULT NULL COMMENT '月份（用于月度目标筛选）',
    `day` int DEFAULT NULL COMMENT '日期（用于日目标筛选）',
    `parent_id` bigint DEFAULT NULL COMMENT '父目标ID',
    `start_date` datetime DEFAULT NULL COMMENT '开始时间',
    `end_date` datetime DEFAULT NULL COMMENT '结束时间',
    `completed_at` datetime DEFAULT NULL COMMENT '完成时间',
    `tags` varchar(1000) DEFAULT NULL COMMENT '目标标签（JSON格式存储）',
    `is_pinned` tinyint NOT NULL DEFAULT 0 COMMENT '是否固定到首页：0-否，1-是',
    `pinned_sort` int NOT NULL DEFAULT 0 COMMENT '首页固定排序，数值越小越靠前',
    `is_deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记：0=未删除，1=已删除',
    `create_time` datetime DEFAULT NULL COMMENT '创建时间',
    `update_time` datetime DEFAULT NULL COMMENT '更新时间',
    `create_user` bigint DEFAULT NULL COMMENT '创建人ID',
    `update_user` bigint DEFAULT NULL COMMENT '更新人ID',
    PRIMARY KEY (`id`),
    KEY `idx_home_pinned` (`user_id`, `is_deleted`, `is_pinned`, `pinned_sort`, `id`),
    KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='目标管理表';

CREATE TABLE IF NOT EXISTS `honor_category` (
    `id` bigint NOT NULL COMMENT '分类ID',
    `user_id` bigint DEFAULT NULL COMMENT '用户ID（NULL表示系统预设分类）',
    `name` varchar(50) NOT NULL COMMENT '分类名称',
    `icon` varchar(255) DEFAULT NULL COMMENT '分类图标',
    `color` varchar(20) DEFAULT NULL COMMENT '分类颜色',
    `sort_order` int DEFAULT 0 COMMENT '排序顺序',
    `is_deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记：0=未删除，1=已删除',
    `create_user` bigint DEFAULT NULL COMMENT '创建人ID',
    `create_time` datetime DEFAULT NULL COMMENT '创建时间',
    `update_user` bigint DEFAULT NULL COMMENT '更新人ID',
    `update_time` datetime DEFAULT NULL COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='荣誉分类表';

CREATE TABLE IF NOT EXISTS `honor_record` (
    `id` bigint NOT NULL COMMENT '记录ID',
    `user_id` bigint NOT NULL COMMENT '用户ID',
    `title` varchar(200) NOT NULL COMMENT '荣誉标题',
    `description` text COMMENT '荣誉描述',
    `honor_date` date NOT NULL COMMENT '获得日期',
    `issuer` varchar(200) DEFAULT NULL COMMENT '颁发机构/组织',
    `level` varchar(20) DEFAULT NULL COMMENT '荣誉级别：1-校级，2-市级，3-省级，4-国家级，5-国际级',
    `category_id` bigint DEFAULT NULL COMMENT '所属分类ID（可为空）',
    `custom_category` varchar(50) DEFAULT NULL COMMENT '自定义分类名称（当不选择预设分类时使用）',
    `tags` varchar(500) DEFAULT NULL COMMENT '标签（JSON格式存储）',
    `is_top` tinyint DEFAULT 0 COMMENT '是否置顶：0-否，1-是',
    `is_public` tinyint DEFAULT 1 COMMENT '是否公开：0-私密，1-公开',
    `sort_order` int DEFAULT 0 COMMENT '排序顺序',
    `is_deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记：0=未删除，1=已删除',
    `create_time` datetime DEFAULT NULL COMMENT '创建时间',
    `update_time` datetime DEFAULT NULL COMMENT '更新时间',
    `create_user` bigint DEFAULT NULL COMMENT '创建人ID',
    `update_user` bigint DEFAULT NULL COMMENT '更新人ID',
    `file_id` varchar(32) DEFAULT NULL COMMENT '证书/奖牌图片文件ID(UUID)',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_honor_date` (`honor_date`),
    KEY `idx_category_id` (`category_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='荣誉记录表';

CREATE TABLE IF NOT EXISTS `anniversary_record` (
    `id` bigint NOT NULL COMMENT '记录ID',
    `user_id` bigint NOT NULL COMMENT '用户ID',
    `title` varchar(200) NOT NULL COMMENT '标题',
    `target_date` date NOT NULL COMMENT '目标日期',
    `type` varchar(20) NOT NULL COMMENT '类型：anniversary-纪念日(正数), countdown-倒数日(倒数)',
    `note` varchar(500) DEFAULT NULL COMMENT '备注',
    `color` varchar(50) DEFAULT NULL COMMENT '渐变色class',
    `icon` varchar(20) DEFAULT NULL COMMENT 'Emoji图标',
    `is_pinned` tinyint NOT NULL DEFAULT 0 COMMENT '是否固定到首页：0-否，1-是',
    `pinned_sort` int NOT NULL DEFAULT 0 COMMENT '首页固定排序，数值越小越靠前',
    `is_deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记：0=未删除，1=已删除',
    `create_time` datetime DEFAULT NULL COMMENT '创建时间',
    `update_time` datetime DEFAULT NULL COMMENT '更新时间',
    `create_user` bigint DEFAULT NULL COMMENT '创建人ID',
    `update_user` bigint DEFAULT NULL COMMENT '更新人ID',
    PRIMARY KEY (`id`),
    KEY `idx_home_pinned` (`user_id`, `is_deleted`, `is_pinned`, `pinned_sort`, `id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_target_date` (`target_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='纪念日记录表';

CREATE TABLE IF NOT EXISTS `milestone` (
    `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `user_id` bigint(20) NOT NULL COMMENT '用户ID',
    `title` varchar(255) NOT NULL COMMENT '标题',
    `description` text COMMENT '详细描述',
    `date` date NOT NULL COMMENT '开始日期',
    `end_date` date DEFAULT NULL COMMENT '结束日期',
    `type` varchar(50) NOT NULL DEFAULT 'other' COMMENT '类型: work, study, life, other',
    `tags` json DEFAULT NULL COMMENT '标签数组',
    `create_user` bigint(20) DEFAULT NULL COMMENT '创建人',
    `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_user` bigint(20) DEFAULT NULL COMMENT '更新人',
    `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `is_deleted` int(11) NOT NULL DEFAULT '0' COMMENT '是否删除',
    PRIMARY KEY (`id`),
    KEY `idx_user_date` (`user_id`, `date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='里程碑表';

-- =====================================================
-- 记录模块（record）—— 备忘 / 闪念
-- =====================================================

CREATE TABLE IF NOT EXISTS `memo` (
    `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `user_id` bigint(20) NOT NULL COMMENT '用户ID',
    `title` varchar(100) DEFAULT NULL COMMENT '备忘录标题，未填写时为空',
    `content` text COMMENT '备忘录内容',
    `hidden_content` tinyint(1) DEFAULT 0 COMMENT '是否隐藏内容',
    `create_user` bigint(20) DEFAULT NULL COMMENT '创建人',
    `create_time` datetime DEFAULT NULL COMMENT '创建时间',
    `update_user` bigint(20) DEFAULT NULL COMMENT '更新人',
    `update_time` datetime DEFAULT NULL COMMENT '更新时间',
    `is_deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记：0=未删除，1=已删除',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='备忘录表';

CREATE TABLE IF NOT EXISTS `thought` (
    `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `content` mediumtext NOT NULL COMMENT '内容',
    `user_id` bigint(19) NOT NULL COMMENT '用户 ID',
    `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_user` bigint(20) DEFAULT NULL COMMENT '创建人用户ID',
    `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
    `update_user` bigint(20) DEFAULT NULL COMMENT '最后更新人用户ID',
    `is_deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记：0=未删除，1=已删除',
    `is_pinned` tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否固定(用于在看板等重要位置展示): 0-否, 1-是',
    `hidden_content` tinyint(1) DEFAULT 0 COMMENT '是否隐藏内容',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='思考表';

CREATE TABLE IF NOT EXISTS `thought_rela_event` (
    `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `thought_id` bigint(20) NOT NULL COMMENT '所属闪念ID，关联thought.id',
    `content` text NOT NULL COMMENT '闪念关联事件内容',
    `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_user` bigint(20) DEFAULT NULL COMMENT '创建人用户ID',
    `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
    `update_user` bigint(20) DEFAULT NULL COMMENT '最后更新人用户ID',
    `is_deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记：0=未删除，1=已删除',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='思考关联事件表';

CREATE TABLE IF NOT EXISTS `record_article` (
    `id` bigint(20) NOT NULL COMMENT '文章ID',
    `url` varchar(500) DEFAULT NULL COMMENT '原文链接',
    `title` varchar(200) DEFAULT NULL COMMENT '标题',
    `author` varchar(100) DEFAULT NULL COMMENT '作者',
    `content_html` longtext COMMENT '文章HTML内容',
    `category` varchar(50) DEFAULT NULL COMMENT '分类',
    `tags` varchar(200) DEFAULT NULL COMMENT '标签',
    `create_user` bigint(20) DEFAULT NULL COMMENT '创建人',
    `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_user` bigint(20) DEFAULT NULL COMMENT '更新人',
    `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `is_deleted` tinyint(4) NOT NULL DEFAULT '0' COMMENT '是否删除',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='收藏文章表';

CREATE TABLE IF NOT EXISTS `record_article_annotation` (
    `id` bigint(20) NOT NULL COMMENT '标注ID',
    `article_id` bigint(20) NOT NULL COMMENT '文章ID',
    `selected_text` text COMMENT '选中文本',
    `note_content` text COMMENT '标注内容',
    `start_container_path` varchar(200) DEFAULT NULL COMMENT '起始容器路径',
    `start_offset` int(11) DEFAULT NULL COMMENT '起始偏移量',
    `end_container_path` varchar(200) DEFAULT NULL COMMENT '结束容器路径',
    `end_offset` int(11) DEFAULT NULL COMMENT '结束偏移量',
    `color` varchar(20) DEFAULT NULL COMMENT '标注颜色',
    `create_user` bigint(20) DEFAULT NULL COMMENT '创建人',
    `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_user` bigint(20) DEFAULT NULL COMMENT '更新人',
    `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `is_deleted` tinyint(4) NOT NULL DEFAULT '0' COMMENT '是否删除',
    PRIMARY KEY (`id`),
    KEY `idx_article_id` (`article_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文章标注表';

-- =====================================================
-- 记录模块（record）—— 财务（账单）
-- =====================================================

CREATE TABLE IF NOT EXISTS `expense` (
    `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '账单ID',
    `amt` decimal(10,2) NOT NULL COMMENT '花费金额',
    `transaction_amt` decimal(10,2) DEFAULT NULL COMMENT '交易金额',
    `exp_type_id` bigint NOT NULL COMMENT '支出类型ID',
    `pay_type_id` bigint DEFAULT NULL COMMENT '支付方式ID(关联user_dict_data)',
    `remark` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
    `user_id` bigint(20) NOT NULL COMMENT '用户id',
    `exp_time` datetime NOT NULL COMMENT '支出时间',
    `is_deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记：0=未删除，1=已删除',
    `create_user` bigint(20) DEFAULT NULL COMMENT '创建人',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_user` bigint(20) DEFAULT NULL COMMENT '修改人',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `exp_desc` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '交易描述，未记录时为空',
    `transaction_id` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '支付平台交易号，保持原始字符串；未记录时为空',
    `counterparty` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '交易对方名称，未记录时为空',
    `counterparty_acct` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '交易对方账号，保持原始字符串；未记录时为空',
    `merchant_order_no` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '商家订单号',
    `transaction_status` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '交易状态',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='支出表';

-- 银行卡与标签关联。银行选择 sys_dict_data 或填写自定义名称；卡面通过 file.biz_type/biz_id 反向关联。
CREATE TABLE IF NOT EXISTS `bank_card` (
  `id` BIGINT NOT NULL COMMENT '雪花ID',
  `user_id` BIGINT NOT NULL COMMENT '所属用户',
  `bank_id` BIGINT DEFAULT NULL COMMENT 'sys_dict_data.dict_code，类型bank；自定义银行时为空',
  `custom_bank_name` VARCHAR(100) DEFAULT NULL COMMENT '用户填写的银行名称，仅bank_id为空时使用',
  `card_name` VARCHAR(100) DEFAULT NULL COMMENT '卡片产品名称',
  `alias` VARCHAR(50) DEFAULT NULL COMMENT '个人别名',
  `card_type` VARCHAR(20) NOT NULL COMMENT 'debit储蓄卡 credit信用卡',
  `card_no_ciphertext` VARCHAR(512) DEFAULT NULL COMMENT '版本化SM4-GCM卡号密文',
  `card_no_fingerprint` BINARY(32) DEFAULT NULL COMMENT '主密钥派生HMAC-SM3判重指纹',
  `card_no_last4` CHAR(4) DEFAULT NULL COMMENT '卡号末四位',
  `branch_name` VARCHAR(200) DEFAULT NULL COMMENT '开户支行',
  `status` VARCHAR(20) NOT NULL DEFAULT 'normal' COMMENT 'normal frozen lost closed',
  `opened_date` DATE DEFAULT NULL COMMENT '开卡日期',
  `expiry_month` DATE DEFAULT NULL COMMENT '有效期月份，存当月第一天',
  `balance` DECIMAL(18,2) DEFAULT NULL COMMENT '储蓄卡余额，人民币元；NULL未记录',
  `credit_limit` DECIMAL(18,2) DEFAULT NULL COMMENT '信用额度，人民币元；NULL未记录',
  `statement_day` TINYINT UNSIGNED DEFAULT NULL COMMENT '每月账单日1至31',
  `repayment_day` TINYINT UNSIGNED DEFAULT NULL COMMENT '每月固定还款日1至31',
  `cover_template_id` BIGINT DEFAULT NULL COMMENT '公共卡面ID',
  `cover_color` CHAR(7) DEFAULT NULL COMMENT '默认卡面Hex颜色',
  `cover_source_url` VARCHAR(1000) DEFAULT NULL COMMENT '当前卡面出处，不用于远程抓取',
  `sort_order` INT NOT NULL DEFAULT 0 COMMENT '排序值，小值在前',
  `remark` VARCHAR(1000) DEFAULT NULL COMMENT '备注',
  `is_deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记：0=未删除，1=已删除',
  `create_user` BIGINT NOT NULL COMMENT '创建人用户ID',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_user` BIGINT NOT NULL COMMENT '最后更新人用户ID',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_sort` (`user_id`,`is_deleted`,`sort_order`,`id`),
  KEY `idx_cover_template_reference` (`cover_template_id`,`is_deleted`),
  KEY `idx_bank_reference` (`bank_id`,`is_deleted`),
  KEY `idx_user_bank` (`user_id`,`bank_id`,`is_deleted`),
  KEY `idx_user_fingerprint` (`user_id`,`card_no_fingerprint`,`is_deleted`),
  KEY `idx_user_last4` (`user_id`,`card_no_last4`,`is_deleted`),
  CONSTRAINT `chk_bank_card_bank_source` CHECK (
    (bank_id IS NOT NULL AND custom_bank_name IS NULL)
    OR (bank_id IS NULL AND custom_bank_name IS NOT NULL AND CHAR_LENGTH(TRIM(custom_bank_name)) > 0)
  )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='银行卡档案';
CREATE TABLE IF NOT EXISTS `bank_card_tag_rel` (
  `id` BIGINT NOT NULL COMMENT '主键ID',
  `user_id` BIGINT NOT NULL COMMENT '与银行卡和标签的所属用户一致',
  `bank_card_id` BIGINT NOT NULL COMMENT '关联银行卡ID，关联bank_card.id',
  `tag_id` BIGINT NOT NULL COMMENT 'user_dict_data.id，类型bank_card_tag',
  `is_deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记：0=未删除，1=已删除',
  `create_user` BIGINT NOT NULL COMMENT '创建人用户ID',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_user` BIGINT NOT NULL COMMENT '最后更新人用户ID',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_card_tag` (`user_id`,`bank_card_id`,`tag_id`,`is_deleted`),
  KEY `idx_user_tag_card` (`user_id`,`tag_id`,`is_deleted`,`bank_card_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='银行卡标签关联';

CREATE TABLE IF NOT EXISTS `income` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `amt` decimal(10,2) NOT NULL COMMENT '收入',
    `inc_date` date NOT NULL COMMENT '收入时间',
    `remark` varchar(255) DEFAULT NULL COMMENT '备注',
    `user_id` bigint(20) NOT NULL COMMENT '用户id',
    `create_user` bigint(20) DEFAULT NULL COMMENT '创建人',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_user` bigint(20) DEFAULT NULL COMMENT '修改人',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `is_deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记：0=未删除，1=已删除',
    `inc_type_id` bigint NOT NULL COMMENT '收入类型',
    `tax` decimal(10,2) DEFAULT NULL COMMENT '收入税额，单位元；NULL表示未记录，0表示零税额',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='收入表';

-- =====================================================
-- 记录模块（record）—— 运动 / 阅读 / 观影 / 视频
-- =====================================================

CREATE TABLE IF NOT EXISTS `exercise_record` (
    `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `user_id` bigint(20) NOT NULL COMMENT '用户ID',
    `exercise_type_id` bigint DEFAULT NULL COMMENT '运动类型ID(关联user_dict_data.id)',
    `exercise_date` date NOT NULL COMMENT '运动日期',
    `exercise_count` int(11) NOT NULL DEFAULT '0' COMMENT '运动次数，单位次；0表示本条未记录有效次数',
    `description` text COLLATE utf8mb4_unicode_ci COMMENT '运动描述',
    `create_user` bigint(20) DEFAULT NULL COMMENT '创建用户',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_user` bigint(20) DEFAULT NULL COMMENT '更新用户',
    `is_deleted` int(11) NOT NULL DEFAULT '0' COMMENT '是否删除（0：未删除，1：已删除）',
    `time_id` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '时间ID',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='运动记录表';

CREATE TABLE IF NOT EXISTS `read_record` (
    `id` bigint(20) NOT NULL COMMENT '主键',
    `title` varchar(255) NOT NULL COMMENT '书名或文章标题',
    `type` tinyint(4) NOT NULL COMMENT '类型：1-书籍，2-文章/网页',
    `author` varchar(100) DEFAULT NULL COMMENT '作者',
    `url` varchar(500) DEFAULT NULL COMMENT '链接（主要针对文章/网页或豆瓣链接）',
    `status` varchar(32) DEFAULT 'not_started' COMMENT '状态：not_started/in_progress/completed/on_hold',
    `total_progress` int(11) DEFAULT '0' COMMENT '总进度（总页数或100表示百分比）',
    `current_progress` int(11) DEFAULT '0' COMMENT '当前进度（当前页数或当前百分比）',
    `start_time` datetime DEFAULT NULL COMMENT '开始阅读时间',
    `finish_time` datetime DEFAULT NULL COMMENT '完成时间',
    `remark` varchar(1000) DEFAULT NULL COMMENT '备注/读后感',
    `user_id` bigint(20) NOT NULL COMMENT '归属用户ID',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `create_user` bigint(20) DEFAULT NULL COMMENT '创建人',
    `update_user` bigint(20) DEFAULT NULL COMMENT '更新人',
    `is_deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记：0=未删除，1=已删除',
    `file_id` varchar(32) DEFAULT NULL COMMENT '封面文件ID(UUID)',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='阅读记录表';

CREATE TABLE IF NOT EXISTS `movie` (
    `id` bigint(20) NOT NULL COMMENT '主键',
    `title` varchar(255) NOT NULL COMMENT '影视名称',
    `type` tinyint(4) NOT NULL COMMENT '类型：1-电影，2-剧集，3-动漫，4-纪录片，5-其他',
    `director` varchar(100) DEFAULT NULL COMMENT '导演/演员',
    `url` varchar(500) DEFAULT NULL COMMENT '链接（主要针对豆瓣等外部链接）',
    `status` varchar(32) DEFAULT 'not_started' COMMENT '状态：not_started/in_progress/completed/on_hold',
    `total_progress` int(11) DEFAULT '0' COMMENT '总进度（总集数或时长）',
    `current_progress` int(11) DEFAULT '0' COMMENT '当前进度（当前集数或观看时长）',
    `start_time` datetime DEFAULT NULL COMMENT '开始观看时间',
    `finish_time` datetime DEFAULT NULL COMMENT '看完时间',
    `rating` tinyint DEFAULT NULL COMMENT '个人评分：1-5',
    `remark` varchar(1000) DEFAULT NULL COMMENT '短评/备注',
    `user_id` bigint(20) NOT NULL COMMENT '归属用户ID',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `create_user` bigint(20) DEFAULT NULL COMMENT '创建人',
    `update_user` bigint(20) DEFAULT NULL COMMENT '更新人',
    `is_deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记：0=未删除，1=已删除',
    `file_id` varchar(32) DEFAULT NULL COMMENT '封面文件ID(UUID)',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='影视记录表';

CREATE TABLE IF NOT EXISTS `b_video` (
    `cover_file_id` varchar(32) DEFAULT NULL COMMENT '当前封面附件ID，关联file.id；尚无内部封面时为空',
    `cover_state` varchar(16) NOT NULL DEFAULT 'NONE' COMMENT '封面状态：NONE=无来源，PENDING=导入中，READY=可用，FAILED=失败',
    `cover_version` bigint NOT NULL DEFAULT 0 COMMENT '封面导入版本，从0开始，每次重新入队递增，防止旧任务覆盖新封面',
    `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `title` varchar(500) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '视频标题',
    `url` varchar(1000) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'B站视频URL',
    `cover` varchar(1000) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '视频封面URL',
    `duration` int(11) NOT NULL COMMENT '视频时长（秒）',
    `watched_duration` int(11) NOT NULL DEFAULT '0' COMMENT '观看时长',
    `episodes` int(11) DEFAULT '1' COMMENT '总集数',
    `current_episode` int(11) DEFAULT '1' COMMENT '当前观看集数',
    `status` varchar(32) NOT NULL DEFAULT 'in_progress' COMMENT '学习状态：not_started/in_progress/on_hold/completed',
    `last_watched` datetime DEFAULT NULL COMMENT '最后观看时间（syncProgress 同步时写入）',
    `notes` text COLLATE utf8mb4_unicode_ci COMMENT '学习笔记',
    `bvid` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'BV号',
    `aid` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'AV号',
    `description` text COLLATE utf8mb4_unicode_ci COMMENT '视频描述',
    `owner_name` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '视频UP主名称，未获取时为空',
    `pages_info` json DEFAULT NULL COMMENT '分集信息：cid-分集ID, page-页码, part-分集标题, duration-分集时长',
    `user_id` bigint(20) NOT NULL COMMENT '所属用户ID，用于用户数据隔离',
    `create_user` bigint(20) DEFAULT NULL COMMENT '创建人',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_user` bigint(20) DEFAULT NULL COMMENT '修改人',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `is_deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记：0=未删除，1=已删除',
    PRIMARY KEY (`id`),
    KEY `idx_status` (`status`),
    KEY `idx_bvid` (`bvid`),
    KEY `idx_aid` (`aid`),
    KEY `idx_title` (`title`(100))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='B 站视频记录表';

-- =====================================================
-- 记录模块（record）—— 演出 / 设备 / 会员
-- =====================================================

CREATE TABLE IF NOT EXISTS `performance` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '唯一标识',
    `user_id` bigint NOT NULL COMMENT '所属用户ID',
    `performance_name` varchar(100) NOT NULL COMMENT '演出名称',
    `performer` varchar(50) DEFAULT NULL COMMENT '演出者或团体名称，未填写时为空',
    `performance_type` varchar(50) NOT NULL COMMENT '演出类型(演唱会/话剧/音乐会等)',
    `performance_date` datetime NOT NULL COMMENT '演出日期',
    `city` varchar(50) NOT NULL COMMENT '演出城市',
    `venue` varchar(100) NOT NULL COMMENT '演出地点',
    `ticket_price` decimal(10,2) NOT NULL COMMENT '票价',
    `seat_info` varchar(100) DEFAULT NULL COMMENT '座位信息',
    `duration` int(11) DEFAULT NULL COMMENT '演出时长(分钟)',
    `rating` tinyint(4) DEFAULT NULL COMMENT '演出评分(1-5)',
    `review` text COMMENT '演出评价',
    `purchase_platform` varchar(50) DEFAULT NULL COMMENT '购票平台',
    `order_number` varchar(50) DEFAULT NULL COMMENT '购票订单号',
    `create_user` bigint DEFAULT NULL COMMENT '创建人ID',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_user` bigint DEFAULT NULL COMMENT '更新人ID',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `is_deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记：0=未删除，1=已删除',
    PRIMARY KEY (`id`),
    KEY `idx_performance_user_deleted` (`user_id`, `is_deleted`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='演出表';

CREATE TABLE IF NOT EXISTS `device` (
    `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `user_id` bigint(20) NOT NULL COMMENT '用户ID',
    `name` varchar(255) NOT NULL COMMENT '设备名称',
    `spec` varchar(255) DEFAULT NULL COMMENT '设备规格',
    `type` varchar(255) DEFAULT NULL COMMENT '设备类型',
    `status` varchar(255) DEFAULT NULL COMMENT '设备状态',
    `remark` varchar(255) DEFAULT NULL COMMENT '备注',
    `purchase_date` date DEFAULT NULL COMMENT '购买日期',
    `purchase_price` decimal(10,2) DEFAULT NULL COMMENT '购买价格',
    `purchase_place` varchar(255) DEFAULT NULL COMMENT '购买地点',
    `purchase_company` varchar(255) DEFAULT NULL COMMENT '购买公司',
    `end_date` date DEFAULT NULL COMMENT '结束日期（用于计算日均费用）',
    `file_id` varchar(32) DEFAULT NULL COMMENT '图片文件ID(UUID)',
  `source_platform` VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '采集平台：jd=京东、taobao=淘宝；手工录入或历史记录为NULL',
  `source_order_id` VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '来源订单ID，原始字符串；无采集来源时为NULL',
  `source_product_id` VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '来源商品ID，采集行sku原始字符串；无采集来源时为NULL',
  -- 按用户、平台、订单和未删除状态查找来源；跨设备和衣柜唯一性由采集事务用户行锁保证。
  KEY `idx_device_capture_order` (`user_id`, `source_platform`, `source_order_id`, `is_deleted`),
    `create_user` bigint(20) DEFAULT NULL COMMENT '创建人',
    `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_user` bigint(20) DEFAULT NULL COMMENT '更新人',
    `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `is_deleted` tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否删除: 0-未删除, 1-已删除',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='设备表';

CREATE TABLE IF NOT EXISTS `membership_provider` (
    `id` bigint NOT NULL COMMENT '主键ID',
    `name` varchar(100) NOT NULL COMMENT '平台名称',
    `code` varchar(50) NOT NULL COMMENT '稳定编码',
    `category` varchar(50) NOT NULL DEFAULT 'other' COMMENT '默认分类:video/music/shopping/cloud/study/game/AI/other',
    `icon_key` varchar(64) DEFAULT NULL COMMENT '内置图标标识，不支持上传',
    `sort_order` int NOT NULL DEFAULT 0 COMMENT '展示顺序',
    `is_enabled` tinyint NOT NULL DEFAULT 1 COMMENT '是否启用:1-启用 0-停用',
    `create_user` bigint DEFAULT NULL COMMENT '创建人',
    `create_time` datetime DEFAULT NULL COMMENT '创建时间',
    `update_user` bigint DEFAULT NULL COMMENT '更新人',
    `update_time` datetime DEFAULT NULL COMMENT '更新时间',
    `is_deleted` tinyint NOT NULL DEFAULT 0 COMMENT '是否删除',
    `active_flag` tinyint GENERATED ALWAYS AS (CASE WHEN `is_deleted` = 0 THEN 1 ELSE NULL END) STORED COMMENT '仅有效平台参与编码唯一约束',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_membership_provider_code` (`code`, `active_flag`),
    KEY `idx_membership_provider_enabled_sort` (`is_enabled`, `sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='会员平台表';

CREATE TABLE IF NOT EXISTS `membership_record` (
    `id` bigint NOT NULL COMMENT '主键ID',
    `user_id` bigint NOT NULL COMMENT '用户ID',
    `name` varchar(100) NOT NULL COMMENT '会员名称',
    `category` varchar(50) NOT NULL DEFAULT 'other' COMMENT '分类:video/music/shopping/cloud/study/game/AI/other',
    `provider_id` bigint DEFAULT NULL COMMENT '会员平台ID，未关联平台时可为空',
    `provider` varchar(100) DEFAULT NULL COMMENT '平台/服务商',
    `icon` varchar(50) DEFAULT NULL COMMENT 'emoji图标',
    `color` varchar(50) DEFAULT NULL COMMENT '卡片背景色class',
    `start_date` date DEFAULT NULL COMMENT '开通日期',
    `expiry_date` date NOT NULL COMMENT '到期日期',
    `price` decimal(10,2) DEFAULT NULL COMMENT '支付金额',
    `billing_cycle` varchar(20) NOT NULL DEFAULT 'month' COMMENT '计费周期:week/two_weeks/month/quarter/half_year/year',
    `monthly_amount` decimal(10,2) NOT NULL DEFAULT '0.00' COMMENT '折算后的月均成本，可手动修改',
    `auto_renew` tinyint DEFAULT 0 COMMENT '是否自动续费:0-否 1-是',
    `note` varchar(500) DEFAULT NULL COMMENT '备注',
    `create_user` bigint DEFAULT NULL COMMENT '创建人',
    `create_time` datetime DEFAULT NULL COMMENT '创建时间',
    `update_user` bigint DEFAULT NULL COMMENT '更新人',
    `update_time` datetime DEFAULT NULL COMMENT '更新时间',
    `is_deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记：0=未删除，1=已删除',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_expiry_date` (`expiry_date`),
    KEY `idx_membership_provider_id` (`provider_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='会员记录表';

-- =====================================================
-- 记录模块（record）—— 消息与通知
-- =====================================================

CREATE TABLE IF NOT EXISTS `message` (
    `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `sender_id` bigint(20) NOT NULL COMMENT '发送用户ID',
    `receiver_id` bigint(20) NOT NULL COMMENT '接收用户ID',
    `title` varchar(255) NOT NULL COMMENT '消息标题',
    `content` text COMMENT '消息内容',
    `type` int(11) NOT NULL DEFAULT '0' COMMENT '消息类型: 0-系统通知, 1-用户消息',
    `is_read` tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否已读: 0-未读, 1-已读',
    `create_user` bigint(20) DEFAULT NULL COMMENT '创建人',
    `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_user` bigint(20) DEFAULT NULL COMMENT '更新人',
    `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `is_deleted` tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否删除: 0-未删除, 1-已删除',
    PRIMARY KEY (`id`),
    KEY `idx_receiver_is_read` (`receiver_id`, `is_read`),
    KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='消息表';

CREATE TABLE IF NOT EXISTS `notification_channel_config` (
    `id` bigint NOT NULL COMMENT 'ID',
    `user_id` bigint NOT NULL COMMENT 'AIO Life 用户 ID',
    `channel` varchar(32) NOT NULL COMMENT '通知渠道：FEISHU',
    `enabled` tinyint NOT NULL DEFAULT 0 COMMENT '是否启用',
    `app_id` varchar(128) NOT NULL COMMENT '用户自己的飞书应用 App ID',
    `app_secret_ciphertext` text NOT NULL COMMENT '加密的飞书应用 App Secret',
    `receiver_open_id` varchar(128) DEFAULT NULL COMMENT '接收用户在该应用下的 open_id',
    `receiver_name` varchar(128) DEFAULT NULL COMMENT '接收用户名称',
    `create_user` bigint DEFAULT NULL COMMENT '创建人用户ID',
    `create_time` datetime NOT NULL COMMENT '创建时间',
    `update_user` bigint DEFAULT NULL COMMENT '最后更新人用户ID',
    `update_time` datetime NOT NULL COMMENT '最后更新时间',
    `is_deleted` tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除标记：0=未删除，1=已删除',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户通知渠道配置';

CREATE TABLE IF NOT EXISTS `notification_preference` (
    `id` bigint NOT NULL COMMENT 'ID',
    `user_id` bigint NOT NULL COMMENT '所属用户ID，用于用户数据隔离',
    `biz_type` varchar(64) NOT NULL COMMENT '通知业务类型：LEETCODE_REMINDER=未打卡提醒，LEETCODE_DAILY=每日一题，FEEDBACK_ADMIN=新反馈，FEEDBACK_REPLY=反馈回复',
    `channel` varchar(32) NOT NULL COMMENT '通知渠道：FEISHU',
    `enabled` tinyint NOT NULL DEFAULT 1 COMMENT '是否启用该业务渠道通知：0=关闭，1=启用',
    `create_user` bigint DEFAULT NULL COMMENT '创建人用户ID',
    `create_time` datetime NOT NULL COMMENT '创建时间',
    `update_user` bigint DEFAULT NULL COMMENT '最后更新人用户ID',
    `update_time` datetime NOT NULL COMMENT '最后更新时间',
    `is_deleted` tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除标记：0=未删除，1=已删除',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户通知偏好';

CREATE TABLE IF NOT EXISTS `notification_delivery` (
    `id` bigint NOT NULL COMMENT 'ID',
    `dedup_key` varchar(128) NOT NULL COMMENT '通知事件去重标识，与用户ID和渠道共同确定同一次投递',
    `user_id` bigint NOT NULL COMMENT '所属用户ID，用于用户数据隔离',
    `biz_type` varchar(64) NOT NULL COMMENT '通知业务类型：LEETCODE_REMINDER=未打卡提醒，LEETCODE_DAILY=每日一题，FEEDBACK_ADMIN=新反馈，FEEDBACK_REPLY=反馈回复',
    `channel` varchar(32) NOT NULL COMMENT '投递渠道，当前持久化重试由FEISHU飞书渠道使用',
    `status` varchar(16) NOT NULL COMMENT 'PENDING/SUCCESS/RETRY/FAILED',
    `payload_ciphertext` mediumtext NOT NULL COMMENT '重试所需的加密消息载荷',
    `retry_count` int NOT NULL DEFAULT 0 COMMENT '累计发送失败次数，初始0，每次失败递增',
    `next_retry_time` datetime DEFAULT NULL COMMENT '下次重试时间，未安排重试时为空',
    `provider_code` varchar(64) DEFAULT NULL COMMENT '最近渠道返回码或内部失败码，尚未返回时为空',
    `error_message` varchar(512) DEFAULT NULL COMMENT '最近投递失败原因，成功或尚未失败时为空',
    `create_user` bigint DEFAULT NULL COMMENT '创建人用户ID',
    `create_time` datetime NOT NULL COMMENT '创建时间',
    `update_user` bigint DEFAULT NULL COMMENT '最后更新人用户ID',
    `update_time` datetime NOT NULL COMMENT '最后更新时间',
    `is_deleted` tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除标记：0=未删除，1=已删除',
    PRIMARY KEY (`id`),
    KEY `idx_retry` (`status`, `next_retry_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='通知投递记录';

-- =====================================================
-- 衣柜（wardrobe）
-- =====================================================

CREATE TABLE IF NOT EXISTS `wardrobe_category` (
    `id` bigint NOT NULL COMMENT '主键ID',
    `name` varchar(50) NOT NULL COMMENT '分类名称',
    `icon` varchar(50) DEFAULT NULL COMMENT '图标',
    `parent_id` bigint DEFAULT NULL COMMENT '父分类ID',
    `sort` int DEFAULT 0 COMMENT '排序',
    `category_type` tinyint DEFAULT 0 COMMENT '0=系统预设 1=用户自定义',
    `user_id` bigint DEFAULT NULL COMMENT '用户ID(用户自定义分类时)',
    `create_user` bigint DEFAULT NULL COMMENT '创建人',
    `create_time` datetime DEFAULT NULL COMMENT '创建时间',
    `update_user` bigint DEFAULT NULL COMMENT '更新人',
    `update_time` datetime DEFAULT NULL COMMENT '更新时间',
    `is_deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记：0=未删除，1=已删除',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='衣柜分类表';

CREATE TABLE IF NOT EXISTS `wardrobe_item` (
    `id` bigint NOT NULL COMMENT '主键ID',
    `name` varchar(100) NOT NULL COMMENT '衣物名称',
    `category_id` bigint DEFAULT NULL COMMENT '分类ID',
    `color` varchar(50) DEFAULT NULL COMMENT '颜色',
    `brand` varchar(100) DEFAULT NULL COMMENT '品牌',
    `season` varchar(20) DEFAULT NULL COMMENT '适用季节:春,夏,秋,冬',
    `purchase_date` date DEFAULT NULL COMMENT '购买日期',
    `price` decimal(10,2) DEFAULT NULL COMMENT '价格',
    `file_id` varchar(50) DEFAULT NULL COMMENT '图片文件ID',
    `size` varchar(20) DEFAULT NULL COMMENT '尺码',
    `memo` varchar(500) DEFAULT NULL COMMENT '备注',
    `user_id` bigint NOT NULL COMMENT '用户ID',
  `source_platform` VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '采集平台：jd=京东、taobao=淘宝；手工录入或历史记录为NULL',
  `source_order_id` VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '来源订单ID，原始字符串；无采集来源时为NULL',
  `source_product_id` VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '来源商品ID，采集行sku原始字符串；无采集来源时为NULL',
  -- 按用户、平台、订单和未删除状态查找来源；跨设备和衣柜唯一性由采集事务用户行锁保证。
  KEY `idx_wardrobe_item_capture_order` (`user_id`, `source_platform`, `source_order_id`, `is_deleted`),
    `create_user` bigint DEFAULT NULL COMMENT '创建人',
    `create_time` datetime DEFAULT NULL COMMENT '创建时间',
    `update_user` bigint DEFAULT NULL COMMENT '更新人',
    `update_time` datetime DEFAULT NULL COMMENT '更新时间',
    `is_deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记：0=未删除，1=已删除',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_category_id` (`category_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='衣柜衣物表';

-- =====================================================
-- 其他 / AI —— 人格测试
-- =====================================================

CREATE TABLE IF NOT EXISTS `mbti_result` (
    `id` bigint NOT NULL COMMENT '主键ID',
    `user_id` bigint NOT NULL COMMENT '用户ID',
    `test_id` varchar(255) DEFAULT NULL COMMENT '测试ID',
    `mbti_type` varchar(10) DEFAULT NULL COMMENT 'MBTI类型',
    `raw_result` text DEFAULT NULL COMMENT '原始结果数据(JSON)',
    `results_page` varchar(500) DEFAULT NULL COMMENT '官方结果页面URL',
    `create_user` bigint DEFAULT NULL COMMENT '创建人',
    `create_time` datetime DEFAULT NULL COMMENT '创建时间',
    `update_user` bigint DEFAULT NULL COMMENT '更新人',
    `update_time` datetime DEFAULT NULL COMMENT '更新时间',
    `is_deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记：0=未删除，1=已删除',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_test_id` (`test_id`),
    KEY `idx_mbti_type` (`mbti_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='MBTI测试结果表';

CREATE TABLE IF NOT EXISTS `cbti_personality` (
    `id` bigint NOT NULL COMMENT '主键ID',
    `code` varchar(20) NOT NULL COMMENT '人格代码（唯一）',
    `name` varchar(64) NOT NULL COMMENT '人格名称',
    `motto` varchar(255) DEFAULT NULL COMMENT '座右铭',
    `color` varchar(20) DEFAULT NULL COMMENT '主题色（HEX）',
    `vector` json DEFAULT NULL COMMENT '人格向量（长度15，数值为-1/0/1/2）',
    `description` text DEFAULT NULL COMMENT '人格描述',
    `strengths` json DEFAULT NULL COMMENT '优势（字符串数组）',
    `weaknesses` json DEFAULT NULL COMMENT '弱点/注意（字符串数组）',
    `tech_stack` varchar(255) DEFAULT NULL COMMENT '技术栈',
    `spirit` text DEFAULT NULL COMMENT '灵魂格言',
    `image_object` varchar(255) DEFAULT NULL COMMENT 'MinIO对象路径（如 images/cbti/characters/SUDO.png）',
    `is_special` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否隐藏人格（0-否，1-是）',
    `create_user` bigint DEFAULT NULL COMMENT '创建人',
    `create_time` datetime DEFAULT NULL COMMENT '创建时间',
    `update_user` bigint DEFAULT NULL COMMENT '更新人',
    `update_time` datetime DEFAULT NULL COMMENT '更新时间',
    `is_deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记：0=未删除，1=已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_cbti_personality_code` (`code`),
    KEY `idx_cbti_personality_special` (`is_special`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='CBTI 人格类型表';

CREATE TABLE IF NOT EXISTS `cbti_result` (
    `id` bigint NOT NULL COMMENT '主键ID',
    `user_id` bigint NOT NULL COMMENT '用户ID',
    `personality_code` varchar(20) NOT NULL COMMENT '人格代码',
    `similarity` int NOT NULL DEFAULT 0 COMMENT '匹配度（0-100）',
    `dimensions` json DEFAULT NULL COMMENT '15维度结果(JSON)',
    `answers` json DEFAULT NULL COMMENT '答题结果(JSON，题号->选项值)',
    `hidden_answers` json DEFAULT NULL COMMENT '彩蛋答题结果(JSON)',
    `create_user` bigint DEFAULT NULL COMMENT '创建人',
    `create_time` datetime DEFAULT NULL COMMENT '创建时间',
    `update_user` bigint DEFAULT NULL COMMENT '更新人',
    `update_time` datetime DEFAULT NULL COMMENT '更新时间',
    `is_deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记：0=未删除，1=已删除',
    PRIMARY KEY (`id`),
    KEY `idx_cbti_result_user_id` (`user_id`),
    KEY `idx_cbti_result_personality_code` (`personality_code`),
    KEY `idx_cbti_result_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='CBTI 测试历史表';

-- =====================================================
-- 其他 / AI —— LLM / AI 对话
-- =====================================================




-- =====================================================
-- 其他 / AI —— 文件存储
-- =====================================================

CREATE TABLE IF NOT EXISTS `file` (
    `storage_object_id` bigint DEFAULT NULL COMMENT '关联不可变图片对象ID；普通上传未接入对象表时为空',
    KEY `idx_file_storage_object` (`storage_object_id`),
    `id` varchar(32) NOT NULL COMMENT '主键UUID',
    `file_name` varchar(255) NOT NULL COMMENT 'MinIO完整对象键',
    `file_size` bigint DEFAULT NULL COMMENT '文件大小(字节)',
    `file_type` varchar(100) DEFAULT NULL COMMENT '文件MIME类型',
    `hash_value` varchar(128) DEFAULT NULL COMMENT '文件哈希值(如 MD5，用于防重)',
    `biz_type` varchar(50) NOT NULL COMMENT '业务类型(如 movie, device, honor, wardrobe_item)',
    `biz_id` bigint DEFAULT NULL COMMENT '业务记录ID(用于反向关联，单附件时也可为空)',
    `is_public` tinyint NOT NULL DEFAULT 0 COMMENT '是否公开：0-否，1-是',
    `create_user` bigint DEFAULT NULL COMMENT '创建人',
    `create_time` datetime NOT NULL COMMENT '创建时间',
    `update_user` bigint DEFAULT NULL COMMENT '更新人',
    `update_time` datetime NOT NULL COMMENT '更新时间',
    `is_deleted` tinyint NOT NULL DEFAULT 0 COMMENT '是否删除',
    PRIMARY KEY (`id`),
    KEY `idx_biz` (`biz_type`, `biz_id`),
    KEY `idx_hash` (`hash_value`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统文件表';

ALTER TABLE `user` ADD CONSTRAINT `fk_user_avatar_file` FOREIGN KEY (`avatar_file_id`) REFERENCES `file` (`id`) ON DELETE RESTRICT;

CREATE TABLE IF NOT EXISTS `bank_card_cover_template` (
  `id` BIGINT NOT NULL COMMENT '雪花ID',
  `name` VARCHAR(100) NOT NULL COMMENT '卡面名称',
  `bank_id` BIGINT NOT NULL COMMENT 'sys_dict_data.dict_code，类型bank',
  `card_type` VARCHAR(20) NOT NULL COMMENT 'debit借记卡 credit信用卡',
  `source_url` VARCHAR(1000) DEFAULT NULL COMMENT '图片出处',
  `is_enabled` TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用：1-启用，0-停用',
  `sort_order` INT NOT NULL DEFAULT 0 COMMENT '排序值，数值越小越靠前',
  `is_deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记：0=未删除，1=已删除',
  `create_user` BIGINT NOT NULL COMMENT '创建人用户ID',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_user` BIGINT NOT NULL COMMENT '最后更新人用户ID',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_bank_type_enabled` (`bank_id`,`card_type`,`is_deleted`,`is_enabled`,`sort_order`,`id`),
  CONSTRAINT `chk_cover_template_type` CHECK (`card_type` IN ('debit','credit')),
  CONSTRAINT `chk_cover_template_enabled` CHECK (`is_enabled` IN (0,1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='公共银行卡卡面';

CREATE TABLE IF NOT EXISTS `storage_object` (
    `id` bigint NOT NULL COMMENT '主键ID',
    `bucket` varchar(63) NOT NULL COMMENT '对象存储桶名称，视频封面使用system',
    `object_key` varchar(255) NOT NULL COMMENT '桶内完整对象键，视频封面由bvedio前缀、SHA-256和扩展名组成',
    `sha256` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '最终存储文件字节的SHA-256摘要，64位小写十六进制',
    `file_size` bigint NOT NULL COMMENT '文件大小，单位字节',
    `content_type` varchar(100) NOT NULL COMMENT '实际存储文件的MIME类型',
    `create_user` bigint DEFAULT NULL COMMENT '创建人用户ID',
    `create_time` datetime NOT NULL COMMENT '创建时间',
    `update_user` bigint DEFAULT NULL COMMENT '最后更新人用户ID',
    `update_time` datetime NOT NULL COMMENT '最后更新时间',
    `is_deleted` tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除标记：0=未删除，1=已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_storage_hash` (`bucket`, `sha256`),
    UNIQUE KEY `uk_storage_key` (`bucket`, `object_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='不可变图片对象';

CREATE TABLE IF NOT EXISTS `image_import_task` (
    `id` bigint NOT NULL COMMENT '主键ID',
    `video_id` bigint NOT NULL COMMENT '关联视频ID，关联b_video.id',
    `cover_version` bigint NOT NULL COMMENT '入队时的封面版本，须与视频当前版本一致才可发布',
    `source_url` varchar(1000) NOT NULL COMMENT '本次任务抓取的原始封面URL',
    `state` varchar(16) NOT NULL COMMENT '任务状态：PENDING=待执行，RUNNING=执行中，READY=完成，FAILED=失败，CANCELLED=已取消',
    `attempts` int NOT NULL DEFAULT 0 COMMENT '领取执行次数，初始0，每次领取递增至最多5',
    `next_attempt_at` datetime NOT NULL COMMENT '首次可执行或退避后的下次重试时间',
    `lease_token` varchar(32) DEFAULT NULL COMMENT '本次执行租约令牌，未领取或已释放时为空',
    `lease_until` datetime DEFAULT NULL COMMENT '执行租约到期时间，未领取或已释放时为空',
    `error_code` varchar(64) DEFAULT NULL COMMENT '最近一次失败码，重试成功后保留；尚未失败时为空',
    `create_user` bigint DEFAULT NULL COMMENT '创建人用户ID',
    `create_time` datetime NOT NULL COMMENT '创建时间',
    `update_user` bigint DEFAULT NULL COMMENT '最后更新人用户ID',
    `update_time` datetime NOT NULL COMMENT '最后更新时间',
    `is_deleted` tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除标记：0=未删除，1=已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_image_import_version` (`video_id`, `cover_version`),
    KEY `idx_image_import_due` (`state`, `next_attempt_at`, `lease_until`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='视频封面持久化导入任务';

-- Per-user presentation preferences. Reset uses physical DELETE; business records are untouched.
CREATE TABLE IF NOT EXISTS `user_home_card_preference` (
  `id` bigint NOT NULL COMMENT '主键ID',
  `user_id` bigint NOT NULL COMMENT '所属用户ID，用于用户数据隔离',
  `card_key` varchar(64) NOT NULL COMMENT '首页卡片注册清单中的稳定标识',
  `enabled` tinyint NOT NULL DEFAULT 1 COMMENT '是否显示首页卡片：0=隐藏，1=显示',
  `sort_order` int NOT NULL DEFAULT 0 COMMENT '首页卡片分组内排序，数值越小越靠前',
  `create_user` bigint DEFAULT NULL COMMENT '创建人用户ID',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_user` bigint DEFAULT NULL COMMENT '最后更新人用户ID',
  `update_time` datetime DEFAULT NULL COMMENT '最后更新时间',
  `is_deleted` int NOT NULL DEFAULT 0 COMMENT '统一保留的删除标记，固定为0；恢复默认使用物理删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_home_card` (`user_id`, `card_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户首页卡片偏好';

-- 每个用户只有当前身体档案，更新覆盖原值；BMI 不存储。
CREATE TABLE IF NOT EXISTS `user_body_profile` (
  `id` bigint NOT NULL COMMENT '主键ID',
  `user_id` bigint NOT NULL COMMENT '所属用户ID，用于用户数据隔离',
  `version` int NOT NULL COMMENT '乐观锁版本，首次保存为1，每次成功修改递增',
  `height_cm` decimal(5,1) DEFAULT NULL COMMENT '身高（cm）',
  `weight_kg` decimal(5,1) DEFAULT NULL COMMENT '体重（kg）',
  `body_fat_percent` decimal(5,1) DEFAULT NULL COMMENT '体脂率（%）',
  `shoulder_width_cm` decimal(5,1) DEFAULT NULL COMMENT '肩宽',
  `chest_cm` decimal(5,1) DEFAULT NULL COMMENT '胸围',
  `upper_arm_cm` decimal(5,1) DEFAULT NULL COMMENT '上臂围',
  `forearm_cm` decimal(5,1) DEFAULT NULL COMMENT '前臂围',
  `waist_cm` decimal(5,1) DEFAULT NULL COMMENT '腰围',
  `hip_cm` decimal(5,1) DEFAULT NULL COMMENT '臀围',
  `thigh_cm` decimal(5,1) DEFAULT NULL COMMENT '大腿围',
  `calf_cm` decimal(5,1) DEFAULT NULL COMMENT '小腿围',
  `inseam_cm` decimal(5,1) DEFAULT NULL COMMENT '腿内侧长',
  `gender` varchar(16) DEFAULT NULL COMMENT '性别：male=男，female=女；未填写时为空',
  `measurement_side` varchar(16) DEFAULT NULL COMMENT '四肢围度测量侧别：left=左侧，right=右侧；未填写时为空',
  `arm_state` varchar(16) DEFAULT NULL COMMENT '上臂测量状态：relaxed=放松，flexed=屈臂；未填写时为空',
  `shoe_system` varchar(8) DEFAULT NULL COMMENT '鞋码制式：EU=欧码，CN=中国码，US=美码，UK=英码；未填写时为空',
  `top_size` varchar(32) DEFAULT NULL COMMENT '上装尺码，自由文本；未填写时为空',
  `trousers_size` varchar(32) DEFAULT NULL COMMENT '裤装尺码，自由文本；未填写时为空',
  `shoe_size` varchar(32) DEFAULT NULL COMMENT '鞋码文本，按shoe_system解释；未填写时为空',
  `create_user` bigint DEFAULT NULL COMMENT '创建人用户ID',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_user` bigint DEFAULT NULL COMMENT '最后更新人用户ID',
  `update_time` datetime DEFAULT NULL COMMENT '最后更新时间',
  `is_deleted` int NOT NULL DEFAULT 0 COMMENT '逻辑删除标记：0=未删除，1=已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_body_profile_user` (`user_id`),
  CONSTRAINT `ck_body_profile_version` CHECK (`version` > 0),
  CONSTRAINT `ck_body_profile_gender` CHECK (`gender` IN ('male','female')),
  CONSTRAINT `ck_body_profile_side` CHECK (`measurement_side` IN ('left','right')),
  CONSTRAINT `ck_body_profile_arm_state` CHECK (`arm_state` IN ('relaxed','flexed')),
  CONSTRAINT `ck_body_profile_shoe_system` CHECK (`shoe_system` IN ('EU','CN','US','UK')),
  CONSTRAINT `ck_body_profile_height_cm` CHECK (`height_cm` > 0 AND `height_cm` <= 999.9),
  CONSTRAINT `ck_body_profile_weight_kg` CHECK (`weight_kg` > 0 AND `weight_kg` <= 999.9),
  CONSTRAINT `ck_body_profile_body_fat_percent` CHECK (`body_fat_percent` > 0 AND `body_fat_percent` <= 99.9),
  CONSTRAINT `ck_body_profile_shoulder_width_cm` CHECK (`shoulder_width_cm` > 0 AND `shoulder_width_cm` <= 999.9),
  CONSTRAINT `ck_body_profile_chest_cm` CHECK (`chest_cm` > 0 AND `chest_cm` <= 999.9),
  CONSTRAINT `ck_body_profile_upper_arm_cm` CHECK (`upper_arm_cm` > 0 AND `upper_arm_cm` <= 999.9),
  CONSTRAINT `ck_body_profile_forearm_cm` CHECK (`forearm_cm` > 0 AND `forearm_cm` <= 999.9),
  CONSTRAINT `ck_body_profile_waist_cm` CHECK (`waist_cm` > 0 AND `waist_cm` <= 999.9),
  CONSTRAINT `ck_body_profile_hip_cm` CHECK (`hip_cm` > 0 AND `hip_cm` <= 999.9),
  CONSTRAINT `ck_body_profile_thigh_cm` CHECK (`thigh_cm` > 0 AND `thigh_cm` <= 999.9),
  CONSTRAINT `ck_body_profile_calf_cm` CHECK (`calf_cm` > 0 AND `calf_cm` <= 999.9),
  CONSTRAINT `ck_body_profile_inseam_cm` CHECK (`inseam_cm` > 0 AND `inseam_cm` <= 999.9)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='身体档案';

-- 生活足迹
CREATE TABLE IF NOT EXISTS `life_footprint` (
  `id` BIGINT NOT NULL COMMENT '足迹ID，由应用生成雪花ID',
  `user_id` BIGINT NOT NULL COMMENT '所属用户ID，用于用户数据隔离',
  `start_date` DATE NOT NULL COMMENT '开始日期，精确到日，格式YYYY-MM-DD',
  `end_date` DATE NOT NULL COMMENT '结束日期，9999-12-31表示至今',
  `place_name` VARCHAR(100) NOT NULL COMMENT '地点名称，可填写住所名称或自定义名称',
  `address` VARCHAR(500) DEFAULT NULL COMMENT '详细地址，可补充楼栋和房号，未填写时为空',
  `city` VARCHAR(100) DEFAULT NULL COMMENT '地点所属城市名称，未获取时为空',
  `region_code` VARCHAR(12) DEFAULT NULL COMMENT '所选最深层地区编码，可停在省市区县或乡镇，未选时为空',
  `longitude` DECIMAL(10,6) NOT NULL COMMENT '经度，GCJ02坐标，单位度，范围-180至180',
  `latitude` DECIMAL(9,6) NOT NULL COMMENT '纬度，GCJ02坐标，单位度，范围-90至90',
  `coordinate_system` VARCHAR(16) NOT NULL DEFAULT 'GCJ02' COMMENT '坐标系，固定为GCJ02，写入前须完成坐标转换',
  `provider_uid` VARCHAR(128) DEFAULT NULL COMMENT '百度地点UID，手动选点或未获取时为空',
  `note` TEXT COMMENT '备注，应用限制5000字符，未填写时为空',
  `create_user` BIGINT DEFAULT NULL COMMENT '创建人用户ID，未记录时为空',
  `create_time` DATETIME DEFAULT NULL COMMENT '创建时间，由应用写入，未记录时为空',
  `update_user` BIGINT DEFAULT NULL COMMENT '最后更新人用户ID，未记录时为空',
  `update_time` DATETIME DEFAULT NULL COMMENT '最后更新时间，由应用写入，未记录时为空',
  `is_deleted` INT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记：0=未删除，1=已删除',
  -- 应用生成的足迹ID全表唯一。
  PRIMARY KEY (`id`),
  -- 按用户和删除状态筛选，并按开始日期排序读取足迹。
  KEY `idx_footprint_user_start` (`user_id`, `is_deleted`, `start_date`),
  -- 统一存储GCJ02坐标，避免跨端地图坐标系混用。
  CONSTRAINT `ck_footprint_coordinate` CHECK (`coordinate_system` = 'GCJ02'),
  -- 经度必须处于有效范围，单位为度。
  CONSTRAINT `ck_footprint_lng` CHECK (`longitude` BETWEEN -180 AND 180),
  -- 纬度必须处于有效范围，单位为度。
  CONSTRAINT `ck_footprint_lat` CHECK (`latitude` BETWEEN -90 AND 90),
  -- 结束日期不得早于开始日期；9999-12-31表示至今。
  CONSTRAINT `ck_footprint_end` CHECK (`end_date` >= `start_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='生活足迹，记录长期居住地点';

-- 目的：创建省、市、区县、乡镇街道基础目录，供多端按级选择。
-- 前置：MySQL 8.0.16+；调用方选择目标库。
-- 影响：只创建缺失表，不写入足迹或用户数据；种子数据由后续独立脚本补充。
-- 重跑：表存在时跳过；不覆盖已有结构及目录维护结果。
-- 执行：按upgrade-plan.json顺序执行，遇错停止。
-- 恢复：DDL不可整体回滚；失败后核对结构再重跑，不自动删除目录。
CREATE TABLE IF NOT EXISTS `system_region` (
  `id` BIGINT NOT NULL COMMENT '目录记录ID，初始化数据使用编码映射的固定ID，应用新增使用雪花ID',
  `code` VARCHAR(12) NOT NULL COMMENT '地区编码，保留原始字符串，目录内唯一',
  `parent_code` VARCHAR(12) NOT NULL COMMENT '上级地区编码，0表示省级根节点',
  `name` VARCHAR(100) NOT NULL COMMENT '地区名称，按数据版本保留原名',
  `level` INT NOT NULL COMMENT '地区层级：1=省，2=市，3=区县，4=乡镇街道',
  `data_version` VARCHAR(32) NOT NULL COMMENT '来源版本，NBS-2023表示2023年统计区划快照，不表示当前实时区划',
  `create_user` BIGINT DEFAULT NULL COMMENT '创建人用户ID，初始化数据为空',
  `create_time` DATETIME DEFAULT NULL COMMENT '创建时间，由应用写入，初始化数据为空',
  `update_user` BIGINT DEFAULT NULL COMMENT '最后更新人用户ID，未记录时为空',
  `update_time` DATETIME DEFAULT NULL COMMENT '最后更新时间，未记录时为空',
  `is_deleted` INT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记：0=未删除，1=已删除',
  -- 目录记录唯一标识。
  PRIMARY KEY (`id`),
  -- 编码保留为字符串；软删除后仍保留原编码，补数据不恢复停用地区。
  UNIQUE KEY `uk_system_region_code` (`code`),
  -- 按父编码和未删除状态查询下级，并按编码排序。
  KEY `idx_system_region_parent` (`parent_code`, `is_deleted`, `code`),
  -- 目录最多四级，不包含村级和具体门牌。
  CONSTRAINT `ck_system_region_level` CHECK (`level` BETWEEN 1 AND 4)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='省市区县乡镇街道基础目录，版本化统计区划快照';

CREATE TABLE `device_expense` (
  `id` BIGINT NOT NULL COMMENT '关联ID，由应用生成雪花ID',
  `user_id` BIGINT NOT NULL COMMENT '所属用户ID，用于数据隔离',
  `device_id` BIGINT NOT NULL COMMENT '关联设备ID',
  `expense_id` BIGINT NOT NULL COMMENT '关联支出ID，解绑后复用同一关联记录',
  `create_user` BIGINT DEFAULT NULL COMMENT '首次创建人用户ID，未记录时为空',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '首次创建时间',
  `update_user` BIGINT DEFAULT NULL COMMENT '最后更新人用户ID，未记录时为空',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
  `is_deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记：0=已绑定，1=已解绑',
  -- 应用生成的关联唯一标识。
  PRIMARY KEY (`id`),
  -- 包含已解绑记录也唯一，重新绑定恢复该记录，避免软删除后唯一键冲突。
  UNIQUE KEY `uk_device_expense_expense` (`expense_id`),
  -- 按用户查询设备的有效支出关联。
  KEY `idx_device_expense_user_device` (`user_id`, `device_id`, `is_deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='设备支出关联表';

-- 个人数据导出
CREATE TABLE IF NOT EXISTS `data_export_audit_event` (
  `id` BIGINT NOT NULL COMMENT '记录ID，由应用生成雪花ID',
  `job_id` BIGINT DEFAULT NULL COMMENT '关联任务ID，建单前或无有效任务时为空',
  `user_id` BIGINT DEFAULT NULL COMMENT '所属用户ID，用于个人数据隔离',
  `event_type` VARCHAR(128) NOT NULL COMMENT '审计事件类型',
  `result_code` VARCHAR(128) NOT NULL COMMENT '安全结果码，不含业务内容',
  `request_id` VARCHAR(64) NOT NULL COMMENT '服务端事件请求标识',
  `client_ip` VARCHAR(128) DEFAULT NULL COMMENT '可信代理处理后的来源IP，后台任务为空',
  `trace_id` VARCHAR(64) DEFAULT NULL COMMENT '链路追踪标识，无上下文为空',
  `details` MEDIUMTEXT DEFAULT NULL COMMENT '白名单事件元数据，不含正文或凭据',
  `event_time` DATETIME(3) NOT NULL COMMENT '审计事件时间，上海时区',
  `create_user` BIGINT DEFAULT NULL COMMENT '创建人用户ID，系统操作为空',
  `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '记录创建时间',
  `update_user` BIGINT DEFAULT NULL COMMENT '最后更新人用户ID，系统操作为空',
  `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '最后更新时间',
  `is_deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除、1已删除',
  -- 主键保护记录唯一性。
  PRIMARY KEY (`id`),
  -- 按所属用户、状态或关联任务进行范围查询与清理。
  KEY `idx_data_export_audit_event_job` (`job_id`, `event_time`, `id`),
  -- 按所属用户、状态或关联任务进行范围查询与清理。
  KEY `idx_data_export_audit_event_user` (`user_id`, `event_time`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='个人数据导出追加审计事件';

CREATE TABLE IF NOT EXISTS `data_export_coordinator` (
  `id` BIGINT NOT NULL COMMENT '记录ID，由应用生成雪花ID，控制行固定为1',
  `name` VARCHAR(128) NOT NULL COMMENT '控制行名称，用于串行化领取和月额度结算',
  `create_user` BIGINT DEFAULT NULL COMMENT '创建人用户ID，系统操作为空',
  `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '记录创建时间',
  `update_user` BIGINT DEFAULT NULL COMMENT '最后更新人用户ID，系统操作为空',
  `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '最后更新时间',
  `is_deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除、1已删除',
  -- 主键保护记录唯一性。
  PRIMARY KEY (`id`),
  -- 唯一键防止幂等申请或授权票据重复。
  UNIQUE KEY `uk_data_export_coordinator_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='个人数据导出全局队列控制';

CREATE TABLE IF NOT EXISTS `data_export_grant` (
  `id` BIGINT NOT NULL COMMENT '记录ID，由应用生成雪花ID',
  `user_id` BIGINT NOT NULL COMMENT '所属用户ID，用于个人数据隔离',
  `token_hash` VARCHAR(64) NOT NULL COMMENT '一次性核验票据SHA256摘要',
  `session_hash` VARCHAR(64) NOT NULL COMMENT '申请会话Token摘要，不保存原值',
  `security_hash` VARCHAR(64) NOT NULL COMMENT '账户凭据状态摘要，用于凭据变更后撤销授权',
  `scope_hash` VARCHAR(64) NOT NULL COMMENT '获核验范围和目录版本的SHA256摘要',
  `purpose` VARCHAR(128) NOT NULL COMMENT '授权用途：CREATE创建、DOWNLOAD下载',
  `job_id` BIGINT DEFAULT NULL COMMENT '关联任务ID，建单前或无有效任务时为空',
  `expires_at` DATETIME(3) NOT NULL COMMENT '一次性授权到期时间',
  `consumed` INT NOT NULL COMMENT '核验票据状态：0=未消费，1=已消费',
  `create_user` BIGINT DEFAULT NULL COMMENT '创建人用户ID，系统操作为空',
  `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '记录创建时间',
  `update_user` BIGINT DEFAULT NULL COMMENT '最后更新人用户ID，系统操作为空',
  `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '最后更新时间',
  `is_deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除、1已删除',
  -- 主键保护记录唯一性。
  PRIMARY KEY (`id`),
  -- 唯一键防止幂等申请或授权票据重复。
  UNIQUE KEY `uk_data_export_grant_token` (`token_hash`),
  -- 按所属用户、状态或关联任务进行范围查询与清理。
  KEY `idx_data_export_grant_expiry` (`expires_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='个人数据导出一次性身份核验授权';

CREATE TABLE IF NOT EXISTS `data_export_job` (
  `id` BIGINT NOT NULL COMMENT '记录ID，由应用生成雪花ID',
  `user_id` BIGINT NOT NULL COMMENT '所属用户ID，用于个人数据隔离',
  `request_key` VARCHAR(64) NOT NULL COMMENT '客户端幂等键，同一用户内唯一',
  `request_hash` VARCHAR(64) NOT NULL COMMENT '归一化申请范围的SHA256摘要',
  `scope_json` TEXT NOT NULL COMMENT '已授权数据集键数组，不含业务正文',
  `session_hash` VARCHAR(64) NOT NULL COMMENT '申请会话Token摘要，不保存原值',
  `security_hash` VARCHAR(64) NOT NULL COMMENT '账户凭据状态摘要，用于凭据变更后撤销授权',
  `quota_month` VARCHAR(7) NOT NULL COMMENT '申请所属自然月，上海时区yyyy-MM',
  `quota_charged` INT NOT NULL COMMENT '月额度标记：1=预占或已扣除，0=已退还',
  `status` VARCHAR(128) NOT NULL COMMENT '任务状态：QUEUED排队、RUNNING执行、SUCCEEDED成功、FAILED失败、CANCELLED取消、REVOKED撤销',
  `phase` VARCHAR(128) NOT NULL COMMENT '执行阶段：QUEUED排队、CAPTURING读取、DONE终态',
  `attempt` INT NOT NULL COMMENT '尝试次数，初始0，每次领取加1',
  `lease_token` VARCHAR(128) DEFAULT NULL COMMENT '当前执行租约随机标识，防止迟到执行者发布',
  `lease_until` DATETIME(3) DEFAULT NULL COMMENT '当前租约到期时间，未领取为空',
  `deadline` DATETIME(3) NOT NULL COMMENT '任务最迟结束时间，上海时区',
  `started_at` DATETIME(3) DEFAULT NULL COMMENT '首次开始读取时间，尚未执行为空',
  `finished_at` DATETIME(3) DEFAULT NULL COMMENT '终态时间，尚未结束为空',
  `cancel_requested` INT NOT NULL COMMENT '取消请求：0=未请求，1=用户已请求',
  `error_code` VARCHAR(128) DEFAULT NULL COMMENT '安全错误码，成功或尚未失败为空',
  `summary_json` MEDIUMTEXT DEFAULT NULL COMMENT '数据集行数及文件摘要清单，完成前为空',
  `object_key` VARCHAR(255) DEFAULT NULL COMMENT '专用私有桶内部对象键，不返回客户端',
  `file_name` VARCHAR(128) DEFAULT NULL COMMENT '用户下载文件名，不含个人信息',
  `content_type` VARCHAR(128) DEFAULT NULL COMMENT '生成文件MIME类型，完成前为空',
  `sha256` VARCHAR(64) DEFAULT NULL COMMENT '生成文件SHA256摘要，完成前为空',
  `file_bytes` BIGINT DEFAULT NULL COMMENT '文件字节数，完成前为空，0表示空文件',
  `row_count` INT NOT NULL COMMENT '业务数据总行数，0表示空数据',
  `artifact_status` VARCHAR(128) NOT NULL COMMENT '文件状态：NONE未生成、AVAILABLE可用、EXPIRED过期、DELETED删除、REVOKED撤销',
  `expires_at` DATETIME(3) DEFAULT NULL COMMENT '文件或授权到期时间，文件完成前为空',
  `download_count` INT NOT NULL COMMENT '已授权开始传输次数，中断也计次',
  `create_user` BIGINT DEFAULT NULL COMMENT '创建人用户ID，系统操作为空',
  `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '记录创建时间',
  `update_user` BIGINT DEFAULT NULL COMMENT '最后更新人用户ID，系统操作为空',
  `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '最后更新时间',
  `is_deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除、1已删除，额度有效期内禁止删除任务',
  -- 主键保护记录唯一性。
  PRIMARY KEY (`id`),
  -- 唯一键防止幂等申请或授权票据重复。
  UNIQUE KEY `uk_data_export_job_request` (`user_id`, `request_key`),
  -- 按所属用户、状态或关联任务进行范围查询与清理。
  KEY `idx_data_export_job_month` (`user_id`, `quota_month`, `quota_charged`),
  -- 按所属用户、状态或关联任务进行范围查询与清理。
  KEY `idx_data_export_job_history` (`user_id`, `create_time`, `id`),
  -- 按所属用户、状态或关联任务进行范围查询与清理。
  KEY `idx_data_export_job_queue` (`status`, `create_time`, `id`),
  -- 按所属用户、状态或关联任务进行范围查询与清理。
  KEY `idx_data_export_job_expiry` (`artifact_status`, `expires_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='个人数据导出任务与月额度账本';

-- 固定控制行只补缺失，不覆盖已有数据。
INSERT INTO `data_export_coordinator` (`id`, `name`) VALUES (1, 'global') ON DUPLICATE KEY UPDATE `id` = `id`;

CREATE TABLE IF NOT EXISTS `sys_scheduled_job` (
  `id` BIGINT NOT NULL COMMENT '任务ID，由应用生成雪花ID',
  `job_code` VARCHAR(80) NOT NULL COMMENT '代码注册的任务编码，不允许任意脚本或方法名',
  `name` VARCHAR(100) NOT NULL COMMENT '任务名称',
  `cron` VARCHAR(100) NOT NULL COMMENT '六段Spring Cron，秒为单个整数，每分钟最多一次',
  `zone_id` VARCHAR(40) NOT NULL DEFAULT 'Asia/Shanghai' COMMENT '调度时区，首版固定Asia/Shanghai',
  `enabled` TINYINT NOT NULL DEFAULT 1 COMMENT '自动调度开关：0=暂停，1=启用；不限制手动执行',
  `revision` INT NOT NULL DEFAULT 0 COMMENT '配置版本，从0开始，每次修改递增',
  `create_user` BIGINT DEFAULT NULL COMMENT '创建人用户ID，自动任务为空',
  `create_time` DATETIME DEFAULT NULL COMMENT '创建时间',
  `update_user` BIGINT DEFAULT NULL COMMENT '最后更新人用户ID，自动任务为空',
  `update_time` DATETIME DEFAULT NULL COMMENT '最后更新时间',
  `is_deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记：0=未删除，1=已删除',
  -- 雪花主键标识任务。
  PRIMARY KEY (`id`),
  -- 编码全表唯一，含软删除记录，启动初始化不得恢复已删除配置。
  UNIQUE KEY `uk_sys_scheduled_job_code` (`job_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='可配置定时任务';

CREATE TABLE IF NOT EXISTS `sys_scheduled_job_log` (
  `id` BIGINT NOT NULL COMMENT '执行日志ID，由应用生成雪花ID',
  `job_id` BIGINT NOT NULL COMMENT '所属定时任务ID',
  `trigger_key` VARCHAR(80) NOT NULL COMMENT '自动执行为AUTO加计划时点毫秒值，手动及跳过记录为UUID',
  `trigger_type` VARCHAR(10) NOT NULL COMMENT '触发类型：AUTO=定时，MANUAL=手动',
  `status` VARCHAR(20) NOT NULL COMMENT '状态：RUNNING=执行中，SUCCESS=方法完成，FAILED=异常，SKIPPED=跳过，INTERRUPTED=结果未知',
  `scheduled_time` DATETIME DEFAULT NULL COMMENT '计划触发时间，Asia/Shanghai，手动触发为空',
  `start_time` DATETIME NOT NULL COMMENT '执行或跳过时间，Asia/Shanghai',
  `end_time` DATETIME DEFAULT NULL COMMENT '完成或检测中断时间，Asia/Shanghai，执行中为空',
  `duration_ms` BIGINT DEFAULT NULL COMMENT '执行耗时毫秒，0=不足1毫秒或跳过，中断及执行中为空',
  `message` VARCHAR(1000) DEFAULT NULL COMMENT '执行结果摘要，不存储外部响应或敏感参数，执行中为空',
  `instance_id` VARCHAR(36) NOT NULL COMMENT '执行服务实例启动时生成的UUID',
  `create_user` BIGINT DEFAULT NULL COMMENT '创建人用户ID，自动任务为空',
  `create_time` DATETIME DEFAULT NULL COMMENT '创建时间',
  `update_user` BIGINT DEFAULT NULL COMMENT '最后更新人用户ID，自动任务为空',
  `update_time` DATETIME DEFAULT NULL COMMENT '最后更新时间',
  `is_deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记：0=未删除，1=已删除',
  -- 雪花主键标识执行日志。
  PRIMARY KEY (`id`),
  -- 自动触发同一任务同一时点只允许一条执行记录，软删除也不重放。
  UNIQUE KEY `uk_sys_scheduled_job_log_trigger` (`job_id`, `trigger_key`),
  -- 按任务读取未删除日志，按执行时间和ID倒序分页。
  KEY `idx_sys_scheduled_job_log_page` (`job_id`, `is_deleted`, `start_time`, `id`),
  -- 执行锁空闲时查找遗留执行状态。
  KEY `idx_sys_scheduled_job_log_status` (`job_id`, `is_deleted`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='定时任务执行日志';
