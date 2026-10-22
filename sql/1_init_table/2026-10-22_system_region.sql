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
