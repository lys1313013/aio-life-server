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
            var match = Pattern.compile("^(\\d+)\\.(\\d+)(?:\\.(\\d+))?").matcher(value);
            if (!match.find()) throw new IllegalArgumentException("Expected PostgreSQL version, got " + value);
            var version = new Version(Integer.parseInt(match.group(1)), Integer.parseInt(match.group(2)),
                    match.group(3) == null ? 0 : Integer.parseInt(match.group(3)));
            if (version.major < 18) throw new IllegalArgumentException("Requires PostgreSQL 18+");
            return version;
        }
        public int compareTo(Version other) {
            int result = Integer.compare(major, other.major);
            if (result == 0) result = Integer.compare(minor, other.minor);
            return result == 0 ? Integer.compare(patch, other.patch) : result;
        }
    }
    public static Version version(Connection connection) throws SQLException {
        if (!"PostgreSQL".equals(connection.getMetaData().getDatabaseProductName()))
            throw new IllegalArgumentException("Only PostgreSQL is supported");
        return Version.parse(rows(connection, "SHOW server_version").getFirst().getFirst());
    }
    public static Map<String, String> queries() {
        Map<String, String> queries = new LinkedHashMap<>();
        queries.put("tables", """
            SELECT c.relname, c.relpersistence::text, current_setting('server_encoding'),
                   COALESCE(obj_description(c.oid), '')
            FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace
            WHERE n.nspname=current_schema() AND c.relkind='r' ORDER BY c.relname
            """);
        queries.put("columns", """
            SELECT c.relname, a.attname, format_type(a.atttypid,a.atttypmod),
                   CASE WHEN a.attnotnull THEN 'NO' ELSE 'YES' END,
                   CASE WHEN a.attgenerated='' THEN pg_get_expr(d.adbin,d.adrelid) END,
                   a.attidentity::text,
                   CASE WHEN a.attgenerated<>'' THEN pg_get_expr(d.adbin,d.adrelid) END,
                   current_setting('server_encoding'), co.collname, COALESCE(col_description(c.oid,a.attnum),'')
            FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace
            JOIN pg_attribute a ON a.attrelid=c.oid AND a.attnum>0 AND NOT a.attisdropped
            LEFT JOIN pg_attrdef d ON d.adrelid=c.oid AND d.adnum=a.attnum
            LEFT JOIN pg_collation co ON co.oid=a.attcollation
            WHERE n.nspname=current_schema() AND c.relkind='r' ORDER BY c.relname,a.attname
            """);
        queries.put("indexes", """
            SELECT t.relname, i.relname, CASE WHEN x.indisunique THEN '0' ELSE '1' END,
                   k.ordinality::text, a.attname, NULL::text, am.amname, k.options::text,
                   x.indisvalid::text, pg_get_indexdef(i.oid)
            FROM pg_index x JOIN pg_class t ON t.oid=x.indrelid
            JOIN pg_namespace n ON n.oid=t.relnamespace JOIN pg_class i ON i.oid=x.indexrelid
            JOIN pg_am am ON am.oid=i.relam
            CROSS JOIN LATERAL unnest(x.indkey::smallint[],x.indoption::smallint[])
                WITH ORDINALITY AS k(attnum,options,ordinality)
            LEFT JOIN pg_attribute a ON a.attrelid=t.oid AND a.attnum=k.attnum
            WHERE n.nspname=current_schema() ORDER BY t.relname,i.relname,k.ordinality
            """);
        queries.put("checks", """
            SELECT t.relname,c.conname,pg_get_constraintdef(c.oid),c.convalidated::text
            FROM pg_constraint c JOIN pg_class t ON t.oid=c.conrelid
            JOIN pg_namespace n ON n.oid=t.relnamespace
            WHERE n.nspname=current_schema() AND c.contype='c' ORDER BY t.relname,c.conname
            """);
        queries.put("foreign_keys", """
            SELECT t.relname,c.conname,a.attname,k.ordinality::text,r.relname,b.attname,
                   c.confupdtype::text,c.confdeltype::text
            FROM pg_constraint c JOIN pg_class t ON t.oid=c.conrelid
            JOIN pg_namespace n ON n.oid=t.relnamespace JOIN pg_class r ON r.oid=c.confrelid
            CROSS JOIN LATERAL unnest(c.conkey,c.confkey) WITH ORDINALITY AS k(local_col,remote_col,ordinality)
            JOIN pg_attribute a ON a.attrelid=t.oid AND a.attnum=k.local_col
            JOIN pg_attribute b ON b.attrelid=r.oid AND b.attnum=k.remote_col
            WHERE n.nspname=current_schema() AND c.contype='f' ORDER BY t.relname,c.conname,k.ordinality
            """);
        return queries;
    }
    public static SchemaSnapshot read(Connection connection) throws SQLException {
        Map<String, List<List<String>>> result = new LinkedHashMap<>();
        version(connection);
        for (var query : queries().entrySet()) {
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
    public List<String> differences(SchemaSnapshot actual) {
        List<String> issues = new ArrayList<>();
        for (String section : WIDTHS.keySet()) {
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
            row.set(2, row.get(2).toLowerCase(Locale.ROOT));
            row.set(6, expression(row.get(6)));
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
