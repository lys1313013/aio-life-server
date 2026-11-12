-- 目的：补齐用户维度的热点查询索引，覆盖列表、首页、清理任务的过滤与排序。
-- 前置：PostgreSQL 18+，已执行 V20261110_01 及 V20261112_01 至 V20261112_06。
-- 影响：只新增索引，不改写业务数据；索引列顺序按等值列在前、排序 / 范围列在后。
-- 重跑：由 Flyway 版本历史保证只执行一次；失败事务回滚后修正原因再迁移。
-- 执行：mvn -f tools/flyway/pom.xml compile flyway:migrate flyway:validate；失败事务回滚。

CREATE INDEX expense_idx_user_deleted_time
  ON expense (user_id, is_deleted, exp_time DESC, id DESC);
COMMENT ON INDEX expense_idx_user_deleted_time IS '支出列表：用户、删除标记与支出时间倒序';

CREATE INDEX income_idx_user_deleted_date
  ON income (user_id, is_deleted, inc_date DESC, id DESC);
COMMENT ON INDEX income_idx_user_deleted_date IS '收入列表：用户、删除标记与收入日期倒序';

CREATE INDEX time_record_idx_user_date_update
  ON time_record (user_id, is_deleted, date, update_time, id);
COMMENT ON INDEX time_record_idx_user_date_update IS '时迹按日查询：用户、日期与更新时间';

CREATE INDEX b_video_idx_user_deleted_update
  ON b_video (user_id, is_deleted, update_time DESC NULLS LAST, id DESC);
COMMENT ON INDEX b_video_idx_user_deleted_update IS '视频列表：用户、删除标记与更新时间倒序';

CREATE INDEX exercise_record_idx_user_deleted_date
  ON exercise_record (user_id, is_deleted, exercise_date DESC, id DESC);
COMMENT ON INDEX exercise_record_idx_user_deleted_date IS '运动记录列表：用户、删除标记与运动日期倒序';

CREATE INDEX exercise_record_idx_user_time
  ON exercise_record (user_id, time_id);
COMMENT ON INDEX exercise_record_idx_user_time IS '运动记录按时迹反查：用户与时迹ID';

CREATE INDEX memo_idx_user_deleted_update
  ON memo (user_id, is_deleted, update_time DESC NULLS LAST, id DESC);
COMMENT ON INDEX memo_idx_user_deleted_update IS '备忘录列表：用户、删除标记与更新时间倒序';

CREATE INDEX thought_idx_user_deleted_update
  ON thought (user_id, is_deleted, update_time DESC NULLS LAST, id DESC);
COMMENT ON INDEX thought_idx_user_deleted_update IS '闪念列表：用户、删除标记与更新时间倒序';

CREATE INDEX thought_rela_event_idx_thought
  ON thought_rela_event (thought_id, id);
COMMENT ON INDEX thought_rela_event_idx_thought IS '闪念关联事件：按闪念批量读取';

CREATE INDEX task_detail_idx_task_user_status_sort
  ON task_detail (task_id, user_id, is_completed, priority ASC NULLS FIRST, sort_order, id);
COMMENT ON INDEX task_detail_idx_task_user_status_sort IS '任务明细：任务、用户、完成状态与排序';

CREATE INDEX task_detail_idx_user_starred_status
  ON task_detail (user_id, is_starred, is_completed, priority ASC NULLS FIRST, sort_order, id);
COMMENT ON INDEX task_detail_idx_user_starred_status IS '关注明细：用户、关注与完成状态与排序';

CREATE INDEX wardrobe_category_idx_type_user_sort
  ON wardrobe_category (category_type, user_id, sort_order ASC NULLS FIRST);
COMMENT ON INDEX wardrobe_category_idx_type_user_sort IS '衣柜分类：系统 / 用户分类与排序';

CREATE INDEX login_log_idx_user_created
  ON login_log (user_id, created_at);
COMMENT ON INDEX login_log_idx_user_created IS '登录日志按用户与时间清理 / 查询';

CREATE INDEX login_log_idx_created
  ON login_log (created_at);
COMMENT ON INDEX login_log_idx_created IS '登录日志按时间清理';

CREATE INDEX device_idx_user_deleted_purchase
  ON device (user_id, is_deleted, purchase_date DESC NULLS LAST, id DESC);
COMMENT ON INDEX device_idx_user_deleted_purchase IS '设备列表：用户、删除标记与购买日期倒序';

CREATE INDEX performance_idx_user_deleted_date
  ON performance (user_id, is_deleted, performance_date DESC, id DESC);
COMMENT ON INDEX performance_idx_user_deleted_date IS '演出列表：用户、删除标记与演出日期倒序';
