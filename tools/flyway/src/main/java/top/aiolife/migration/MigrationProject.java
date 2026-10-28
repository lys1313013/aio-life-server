package top.aiolife.migration;

import org.flywaydb.core.api.configuration.Configuration;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;

/** 摘要只用于证明结构契约来源；迁移发现、排序和执行全部交给 Flyway。 */
public final class MigrationProject {
    private MigrationProject() {}
    public static Path root() {
        return Path.of(System.getProperty("migration.root", "../..")).toAbsolutePath().normalize();
    }
    public static Path root(Configuration configuration) {
        var locations = configuration.getLocations();
        if (locations.length != 1 || !locations[0].isFileSystem())
            throw new IllegalArgumentException("Use the project's single filesystem migration location");
        Path sql = Path.of(locations[0].getPath()).toAbsolutePath().normalize();
        if (!sql.endsWith(Path.of("sql", "migrations")))
            throw new IllegalArgumentException("Migration location must end in sql/migrations");
        return sql.getParent().getParent();
    }
    /** 强校验只绑定 SQL 和影响迁移的配置；不绑定 Java 文本、POM 排版和测试依赖。 */
    public static Map<String, String> sources(Path root) throws IOException {
        Map<String, String> result = new TreeMap<>();
        collect(root, "sql/migrations", ".sql", result);
        result.put("flyway.settings", sha256(settings(root).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        return result;
    }
    /** 工具来源只作追溯，不参与契约有效性判断。 */
    public static Map<String, String> toolSources(Path root) throws IOException {
        Map<String, String> result = new TreeMap<>();
        add(root, root.resolve("tools/flyway/pom.xml"), result);
        collect(root, "tools/flyway/src/main/java", ".java", result);
        return result;
    }
    private static void collect(Path root, String directory, String suffix, Map<String, String> result) throws IOException {
        try (var paths = Files.walk(root.resolve(directory))) {
            for (Path file : paths.filter(Files::isRegularFile).filter(p -> p.toString().endsWith(suffix)).sorted().toList())
                add(root, file, result);
        }
    }
    static String settings(Path root) throws IOException {
        try {
            var factory = javax.xml.parsers.DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            var document = factory.newDocumentBuilder().parse(root.resolve("tools/flyway/pom.xml").toFile());
            var xpath = javax.xml.xpath.XPathFactory.newInstance().newXPath();
            Map<String, String> values = new TreeMap<>();
            for (String path : new String[]{"/project/properties/flyway.version", "/project/properties/adoption.version",
                    "/project/dependencies/dependency[artifactId='mariadb-java-client']/version"})
                values.put(path, xpath.evaluate(path, document).trim());
            var plugin = (org.w3c.dom.Node) xpath.evaluate("/project/build/plugins/plugin[artifactId='flyway-maven-plugin']", document, javax.xml.xpath.XPathConstants.NODE);
            if (plugin == null) throw new IllegalArgumentException("Missing Flyway plugin configuration");
            values.put("plugin.version", xpath.evaluate("version", plugin).trim());
            var configuration = (org.w3c.dom.Node) xpath.evaluate("configuration", plugin, javax.xml.xpath.XPathConstants.NODE);
            if (configuration == null) throw new IllegalArgumentException("Missing Flyway configuration");
            collectSettings(configuration, "flyway", values);
            return SchemaContract.JSON.writeValueAsString(values);
        } catch (Exception error) { throw new IOException("Cannot read migration settings", error); }
    }
    private static void collectSettings(org.w3c.dom.Node node, String path, Map<String, String> values) {
        boolean leaf = true;
        var children = node.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            var child = children.item(i);
            if (child.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
                leaf = false;
                collectSettings(child, path + "." + child.getNodeName(), values);
            }
        }
        if (leaf) values.merge(path, node.getTextContent().trim(), (left, right) -> left + "\n" + right);
    }
    private static void add(Path root, Path file, Map<String, String> result) throws IOException {
        result.put(root.relativize(file).toString().replace('\\', '/'), sha256(Files.readAllBytes(file)));
    }
    public static String sha256(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
    }
}
