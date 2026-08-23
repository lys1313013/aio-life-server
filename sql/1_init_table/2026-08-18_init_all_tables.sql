-- =====================================================
-- AIO Life 数据库初始化脚本（全量建表）
-- 创建时间: 2026-08-18
-- 说明: 由原 1_init_table 目录下全部历史脚本合并而成，
--       所有 ALTER TABLE ADD/MODIFY/DROP COLUMN 已合并进最终结构，
--       同名列以日期靠后的脚本为准。
-- 统一约定: InnoDB + utf8mb4 + utf8mb4_unicode_ci
-- =====================================================

-- 创建库
create database if not exists `aio_life`;

-- 切换库
use `aio_life`;

-- =====================================================
-- 用户与认证
-- =====================================================

CREATE TABLE IF NOT EXISTS `user` (
    `id` bigint(20) NOT NULL AUTO_INCREMENT,
    `username` varchar(50) NOT NULL COMMENT '用户名',
    `password` varchar(255) NOT NULL COMMENT '密码',
    `nickname` varchar(50) NOT NULL COMMENT '昵称',
    `create_user` bigint(20) DEFAULT NULL COMMENT '创建人',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_user` bigint(20) DEFAULT NULL COMMENT '更新人',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `last_active_at` timestamp NULL DEFAULT NULL COMMENT '最后活跃时间',
    `email` varchar(50) DEFAULT NULL COMMENT '邮箱',
    `avatar` varchar(100) DEFAULT NULL,
    `password_salt` varchar(32) DEFAULT NULL COMMENT '密码盐值',
    `role` varchar(50) DEFAULT 'user' COMMENT '角色类型',
    `introduction` varchar(255) DEFAULT NULL COMMENT '个人简介',
    `is_deleted` tinyint(4) NOT NULL DEFAULT '0',
    `secondary_password` varchar(128) DEFAULT NULL COMMENT '二级密码（加盐哈希）',
    `secondary_password_salt` varchar(64) DEFAULT NULL COMMENT '二级密码盐值',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

CREATE TABLE IF NOT EXISTS `user_bind` (
    `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id` bigint(20) NOT NULL COMMENT '本系统用户ID',
    `platform` varchar(32) NOT NULL COMMENT '平台类型：github, leetcode, shanbay',
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
    `username` text COMMENT '账号（SM4加密存储）',
    `password` text COMMENT '密码（SM4加密存储）',
    `salt` varchar(64) NOT NULL COMMENT 'PBKDF2盐值，每条记录唯一',
    `remark` text COMMENT '备注（SM4加密存储）',
    `favorite` boolean DEFAULT FALSE COMMENT '是否收藏',
    `is_deleted` tinyint DEFAULT 0 COMMENT '是否删除：0-未删除，1-已删除',
    `create_time` datetime DEFAULT NULL COMMENT '创建时间',
    `update_time` datetime DEFAULT NULL COMMENT '更新时间',
    `create_user` bigint DEFAULT NULL COMMENT '创建人ID',
    `update_user` bigint DEFAULT NULL COMMENT '更新人ID',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='密码库表';

CREATE TABLE IF NOT EXISTS `user_secondary_lock_menu` (
    `id` bigint NOT NULL COMMENT '主键（雪花）',
    `user_id` bigint NOT NULL COMMENT '用户ID',
    `menu_id` bigint NOT NULL COMMENT '菜单ID',
    `is_deleted` tinyint DEFAULT 0 COMMENT '是否删除：0-未删除，1-已删除',
    `create_time` datetime DEFAULT NULL COMMENT '创建时间',
    `update_time` datetime DEFAULT NULL COMMENT '更新时间',
    `create_user` bigint DEFAULT NULL COMMENT '创建人ID',
    `update_user` bigint DEFAULT NULL COMMENT '更新人ID',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户二级锁菜单关联';

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
    `is_deleted` int DEFAULT 0 COMMENT '是否删除(0-否,1-是)',
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
    `id` bigint(20) NOT NULL AUTO_INCREMENT,
    `user_id` bigint(20) DEFAULT NULL COMMENT '用户ID',
    `username` varchar(50) NOT NULL COMMENT '用户名',
    `password` varchar(255) DEFAULT NULL COMMENT '明文密码（仅登录失败时记录）——【有意保留，勿删勿改】：用于抓取弱密码/常见密码样本构建密码本日常排查。属安全审计功能，非缺陷。',
    `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `ip_address` varchar(45) NOT NULL COMMENT 'IP地址',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='登录日志';

CREATE TABLE IF NOT EXISTS `mail_log` (
    `id` bigint(20) NOT NULL COMMENT '主键ID',
    `send_to` varchar(100) NOT NULL COMMENT '接收者邮箱',
    `subject` varchar(255) DEFAULT NULL COMMENT '邮件标题',
    `content` text COMMENT '邮件内容',
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

CREATE TABLE IF NOT EXISTS `sys_menu` (
    `id` bigint NOT NULL COMMENT '主键ID',
    `parent_id` bigint DEFAULT 0 COMMENT '父级ID（0为根）',
    `name` varchar(64) NOT NULL COMMENT '路由名称',
    `path` varchar(255) NOT NULL COMMENT '路由路径（唯一）',
    `component` varchar(255) DEFAULT NULL COMMENT '组件标识（BasicLayout/IFrameView 或 views 相对路径，如 system/user/index）',
    `redirect` varchar(255) DEFAULT NULL COMMENT '重定向',
    `meta` json DEFAULT NULL COMMENT '路由 meta（title/icon/order/keepAlive/hideInMenu/link等）',
    `roles` varchar(255) DEFAULT NULL COMMENT '可访问角色（逗号分隔，空表示所有）',
    `sort` int NOT NULL DEFAULT 0 COMMENT '排序',
    `status` tinyint NOT NULL DEFAULT 1 COMMENT '状态（1启用，0禁用）',
    `create_user` bigint DEFAULT NULL COMMENT '创建人',
    `create_time` datetime DEFAULT NULL COMMENT '创建时间',
    `update_user` bigint DEFAULT NULL COMMENT '更新人',
    `update_time` datetime DEFAULT NULL COMMENT '更新时间',
    `is_deleted` int DEFAULT 0 COMMENT '是否删除(0-否,1-是)',
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
    `dict_id` bigint(20) NOT NULL,
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
    `is_deleted` tinyint DEFAULT 0 COMMENT '是否删除：0-未删除，1-已删除',
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
    `is_deleted` tinyint DEFAULT 0 COMMENT '是否删除：0-未删除，1-已删除',
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
    `is_deleted` tinyint DEFAULT 0 COMMENT '是否删除：0-未删除，1-已删除',
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
    `icon` varchar(100) DEFAULT NULL COMMENT '图标名称(Iconify格式)',
    `time_type` tinyint NOT NULL DEFAULT 1 COMMENT '时间类型: 1-必须, 2-积极, 3-休闲',
    PRIMARY KEY (`id`),
    KEY `idx_user_code` (`user_id`, `code`)
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
    `is_deleted` tinyint(4) NOT NULL DEFAULT '0',
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
    `content` varchar(100) DEFAULT NULL,
    `detail` varchar(1000) DEFAULT NULL,
    `column_id` bigint DEFAULT NULL,
    `due_date` datetime DEFAULT NULL COMMENT '最后时间',
    `sort_order` int(11) DEFAULT NULL COMMENT '排序字段',
    `is_deleted` int(11) NOT NULL DEFAULT '0',
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
    `bg_color` varchar(10) DEFAULT NULL,
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
    `user_id` bigint DEFAULT NULL COMMENT '用户ID',
    `type` tinyint DEFAULT NULL COMMENT '目标类型：1=年度目标，2=月度目标，3=日目标',
    `title` varchar(255) DEFAULT NULL COMMENT '目标标题',
    `description` text COMMENT '目标描述',
    `content` text COMMENT '目标详细内容/行动计划',
    `status` tinyint DEFAULT NULL COMMENT '目标状态：0=待开始，1=进行中，2=已完成，3=已放弃',
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
    `is_deleted` tinyint DEFAULT 0 COMMENT '是否删除：0=未删除，1=已删除',
    `create_time` datetime DEFAULT NULL COMMENT '创建时间',
    `update_time` datetime DEFAULT NULL COMMENT '更新时间',
    `create_user` bigint DEFAULT NULL COMMENT '创建人ID',
    `update_user` bigint DEFAULT NULL COMMENT '更新人ID',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='目标管理表';

CREATE TABLE IF NOT EXISTS `honor_category` (
    `id` bigint NOT NULL COMMENT '分类ID',
    `user_id` bigint DEFAULT NULL COMMENT '用户ID（NULL表示系统预设分类）',
    `name` varchar(50) NOT NULL COMMENT '分类名称',
    `icon` varchar(255) DEFAULT NULL COMMENT '分类图标',
    `color` varchar(20) DEFAULT NULL COMMENT '分类颜色',
    `sort_order` int DEFAULT 0 COMMENT '排序顺序',
    `is_deleted` tinyint DEFAULT 0 COMMENT '是否删除：0-未删除，1-已删除',
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
    `is_deleted` tinyint DEFAULT 0 COMMENT '是否删除：0-未删除，1-已删除',
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
    `is_deleted` tinyint DEFAULT 0 COMMENT '是否删除：0-未删除，1-已删除',
    `create_time` datetime DEFAULT NULL COMMENT '创建时间',
    `update_time` datetime DEFAULT NULL COMMENT '更新时间',
    `create_user` bigint DEFAULT NULL COMMENT '创建人ID',
    `update_user` bigint DEFAULT NULL COMMENT '更新人ID',
    PRIMARY KEY (`id`),
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
    `title` varchar(100) DEFAULT NULL,
    `content` text COMMENT '备忘录内容',
    `hidden_content` tinyint(1) DEFAULT 0 COMMENT '是否隐藏内容',
    `create_user` bigint(20) DEFAULT NULL COMMENT '创建人',
    `create_time` datetime DEFAULT NULL COMMENT '创建时间',
    `update_user` bigint(20) DEFAULT NULL COMMENT '更新人',
    `update_time` datetime DEFAULT NULL COMMENT '更新时间',
    `is_deleted` int(11) DEFAULT '0' COMMENT '是否删除 0-未删除 1-已删除',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='备忘录表';

CREATE TABLE IF NOT EXISTS `thought` (
    `id` bigint(20) NOT NULL AUTO_INCREMENT,
    `content` mediumtext NOT NULL COMMENT '内容',
    `user_id` bigint(19) NOT NULL COMMENT '用户 ID',
    `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_user` bigint(20) DEFAULT NULL,
    `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
    `update_user` bigint(20) DEFAULT NULL,
    `is_deleted` int(11) DEFAULT '0',
    `is_pinned` tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否固定(用于在看板等重要位置展示): 0-否, 1-是',
    `hidden_content` tinyint(1) DEFAULT 0 COMMENT '是否隐藏内容',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='思考表';

CREATE TABLE IF NOT EXISTS `thought_rela_event` (
    `id` bigint(20) NOT NULL AUTO_INCREMENT,
    `thought_id` bigint(20) NOT NULL,
    `content` text NOT NULL,
    `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_user` bigint(20) DEFAULT NULL,
    `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
    `update_user` bigint(20) DEFAULT NULL,
    `is_deleted` int(11) DEFAULT '0',
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
    `is_deleted` int(11) DEFAULT '0' COMMENT '是否删除',
    `create_user` bigint(20) DEFAULT NULL COMMENT '创建人',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_user` bigint(20) DEFAULT NULL COMMENT '修改人',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `exp_desc` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
    `transaction_id` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
    `counterparty` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
    `counterparty_acct` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
    `merchant_order_no` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '商家订单号',
    `transaction_status` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '交易状态',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='支出表';

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
    `is_deleted` int(11) DEFAULT '0' COMMENT '是否删除',
    `inc_type_id` bigint NOT NULL COMMENT '收入类型',
    `tax` decimal(10,2) DEFAULT NULL,
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
    `exercise_count` int(11) NOT NULL DEFAULT '0',
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
    `status` tinyint(4) DEFAULT '0' COMMENT '状态：0-未开始，1-阅读中，2-已读完，3-搁置',
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
    `is_deleted` tinyint(4) DEFAULT '0' COMMENT '是否删除：0-未删除，1-已删除',
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
    `status` tinyint(4) DEFAULT '0' COMMENT '状态：0-想看，1-在看，2-看过，3-搁置',
    `total_progress` int(11) DEFAULT '0' COMMENT '总进度（总集数或时长）',
    `current_progress` int(11) DEFAULT '0' COMMENT '当前进度（当前集数或观看时长）',
    `start_time` datetime DEFAULT NULL COMMENT '开始观看时间',
    `finish_time` datetime DEFAULT NULL COMMENT '看完时间',
    `remark` varchar(1000) DEFAULT NULL COMMENT '短评/备注',
    `user_id` bigint(20) NOT NULL COMMENT '归属用户ID',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `create_user` bigint(20) DEFAULT NULL COMMENT '创建人',
    `update_user` bigint(20) DEFAULT NULL COMMENT '更新人',
    `is_deleted` tinyint(4) DEFAULT '0' COMMENT '是否删除：0-未删除，1-已删除',
    `file_id` varchar(32) DEFAULT NULL COMMENT '封面文件ID(UUID)',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='影视记录表';

CREATE TABLE IF NOT EXISTS `b_video` (
    `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `title` varchar(500) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '视频标题',
    `url` varchar(1000) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'B站视频URL',
    `cover` varchar(1000) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '视频封面URL',
    `duration` int(11) NOT NULL COMMENT '视频时长（秒）',
    `watched_duration` int(11) NOT NULL DEFAULT '0' COMMENT '观看时长',
    `episodes` int(11) DEFAULT '1' COMMENT '总集数',
    `current_episode` int(11) DEFAULT '1' COMMENT '当前观看集数',
    `status` int(11) DEFAULT NULL COMMENT '学习状态',
    `last_watched` datetime DEFAULT NULL COMMENT '最后观看时间（syncProgress 同步时写入）',
    `notes` text COLLATE utf8mb4_unicode_ci COMMENT '学习笔记',
    `bvid` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'BV号',
    `aid` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'AV号',
    `description` text COLLATE utf8mb4_unicode_ci COMMENT '视频描述',
    `owner_name` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
    `pages_info` json DEFAULT NULL COMMENT '分集信息：cid-分集ID, page-页码, part-分集标题, duration-分集时长',
    `user_id` bigint(20) DEFAULT NULL COMMENT '用户 ID',
    `create_user` bigint(20) DEFAULT NULL COMMENT '创建人',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_user` bigint(20) DEFAULT NULL COMMENT '修改人',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `is_deleted` int(11) DEFAULT '0' COMMENT '是否删除',
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
    `performance_name` varchar(100) NOT NULL COMMENT '演出名称',
    `performer` varchar(50) DEFAULT NULL,
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
    `is_deleted` tinyint DEFAULT 0 COMMENT '是否删除：0-未删除，1-已删除',
    PRIMARY KEY (`id`)
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
    `create_user` bigint(20) DEFAULT NULL COMMENT '创建人',
    `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_user` bigint(20) DEFAULT NULL COMMENT '更新人',
    `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `is_deleted` tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否删除: 0-未删除, 1-已删除',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='设备表';

CREATE TABLE IF NOT EXISTS `membership_record` (
    `id` bigint NOT NULL COMMENT '主键ID',
    `user_id` bigint NOT NULL COMMENT '用户ID',
    `name` varchar(100) NOT NULL COMMENT '会员名称',
    `category` varchar(50) NOT NULL DEFAULT 'other' COMMENT '分类:video/music/shopping/cloud/study/game/other',
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
    `is_deleted` tinyint DEFAULT 0 COMMENT '是否删除',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_expiry_date` (`expiry_date`)
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
    `create_user` bigint DEFAULT NULL,
    `create_time` datetime NOT NULL,
    `update_user` bigint DEFAULT NULL,
    `update_time` datetime NOT NULL,
    `is_deleted` tinyint NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户通知渠道配置';

CREATE TABLE IF NOT EXISTS `notification_preference` (
    `id` bigint NOT NULL COMMENT 'ID',
    `user_id` bigint NOT NULL,
    `biz_type` varchar(64) NOT NULL,
    `channel` varchar(32) NOT NULL COMMENT '通知渠道：FEISHU',
    `enabled` tinyint NOT NULL DEFAULT 1,
    `create_user` bigint DEFAULT NULL,
    `create_time` datetime NOT NULL,
    `update_user` bigint DEFAULT NULL,
    `update_time` datetime NOT NULL,
    `is_deleted` tinyint NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户通知偏好';

CREATE TABLE IF NOT EXISTS `notification_delivery` (
    `id` bigint NOT NULL COMMENT 'ID',
    `dedup_key` varchar(128) NOT NULL,
    `user_id` bigint NOT NULL,
    `biz_type` varchar(64) NOT NULL,
    `channel` varchar(32) NOT NULL,
    `status` varchar(16) NOT NULL COMMENT 'PENDING/SUCCESS/RETRY/FAILED',
    `payload_ciphertext` mediumtext NOT NULL COMMENT '重试所需的加密消息载荷',
    `retry_count` int NOT NULL DEFAULT 0,
    `next_retry_time` datetime DEFAULT NULL,
    `provider_code` varchar(64) DEFAULT NULL,
    `error_message` varchar(512) DEFAULT NULL,
    `create_user` bigint DEFAULT NULL,
    `create_time` datetime NOT NULL,
    `update_user` bigint DEFAULT NULL,
    `update_time` datetime NOT NULL,
    `is_deleted` tinyint NOT NULL DEFAULT 0,
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
    `is_deleted` tinyint DEFAULT 0 COMMENT '是否删除',
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
    `create_user` bigint DEFAULT NULL COMMENT '创建人',
    `create_time` datetime DEFAULT NULL COMMENT '创建时间',
    `update_user` bigint DEFAULT NULL COMMENT '更新人',
    `update_time` datetime DEFAULT NULL COMMENT '更新时间',
    `is_deleted` tinyint DEFAULT 0 COMMENT '是否删除',
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
    `is_deleted` int DEFAULT 0 COMMENT '是否删除(0-否,1-是)',
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
    `is_deleted` int DEFAULT 0 COMMENT '是否删除(0-否,1-是)',
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
    `is_deleted` int DEFAULT 0 COMMENT '是否删除(0-否,1-是)',
    PRIMARY KEY (`id`),
    KEY `idx_cbti_result_user_id` (`user_id`),
    KEY `idx_cbti_result_personality_code` (`personality_code`),
    KEY `idx_cbti_result_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='CBTI 测试历史表';

-- =====================================================
-- 其他 / AI —— LLM / AI 对话
-- =====================================================

CREATE TABLE IF NOT EXISTS `llm_key` (
    `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `user_id` bigint(20) NOT NULL COMMENT '用户ID',
    `model_name` varchar(100) NOT NULL COMMENT '模型名称',
    `api_key` varchar(255) NOT NULL COMMENT 'API密钥（加密存储）',
    `base_url` varchar(255) NOT NULL COMMENT '基础URL',
    `is_default` int(1) DEFAULT '0' COMMENT '是否默认：0-否，1-是',
    `create_user` bigint(20) DEFAULT NULL COMMENT '创建人',
    `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_user` bigint(20) DEFAULT NULL COMMENT '更新人',
    `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `is_deleted` tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否删除: 0-未删除, 1-已删除',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='大模型密钥表';

CREATE TABLE IF NOT EXISTS `conversation` (
    `id` bigint(20) NOT NULL COMMENT '会话ID',
    `user_id` bigint(20) NOT NULL COMMENT '用户ID',
    `title` varchar(255) DEFAULT NULL COMMENT '会话标题',
    `create_user` bigint(20) DEFAULT NULL COMMENT '创建人',
    `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_user` bigint(20) DEFAULT NULL COMMENT '更新人',
    `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `is_deleted` tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否删除',
    PRIMARY KEY (`id`),
    KEY `idx_conversation_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI会话表';

CREATE TABLE IF NOT EXISTS `chat_message` (
    `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `user_id` bigint(20) NOT NULL COMMENT '用户ID',
    `conversation_id` bigint(20) DEFAULT NULL COMMENT '会话ID',
    `role` varchar(50) NOT NULL COMMENT '角色: user-用户, assistant-助手',
    `content` text COMMENT '消息内容',
    `model_name` varchar(100) DEFAULT NULL COMMENT '模型名称',
    `create_user` bigint(20) DEFAULT NULL COMMENT '创建人',
    `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_user` bigint(20) DEFAULT NULL COMMENT '更新人',
    `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `is_deleted` tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否删除: 0-未删除, 1-已删除',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_conversation_id` (`conversation_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI对话消息表';

-- =====================================================
-- 其他 / AI —— 文件存储
-- =====================================================

CREATE TABLE IF NOT EXISTS `file` (
    `id` varchar(32) NOT NULL COMMENT '主键UUID',
    `file_name` varchar(255) NOT NULL COMMENT '文件原名',
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
