-- 中国大陆工作日历：每天一行，所有年份共用一张表。
-- 在 aio_life 数据库执行；仅创建表结构，不导入年度数据。
-- 工作日：day_type IN (0, 3)；非工作日：day_type IN (1, 2)。
CREATE TABLE IF NOT EXISTS `sys_work_calendar` (
    `calendar_date` date NOT NULL COMMENT '日历日期（中国大陆，按北京时间）',
    `day_type` tinyint NOT NULL COMMENT '日期类型：0普通工作日，1普通周末，2节假日休息，3调休补班',
    `holiday_name` varchar(50) DEFAULT NULL COMMENT '关联节日名称，如春节、国庆节；普通日期为空',
    `source` varchar(50) NOT NULL COMMENT '数据来源，如 weekday_rule、holiday-cn、manual',
    `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    PRIMARY KEY (`calendar_date`),
    CONSTRAINT `chk_sys_work_calendar_day_type` CHECK (`day_type` IN (0, 1, 2, 3))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='中国大陆工作日历表';
