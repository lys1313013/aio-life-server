-- Synthetic records only, loaded exclusively into the verifier's disposable database.
INSERT INTO `user` (`id`,`username`,`password`,`nickname`,`role`)
VALUES (-900001,'upgrade_fixture','synthetic-hash','旧账号迁移夹具','user');
INSERT INTO `sys_menu` (`id`,`parent_id`,`name`,`path`,`component`)
VALUES (-900001,0,'System','/system','BasicLayout');
INSERT INTO `bank_card` (`id`,`user_id`,`custom_bank_name`,`card_type`,
  `card_no_ciphertext`,`card_no_fingerprint`,`card_no_last4`,`create_user`,`update_user`)
VALUES (-900001,-900001,'迁移夹具银行','debit',
  'synthetic-ciphertext',UNHEX(REPEAT('AB',32)),'1234',-900001,-900001);
