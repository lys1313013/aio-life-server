-- 二级锁功能：为 sys_menu 添加 secondary_lock 字段
ALTER TABLE sys_menu ADD COLUMN secondary_lock TINYINT(1) DEFAULT 0 COMMENT '是否启用二级锁（0=否，1=是）';

-- 二级锁功能：为 user 添加二级密码字段
ALTER TABLE user ADD COLUMN secondary_password VARCHAR(128) COMMENT '二级密码（加盐哈希）';
ALTER TABLE user ADD COLUMN secondary_password_salt VARCHAR(64) COMMENT '二级密码盐值';
