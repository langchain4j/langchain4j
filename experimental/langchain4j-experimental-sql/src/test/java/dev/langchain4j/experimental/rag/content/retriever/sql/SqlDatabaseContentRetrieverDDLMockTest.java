package dev.langchain4j.experimental.rag.content.retriever.sql;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

public class SqlDatabaseContentRetrieverDDLMockTest {

    @Test
    void testSinglePrimaryKeyDDL() throws Exception {
        String expectedDDL =
                """
				CREATE TABLE customers (
				  customer_id INT(10) NOT NULL PRIMARY KEY,
				  first_name VARCHAR(50) NULL,
				  last_name VARCHAR(50) NULL,
				  email VARCHAR(100) NULL
				);
				""";

        String ddl = generateMockDDL(
                "customers",
                new String[][] {
                    {"customer_id", "INT", "10", "NO", null, "", "0"},
                    {"first_name", "VARCHAR", "50", "YES", null, "", "0"},
                    {"last_name", "VARCHAR", "50", "YES", null, "", "0"},
                    {"email", "VARCHAR", "100", "YES", null, "", "0"},
                },
                new String[] {"customer_id"},
                null,
                null);

        assertTrue(
                ddl.replaceAll("\\s+", " ")
                        .contains(expectedDDL.replaceAll("\\s+", " ").trim()),
                "DDL should match expected structure for single primary key");
    }

    @Test
    void testCompositePrimaryKeyDDL() throws Exception {
        String expectedDDL =
                """
				CREATE TABLE order_items (
				  order_id INT(10) NOT NULL,
				  product_id INT(10) NOT NULL,
				  quantity INT(10) NULL,
				  price DECIMAL(10) NULL,
				  PRIMARY KEY (order_id, product_id)
				);
				""";

        String ddl = generateMockDDL(
                "order_items",
                new String[][] {
                    {"order_id", "INT", "10", "NO", null, "", "0"},
                    {"product_id", "INT", "10", "NO", null, "", "0"},
                    {"quantity", "INT", "10", "YES", null, "", "0"},
                    {"price", "DECIMAL", "10", "YES", null, "", "2"}, // scale is ignored in logic
                },
                new String[] {"order_id", "product_id"},
                null,
                null);

        assertTrue(
                ddl.replaceAll("\\s+", " ")
                        .contains(expectedDDL.replaceAll("\\s+", " ").trim()),
                "DDL should match expected structure for composite primary key");
    }

    @Test
    void testForeignKeysAndCommentsDDL() throws Exception {
        String ddl = generateMockDDL(
                "orders",
                new String[][] {
                    {"customer_id", "INT", "10", "NO", null, "The customer placing the order", "0"},
                    {"product_id", "INT", "10", "NO", null, "The product being ordered", "0"},
                },
                new String[] {"order_id"},
                new String[][] {
                    {"customer_id", "customers", "customer_id"}, {"product_id", "products", "product_id"},
                },
                "Order data");

        assertTrue(
                ddl.replaceAll("\\s+", " ")
                        .contains(
                                "customer_id INT(10) NOT NULL, product_id INT(10) NOT NULL, FOREIGN KEY (customer_id) REFERENCES customers(customer_id), FOREIGN KEY (product_id) REFERENCES products(product_id)"
                                        .replaceAll("\\s+", " ")
                                        .trim()),
                "DDL should include foreign key definitions");

        assertTrue(ddl.contains("COMMENT ON COLUMN orders.customer_id IS 'The customer placing the order';"));
        assertTrue(ddl.contains("COMMENT ON COLUMN orders.product_id IS 'The product being ordered';"));
        assertTrue(ddl.contains("COMMENT ON TABLE orders IS 'Order data';"));
    }

    private String generateMockDDL(
            String tableName, String[][] columnsMeta, String[] pkColumns, String[][] foreignKeys, String tableComment)
            throws Exception {
        DataSource ds = mock(DataSource.class);
        Connection conn = mock(Connection.class);
        DatabaseMetaData metaData = mock(DatabaseMetaData.class);

        ResultSet tables = mock(ResultSet.class);
        ResultSet columns = mock(ResultSet.class);
        ResultSet pk = mock(ResultSet.class);
        ResultSet fks = mock(ResultSet.class);
        ResultSet remarks = mock(ResultSet.class);

        when(ds.getConnection()).thenReturn(conn);
        when(conn.getMetaData()).thenReturn(metaData);

        when(metaData.getTables(null, null, "%", new String[] {"TABLE"})).thenReturn(tables);
        when(tables.next()).thenReturn(true, false);
        when(tables.getString("TABLE_NAME")).thenReturn(tableName);

        when(metaData.getColumns(null, null, tableName, null)).thenReturn(columns);
        final int[] colIndex = {-1};
        when(columns.next()).thenAnswer(invocation -> ++colIndex[0] < columnsMeta.length);
        when(columns.getString("COLUMN_NAME")).thenAnswer(invocation -> columnsMeta[colIndex[0]][0]);
        when(columns.getString("TYPE_NAME")).thenAnswer(invocation -> columnsMeta[colIndex[0]][1]);
        when(columns.getInt("COLUMN_SIZE")).thenAnswer(invocation -> parseSize(columnsMeta[colIndex[0]][2]));
        when(columns.getInt("DECIMAL_DIGITS")).thenAnswer(invocation -> parseScale(columnsMeta[colIndex[0]][6]));
        when(columns.getString("IS_NULLABLE")).thenAnswer(invocation -> columnsMeta[colIndex[0]][3]);
        when(columns.getString("COLUMN_DEF")).thenAnswer(invocation -> columnsMeta[colIndex[0]][4]);
        when(columns.getString("REMARKS")).thenAnswer(invocation -> columnsMeta[colIndex[0]][5]);

        when(metaData.getPrimaryKeys(null, null, tableName)).thenReturn(pk);
        final int[] pkIndex = {-1};
        when(pk.next()).thenAnswer(invocation -> ++pkIndex[0] < pkColumns.length);
        when(pk.getString("COLUMN_NAME")).thenAnswer(invocation -> pkColumns[pkIndex[0]]);

        if (foreignKeys != null) {
            when(metaData.getImportedKeys(null, null, tableName)).thenReturn(fks);
            final int[] fkIndex = {-1};
            when(fks.next()).thenAnswer(invocation -> ++fkIndex[0] < foreignKeys.length);
            when(fks.getString("FKCOLUMN_NAME")).thenAnswer(invocation -> foreignKeys[fkIndex[0]][0]);
            when(fks.getString("PKTABLE_NAME")).thenAnswer(invocation -> foreignKeys[fkIndex[0]][1]);
            when(fks.getString("PKCOLUMN_NAME")).thenAnswer(invocation -> foreignKeys[fkIndex[0]][2]);
        } else {
            when(metaData.getImportedKeys(null, null, tableName)).thenReturn(fks);
            when(fks.next()).thenReturn(false);
        }

        when(metaData.getTables(null, null, tableName, null)).thenReturn(remarks);
        when(remarks.next()).thenReturn(true);
        when(remarks.getString("REMARKS")).thenReturn(tableComment);

        return SqlDatabaseContentRetriever.generateDDL(ds);
    }

    private int parseSize(String size) {
        if (size.contains(",")) return Integer.parseInt(size.split(",")[0]);
        return Integer.parseInt(size);
    }

    private int parseScale(String scale) {
        return (scale != null && !scale.isEmpty()) ? Integer.parseInt(scale) : 0;
    }
}
