import importlib.util
import unittest
from pathlib import Path

spec = importlib.util.spec_from_file_location('schema', Path(__file__).with_name('check-database-schema.py'))
schema = importlib.util.module_from_spec(spec)
spec.loader.exec_module(schema)


class SchemaSafetyTest(unittest.TestCase):
    def test_bootstrap_database_directives_cannot_select_real_database(self):
        source = 'create database if not exists `aio_life`;\nuse `aio_life`;\nCREATE TABLE fixture (id INT);'
        self.assertEqual(schema.scoped_sql(source).strip(), 'CREATE TABLE fixture (id INT);')

    def test_unexpected_database_switch_or_qualified_write_is_rejected(self):
        for source in ('USE production;', 'DROP DATABASE aio_life;', 'DELETE FROM aio_life.user;',
                       'INSERT INTO `aio_life`.`user` VALUES (1);', 'ALTER DATABASE production CHARACTER SET ascii;'):
            with self.subTest(source=source), self.assertRaises(ValueError):
                schema.scoped_sql(source)

    def test_invalid_database_identifier_is_rejected(self):
        for value in ('live; DROP DATABASE live', '`live`', 'live.data', ''):
            with self.subTest(value=value), self.assertRaises(ValueError):
                schema.identifier(value)

    def test_schema_comparison_detects_nullable_index_and_constraint_changes(self):
        expected = {name: [] for name in schema.QUERIES}
        actual = {name: [] for name in schema.QUERIES}
        expected['columns'] = [['user', 'password', 'varchar(255)', 'YES', None]]
        actual['columns'] = [['user', 'password', 'varchar(255)', 'NO', None]]
        expected['indexes'] = [['user', 'uk_user_phone_active', 0]]
        expected['checks'] = [['user', 'ck_user_phone_pair', 'phone pair', 'YES']]
        differences = schema.differences(expected, actual)
        self.assertTrue(any(line.startswith('columns:') for line in differences))
        self.assertTrue(any(line.startswith('indexes:') for line in differences))
        self.assertTrue(any(line.startswith('checks:') for line in differences))

    def test_column_order_does_not_change_schema_contract(self):
        left = {name: [] for name in schema.QUERIES}
        right = {name: [] for name in schema.QUERIES}
        left['columns'] = [['user', 'id'], ['user', 'phone']]
        right['columns'] = list(reversed(left['columns']))
        self.assertEqual(schema.differences(left, right), [])

    def test_preflight_metadata_queries_are_select_only(self):
        for statement in schema.QUERIES.values():
            self.assertTrue(statement.lstrip().startswith('SELECT '))
            self.assertNotIn(';', statement)

    def test_current_initialization_has_no_out_of_scope_database_writes(self):
        paths = schema.initialization_files()
        self.assertEqual(paths[0], schema.SCHEMA)
        self.assertEqual(len(paths), len(set(paths)))
        for path in paths:
            self.assertTrue(schema.scoped_sql(path.read_text()).strip(), str(path))


if __name__ == '__main__':
    unittest.main()
