-- 目的：目标保存自定义 GraphQL 配置及最近成功进度，支持金额精度和并发修订保护。
-- 前置：MySQL 8.x（最低 8.0.16），已执行当前基线及之前增量，goal 表存在。
-- 影响：已有目标默认手动模式，原整数值无损转为两位小数；不计算或回填业务进度。
-- 重跑：不可直接重跑，由 Flyway 版本历史保证仅执行一次。
-- 执行：mvn -f tools/flyway/pom.xml compile flyway:migrate；业务库执行需用户另行确认。
ALTER TABLE `goal`
  MODIFY COLUMN `target_value` DECIMAL(14,2) NULL COMMENT '目标值，非负数最多两位小数；NULL表示无量化目标',
  MODIFY COLUMN `current_value` DECIMAL(14,2) NULL COMMENT '当前值，非负数最多两位小数；自动模式为最近成功计算值，NULL表示未计算',
  ADD COLUMN `progress_mode` VARCHAR(16) NOT NULL DEFAULT 'manual' COMMENT '进度来源：manual=手动，graphql=自定义只读查询',
  ADD COLUMN `progress_query` TEXT NULL COMMENT '自定义GraphQL查询文本，NULL表示未配置',
  ADD COLUMN `progress_variables` TEXT NULL COMMENT '固定查询变量的JSON对象文本，NULL表示空对象',
  ADD COLUMN `progress_variable_bindings` TEXT NULL COMMENT '变量到目标日期上下文的JSON绑定对象文本，NULL表示不绑定',
  ADD COLUMN `progress_value_path` VARCHAR(256) NULL COMMENT '相对于GraphQL data的数值路径，支持字段及数组下标；NULL表示未配置',
  ADD COLUMN `target_operator` VARCHAR(8) NOT NULL DEFAULT 'gte' COMMENT '达标比较：gte=达到目标值，gt=严格超过目标值',
  ADD COLUMN `progress_checked_at` DATETIME NULL COMMENT '最近成功计算的本地时间，NULL表示尚未成功计算',
  ADD COLUMN `progress_error` VARCHAR(255) NULL COMMENT '最近计算错误，NULL表示无错误；失败保留成功快照',
  ADD COLUMN `progress_refresh_id` VARCHAR(36) NULL COMMENT '最近刷新请求UUID，仅内部使用，阻止同一配置下旧查询覆盖较新刷新；NULL表示未刷新',
  ADD COLUMN `progress_version` BIGINT NOT NULL DEFAULT 0 COMMENT '目标配置修订号，从0开始，每次编辑递增以拒绝过期计算';
-- 约束保护合法来源、比较符与非负业务值；不新增索引，刷新沿用主键及用户归属筛选。
ALTER TABLE `goal`
  ADD CONSTRAINT `ck_goal_progress_mode` CHECK (`progress_mode` IN ('manual', 'graphql')),
  ADD CONSTRAINT `ck_goal_target_operator` CHECK (`target_operator` IN ('gte', 'gt')),
  ADD CONSTRAINT `ck_goal_progress_values` CHECK ((`target_value` IS NULL OR `target_value` >= 0) AND (`current_value` IS NULL OR `current_value` >= 0));
