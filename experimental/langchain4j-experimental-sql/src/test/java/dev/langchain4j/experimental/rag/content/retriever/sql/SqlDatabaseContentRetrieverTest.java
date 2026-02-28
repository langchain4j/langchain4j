package dev.langchain4j.experimental.rag.content.retriever.sql;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import javax.sql.DataSource;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class SqlDatabaseContentRetrieverTest {

    static DataSource dataSource;

    @BeforeAll
    static void beforeAll() throws SQLException {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1");
        ds.setUser("sa");
        ds.setPassword("");
        dataSource = ds;

        try (Connection conn = ds.getConnection();
                Statement stmt = conn.createStatement()) {
            stmt.execute(
                    "CREATE TABLE customers (customer_id INT PRIMARY KEY, first_name VARCHAR(50), last_name VARCHAR(50))");
            stmt.execute(
                    "CREATE TABLE products (product_id INT PRIMARY KEY, product_name VARCHAR(100), price DECIMAL(10,2))");
            stmt.execute(
                    "CREATE TABLE orders (order_id INT PRIMARY KEY, customer_id INT, product_id INT, quantity INT)");

            stmt.execute("INSERT INTO customers VALUES (1, 'John', 'Doe')");
            stmt.execute("INSERT INTO customers VALUES (2, 'Jane', 'Smith')");
            stmt.execute("INSERT INTO customers VALUES (3, 'Alice', 'Johnson')");
            stmt.execute("INSERT INTO products VALUES (10, 'Notebook', 12.99)");
            stmt.execute("INSERT INTO products VALUES (20, 'Pen', 1.50)");
            stmt.execute("INSERT INTO orders VALUES (100, 1, 10, 2)");
            stmt.execute("INSERT INTO orders VALUES (200, 2, 20, 5)");
        }
    }

    @AfterAll
    static void afterAll() throws SQLException {
        try (Connection conn = dataSource.getConnection();
                Statement stmt = conn.createStatement()) {
            stmt.execute("DROP TABLE orders");
            stmt.execute("DROP TABLE products");
            stmt.execute("DROP TABLE customers");
        }
    }

    // --- extractTableNames tests ---

    @Test
    void should_extract_table_names_from_ddl() {
        String ddl = "CREATE TABLE customers (\n  id INT\n);\nCREATE TABLE products (\n  id INT\n);";
        List<String> names = SqlDatabaseContentRetriever.extractTableNames(ddl);
        assertThat(names).containsExactly("customers", "products");
    }

    @Test
    void should_extract_table_names_case_insensitive() {
        String ddl = "create table Foo (\n  id INT\n);\nCREATE TABLE bar (\n  id INT\n);";
        List<String> names = SqlDatabaseContentRetriever.extractTableNames(ddl);
        assertThat(names).containsExactly("Foo", "bar");
    }

    @Test
    void should_return_empty_for_no_tables() {
        List<String> names = SqlDatabaseContentRetriever.extractTableNames("SELECT 1");
        assertThat(names).isEmpty();
    }

    // --- generateSampleData tests ---

    @Test
    void should_generate_sample_data_with_limit() {
        String ddl = "CREATE TABLE customers (\n  customer_id INT\n);";
        String sampleData = SqlDatabaseContentRetriever.generateSampleData(dataSource, ddl, 2);

        assertThat(sampleData)
                .contains("Table customers:")
                .contains("CUSTOMER_ID") // H2 upper-cases column names
                .contains("John")
                .contains("Jane");
    }

    @Test
    void should_limit_sample_rows() {
        String ddl = "CREATE TABLE customers (\n  customer_id INT\n);";
        String sampleData = SqlDatabaseContentRetriever.generateSampleData(dataSource, ddl, 1);

        assertThat(sampleData).contains("Table customers:");
        // Only 1 data row (plus header), so at most 2 customer names
        long dataLines = sampleData
                .lines()
                .filter(line -> line.contains("John") || line.contains("Jane") || line.contains("Alice"))
                .count();
        assertThat(dataLines).isEqualTo(1);
    }

    @Test
    void should_return_empty_when_max_rows_zero() {
        String ddl = "CREATE TABLE customers (\n  customer_id INT\n);";
        String sampleData = SqlDatabaseContentRetriever.generateSampleData(dataSource, ddl, 0);
        assertThat(sampleData).isEmpty();
    }

    @Test
    void should_generate_sample_data_for_multiple_tables() {
        String ddl = "CREATE TABLE customers (\n  customer_id INT\n);\nCREATE TABLE products (\n  product_id INT\n);";
        String sampleData = SqlDatabaseContentRetriever.generateSampleData(dataSource, ddl, 2);

        assertThat(sampleData)
                .contains("Table customers:")
                .contains("Table products:")
                .contains("Notebook");
    }

    // --- table filtering (via generateDDL) tests ---

    @Test
    void should_include_only_specified_tables() {
        Set<String> include = new LinkedHashSet<>(Arrays.asList("customers"));
        String ddl = invokeDDL(include, null);

        assertThat(ddl).containsIgnoringCase("customers");
        assertThat(ddl).doesNotContainIgnoringCase("products");
        assertThat(ddl).doesNotContainIgnoringCase("orders");
    }

    @Test
    void should_exclude_specified_tables() {
        Set<String> exclude = new LinkedHashSet<>(Arrays.asList("orders"));
        String ddl = invokeDDL(null, exclude);

        assertThat(ddl).containsIgnoringCase("customers");
        assertThat(ddl).containsIgnoringCase("products");
        assertThat(ddl).doesNotContainIgnoringCase("CREATE TABLE orders");
    }

    @Test
    void should_include_all_tables_when_no_filter() {
        String ddl = invokeDDL(null, null);

        assertThat(ddl).containsIgnoringCase("customers");
        assertThat(ddl).containsIgnoringCase("products");
        assertThat(ddl).containsIgnoringCase("orders");
    }

    @Test
    void include_takes_precedence_over_exclude() {
        Set<String> include = new LinkedHashSet<>(Arrays.asList("products"));
        Set<String> exclude = new LinkedHashSet<>(Arrays.asList("customers"));
        String ddl = invokeDDL(include, exclude);

        assertThat(ddl).containsIgnoringCase("products");
        assertThat(ddl).doesNotContainIgnoringCase("customers");
        assertThat(ddl).doesNotContainIgnoringCase("orders");
    }

    /**
     * Helper to call the private generateDDL method indirectly via getSqlDialect + generateDDL reflectively.
     */
    private String invokeDDL(Set<String> includeTables, Set<String> excludeTables) {
        try {
            var method = SqlDatabaseContentRetriever.class.getDeclaredMethod(
                    "generateDDL", DataSource.class, Set.class, Set.class);
            method.setAccessible(true);
            return (String) method.invoke(null, dataSource, includeTables, excludeTables);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
