ALTER TABLE `movie`
    ADD COLUMN `rating` tinyint DEFAULT NULL COMMENT '个人评分：1-5' AFTER `finish_time`;
