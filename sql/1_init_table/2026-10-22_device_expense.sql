-- 目的：新增设备支出关联，一个设备支持多笔支出，每笔支出最多关联一个设备。
-- 前置：MySQL 5.7.44 或 8.0.16+；device、expense 已存在；上线新应用前执行。
-- 兼容：仅本建表脚本已在 MySQL 5.7.44 验证，不代表完整应用或其他迁移支持 5.7。
-- 影响：仅新增表，不回填历史账单，不修改设备购买价格；旧应用可继续读写原表。
-- 重跑：仅首次执行；表已存在时停止并核对结构，禁止删除已有关系后重跑。
-- 执行：按 upgrade-plan.json 顺序执行，遇错停止。
-- 恢复：DDL 不可整体回滚；失败后检查实际结构。应用回退时保留该表和关联数据。
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
