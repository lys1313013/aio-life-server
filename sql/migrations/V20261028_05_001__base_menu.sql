-- 目的：补齐基础菜单；MySQL 5.7.44 / 8.0，已完成当前结构迁移。
-- 影响：按 ID、路由名称或路径识别已有菜单，包含软删除记录；保留全部管理员配置。
-- 重跑：仅插入不存在的菜单，不覆盖、启用或恢复已有项；未知 SQL 错误直接失败。
-- 执行：通过 Flyway 迁移入口执行，目标库由连接指定。
INSERT INTO sys_menu (id, parent_id, name, path, component, redirect, meta, roles, sort_order, web_is_enabled, mobile_is_enabled, create_user, create_time, update_user, update_time, is_deleted)
SELECT 1001, 0, 'Analytics', '/', 'dashboard/home/index', null, '{"icon": "lucide:home", "title": "主页", "backTop": false, "affixTab": true, "keepAlive": true, "maxIdleTime": 60}', null, -1, 1, 1, null, null, 13, '2026-06-21 12:45:39', 0
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` existing WHERE existing.id = 1001 OR existing.name = 'Analytics' OR existing.path = '/');
INSERT INTO sys_menu (id, parent_id, name, path, component, redirect, meta, roles, sort_order, web_is_enabled, mobile_is_enabled, create_user, create_time, update_user, update_time, is_deleted)
SELECT 1100, 0, 'Dashboard', '/dashboard', 'BasicLayout', null, '{"icon": "lucide:layout-dashboard", "title": "仪表盘"}', null, 0, 0, 0, null, null, 13, '2026-06-21 12:45:48', 0
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` existing WHERE existing.id = 1100 OR existing.name = 'Dashboard' OR existing.path = '/dashboard');
INSERT INTO sys_menu (id, parent_id, name, path, component, redirect, meta, roles, sort_order, web_is_enabled, mobile_is_enabled, create_user, create_time, update_user, update_time, is_deleted)
SELECT 1101, 1100, 'Workspace', '/workspace', 'dashboard/workspace/index', null, '{"icon": "carbon:workspace", "title": "工作台"}', null, 0, 1, 1, null, null, null, null, 0
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` existing WHERE existing.id = 1101 OR existing.name = 'Workspace' OR existing.path = '/workspace');
INSERT INTO sys_menu (id, parent_id, name, path, component, redirect, meta, roles, sort_order, web_is_enabled, mobile_is_enabled, create_user, create_time, update_user, update_time, is_deleted)
SELECT 1200, 0, 'TaskCenter', '/task', 'BasicLayout', null, '{"icon": "mdi:clipboard-text-clock-outline", "title": "任务"}', null, 1, 1, 1, null, null, 13, '2026-06-26 21:36:07', 0
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` existing WHERE existing.id = 1200 OR existing.name = 'TaskCenter' OR existing.path = '/task');
INSERT INTO sys_menu (id, parent_id, name, path, component, redirect, meta, roles, sort_order, web_is_enabled, mobile_is_enabled, create_user, create_time, update_user, update_time, is_deleted)
SELECT 1201, 1200, 'TaskCenterTodo', '/task/todo', 'task-center/todo/index', null, '{"icon": "mdi:format-list-checks", "title": "待办"}', null, 0, 1, 1, null, null, 13, '2026-06-26 21:36:13', 0
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` existing WHERE existing.id = 1201 OR existing.name = 'TaskCenterTodo' OR existing.path = '/task/todo');
INSERT INTO sys_menu (id, parent_id, name, path, component, redirect, meta, roles, sort_order, web_is_enabled, mobile_is_enabled, create_user, create_time, update_user, update_time, is_deleted)
SELECT 1300, 0, 'TimeManagement', '/time', 'BasicLayout', null, '{"icon": "mdi:clock-outline", "title": "时间", "keepAlive": true}', null, 2, 1, 1, null, null, 13, '2026-06-21 12:46:00', 0
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` existing WHERE existing.id = 1300 OR existing.name = 'TimeManagement' OR existing.path = '/time');
INSERT INTO sys_menu (id, parent_id, name, path, component, redirect, meta, roles, sort_order, web_is_enabled, mobile_is_enabled, create_user, create_time, update_user, update_time, is_deleted)
SELECT 1301, 1300, 'TimeTracker', '/time/time-tracker', 'time/time-tracker/index', null, '{"icon": "mdi:history", "title": "时迹", "backTop": false, "keepAlive": true, "maxIdleTime": 60}', null, 0, 1, 1, null, null, null, null, 0
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` existing WHERE existing.id = 1301 OR existing.name = 'TimeTracker' OR existing.path = '/time/time-tracker');
INSERT INTO sys_menu (id, parent_id, name, path, component, redirect, meta, roles, sort_order, web_is_enabled, mobile_is_enabled, create_user, create_time, update_user, update_time, is_deleted)
SELECT 1302, 1300, 'TimeTrackerDashboard', '/time/dashboard', 'time/dashboard/index', null, '{"icon": "mdi:view-dashboard-outline", "title": "看板", "backTop": false, "keepAlive": true}', null, 1, 0, 0, null, null, 13, '2026-06-19 15:13:51', 0
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` existing WHERE existing.id = 1302 OR existing.name = 'TimeTrackerDashboard' OR existing.path = '/time/dashboard');
INSERT INTO sys_menu (id, parent_id, name, path, component, redirect, meta, roles, sort_order, web_is_enabled, mobile_is_enabled, create_user, create_time, update_user, update_time, is_deleted)
SELECT 1303, 1300, 'CategoryConfig', '/time/my-categories', 'time/time-tracker/category-config/index', null, '{"icon": "mdi:tag-multiple-outline", "title": "我的分类", "backTop": false}', null, 2, 1, 1, null, null, null, null, 0
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` existing WHERE existing.id = 1303 OR existing.name = 'CategoryConfig' OR existing.path = '/time/my-categories');
INSERT INTO sys_menu (id, parent_id, name, path, component, redirect, meta, roles, sort_order, web_is_enabled, mobile_is_enabled, create_user, create_time, update_user, update_time, is_deleted)
SELECT 1304, 1300, 'TimeTrackerCategoryAdmin', '/time/category-admin', 'time/time-tracker/admin/index', null, '{"icon": "mdi:shield-account-outline", "title": "分类管理（管理员）", "backTop": false}', 'admin', 3, 1, 1, null, null, null, null, 0
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` existing WHERE existing.id = 1304 OR existing.name = 'TimeTrackerCategoryAdmin' OR existing.path = '/time/category-admin');
INSERT INTO sys_menu (id, parent_id, name, path, component, redirect, meta, roles, sort_order, web_is_enabled, mobile_is_enabled, create_user, create_time, update_user, update_time, is_deleted)
SELECT 1400, 0, 'Coding', '/coding', 'BasicLayout', null, '{"icon": "lucide:code-2", "title": "编程", "keepAlive": true}', null, 7, 1, 1, null, null, 13, '2026-06-21 12:44:52', 0
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` existing WHERE existing.id = 1400 OR existing.name = 'Coding' OR existing.path = '/coding');
INSERT INTO sys_menu (id, parent_id, name, path, component, redirect, meta, roles, sort_order, web_is_enabled, mobile_is_enabled, create_user, create_time, update_user, update_time, is_deleted)
SELECT 1401, 1400, 'GithubGraph', '/coding/github', 'coding/github/index', null, '{"icon": "mdi:github", "title": "Github", "backTop": false}', null, 1, 1, 1, null, null, 13, '2026-06-21 12:43:44', 0
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` existing WHERE existing.id = 1401 OR existing.name = 'GithubGraph' OR existing.path = '/coding/github');
INSERT INTO sys_menu (id, parent_id, name, path, component, redirect, meta, roles, sort_order, web_is_enabled, mobile_is_enabled, create_user, create_time, update_user, update_time, is_deleted)
SELECT 1402, 1400, 'LeetCode', '/coding/leetcode', 'coding/leetcode/index', null, '{"icon": "devicon:leetcode", "title": "LeetCode"}', null, 2, 1, 1, null, null, 13, '2026-06-21 12:43:44', 0
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` existing WHERE existing.id = 1402 OR existing.name = 'LeetCode' OR existing.path = '/coding/leetcode');
INSERT INTO sys_menu (id, parent_id, name, path, component, redirect, meta, roles, sort_order, web_is_enabled, mobile_is_enabled, create_user, create_time, update_user, update_time, is_deleted)
SELECT 1500, 0, 'Demos', '/record', 'BasicLayout', null, '{"icon": "ic:baseline-view-in-ar", "title": "记录", "keepAlive": true}', null, 3, 1, 1, null, null, 13, '2026-06-21 12:46:06', 0
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` existing WHERE existing.id = 1500 OR existing.name = 'Demos' OR existing.path = '/record');
INSERT INTO sys_menu (id, parent_id, name, path, component, redirect, meta, roles, sort_order, web_is_enabled, mobile_is_enabled, create_user, create_time, update_user, update_time, is_deleted)
SELECT 1501, 1500, 'exercise', '/record/exercise', 'my-hub/exercise/index', null, '{"icon": "mdi:run-fast", "title": "运动", "backTop": false}', null, 0, 1, 1, null, null, 13, '2026-06-19 17:41:31', 0
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` existing WHERE existing.id = 1501 OR existing.name = 'exercise' OR existing.path = '/record/exercise');
INSERT INTO sys_menu (id, parent_id, name, path, component, redirect, meta, roles, sort_order, web_is_enabled, mobile_is_enabled, create_user, create_time, update_user, update_time, is_deleted)
SELECT 1502, 1500, 'videoWatch', '/record/videoWatch', 'my-hub/videoWatch/index', null, '{"icon": "mdi:video-vintage", "title": "视频观看", "backTop": false}', null, 1, 1, 1, null, null, 13, '2026-06-19 17:44:00', 0
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` existing WHERE existing.id = 1502 OR existing.name = 'videoWatch' OR existing.path = '/record/videoWatch');
INSERT INTO sys_menu (id, parent_id, name, path, component, redirect, meta, roles, sort_order, web_is_enabled, mobile_is_enabled, create_user, create_time, update_user, update_time, is_deleted)
SELECT 1503, 1500, 'think', '/record/think', 'my-hub/think/index', null, '{"icon": "mdi:lightbulb-on-outline", "title": "闪念", "backTop": false}', null, 2, 1, 1, null, null, 13, '2026-06-19 17:43:51', 0
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` existing WHERE existing.id = 1503 OR existing.name = 'think' OR existing.path = '/record/think');
INSERT INTO sys_menu (id, parent_id, name, path, component, redirect, meta, roles, sort_order, web_is_enabled, mobile_is_enabled, create_user, create_time, update_user, update_time, is_deleted)
SELECT 1504, 1500, 'memo', '/record/memo', 'my-hub/memo/index', null, '{"icon": "mdi:note-text-outline", "title": "笔记", "backTop": false}', null, 3, 1, 1, null, null, 13, '2026-06-19 17:43:41', 0
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` existing WHERE existing.id = 1504 OR existing.name = 'memo' OR existing.path = '/record/memo');
INSERT INTO sys_menu (id, parent_id, name, path, component, redirect, meta, roles, sort_order, web_is_enabled, mobile_is_enabled, create_user, create_time, update_user, update_time, is_deleted)
SELECT 1505, 1500, 'performance', '/record/performance', 'my-hub/performance/index', null, '{"icon": "mdi:chart-line-variant", "title": "活动", "backTop": false}', null, 4, 1, 1, null, null, 13, '2026-06-19 17:43:31', 0
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` existing WHERE existing.id = 1505 OR existing.name = 'performance' OR existing.path = '/record/performance');
INSERT INTO sys_menu (id, parent_id, name, path, component, redirect, meta, roles, sort_order, web_is_enabled, mobile_is_enabled, create_user, create_time, update_user, update_time, is_deleted)
SELECT 1506, 1500, 'milestone', '/record/milestone', 'my-hub/milestone/index', null, '{"icon": "mdi:flag-variant", "title": "里程碑", "backTop": false}', null, 5, 1, 1, null, null, 13, '2026-06-19 17:43:23', 0
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` existing WHERE existing.id = 1506 OR existing.name = 'milestone' OR existing.path = '/record/milestone');
INSERT INTO sys_menu (id, parent_id, name, path, component, redirect, meta, roles, sort_order, web_is_enabled, mobile_is_enabled, create_user, create_time, update_user, update_time, is_deleted)
SELECT 1507, 1500, 'anniversary', '/record/anniversary', 'my-hub/anniversary/index', null, '{"icon": "mdi:calendar-heart", "title": "纪念日", "backTop": false}', null, 6, 1, 1, null, null, 13, '2026-06-19 17:43:13', 0
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` existing WHERE existing.id = 1507 OR existing.name = 'anniversary' OR existing.path = '/record/anniversary');
INSERT INTO sys_menu (id, parent_id, name, path, component, redirect, meta, roles, sort_order, web_is_enabled, mobile_is_enabled, create_user, create_time, update_user, update_time, is_deleted)
SELECT 1508, 1800, 'exerciseCategoryConfig', '/my-hub/exercise/category-config', 'my-hub/exercise/category-config/index', null, '{"icon": "mdi:format-list-bulleted-type", "title": "分类配置", "backTop": false}', null, 7, 1, 1, null, null, 13, '2026-06-07 14:42:47', 0
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` existing WHERE existing.id = 1508 OR existing.name = 'exerciseCategoryConfig' OR existing.path = '/my-hub/exercise/category-config');
INSERT INTO sys_menu (id, parent_id, name, path, component, redirect, meta, roles, sort_order, web_is_enabled, mobile_is_enabled, create_user, create_time, update_user, update_time, is_deleted)
SELECT 1509, 1500, 'feedbackCenter', '/my-hub/feedback', 'my-hub/feedback/index', null, '{"icon": "mdi:message-alert-outline", "title": "反馈中心", "backTop": false}', null, 10, 1, 1, null, null, null, null, 0
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` existing WHERE existing.id = 1509 OR existing.name = 'feedbackCenter' OR existing.path = '/my-hub/feedback');
INSERT INTO sys_menu (id, parent_id, name, path, component, redirect, meta, roles, sort_order, web_is_enabled, mobile_is_enabled, create_user, create_time, update_user, update_time, is_deleted)
SELECT 1510, 1500, 'honor', '/my-hub/honor', 'my-hub/honor/index', null, '{"icon": "mdi:trophy-outline", "title": "荣誉中心", "backTop": false}', null, 7, 1, 1, null, null, null, null, 0
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` existing WHERE existing.id = 1510 OR existing.name = 'honor' OR existing.path = '/my-hub/honor');
INSERT INTO sys_menu (id, parent_id, name, path, component, redirect, meta, roles, sort_order, web_is_enabled, mobile_is_enabled, create_user, create_time, update_user, update_time, is_deleted)
SELECT 1600, 0, 'FinanceManagement', '/finance', 'BasicLayout', null, '{"icon": "mdi:finance", "title": "财务", "keepAlive": true}', null, 4, 1, 1, null, null, 13, '2026-06-21 12:46:13', 0
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` existing WHERE existing.id = 1600 OR existing.name = 'FinanceManagement' OR existing.path = '/finance');
INSERT INTO sys_menu (id, parent_id, name, path, component, redirect, meta, roles, sort_order, web_is_enabled, mobile_is_enabled, create_user, create_time, update_user, update_time, is_deleted)
SELECT 1601, 1600, 'financeDashboard', '/finance/dashboard', 'my-hub/finance-dashboard/index', null, '{"icon": "mdi:chart-areaspline", "title": "概览", "backTop": false}', null, 0, 1, 1, null, null, 13, '2026-06-20 12:50:41', 0
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` existing WHERE existing.id = 1601 OR existing.name = 'financeDashboard' OR existing.path = '/finance/dashboard');
INSERT INTO sys_menu (id, parent_id, name, path, component, redirect, meta, roles, sort_order, web_is_enabled, mobile_is_enabled, create_user, create_time, update_user, update_time, is_deleted)
SELECT 1602, 1600, 'incomeManagement', '/finance/income', 'my-hub/income/index', null, '{"icon": "mdi:cash-plus", "title": "收入", "backTop": false}', null, 1, 1, 1, null, null, 13, '2026-06-20 12:50:48', 0
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` existing WHERE existing.id = 1602 OR existing.name = 'incomeManagement' OR existing.path = '/finance/income');
INSERT INTO sys_menu (id, parent_id, name, path, component, redirect, meta, roles, sort_order, web_is_enabled, mobile_is_enabled, create_user, create_time, update_user, update_time, is_deleted)
SELECT 1603, 1600, 'expenseManagement', '/finance/expense', 'my-hub/expense/index', null, '{"icon": "mdi:cash-minus", "title": "支出", "backTop": false}', null, 2, 1, 1, null, null, 13, '2026-06-20 12:50:54', 0
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` existing WHERE existing.id = 1603 OR existing.name = 'expenseManagement' OR existing.path = '/finance/expense');
INSERT INTO sys_menu (id, parent_id, name, path, component, redirect, meta, roles, sort_order, web_is_enabled, mobile_is_enabled, create_user, create_time, update_user, update_time, is_deleted)
SELECT 1604, 1600, 'alipayImport', '/finance/import', 'my-hub/expense/import', null, '{"icon": "mdi:import", "title": "账单导入", "backTop": false}', null, 3, 1, 1, null, null, 13, '2026-06-20 12:51:00', 0
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` existing WHERE existing.id = 1604 OR existing.name = 'alipayImport' OR existing.path = '/finance/import');
INSERT INTO sys_menu (id, parent_id, name, path, component, redirect, meta, roles, sort_order, web_is_enabled, mobile_is_enabled, create_user, create_time, update_user, update_time, is_deleted)
SELECT 1700, 0, 'Goods', '/goods', 'BasicLayout', null, '{"icon": "lucide:package", "title": "物品", "keepAlive": true}', null, 5, 1, 1, null, null, 13, '2026-06-21 12:46:24', 0
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` existing WHERE existing.id = 1700 OR existing.name = 'Goods' OR existing.path = '/goods');
INSERT INTO sys_menu (id, parent_id, name, path, component, redirect, meta, roles, sort_order, web_is_enabled, mobile_is_enabled, create_user, create_time, update_user, update_time, is_deleted)
SELECT 1701, 1700, 'device', '/my-hub/device', 'my-hub/device/index', null, '{"icon": "mdi:monitor-dashboard", "title": "设备墙", "backTop": false}', null, 0, 1, 1, null, null, null, null, 0
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` existing WHERE existing.id = 1701 OR existing.name = 'device' OR existing.path = '/my-hub/device');
INSERT INTO sys_menu (id, parent_id, name, path, component, redirect, meta, roles, sort_order, web_is_enabled, mobile_is_enabled, create_user, create_time, update_user, update_time, is_deleted)
SELECT 1702, 1700, 'Wardrobe', '/wardrobe', 'wardrobe/index', null, '{"icon": "ant-design:book-outlined", "title": "衣柜", "backTop": false}', null, 1, 1, 1, null, null, 13, '2026-06-25 23:17:29', 0
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` existing WHERE existing.id = 1702 OR existing.name = 'Wardrobe' OR existing.path = '/wardrobe');
INSERT INTO sys_menu (id, parent_id, name, path, component, redirect, meta, roles, sort_order, web_is_enabled, mobile_is_enabled, create_user, create_time, update_user, update_time, is_deleted)
SELECT 1800, 0, 'ConfigManagement', '/config', 'BasicLayout', null, '{"icon": "mdi:cog-outline", "title": "配置管理", "keepAlive": true}', 'admin', 9, 1, 1, null, null, 13, '2026-06-21 12:46:36', 0
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` existing WHERE existing.id = 1800 OR existing.name = 'ConfigManagement' OR existing.path = '/config');
INSERT INTO sys_menu (id, parent_id, name, path, component, redirect, meta, roles, sort_order, web_is_enabled, mobile_is_enabled, create_user, create_time, update_user, update_time, is_deleted)
SELECT 1801, 1800, 'sysDictType', '/config-management/sysDictType', 'config-management/sysDictType/index', null, '{"icon": "mdi:book-settings-outline", "title": "字典类型", "backTop": false}', 'admin', 0, 1, 1, null, null, null, null, 0
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` existing WHERE existing.id = 1801 OR existing.name = 'sysDictType' OR existing.path = '/config-management/sysDictType');
INSERT INTO sys_menu (id, parent_id, name, path, component, redirect, meta, roles, sort_order, web_is_enabled, mobile_is_enabled, create_user, create_time, update_user, update_time, is_deleted)
SELECT 1802, 1800, 'sysDictData', '/config-management/sysDictData', 'config-management/sysDictData/index', null, '{"icon": "mdi:database-search-outline", "title": "字典数据", "backTop": false}', 'admin', 1, 1, 1, null, null, null, null, 0
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` existing WHERE existing.id = 1802 OR existing.name = 'sysDictData' OR existing.path = '/config-management/sysDictData');
INSERT INTO sys_menu (id, parent_id, name, path, component, redirect, meta, roles, sort_order, web_is_enabled, mobile_is_enabled, create_user, create_time, update_user, update_time, is_deleted)
SELECT 1900, 0, 'System', '/system', 'BasicLayout', null, '{"icon": "mdi:shield-account-outline", "title": "系统管理", "keepAlive": true}', 'admin', 10, 1, 1, null, null, 13, '2026-06-21 12:46:41', 0
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` existing WHERE existing.id = 1900 OR existing.name = 'System' OR existing.path = '/system');
INSERT INTO sys_menu (id, parent_id, name, path, component, redirect, meta, roles, sort_order, web_is_enabled, mobile_is_enabled, create_user, create_time, update_user, update_time, is_deleted)
SELECT 1901, 1900, 'UserCenter', '/system/user', 'system/user/index', null, '{"icon": "mdi:account-group-outline", "title": "用户中心"}', 'admin', 0, 1, 1, null, null, null, null, 0
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` existing WHERE existing.id = 1901 OR existing.name = 'UserCenter' OR existing.path = '/system/user');
INSERT INTO sys_menu (id, parent_id, name, path, component, redirect, meta, roles, sort_order, web_is_enabled, mobile_is_enabled, create_user, create_time, update_user, update_time, is_deleted)
SELECT 1902, 1900, 'MenuManagement', '/system/menu', 'system/menu/index', null, '{"icon": "mdi:menu-open", "title": "菜单管理"}', 'admin', 1, 1, 1, null, null, 13, '2026-06-13 13:01:09', 0
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` existing WHERE existing.id = 1902 OR existing.name = 'MenuManagement' OR existing.path = '/system/menu');
INSERT INTO sys_menu (id, parent_id, name, path, component, redirect, meta, roles, sort_order, web_is_enabled, mobile_is_enabled, create_user, create_time, update_user, update_time, is_deleted)
SELECT 1903, 1900, 'UserDictAdmin', '/system/user-dict', 'system/user-dict/index', null, '{"icon": "mdi:book-open-page-variant", "title": "用户字典管理"}', 'admin', 3, 1, 1, null, null, null, null, 0
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` existing WHERE existing.id = 1903 OR existing.name = 'UserDictAdmin' OR existing.path = '/system/user-dict');
INSERT INTO sys_menu (id, parent_id, name, path, component, redirect, meta, roles, sort_order, web_is_enabled, mobile_is_enabled, create_user, create_time, update_user, update_time, is_deleted)
SELECT 1904, 1900, 'FeedbackAdmin', '/system/feedback', 'system/feedback/index', null, '{"icon": "mdi:message-alert-outline", "title": "反馈管理"}', 'admin', 3, 1, 1, null, null, null, null, 0
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` existing WHERE existing.id = 1904 OR existing.name = 'FeedbackAdmin' OR existing.path = '/system/feedback');
INSERT INTO sys_menu (id, parent_id, name, path, component, redirect, meta, roles, sort_order, web_is_enabled, mobile_is_enabled, create_user, create_time, update_user, update_time, is_deleted)
SELECT 1905, 1900, 'SystemConfig', '/system/config', 'system/config/index', null, '{"icon": "mdi:cog-outline", "title": "系统配置"}', 'admin', 4, 1, 1, null, null, null, null, 0
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` existing WHERE existing.id = 1905 OR existing.name = 'SystemConfig' OR existing.path = '/system/config');
INSERT INTO sys_menu (id, parent_id, name, path, component, redirect, meta, roles, sort_order, web_is_enabled, mobile_is_enabled, create_user, create_time, update_user, update_time, is_deleted)
SELECT 2000, 0, 'Message', '/message', 'message/index', null, '{"icon": "ant-design:message-outlined", "title": "消息中心", "hideInMenu": false, "fullPathKey": false}', null, 10, 1, 1, null, null, 13, '2026-06-21 12:46:46', 0
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` existing WHERE existing.id = 2000 OR existing.name = 'Message' OR existing.path = '/message');
INSERT INTO sys_menu (id, parent_id, name, path, component, redirect, meta, roles, sort_order, web_is_enabled, mobile_is_enabled, create_user, create_time, update_user, update_time, is_deleted)
SELECT 2100, 0, 'VbenAbout', '/vben-admin/about', '_core/about/index', null, '{"icon": "lucide:copyright", "title": "关于"}', 'admin', 9999, 1, 1, null, null, 13, '2026-06-21 12:47:03', 0
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` existing WHERE existing.id = 2100 OR existing.name = 'VbenAbout' OR existing.path = '/vben-admin/about');
INSERT INTO sys_menu (id, parent_id, name, path, component, redirect, meta, roles, sort_order, web_is_enabled, mobile_is_enabled, create_user, create_time, update_user, update_time, is_deleted)
SELECT 2200, 0, 'Profile', '/profile', '_core/profile/index', null, '{"icon": "lucide:user", "title": "个人中心", "hideInMenu": true}', null, 9998, 1, 1, null, null, null, null, 0
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` existing WHERE existing.id = 2200 OR existing.name = 'Profile' OR existing.path = '/profile');
INSERT INTO sys_menu (id, parent_id, name, path, component, redirect, meta, roles, sort_order, web_is_enabled, mobile_is_enabled, create_user, create_time, update_user, update_time, is_deleted)
SELECT 2300, 0, 'MCP', '/ai', 'BasicLayout', null, '{"icon": "carbon:tools", "title": "AI 接入", "keepAlive": true}', null, 20, 1, 1, null, null, 13, '2026-06-21 12:46:57', 0
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` existing WHERE existing.id = 2300 OR existing.name = 'MCP' OR existing.path = '/ai');
INSERT INTO sys_menu (id, parent_id, name, path, component, redirect, meta, roles, sort_order, web_is_enabled, mobile_is_enabled, create_user, create_time, update_user, update_time, is_deleted)
SELECT 2301, 2300, 'mcpTools', '/mcp/tools', 'mcp/tools/index', null, '{"icon": "carbon:cube", "title": "连接器（MCP）", "backTop": false}', null, 0, 1, 1, null, null, 13, '2026-05-29 23:57:00', 0
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` existing WHERE existing.id = 2301 OR existing.name = 'mcpTools' OR existing.path = '/mcp/tools');
INSERT INTO sys_menu (id, parent_id, name, path, component, redirect, meta, roles, sort_order, web_is_enabled, mobile_is_enabled, create_user, create_time, update_user, update_time, is_deleted)
SELECT 2302, 0, 'Relationship', '/relationship', 'BasicLayout', null, '{"icon": "ant-design:team-outlined", "order": 8, "title": "关系图谱", "keepAlive": true}', null, 8, 1, 1, null, null, 13, '2026-06-20 21:58:28', 1
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` existing WHERE existing.id = 2302 OR existing.name = 'Relationship' OR existing.path = '/relationship');
INSERT INTO sys_menu (id, parent_id, name, path, component, redirect, meta, roles, sort_order, web_is_enabled, mobile_is_enabled, create_user, create_time, update_user, update_time, is_deleted)
SELECT 2303, 1500, 'RelationshipGraph', '/relationship/graph', 'relationship/index', null, '{"icon": "ant-design:team-outlined", "title": "关系图谱", "backTop": false}', null, 10, 1, 1, null, null, 13, '2026-06-20 22:02:59', 0
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` existing WHERE existing.id = 2303 OR existing.name = 'RelationshipGraph' OR existing.path = '/relationship/graph');
INSERT INTO sys_menu (id, parent_id, name, path, component, redirect, meta, roles, sort_order, web_is_enabled, mobile_is_enabled, create_user, create_time, update_user, update_time, is_deleted)
SELECT 1508222, 1500, 'Movie', '/record/movie', 'my-hub/movie/index', null, '{"icon": "mdi:movie-open-play-outline", "title": "观影", "backTop": false}', null, 7, 1, 1, null, null, 13, '2026-06-19 17:43:01', 0
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` existing WHERE existing.id = 1508222 OR existing.name = 'Movie' OR existing.path = '/record/movie');
INSERT INTO sys_menu (id, parent_id, name, path, component, redirect, meta, roles, sort_order, web_is_enabled, mobile_is_enabled, create_user, create_time, update_user, update_time, is_deleted)
SELECT 2049879990130667522, 0, 'PasswordManager', '/passwordP', 'password-manager/index', null, '{"icon": "mdi:clipboard-text-clock-outline", "order": 1, "title": "密码"}', null, 0, 1, 1, 13, '2026-04-30 23:54:09', 13, '2026-05-20 22:17:27', 1
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` existing WHERE existing.id = 2049879990130667522 OR existing.name = 'PasswordManager' OR existing.path = '/passwordP');
INSERT INTO sys_menu (id, parent_id, name, path, component, redirect, meta, roles, sort_order, web_is_enabled, mobile_is_enabled, create_user, create_time, update_user, update_time, is_deleted)
SELECT 2049881372799758338, 1500, 'PasswordManagerIndex', '/record/password', 'password-manager/index', null, '{"icon": "ant-design:key-outlined", "title": "密码"}', null, 0, 1, 1, 13, '2026-04-30 23:59:39', 13, '2026-06-19 17:44:11', 0
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` existing WHERE existing.id = 2049881372799758338 OR existing.name = 'PasswordManagerIndex' OR existing.path = '/record/password');
INSERT INTO sys_menu (id, parent_id, name, path, component, redirect, meta, roles, sort_order, web_is_enabled, mobile_is_enabled, create_user, create_time, update_user, update_time, is_deleted)
SELECT 2054205291710668802, 1200, 'TaskCenterGoal', '/task/goal', 'task-center/goal/index', null, '{"icon": "mdi:target", "title": "目标"}', null, 0, 1, 1, 13, '2026-05-12 22:21:21', 13, '2026-06-26 21:36:20', 0
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` existing WHERE existing.id = 2054205291710668802 OR existing.name = 'TaskCenterGoal' OR existing.path = '/task/goal');
INSERT INTO sys_menu (id, parent_id, name, path, component, redirect, meta, roles, sort_order, web_is_enabled, mobile_is_enabled, create_user, create_time, update_user, update_time, is_deleted)
SELECT 2056010094796967937, 1400, 'CsdnDashboard', '/coding/csdn', 'coding/csdn/index', null, '{"icon": "simple-icons:csdn", "title": "CSDN", "backTop": false}', null, 3, 1, 1, 13, '2026-05-17 21:53:00', 13, '2026-06-21 12:43:44', 0
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` existing WHERE existing.id = 2056010094796967937 OR existing.name = 'CsdnDashboard' OR existing.path = '/coding/csdn');
INSERT INTO sys_menu (id, parent_id, name, path, component, redirect, meta, roles, sort_order, web_is_enabled, mobile_is_enabled, create_user, create_time, update_user, update_time, is_deleted)
SELECT 2056010094796967938, 1500, 'readRecord', '/record/read', 'my-hub/read-record/index', null, '{"icon": "mdi:book-open-page-variant", "title": "阅读", "backTop": false}', null, 8, 1, 1, null, null, 13, '2026-06-20 22:03:11', 0
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` existing WHERE existing.id = 2056010094796967938 OR existing.name = 'readRecord' OR existing.path = '/record/read');

