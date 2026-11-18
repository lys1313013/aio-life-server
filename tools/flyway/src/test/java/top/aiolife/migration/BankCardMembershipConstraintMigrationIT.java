package top.aiolife.migration;

import java.sql.DriverManager;
import java.time.Duration;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import static org.junit.jupiter.api.Assertions.*;

/** 只使用隔离 PostgreSQL，验证银行卡和会员的取值约束及 NULL 语义。 */
class BankCardMembershipConstraintMigrationIT {
    @Test void 银行卡与会员取值约束拒绝越界值并保留空值() throws Exception {
        try (var container = new GenericContainer<>("postgres:18-alpine")
                .withEnv("POSTGRES_PASSWORD", "isolated-values")
                .withEnv("POSTGRES_DB", "fixture").withExposedPorts(5432)
                .waitingFor(Wait.forLogMessage(".*database system is ready to accept connections.*\\n", 2))
                .withStartupTimeout(Duration.ofMinutes(2))) {
            container.start();
            String url = "jdbc:postgresql://" + container.getHost() + ":" + container.getMappedPort(5432) + "/fixture";
            var config = Flyway.configure().dataSource(url, "postgres", "isolated-values")
                    .locations("filesystem:" + MigrationProject.root().resolve("sql/migrations"))
                    .validateMigrationNaming(true).placeholderReplacement(false).callbacks(new MigrationGuard());
            assertTrue(config.load().migrate().migrationsExecuted > 0);
            try (var connection = DriverManager.getConnection(url, "postgres", "isolated-values");
                 var statement = connection.createStatement()) {
                statement.execute("""
                        INSERT INTO bank_card(id,user_id,bank_id,card_type,status,statement_day,repayment_day,balance,credit_limit,expiry_month,create_user,update_user) VALUES
                          (101,1,1,'credit','normal',1,31,0,0,'2026-12-01',1,1),
                          (102,1,1,'debit','normal',NULL,NULL,NULL,NULL,NULL,1,1)
                        """);
                statement.execute("""
                        INSERT INTO membership_record(id,user_id,name,start_date,expiry_date,price,monthly_amount) VALUES
                          (201,1,'A','2026-01-01','2026-01-01',0,0),
                          (202,1,'B',NULL,'2026-12-31',NULL,0)
                        """);

                assertThrows(Exception.class, () -> statement.execute(
                        "UPDATE bank_card SET statement_day=0 WHERE id=101"));
                assertThrows(Exception.class, () -> statement.execute(
                        "UPDATE bank_card SET statement_day=32 WHERE id=101"));
                assertThrows(Exception.class, () -> statement.execute(
                        "UPDATE bank_card SET repayment_day=0 WHERE id=101"));
                assertThrows(Exception.class, () -> statement.execute(
                        "UPDATE bank_card SET repayment_day=32 WHERE id=101"));
                assertThrows(Exception.class, () -> statement.execute(
                        "UPDATE bank_card SET balance=-0.01 WHERE id=101"));
                assertThrows(Exception.class, () -> statement.execute(
                        "UPDATE bank_card SET credit_limit=-0.01 WHERE id=101"));
                assertThrows(Exception.class, () -> statement.execute(
                        "UPDATE bank_card SET expiry_month='2026-12-15' WHERE id=101"));
                assertThrows(Exception.class, () -> statement.execute(
                        "UPDATE membership_record SET start_date='2026-02-01', expiry_date='2026-01-31' WHERE id=201"));
                assertThrows(Exception.class, () -> statement.execute(
                        "UPDATE membership_record SET price=-0.01 WHERE id=201"));
                assertThrows(Exception.class, () -> statement.execute(
                        "UPDATE membership_record SET monthly_amount=-0.01 WHERE id=201"));
            }
        }
    }
}