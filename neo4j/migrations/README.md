# Neo4j 数据迁移

## 人物学校字段拆分

`Person` 节点新增六个可选字符串属性，对应小学 `primarySchool`、初中 `juniorHighSchool`、高中 `highSchool`、本科 `undergraduateSchool`、硕士 `mastersSchool` 和博士 `doctoralSchool`。人物新增、编辑、详情和档案导出使用这些字段。

升级已有图谱时，备份目标 Neo4j 数据库并暂停应用对它的写入。在部署新版服务和客户端前，使用目标数据库的连接参数执行：

```bash
cypher-shell -a "$NEO4J_URI" -u "$NEO4J_USERNAME" -d "$NEO4J_DATABASE" \
  -f neo4j/migrations/V20261119_01__split_person_school.cypher
```

脚本将原 `school` 原样移入 `undergraduateSchool`，再删除旧属性。原值与已有本科值相同也可迁移。如果两者不同，脚本保留这条节点的两个值并计入 `conflicts`。遇到冲突时，确认两个值后处理对应节点并重跑脚本。只有 `conflicts` 为 0，且下列查询确认 `remaining` 为 0 时，才切换到新版服务。

```cypher
MATCH (person:Person)
WHERE person.school IS NOT NULL
RETURN count(person) AS remaining;
```

脚本可以重复执行。新建的图谱直接使用新版应用。