-- 会员菜单（提取自 1_init_table/2026-08-06_create_membership_record.sql，2026-08-18 合并重建时迁移）
INSERT INTO `sys_menu` (`id`, `parent_id`, `name`, `path`, `component`, `redirect`, `meta`, `roles`, `sort_order`, `web_is_enabled`, `is_deleted`)
SELECT 1703, 1700, 'Membership', '/membership', 'membership/index', NULL,
 JSON_OBJECT('icon','ant-design:gift-outlined','title','会员'),
 NULL, 2, 1, 0
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` existing WHERE existing.id = 1703 OR existing.name = 'Membership' OR existing.path = '/membership');

-- 会员平台管理
INSERT INTO `sys_menu`
    (`id`, `parent_id`, `name`, `path`, `component`, `meta`, `roles`, `sort_order`, `web_is_enabled`, `mobile_is_enabled`, `create_time`, `update_time`, `is_deleted`)
SELECT 1911, parent.id, 'MembershipProviderAdmin', '/system/membership-providers', 'system/membership-providers/index',
    JSON_OBJECT('icon', 'lucide:crown', 'title', '会员平台', 'authority', JSON_ARRAY('admin')),
    'admin', 10, 1, 1, NOW(), NOW(), 0
FROM sys_menu parent
WHERE parent.name = 'System' AND parent.parent_id = 0 AND parent.is_deleted = 0
    AND NOT EXISTS (SELECT 1 FROM sys_menu m WHERE m.id = 1911 OR m.name = 'MembershipProviderAdmin' OR m.path = '/system/membership-providers')
LIMIT 1;

-- 身体数据作为「记录」下的独立业务菜单，两端均可见；重复执行保留已有菜单配置。
INSERT INTO `sys_menu`
    (`id`, `parent_id`, `name`, `path`, `component`, `meta`, `roles`, `sort_order`, `web_is_enabled`, `mobile_is_enabled`, `create_time`, `update_time`, `is_deleted`)
SELECT 1511, parent.id, 'BodyProfile', '/record/body-profile', 'my-hub/body-profile/index',
    JSON_OBJECT('icon', 'mdi:human-handsup', 'title', '身体数据', 'backTop', FALSE),
    NULL, 1, 1, 1, NOW(), NOW(), 0
FROM sys_menu parent
WHERE parent.path = '/record' AND parent.parent_id = 0 AND parent.is_deleted = 0
    AND NOT EXISTS (SELECT 1 FROM sys_menu m WHERE m.id = 1511 OR m.name = 'BodyProfile' OR m.path = '/record/body-profile')
LIMIT 1;

-- 生活足迹
INSERT INTO `sys_menu`
    (`id`, `parent_id`, `name`, `path`, `component`, `meta`, `roles`, `sort_order`, `web_is_enabled`, `mobile_is_enabled`, `create_time`, `update_time`, `is_deleted`)
SELECT 1512, parent.id, 'LifeFootprints', '/record/life-footprints', 'my-hub/life-footprints/index',
    JSON_OBJECT('icon', 'mdi:map-marker-path', 'title', '生活足迹', 'backTop', FALSE),
    NULL, 2, 1, 1, NOW(), NOW(), 0
FROM sys_menu parent
WHERE parent.path = '/record' AND parent.parent_id = 0 AND parent.is_deleted = 0
    AND NOT EXISTS (SELECT 1 FROM sys_menu m WHERE m.id = 1512 OR m.name = 'LifeFootprints' OR m.path = '/record/life-footprints')
LIMIT 1;

-- 操作日志 Web 管理入口；只补缺失记录，不覆盖已有配置或删除状态。
INSERT INTO `sys_menu`
    (`id`, `parent_id`, `name`, `path`, `component`, `meta`, `roles`, `sort_order`, `web_is_enabled`, `mobile_is_enabled`, `create_time`, `update_time`, `is_deleted`)
SELECT 1906, parent.id, 'OperationLog', '/system/operation-log', 'system/operation-log/index',
    JSON_OBJECT('icon', 'mdi:clipboard-text-clock-outline', 'title', '操作日志'),
    'admin', 11, 1, 0, NOW(), NOW(), 0
FROM sys_menu parent
WHERE parent.path = '/system' AND parent.parent_id = 0 AND parent.is_deleted = 0
    AND NOT EXISTS (SELECT 1 FROM sys_menu m WHERE m.id = 1906 OR m.name = 'OperationLog' OR m.path = '/system/operation-log')
LIMIT 1;

-- 目的：补齐访问日志管理员菜单，供动态路由注册 /system/access-log。
-- 前置：已建立 sys_menu 表及 mobile_is_enabled 列，并配置 /system 顶级菜单。
-- 影响：仅新增缺失菜单；沿用历史访问日志的管理员权限与双端启用状态，不回填业务数据。
-- 重跑：按 ID、名称或路径判重，保留已有角色、开关、排序及逻辑删除状态。
-- 执行：通过 Flyway 迁移入口执行，由调用方选择目标库，遇错停止。
-- 恢复：记录本次新增 ID；需撤回时通过菜单管理停用该记录，不修改历史日志。
INSERT INTO `sys_menu`
  (`id`, `parent_id`, `name`, `path`, `component`, `meta`, `roles`, `sort_order`, `web_is_enabled`, `mobile_is_enabled`, `create_time`, `update_time`, `is_deleted`)
SELECT 1907, parent.id, 'AccessLog', '/system/access-log', 'system/access-log/index',
  JSON_OBJECT('icon', 'mdi:login-variant', 'title', '访问日志'),
  'admin', 12, 1, 1, NOW(), NOW(), 0
FROM sys_menu parent
WHERE parent.path = '/system' AND parent.parent_id = 0 AND parent.is_deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_menu m WHERE m.id = 1907 OR m.name = 'AccessLog' OR m.path = '/system/access-log')
LIMIT 1;
