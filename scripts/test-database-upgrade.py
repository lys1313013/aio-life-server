import importlib.util
import unittest
from pathlib import Path

spec = importlib.util.spec_from_file_location('upgrade', Path(__file__).with_name('check-database-upgrade.py'))
upgrade = importlib.util.module_from_spec(spec)
spec.loader.exec_module(upgrade)


class UpgradeSafetyTest(unittest.TestCase):
    def test_historical_database_directives_cannot_select_real_database(self):
        source = 'create database if not exists `aio_life`;\nuse `aio_life`;\nCREATE TABLE fixture (id INT);'
        self.assertEqual(upgrade.scoped_sql(source).strip(), 'CREATE TABLE fixture (id INT);')

    def test_unexpected_database_switch_or_qualified_write_is_rejected(self):
        for source in ('USE production;', 'DROP DATABASE aio_life;', 'DELETE FROM aio_life.user;',
                       'INSERT INTO `aio_life`.`user` VALUES (1);', 'ALTER DATABASE production CHARACTER SET ascii;'):
            with self.subTest(source=source), self.assertRaises(ValueError):
                upgrade.scoped_sql(source)

    def test_invalid_database_identifier_is_rejected(self):
        for value in ('live; DROP DATABASE live', '`live`', 'live.data', ''):
            with self.subTest(value=value), self.assertRaises(ValueError):
                upgrade.identifier(value)

    def test_schema_comparison_detects_nullable_index_and_constraint_changes(self):
        expected = {name: [] for name in upgrade.QUERIES}
        actual = {name: [] for name in upgrade.QUERIES}
        expected['columns'] = [['user', 'password', 'varchar(255)', 'YES', None]]
        actual['columns'] = [['user', 'password', 'varchar(255)', 'NO', None]]
        expected['indexes'] = [['user', 'uk_user_phone_active', 0]]
        expected['checks'] = [['user', 'ck_user_phone_pair', 'phone pair', 'YES']]
        differences = upgrade.differences(expected, actual)
        self.assertTrue(any(line.startswith('columns:') for line in differences))
        self.assertTrue(any(line.startswith('indexes:') for line in differences))
        self.assertTrue(any(line.startswith('checks:') for line in differences))

    def test_column_order_does_not_make_valid_alter_fail(self):
        left = {name: [] for name in upgrade.QUERIES}
        right = {name: [] for name in upgrade.QUERIES}
        left['columns'] = [['user', 'id'], ['user', 'phone']]
        right['columns'] = list(reversed(left['columns']))
        self.assertEqual(upgrade.differences(left, right), [])

    def test_preflight_metadata_queries_are_select_only(self):
        for statement in upgrade.QUERIES.values():
            self.assertTrue(statement.lstrip().startswith('SELECT '))
            self.assertNotIn(';', statement)

    def test_pinned_baseline_and_every_migration_match_checksums(self):
        plan, baseline, migrations = upgrade.load_plan()
        self.assertTrue(baseline)
        self.assertGreater(len(migrations), 0)
        self.assertEqual(len(migrations), len(plan['migrations']))


if __name__ == '__main__':
    unittest.main()
