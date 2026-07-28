
-- 创建库
create database if not exists `aio-life`;

-- 切换库
use  `aio-life`;

CREATE TABLE IF NOT EXISTS `user` (
                        `id` bigint(20) NOT NULL AUTO_INCREMENT,
                        `username` varchar(50) NOT NULL COMMENT '用户名',
                        `password` varchar(255) NOT NULL COMMENT '密码',
                        `nickname` varchar(50) NOT NULL COMMENT '昵称',
                        `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                        `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                        `email` varchar(50) DEFAULT NULL COMMENT '邮箱',
                        `avatar` varchar(100) DEFAULT NULL,
                        `password_salt` varchar(32) DEFAULT NULL COMMENT '密码盐值',
                        `role` varchar(50) DEFAULT 'user' COMMENT '角色类型',
                        `introduction` varchar(255) DEFAULT NULL COMMENT '个人简介',
                        `is_deleted` tinyint(4) NOT NULL DEFAULT '0',
                        PRIMARY KEY (`id`),
                        UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

CREATE TABLE IF NOT EXISTS `user_bind` (
                             `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
                             `user_id` bigint(20) NOT NULL COMMENT '本系统用户ID',
                             `platform` varchar(32) NOT NULL COMMENT '平台类型：github, leetcode, shanbay',
                             `platform_username` varchar(128) DEFAULT NULL COMMENT '第三方平台的用户名/账号',
                             `access_token` text COMMENT '访问令牌',
                             `meta_fields` json DEFAULT NULL COMMENT '额外配置(JSON)',
                             `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '绑定时间',
                             `create_user` bigint(20) NOT NULL COMMENT '创建人',
                             `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                             `update_user` bigint(20) NOT NULL COMMENT '更新人',
                             `is_deleted` tinyint(4) NOT NULL DEFAULT '0' COMMENT '是否删除',
                             PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户第三方账号绑定表';

CREATE TABLE IF NOT EXISTS `b_video` (
                           `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
                           `title` varchar(500) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '视频标题',
                           `url` varchar(1000) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'B站视频URL',
                           `cover` varchar(1000) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '视频封面URL',
                           `duration` int(11) NOT NULL COMMENT '视频时长（秒）',
                           `watched_duration` int(11) NOT NULL DEFAULT '0' COMMENT '观看时长',
                           `episodes` int(11) DEFAULT '1' COMMENT '总集数',
                           `current_episode` int(11) DEFAULT '1' COMMENT '当前观看集数',
                           `progress` decimal(5,2) DEFAULT '0.00' COMMENT '观看进度（百分比）',
                           `status` int(11) DEFAULT NULL COMMENT '学习状态',
                           `last_watched` datetime DEFAULT NULL COMMENT '最后观看时间',
                           `added_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '添加时间',
                           `notes` text COLLATE utf8mb4_unicode_ci COMMENT '学习笔记',
                           `bvid` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'BV号',
                           `aid` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'AV号',
                           `description` text COLLATE utf8mb4_unicode_ci COMMENT '视频描述',
                           `owner_name` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
                           `pages_info` json DEFAULT NULL COMMENT '分集信息：cid-分集ID, page-页码, part-分集标题, duration-分集时长',
                           `user_id` bigint(20) DEFAULT NULL COMMENT '用户 ID',
                           `create_user` bigint(20) DEFAULT NULL COMMENT '创建人',
                           `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                           `update_user` bigint(20) DEFAULT NULL COMMENT '修改人',
                           `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                           `is_deleted` int(11) DEFAULT '0' COMMENT '是否删除',
                           PRIMARY KEY (`id`),
                           KEY `idx_status` (`status`),
                           KEY `idx_progress` (`progress`),
                           KEY `idx_added_at` (`added_at`),
                           KEY `idx_bvid` (`bvid`),
                           KEY `idx_aid` (`aid`),
                           KEY `idx_title` (`title`(100))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='B 站视频记录表';

CREATE TABLE IF NOT EXISTS `device` (
                          `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
                          `user_id` bigint(20) NOT NULL COMMENT '用户ID',
                          `name` varchar(255) NOT NULL COMMENT '设备名称',
                          `type` varchar(255) DEFAULT NULL COMMENT '设备类型',
                          `status` varchar(255) DEFAULT NULL COMMENT '设备状态',
                          `remark` varchar(255) DEFAULT NULL COMMENT '备注',
                          `purchase_date` varchar(255) DEFAULT NULL COMMENT '购买日期',
                          `purchase_price` decimal(10,2) DEFAULT NULL COMMENT '购买价格',
                          `purchase_place` varchar(255) DEFAULT NULL COMMENT '购买地点',
                          `purchase_company` varchar(255) DEFAULT NULL COMMENT '购买公司',
                          `image` varchar(255) DEFAULT NULL COMMENT '图片',
                          `order_number` varchar(255) DEFAULT NULL COMMENT '订单号',
                          PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='设备表';

CREATE TABLE IF NOT EXISTS `enum_type` (
                             `type_id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '类型ID',
                             `type_name` varchar(50) NOT NULL COMMENT '类型名称（英文唯一标识）',
                             `description` varchar(255) DEFAULT NULL COMMENT '类型描述',
                             `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                             `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
                             PRIMARY KEY (`type_id`),
                             UNIQUE KEY `type_name` (`type_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='枚举类型表';

CREATE TABLE IF NOT EXISTS `exercise_record` (
                                   `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
                                   `user_id` bigint(20) NOT NULL COMMENT '用户ID',
                                   `exercise_type_id` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '运动类型',
                                   `exercise_date` date NOT NULL COMMENT '运动日期',
                                   `exercise_count` int(11) NOT NULL DEFAULT '0',
                                   `description` text COLLATE utf8mb4_unicode_ci COMMENT '运动描述',
                                   `create_user` bigint(20) NOT NULL COMMENT '创建用户',
                                   `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                   `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                   `update_user` bigint(20) DEFAULT NULL COMMENT '更新用户',
                                   `is_deleted` int(11) NOT NULL DEFAULT '0' COMMENT '是否删除（0：未删除，1：已删除）',
                                   `time_id` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '时间ID',
                                   PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='运动记录表';

CREATE TABLE IF NOT EXISTS `expense` (
                           `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '账单ID',
                           `amt` decimal(10,2) NOT NULL COMMENT '花费金额',
                           `transaction_amt` decimal(10,2) DEFAULT NULL COMMENT '交易金额',
                           `exp_type_id` int(11) NOT NULL COMMENT '支出类型ID',
                           `pay_type_id` int(11) DEFAULT NULL COMMENT '支付类型ID',
                           `remark` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
                           `user_id` bigint(20) DEFAULT NULL COMMENT '用户id',
                           `exp_time` datetime NOT NULL COMMENT '支出时间',
                           `is_deleted` int(11) DEFAULT '0' COMMENT '是否删除',
                           `create_user` bigint(20) DEFAULT NULL COMMENT '创建人',
                           `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                           `update_user` bigint(20) DEFAULT NULL COMMENT '修改人',
                           `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                           `exp_desc` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
                           `transaction_id` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
                           `counterparty` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
                           `counterparty_acct` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
                           `merchant_order_no` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '商家订单号',
                           `transaction_status` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '交易状态',
                           PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='支出表';

CREATE TABLE IF NOT EXISTS `income` (
                          `income_id` bigint(20) NOT NULL AUTO_INCREMENT,
                          `amt` decimal(10,2) NOT NULL COMMENT '收入',
                          `inc_date` date NOT NULL COMMENT '收入时间',
                          `remark` varchar(255) DEFAULT NULL COMMENT '备注',
                          `user_id` bigint(20) NOT NULL COMMENT '用户id',
                          `create_user` bigint(20) DEFAULT NULL COMMENT '创建人',
                          `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                          `update_user` bigint(20) DEFAULT NULL COMMENT '修改人',
                          `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                          `is_deleted` int(11) DEFAULT '0' COMMENT '是否删除',
                          `inc_type_id` int(11) NOT NULL COMMENT '收入类型',
                          `tax` decimal(10,2) DEFAULT NULL,
                          PRIMARY KEY (`income_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='收入表';

CREATE TABLE IF NOT EXISTS `login_log` (
                             `id` bigint(20) NOT NULL AUTO_INCREMENT,
                             `user_id` bigint(20) DEFAULT NULL COMMENT '用户ID',
                             `username` varchar(50) NOT NULL COMMENT '用户名',
                             `password` varchar(255) DEFAULT NULL COMMENT '密码 密码错误时存储',
                             `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                             `ip_address` varchar(45) NOT NULL COMMENT '创建时间',
                             PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='登录日志';

CREATE TABLE IF NOT EXISTS `memo` (
                        `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
                        `user_id` bigint(20) NOT NULL COMMENT '用户ID',
                        `title` varchar(100) DEFAULT NULL,
                        `content` text COMMENT '备忘录内容',
                        `create_user` bigint(20) DEFAULT NULL COMMENT '创建人',
                        `create_time` datetime DEFAULT NULL COMMENT '创建时间',
                        `update_user` bigint(20) DEFAULT NULL COMMENT '更新人',
                        `update_time` datetime DEFAULT NULL COMMENT '更新时间',
                        `is_deleted` int(11) DEFAULT '0' COMMENT '是否删除 0-未删除 1-已删除',
                        PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='备忘录表';

CREATE TABLE IF NOT EXISTS `milestone` (
                             `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
                             `user_id` bigint(20) NOT NULL COMMENT '用户ID',
                             `title` varchar(255) NOT NULL COMMENT '标题',
                             `description` text COMMENT '详细描述',
                             `date` date NOT NULL COMMENT '开始日期',
                             `end_date` date DEFAULT NULL COMMENT '结束日期',
                             `type` varchar(50) NOT NULL DEFAULT 'other' COMMENT '类型: work, study, life, other',
                             `tags` json DEFAULT NULL COMMENT '标签数组',
                             `create_user` bigint(20) NOT NULL COMMENT '创建人',
                             `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                             `update_user` bigint(20) NOT NULL COMMENT '更新人',
                             `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                             `is_deleted` int(11) NOT NULL DEFAULT '0' COMMENT '是否删除',
                             PRIMARY KEY (`id`),
                             KEY `idx_user_date` (`user_id`,`date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='里程碑表';

CREATE TABLE IF NOT EXISTS `performance` (
                               `id` int(11) NOT NULL AUTO_INCREMENT COMMENT '唯一标识',
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
                               `image_url` varchar(255) DEFAULT NULL COMMENT '演出海报/票根图片链接',
                               `purchase_platform` varchar(50) DEFAULT NULL COMMENT '购票平台',
                               `order_number` varchar(50) DEFAULT NULL COMMENT '购票订单号',
                               `create_by` int(11) DEFAULT NULL COMMENT '创建人',
                               `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                               `update_by` int(11) DEFAULT NULL COMMENT '修改人',
                               `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                               PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='演出表';

CREATE TABLE IF NOT EXISTS `performance_record` (
                                      `id` int(11) NOT NULL AUTO_INCREMENT COMMENT '唯一标识',
                                      `performance_name` varchar(100) NOT NULL COMMENT '演出名称',
                                      `performance_type` varchar(50) NOT NULL COMMENT '演出类型(演唱会/话剧/音乐会等)',
                                      `performance_date` datetime NOT NULL COMMENT '演出日期',
                                      `city` varchar(50) NOT NULL COMMENT '演出城市',
                                      `venue` varchar(100) NOT NULL COMMENT '演出地点',
                                      `ticket_price` decimal(10,2) NOT NULL COMMENT '票价',
                                      `seat_info` varchar(100) DEFAULT NULL COMMENT '座位信息',
                                      `duration` int(11) DEFAULT NULL COMMENT '演出时长(分钟)',
                                      `rating` tinyint(4) DEFAULT NULL COMMENT '演出评分(1-5)',
                                      `review` text COMMENT '演出评价',
                                      `image_url` varchar(255) DEFAULT NULL COMMENT '演出海报/票根图片链接',
                                      `purchase_platform` varchar(50) DEFAULT NULL COMMENT '购票平台',
                                      `order_number` varchar(50) DEFAULT NULL COMMENT '购票订单号',
                                      `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                      `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                      PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='演出记录表';

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
                                 `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
                                 `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                 `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
                                 `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                 `remark` varchar(500) DEFAULT NULL COMMENT '备注',
                                 PRIMARY KEY (`dict_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='字典数据表';

CREATE TABLE IF NOT EXISTS `sys_dict_type` (
                                 `dict_id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '字典主键',
                                 `dict_name` varchar(100) DEFAULT '' COMMENT '字典名称',
                                 `dict_type` varchar(100) DEFAULT '' COMMENT '字典类型',
                                 `status` char(1) DEFAULT '0' COMMENT '状态（0正常 1停用）',
                                 `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
                                 `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                 `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
                                 `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                 `remark` varchar(500) DEFAULT NULL COMMENT '备注',
                                 PRIMARY KEY (`dict_id`),
                                 UNIQUE KEY `dict_type` (`dict_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='字典类型表';

CREATE TABLE IF NOT EXISTS `task` (
                        `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
                        `user_id` bigint(20) NOT NULL COMMENT '用户ID',
                        `content` varchar(100) DEFAULT NULL,
                        `detail` varchar(1000) DEFAULT NULL,
                        `column_id` int(11) DEFAULT NULL,
                        `due_date` datetime DEFAULT NULL COMMENT '最后时间',
                        `sort_order` int(11) DEFAULT NULL COMMENT '排序字段',
                        `is_deleted` int(11) NOT NULL DEFAULT '0',
                        `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
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
                               `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
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
                               `user_id` bigint(20) NOT NULL COMMENT '所属用户ID',
                               `create_user` bigint(20) NOT NULL COMMENT '创建人',
                               `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                               `update_user` bigint(20) NOT NULL COMMENT '更新人',
                               `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                               `is_deleted` tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否删除 0-未删除 1-已删除',
                               PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='任务明细表';

CREATE TABLE IF NOT EXISTS `thought` (
                           `id` bigint(20) NOT NULL AUTO_INCREMENT,
                           `content` mediumtext NOT NULL COMMENT '没人',
                           `user_id` bigint(19) NOT NULL COMMENT '用户 ID',
                           `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                           `create_user` bigint(20) DEFAULT NULL,
                           `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
                           `update_user` bigint(20) DEFAULT NULL,
                           `is_deleted` int(11) DEFAULT '0',
                           PRIMARY KEY (`id`)
) ENGINE=InnoDB  DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='思考表';

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

CREATE TABLE IF NOT EXISTS `time_record` (
                               `id` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '记录ID',
                               `user_id` bigint(20) NOT NULL COMMENT '用户ID',
                               `create_user` bigint(20) NOT NULL COMMENT '用户ID',
                               `category_id` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '分类ID',
                               `date` date NOT NULL COMMENT '日期（YYYY-MM-DD格式）',
                               `start_time` smallint(5) unsigned NOT NULL COMMENT '开始时间（分钟，0-1440）',
                               `end_time` smallint(5) unsigned NOT NULL COMMENT '结束时间（分钟，0-1440）',
                               `title` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '标题',
                               `description` text COLLATE utf8mb4_unicode_ci COMMENT '详细描述',
                               `duration` smallint(5) unsigned NOT NULL COMMENT '时长（分钟）',
                               `is_manual` tinyint(4) NOT NULL DEFAULT '0' COMMENT '是否手动创建：0-系统，1-手动',
                               `is_deleted` tinyint(4) NOT NULL DEFAULT '0',
                               `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                               `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                               PRIMARY KEY (`id`),
                               KEY `idx_time_record_date` (`date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='时间段记录表';

CREATE TABLE IF NOT EXISTS `time_tracker_category` (
                                         `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
                                         `user_id` bigint(20) NOT NULL COMMENT '用户ID',
                                         `code` varchar(50) NOT NULL COMMENT '分类标识(如: rest, work)',
                                         `name` varchar(50) NOT NULL COMMENT '分类名称',
                                         `color` varchar(20) NOT NULL COMMENT '颜色值(Hex)',
                                         `description` varchar(255) DEFAULT NULL COMMENT '描述',
                                         `is_track_time` tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否记录时间',
                                         `sort` int(11) NOT NULL DEFAULT '0' COMMENT '排序权重',
                                         `create_user` bigint(20) NOT NULL COMMENT '创建人',
                                         `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                         `update_user` bigint(20) NOT NULL COMMENT '更新人',
                                         `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                         `is_deleted` int(11) NOT NULL DEFAULT '0' COMMENT '是否删除',
                                         PRIMARY KEY (`id`),
                                         KEY `idx_user_code` (`user_id`,`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='时间追踪-分类配置表';


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

CREATE TABLE IF NOT EXISTS `api_key` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint(20) NOT NULL COMMENT '用户ID',
  `api_key` varchar(128) NOT NULL COMMENT 'API Key',
  `remark` varchar(255) DEFAULT NULL COMMENT '备注',
  `expired_at` datetime DEFAULT NULL COMMENT '过期时间',
  `create_user` bigint(20) NOT NULL COMMENT '创建人',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_user` bigint(20) NOT NULL COMMENT '更新人',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` tinyint(4) NOT NULL DEFAULT '0' COMMENT '是否删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_api_key` (`api_key`),
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

CREATE TABLE IF NOT EXISTS `message` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `sender_id` bigint(20) NOT NULL COMMENT '发送用户ID',
  `receiver_id` bigint(20) NOT NULL COMMENT '接收用户ID',
  `title` varchar(255) NOT NULL COMMENT '消息标题',
  `content` text COMMENT '消息内容',
  `type` int(11) NOT NULL DEFAULT '0' COMMENT '消息类型: 0-系统通知, 1-用户消息',
  `is_read` tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否已读: 0-未读, 1-已读',
  `create_user` bigint(20) NOT NULL COMMENT '创建人',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_user` bigint(20) NOT NULL COMMENT '更新人',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否删除: 0-未删除, 1-已删除',
  PRIMARY KEY (`id`),
  KEY `idx_receiver_is_read` (`receiver_id`, `is_read`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='消息表';

