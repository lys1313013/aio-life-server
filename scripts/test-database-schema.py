import importlib.util
import copy
import tempfile
import unittest
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import Mock, patch

spec = importlib.util.spec_from_file_location('schema', Path(__file__).with_name('check-database-schema.py'))
schema = importlib.util.module_from_spec(spec)
spec.loader.exec_module(schema)


def empty_schema():
    return {name: [] for name in schema.QUERIES}


def column(name='id', kind='bigint', nullable='NO', default=None, extra='', expression='', comment='主键'):
    return ['fixture', name, kind, nullable, default, extra, expression, None, None, comment]


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
        expected = empty_schema()
        actual = empty_schema()
        expected['columns'] = [column('password', 'varchar(255)', 'YES')]
        actual['columns'] = [column('password', 'varchar(255)', 'NO')]
        expected['indexes'] = [['user', 'uk_user_phone_active', 0, 1, 'phone', None, 'BTREE', 'A', 'YES', None]]
        expected['checks'] = [['user', 'ck_user_phone_pair', 'phone pair', 'YES']]
        differences = schema.differences(expected, actual)
        self.assertTrue(any(line.startswith('columns:') for line in differences))
        self.assertTrue(any(line.startswith('indexes:') for line in differences))
        self.assertTrue(any(line.startswith('checks:') for line in differences))

    def test_column_order_does_not_change_schema_contract(self):
        left = empty_schema()
        right = empty_schema()
        left['columns'] = [column(), column('phone', 'varchar(20)', 'YES')]
        right['columns'] = list(reversed(left['columns']))
        self.assertEqual(schema.differences(left, right), [])

    def test_preflight_metadata_queries_are_select_only(self):
        for version in ((5, 7, 44), (8, 0, 12), (8, 0, 40), (8, 4, 2)):
            for statement in schema.queries_for_version(version).values():
                self.assertTrue(statement.lstrip().startswith('SELECT '))
                self.assertNotIn(';', statement)

    def test_mysql57_does_not_query_unavailable_metadata(self):
        mysql = schema.Mysql(SimpleNamespace(mysql='mysql', host='localhost', port=3306, user='test'))
        mysql.run = Mock(side_effect=lambda sql, database=None: '5.7.44-log' if sql == 'SELECT VERSION()' else '')
        self.assertEqual(mysql.schema('fixture'), empty_schema())
        sql = '\n'.join(call.args[0] for call in mysql.run.call_args_list)
        self.assertNotIn('is_visible', sql)
        self.assertNotIn('collation,expression', sql)
        self.assertNotIn('check_constraints', sql)
        self.assertNotIn('t.enforced', sql)

    def test_platform_check_gap_fails_without_hiding_other_differences(self):
        expected = empty_schema()
        actual = empty_schema()
        expected['checks'] = [['fixture', 'ck_value', '`value` > 0', 'YES']]
        expected['columns'] = [column()]
        differences = schema.differences(expected, actual, (5, 7, 44))
        self.assertTrue(any('cannot enforce 1 expected CHECK constraints' in line for line in differences))
        self.assertTrue(any(line.startswith('columns:') for line in differences))
        self.assertEqual(schema.differences(empty_schema(), actual, (5, 7, 44)), [])

    def test_verify_rejects_old_mysql_before_any_database_write(self):
        mysql = Mock()
        mysql.version.return_value = (5, 7, 44)
        with self.assertRaisesRegex(RuntimeError, 'requires MySQL 8.0.16'):
            schema.verify(SimpleNamespace(), mysql)
        mysql.run.assert_not_called()

    def test_equivalent_mysql57_and_mysql80_metadata_compare_equal(self):
        left = empty_schema()
        left['columns'] = [column(kind='bigint(20)'),
                           column('updated_at', 'datetime', default='CURRENT_TIMESTAMP', extra='on update CURRENT_TIMESTAMP'),
                           column('active', 'tinyint(4)', expression='(CASE WHEN (`id` = 0) THEN 1 ELSE NULL END)')]
        left['foreign_keys'] = [['fixture', 'fk_owner', 'id', 1, 'owner', 'id', 'RESTRICT', 'RESTRICT']]
        right = copy.deepcopy(left)
        right['columns'][0][2] = 'bigint'
        right['columns'][1][4:6] = ['CURRENT_TIMESTAMP()', 'DEFAULT_GENERATED on update CURRENT_TIMESTAMP()']
        right['columns'][2][2] = 'tinyint'
        right['columns'][2][6] = 'case when (`id`=0) then 1 else NULL end'
        right['foreign_keys'][0][6] = 'NO ACTION'
        self.assertEqual(schema.differences(left, right), [])

    def test_normalization_keeps_semantic_changes(self):
        for left, right in (
            (column(kind='bigint'), column(kind='bigint unsigned')),
            (column(kind='int(4) unsigned zerofill'), column(kind='int(8) unsigned zerofill')),
            (column('name', 'varchar(50)', default='CURRENT_TIMESTAMP'), column('name', 'varchar(50)', default='current_timestamp')),
            (column(expression='(a + b) * c'), column(expression='a + b * c')),
            (column(expression="CASE WHEN a = 1 THEN 'Yes' ELSE 'No' END"),
             column(expression="CASE WHEN a = 1 THEN 'yes' ELSE 'no' END")),
        ):
            with self.subTest(left=left, right=right):
                self.assertNotEqual(schema.normalize_row('columns', left), schema.normalize_row('columns', right))

    def test_comments_visibility_and_index_expressions_are_checked(self):
        expected = empty_schema()
        expected['tables'] = [['fixture', 'InnoDB', 'utf8mb4_unicode_ci', '测试表']]
        expected['columns'] = [column(comment='主键')]
        expected['indexes'] = [['fixture', 'idx_value', 1, 1, None, None, 'BTREE', 'A', 'YES', '(`id` + 1)']]
        actual = copy.deepcopy(expected)
        actual['tables'][0][3] = ''
        actual['columns'][0][9] = ''
        actual['indexes'][0][8:10] = ['NO', '(`id` + 2)']
        differences = schema.differences(expected, actual)
        for section in ('tables', 'columns', 'indexes'):
            self.assertTrue(any(line.startswith(section + ':') for line in differences))

    def test_old_contract_is_rejected_instead_of_silently_skipping_comments(self):
        with self.assertRaisesRegex(RuntimeError, 'version 2 is required'):
            schema.validate_contract({'version': 1, 'schema': empty_schema()})

    def test_contract_covers_source_contents_and_order(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            files = [root / name for name in ('baseline.sql', 'migration.sql', 'seeds.sql')]
            for path in files:
                path.write_text('SELECT 1;\n')
            with patch.object(schema, 'ROOT', root), patch.object(schema, 'initialization_files', return_value=files):
                sources = schema.initialization_manifest(files)
                contract = {'version': 2, 'initialization_sources': sources,
                            'initialization_sha256': schema.manifest_digest(sources), 'schema': empty_schema()}
                schema.validate_contract(contract)
                contract['initialization_sources'] = list(reversed(sources))
                with self.assertRaisesRegex(RuntimeError, 'sources differ'):
                    schema.validate_contract(contract)
                contract['initialization_sources'] = sources
                files[1].write_text('SELECT 2;\n')
                with self.assertRaisesRegex(RuntimeError, 'sources differ'):
                    schema.validate_contract(contract)

    def test_contract_rejects_incomplete_metadata(self):
        with patch.object(schema, 'initialization_manifest', return_value=[]):
            contract = {'version': 2, 'initialization_sources': [],
                        'initialization_sha256': schema.manifest_digest([]), 'schema': empty_schema()}
            contract['schema']['columns'] = [['fixture', 'id']]
            with self.assertRaisesRegex(RuntimeError, 'columns rows are incomplete'):
                schema.validate_contract(contract)

    def test_current_initialization_has_no_out_of_scope_database_writes(self):
        paths = schema.initialization_files()
        self.assertEqual(paths[0], schema.SCHEMA)
        self.assertEqual(paths[1:1 + len(schema.MIGRATIONS)], schema.MIGRATIONS)
        self.assertEqual(len(paths), len(set(paths)))
        for path in paths:
            self.assertTrue(schema.scoped_sql(path.read_text()).strip(), str(path))


if __name__ == '__main__':
    unittest.main()
