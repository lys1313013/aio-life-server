// 将原学校归入本科。遇到不同的本科值时保留两边，供人工确认。
MATCH (person:Person)
WHERE person.school IS NOT NULL
WITH person, person.undergraduateSchool IS NULL OR person.undergraduateSchool = person.school AS canMigrate
FOREACH (_ IN CASE WHEN canMigrate THEN [1] ELSE [] END |
    SET person.undergraduateSchool = person.school
    REMOVE person.school
)
RETURN count(CASE WHEN canMigrate THEN 1 END) AS migrated,
       count(CASE WHEN NOT canMigrate THEN 1 END) AS conflicts;
