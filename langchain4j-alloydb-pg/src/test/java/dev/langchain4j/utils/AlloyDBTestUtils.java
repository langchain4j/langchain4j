package dev.langchain4j.utils;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

public class AlloyDBTestUtils {

    private static final Random RANDOM = new Random();

    public static void verifyColumns(
            Connection connection, String schemaName, String tableName, Set<String> expectedColumns)
            throws SQLException {
        Set<String> actualNames = new HashSet<>();

        ResultSet resultSet = connection
                .createStatement()
                .executeQuery(String.format("SELECT * FROM \"%s\".\"%s\"", schemaName, tableName));
        ResultSetMetaData rsMeta = resultSet.getMetaData();
        int columnCount = rsMeta.getColumnCount();
        for (int i = 1; i <= columnCount; i++) {
            actualNames.add(rsMeta.getColumnName(i));
        }
        assertThat(actualNames).isEqualTo(expectedColumns);
    }

    public static void verifyIndex(Connection connection, String tableName, String type, String expected)
            throws SQLException {
        ResultSet indexes = connection
                .createStatement()
                .executeQuery(String.format(
                        "SELECT indexdef FROM pg_indexes WHERE tablename = '%s' AND indexname = '%s_%s_index'",
                        tableName.toLowerCase(), tableName.toLowerCase(), type));
        while (indexes.next()) {
            assertThat(indexes.getString("indexdef")).contains(expected);
        }
    }

    public static float[] randomVector(int length) {
        float[] vector = new float[length];
        for (int i = 0; i < vector.length; i++) {
            vector[i] = RANDOM.nextFloat() * 1000;
        }
        return vector;
    }
}
