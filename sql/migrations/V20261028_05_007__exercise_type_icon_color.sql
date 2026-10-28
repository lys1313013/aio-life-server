-- 前置：已通过 Flyway 完成结构基线及版本 20261028.04 之前的结构迁移。

-- 补齐 18 条公共运动类型的 Iconify 图标和颜色。
-- 仅补指定未删除公共记录中 NULL/空字符串的 icon/color；保留已有图标、颜色、排序、状态和审计信息。
-- 脚本可重复执行。
START TRANSACTION;

UPDATE `user_dict_data`
SET
    `icon` = CASE WHEN `icon` IS NULL OR `icon` = '' THEN CASE `id`
        WHEN 2094763306157969409 THEN 'system-uicons:pull-up'              -- 反握引体向上
        WHEN 2094087234311524353 THEN 'system-uicons:pull-up'              -- 正握引体向上
        WHEN 2095833545677201409 THEN 'hugeicons:equipment-bench-press'     -- 推胸
        WHEN 2090055218490707970 THEN 'mdi:barbell'                         -- 曲柄杠铃
        WHEN 2088651368328269826 THEN 'arcticons:plank-workout'             -- 平板支撑
        WHEN 2063252204773797961 THEN 'gg:push-up'                          -- 俯卧撑
        WHEN 2063252204773797962 THEN 'icon-park-outline:abdominal'         -- 健腹轮
        WHEN 2063252204773797963 THEN 'mdi:run'                             -- 跑步
        WHEN 2063252204773797964 THEN 'mdi:badminton'                       -- 羽毛球
        WHEN 2063252204773797965 THEN 'hugeicons:workout-squats'            -- 深蹲
        WHEN 2063252204773797966 THEN 'roentgen:sit-up'                     -- 仰卧起坐
        WHEN 2063252204773797967 THEN 'mdi:walk'                            -- 散步
        WHEN 2063252204773797968 THEN 'hugeicons:workout-gymnastics'        -- 反向屈腿卷腹
        WHEN 2075941215515496449 THEN 'mdi:bike'                            -- 骑行
        WHEN 2063252204773797969 THEN 'mdi:yoga'                            -- 瑜伽
        WHEN 2065445068968349697 THEN 'mdi:human-karate'                    -- 马步
        WHEN 2063252204773797970 THEN 'mdi:dots-horizontal-circle-outline'  -- 其他
        WHEN 2082125147607764994 THEN 'hugeicons:equipment-chest-press'     -- 蝴蝶机夹胸
        ELSE `icon`
    END ELSE `icon` END,
    `color` = CASE WHEN `color` IS NULL OR `color` = '' THEN CASE `id`
        WHEN 2094763306157969409 THEN '#722ed1'
        WHEN 2094087234311524353 THEN '#2f54eb'
        WHEN 2095833545677201409 THEN '#f5222d'
        WHEN 2090055218490707970 THEN '#fa8c16'
        WHEN 2088651368328269826 THEN '#13c2c2'
        WHEN 2063252204773797961 THEN '#1890ff'
        WHEN 2063252204773797962 THEN '#faad14'
        WHEN 2063252204773797963 THEN '#52c41a'
        WHEN 2063252204773797964 THEN '#2f54eb'
        WHEN 2063252204773797965 THEN '#722ed1'
        WHEN 2063252204773797966 THEN '#13c2c2'
        WHEN 2063252204773797967 THEN '#a0d911'
        WHEN 2063252204773797968 THEN '#eb2f96'
        WHEN 2075941215515496449 THEN '#1890ff'
        WHEN 2063252204773797969 THEN '#722ed1'
        WHEN 2065445068968349697 THEN '#fa8c16'
        WHEN 2063252204773797970 THEN '#8c8c8c'
        WHEN 2082125147607764994 THEN '#eb2f96'
        ELSE `color`
    END ELSE `color` END,
    `update_user` = `update_user`,
    `update_time` = `update_time`
WHERE `user_id` = 0
  AND `dict_type` = 'exercise_type'
  AND `is_deleted` = 0
  AND (`icon` IS NULL OR `icon` = '' OR `color` IS NULL OR `color` = '')
  AND `id` IN (
      2094763306157969409,
      2094087234311524353,
      2095833545677201409,
      2090055218490707970,
      2088651368328269826,
      2063252204773797961,
      2063252204773797962,
      2063252204773797963,
      2063252204773797964,
      2063252204773797965,
      2063252204773797966,
      2063252204773797967,
      2063252204773797968,
      2075941215515496449,
      2063252204773797969,
      2065445068968349697,
      2063252204773797970,
      2082125147607764994
  );

COMMIT;

-- 检查实际存在的预设记录；新库可能没有这些历史公共运动记录，不强制补建。
SELECT `id`, `dict_label`, `icon`, `color`, `sort_order`
FROM `user_dict_data`
WHERE `user_id` = 0
  AND `dict_type` = 'exercise_type'
  AND `is_deleted` = 0
  AND `id` IN (
      2094763306157969409,
      2094087234311524353,
      2095833545677201409,
      2090055218490707970,
      2088651368328269826,
      2063252204773797961,
      2063252204773797962,
      2063252204773797963,
      2063252204773797964,
      2063252204773797965,
      2063252204773797966,
      2063252204773797967,
      2063252204773797968,
      2075941215515496449,
      2063252204773797969,
      2065445068968349697,
      2063252204773797970,
      2082125147607764994
  )
ORDER BY `sort_order`, `id`;
