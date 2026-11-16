-- 目的：修正已保存的“零点在休息”标准查询中的旧时迹字段名。
-- 前置：PostgreSQL 18+，goal 已具备 GraphQL 进度配置及修订号字段。
-- 影响：只匹配完整的旧标准查询；保留变量、日期、目标值及最近成功进度，不改表结构。
-- 重跑：可重复执行；修正后的查询不再匹配，修订号不会重复增加。
-- 执行：常规升级通过独立 Flyway；定向数据修复可用 psql -v ON_ERROR_STOP=1 --single-transaction -f 本文件。

UPDATE goal
SET progress_query = $query$query Progress($start:String!,$end:String!,$categoryName:String!){progress:timeRecordStats(where:{date:{_gte:$start,_lt:$end},startMinute:{_eq:0},endMinute:{_gte:1},category:{name:{_eq:$categoryName}}}){date{countDistinct}}}$query$,
  progress_version = progress_version + 1,
  progress_refresh_id = NULL,
  update_time = CURRENT_TIMESTAMP
WHERE is_deleted = 0
  AND progress_mode = 'graphql'
  AND progress_query = $query$query Progress($start:String!,$end:String!,$categoryName:String!){progress:timeRecordStats(where:{date:{_gte:$start,_lt:$end},startTime:{_eq:0},endTime:{_gte:1},category:{name:{_eq:$categoryName}}}){date{countDistinct}}}$query$;
