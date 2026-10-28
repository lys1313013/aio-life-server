package top.aiolife.migration;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Pattern;

/** 真实结构只读比较；不把 Flyway 的历史校验当成实际结构校验。 */
public record SchemaSnapshot(Map<String, List<List<String>>> sections) {
    public static final String HISTORY = "flyway_schema_history";
    public static final Map<String, Integer> WIDTHS = Map.of("tables", 4, "columns", 10, "indexes", 10, "checks", 4, "foreign_keys", 8);
    private static final Pattern TOKEN = Pattern.compile("'(?:''|\\\\.|[^'])*'|\"(?:\"\"|\\\\.|[^\"])*\"|`(?:``|[^`])*`|[\\w$]+|<=>|<=|>=|<>|!=|<<|>>|&&|\\|\\||[^\\s]");

    public record Version(int major, int minor, int patch) implements Comparable<Version> {
        public static Version parse(String value) {
            var match = Pattern.compile("^(\\d+)\\.(\\d+)\\.(\\d+)").matcher(value);
            if (!match.find() || value.toLowerCase(Locale.ROOT).contains("mariadb"))
                throw new IllegalArgumentException("Expected MySQL server, got " + value);
            var version = new Version(Integer.parseInt(match.group(1)), Integer.parseInt(match.group(2)), Integer.parseInt(match.group(3)));
            if (version.compareTo(new Version(5, 7, 8)) < 0) throw new IllegalArgumentException("Requires MySQL 5.7.8+");
            return version;
        }
        public int compareTo(Version other) {
            int result = Integer.compare(major, other.major);
            if (result == 0) result = Integer.compare(minor, other.minor);
            return result == 0 ? Integer.compare(patch, other.patch) : result;
        }
        public boolean enforcesChecks() { return compareTo(new Version(8, 0, 16)) >= 0; }
    }
    public static Version version(Connection connection) throws SQLException {
        return Version.parse(rows(connection, "SELECT VERSION()").getFirst().getFirst());
    }
    public static Map<String, String> queries(Version version) {
        Map<String, String> queries = new LinkedHashMap<>();
        queries.put("tables", "SELECT table_name,engine,table_collation,table_comment FROM information_schema.tables WHERE table_schema=DATABASE() AND table_type='BASE TABLE' ORDER BY table_name");
        queries.put("columns", "SELECT table_name,column_name,column_type,is_nullable,column_default,extra,generation_expression,character_set_name,collation_name,column_comment FROM information_schema.columns WHERE table_schema=DATABASE() ORDER BY table_name,column_name");
        String indexAttributes = version.compareTo(new Version(8, 0, 13)) >= 0 ? "is_visible,expression" : version.major() >= 8 ? "is_visible,NULL" : "'YES',NULL";
        queries.put("indexes", "SELECT table_name,index_name,non_unique,seq_in_index,column_name,sub_part,index_type,collation," + indexAttributes + " FROM information_schema.statistics WHERE table_schema=DATABASE() ORDER BY table_name,index_name,seq_in_index");
        queries.put("checks", version.enforcesChecks()
                ? "SELECT t.table_name,t.constraint_name,c.check_clause,t.enforced FROM information_schema.table_constraints t JOIN information_schema.check_constraints c ON t.constraint_schema=c.constraint_schema AND t.constraint_name=c.constraint_name WHERE t.constraint_schema=DATABASE() AND t.constraint_type='CHECK' ORDER BY t.table_name,t.constraint_name"
                : "SELECT NULL,NULL,NULL,NULL FROM DUAL WHERE 1=0");
        queries.put("foreign_keys", "SELECT k.table_name,k.constraint_name,k.column_name,k.ordinal_position,k.referenced_table_name,k.referenced_column_name,r.update_rule,r.delete_rule FROM information_schema.key_column_usage k JOIN information_schema.referential_constraints r ON k.constraint_schema=r.constraint_schema AND k.constraint_name=r.constraint_name WHERE k.constraint_schema=DATABASE() AND k.referenced_table_name IS NOT NULL ORDER BY k.table_name,k.constraint_name,k.ordinal_position");
        return queries;
    }
    public static SchemaSnapshot read(Connection connection) throws SQLException {
        Map<String, List<List<String>>> result = new LinkedHashMap<>();
        for (var query : queries(version(connection)).entrySet()) {
            result.put(query.getKey(), rows(connection, query.getValue()).stream().filter(row -> !HISTORY.equals(row.getFirst())).toList());
        }
        return new SchemaSnapshot(result);
    }
    public static List<List<String>> rows(Connection connection, String sql) throws SQLException {
        try (var statement = connection.createStatement(); var result = statement.executeQuery(sql)) {
            List<List<String>> rows = new ArrayList<>();
            while (result.next()) {
                List<String> row = new ArrayList<>();
                for (int i = 1; i <= result.getMetaData().getColumnCount(); i++) row.add(result.getString(i));
                rows.add(row);
            }
            return rows;
        }
    }
    public void validate() {
        if (sections == null || !sections.keySet().equals(WIDTHS.keySet())) throw new IllegalArgumentException("Incomplete schema sections");
        for (var section : sections.entrySet()) {
            if (section.getValue() == null || section.getValue().stream().anyMatch(row -> row == null || row.size() != WIDTHS.get(section.getKey())))
                throw new IllegalArgumentException("Incomplete schema rows: " + section.getKey());
        }
        if (sections.get("tables").isEmpty()) throw new IllegalArgumentException("Empty schema contract is not a baseline");
    }
    public List<String> differences(SchemaSnapshot actual, Version actualVersion, boolean acceptMissingChecks) {
        List<String> issues = new ArrayList<>();
        for (String section : WIDTHS.keySet()) {
            if (section.equals("checks") && !actualVersion.enforcesChecks() && !sections.get(section).isEmpty()) {
                if (!acceptMissingChecks) issues.add("platform: MySQL cannot enforce " + sections.get(section).size() + " CHECK constraints; requires 8.0.16+");
                continue;
            }
            var left = normalized(section, sections.get(section));
            var right = normalized(section, actual.sections.get(section));
            left.stream().filter(row -> !right.contains(row)).forEach(row -> issues.add(section + ": missing/different " + row));
            right.stream().filter(row -> !left.contains(row)).forEach(row -> issues.add(section + ": unexpected/different " + row));
        }
        return issues;
    }
    private static TreeSet<String> normalized(String section, List<List<String>> rows) {
        var result = new TreeSet<String>();
        for (var row : rows) result.add(SchemaContract.JSON.valueToTree(normalize(section, row)).toString());
        return result;
    }
    public static List<String> normalize(String section, List<String> source) {
        List<String> row = new ArrayList<>(source);
        if (section.equals("columns")) {
            String type = row.get(2).toLowerCase(Locale.ROOT);
            if (!type.contains("zerofill")) type = type.replaceFirst("^(tinyint|smallint|mediumint|int|bigint)\\(\\d+\\)", "$1");
            row.set(2, type);
            if (type.matches("^(timestamp|datetime)\\b.*") && row.get(4) != null)
                row.set(4, row.get(4).replaceFirst("(?i)^current_timestamp(?:\\(\\))?$", "CURRENT_TIMESTAMP"));
            row.set(5, row.get(5).toLowerCase(Locale.ROOT).replace("default_generated", "").replace("current_timestamp()", "current_timestamp").replaceAll("\\s+", " ").trim());
            row.set(6, expression(row.get(6)));
        } else if (section.equals("foreign_keys")) {
            for (int i = 6; i < 8; i++) if ("NO ACTION".equals(row.get(i))) row.set(i, "RESTRICT");
        } else if (section.equals("indexes") && row.get(9) != null) row.set(9, expression(row.get(9)));
        else if (section.equals("checks")) row.set(2, expression(row.get(2)));
        return row;
    }
    public static String expression(String expression) {
        if (expression == null) return null;
        var matcher = TOKEN.matcher(expression);
        List<String> tokens = new ArrayList<>();
        while (matcher.find()) {
            String token = matcher.group();
            tokens.add("'\"`".indexOf(token.charAt(0)) >= 0 ? token : token.toLowerCase(Locale.ROOT));
        }
        while (tokens.size() >= 2 && tokens.getFirst().equals("(") && tokens.getLast().equals(")")) {
            int depth = 0, end = -1;
            for (int i = 0; i < tokens.size(); i++) {
                if (tokens.get(i).equals("(")) depth++;
                if (tokens.get(i).equals(")")) depth--;
                if (depth == 0) { end = i; break; }
            }
            if (end != tokens.size() - 1) break;
            tokens = new ArrayList<>(tokens.subList(1, tokens.size() - 1));
        }
        return String.join(" ", tokens);
    }
}
