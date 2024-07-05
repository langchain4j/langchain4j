package dev.langchain4j.experimental.rag.content.retriever.sql;

import dev.langchain4j.Experimental;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.input.Prompt;
import dev.langchain4j.model.input.PromptTemplate;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
import lombok.Builder;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.select.Select;

import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;

/**
 * <b>
 * WARNING! Although fun and exciting, this class is dangerous to use! Do not ever use this in production!
 * The database user must have very limited READ-ONLY permissions!
 * Although the generated SQL is somewhat validated (to ensure that the SQL is a SELECT statement) using JSqlParser,
 * this class does not guarantee that the SQL will be harmless. Use it at your own risk!
 * </b>
 * <br>
 * <br>
 * Using the {@link DataSource} and the {@link ChatLanguageModel}, this {@link ContentRetriever}
 * attempts to generate and execute SQL queries for given natural language queries.
 * <br>
 * Optionally, {@link #sqlDialect}, {@link #databaseStructure}, {@link #promptTemplate}, and {@link #maxRetries} can be specified
 * to customize the behavior. See the javadoc of the constructor for more details.
 * Most methods can be overridden to customize the behavior further.
 * <br>
 * The default prompt template is not highly optimized,
 * so it is advised to experiment with it and see what works best for your use case.
 */
@Experimental
public class SqlDatabaseContentRetriever implements ContentRetriever {

    private static final PromptTemplate DEFAULT_PROMPT_TEMPLATE = PromptTemplate.from(
            "You are an expert in writing SQL queries.\n" +
                    "You have access to a {{sqlDialect}} database with the following structure:\n" +
                    "{{databaseStructure}}\n" +
                    "If a user asks a question that can be answered by querying this database, generate an SQL SELECT query.\n" +
                    "Do not output anything else aside from a valid SQL statement!"
    );

    private final DataSource dataSource;
    private final String sqlDialect;
    private final String databaseStructure;

    private final PromptTemplate promptTemplate;
    private final ChatLanguageModel chatLanguageModel;

    private final int maxRetries;

    /**
     * Creates an instance of a {@code SqlDatabaseContentRetriever}.
     *
     * @param dataSource        The {@link DataSource} to be used for executing SQL queries.
     *                          This is a mandatory parameter.
     *                          <b>WARNING! The database user must have very limited READ-ONLY permissions!</b>
     * @param sqlDialect        The SQL dialect, which will be provided to the LLM in the {@link SystemMessage}.
     *                          The LLM should know the specific SQL dialect in order to generate valid SQL queries.
     *                          Example: "MySQL", "PostgreSQL", etc.
     *                          This is an optional parameter. If not specified, it will be determined from the {@code DataSource}.
     * @param databaseStructure The structure of the database, which will be provided to the LLM in the {@code SystemMessage}.
     *                          The LLM should be familiar with available tables, columns, relationships, etc. in order to generate valid SQL queries.
     *                          It is best to specify the complete "CREATE TABLE ..." DDL statement for each table.
     *                          Example (shortened): "CREATE TABLE customers(\n  id INT PRIMARY KEY,\n  name VARCHAR(50), ...);\n CREATE TABLE products(...);\n ..."
     *                          This is an optional parameter. If not specified, it will be generated from the {@code DataSource}.
     *                          <b>WARNING! In this case, all tables will be visible to the LLM!</b>
     * @param promptTemplate    The {@link PromptTemplate} to be used for creating a {@code SystemMessage}.
     *                          This is an optional parameter. Default: {@link #DEFAULT_PROMPT_TEMPLATE}.
     * @param chatLanguageModel The {@link ChatLanguageModel} to be used for generating SQL queries.
     *                          This is a mandatory parameter.
     * @param maxRetries        The maximum number of retries to perform if the database cannot execute the generated SQL query.
     *                          An error message will be sent back to the LLM to try correcting the query.
     *                          This is an optional parameter. Default: 1.
     */
    @Builder
    @Experimental
    public SqlDatabaseContentRetriever(DataSource dataSource,
                                       String sqlDialect,
                                       String databaseStructure,
                                       PromptTemplate promptTemplate,
                                       ChatLanguageModel chatLanguageModel,
                                       Integer maxRetries) {
        this.dataSource = ensureNotNull(dataSource, "dataSource");
        this.sqlDialect = getOrDefault(sqlDialect, () -> getSqlDialect(dataSource));
        this.databaseStructure = getOrDefault(databaseStructure, () -> generateDDL(dataSource));
        this.promptTemplate = getOrDefault(promptTemplate, DEFAULT_PROMPT_TEMPLATE);
        this.chatLanguageModel = ensureNotNull(chatLanguageModel, "chatLanguageModel");
        this.maxRetries = getOrDefault(maxRetries, 1);
    }

    // TODO (for v2)
    // - provide a few rows of data for each table in the prompt
    // - option to select a list of tables to use/ignore

    public static String getSqlDialect(DataSource dataSource) {
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            return metaData.getDatabaseProductName();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private static String generateDDL(DataSource dataSource) {
        StringBuilder ddl = new StringBuilder();

        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();

            ResultSet tables = metaData.getTables(null, null, "%", new String[]{"TABLE"});

            while (tables.next()) {
                String tableName = tables.getString("TABLE_NAME");
                String createTableStatement = generateCreateTableStatement(tableName, metaData);
                ddl.append(createTableStatement).append("\n");
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }

        return ddl.toString();
    }

    private static String generateCreateTableStatement(String tableName, DatabaseMetaData metaData) {
        StringBuilder createTableStatement = new StringBuilder();

        try {
            ResultSet columns = metaData.getColumns(null, null, tableName, null);
            ResultSet pk = metaData.getPrimaryKeys(null, null, tableName);
            ResultSet fks = metaData.getImportedKeys(null, null, tableName);

            String primaryKeyColumn = "";
            if (pk.next()) {
                primaryKeyColumn = pk.getString("COLUMN_NAME");
            }

            createTableStatement
                    .append("CREATE TABLE ")
                    .append(tableName)
                    .append(" (\n");

            while (columns.next()) {
                String columnName = columns.getString("COLUMN_NAME");
                String columnType = columns.getString("TYPE_NAME");
                int size = columns.getInt("COLUMN_SIZE");
                String nullable = columns.getString("IS_NULLABLE").equals("YES") ? " NULL" : " NOT NULL";
                String columnDef = columns.getString("COLUMN_DEF") != null ? " DEFAULT " + columns.getString("COLUMN_DEF") : "";
                String comment = columns.getString("REMARKS");

                createTableStatement
                        .append("  ")
                        .append(columnName)
                        .append(" ")
                        .append(columnType)
                        .append("(")
                        .append(size)
                        .append(")")
                        .append(nullable)
                        .append(columnDef);

                if (columnName.equals(primaryKeyColumn)) {
                    createTableStatement.append(" PRIMARY KEY");
                }

                createTableStatement.append(",\n");

                if (comment != null && !comment.isEmpty()) {
                    createTableStatement
                            .append("  COMMENT ON COLUMN ")
                            .append(tableName)
                            .append(".")
                            .append(columnName)
                            .append(" IS '")
                            .append(comment)
                            .append("',\n");
                }
            }

            while (fks.next()) {
                String fkColumnName = fks.getString("FKCOLUMN_NAME");
                String pkTableName = fks.getString("PKTABLE_NAME");
                String pkColumnName = fks.getString("PKCOLUMN_NAME");
                createTableStatement
                        .append("  FOREIGN KEY (")
                        .append(fkColumnName)
                        .append(") REFERENCES ")
                        .append(pkTableName)
                        .append("(")
                        .append(pkColumnName)
                        .append("),\n");
            }

            if (createTableStatement.charAt(createTableStatement.length() - 2) == ',') {
                createTableStatement.delete(createTableStatement.length() - 2, createTableStatement.length());
            }

            createTableStatement.append(");\n");

            ResultSet tableRemarks = metaData.getTables(null, null, tableName, null);
            if (tableRemarks.next()) {
                String tableComment = tableRemarks.getString("REMARKS");
                if (tableComment != null && !tableComment.isEmpty()) {
                    createTableStatement
                            .append("COMMENT ON TABLE ")
                            .append(tableName)
                            .append(" IS '")
                            .append(tableComment)
                            .append("';\n");
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }

        return createTableStatement.toString();
    }

    @Override
    public List<Content> retrieve(Query naturalLanguageQuery) {

        String sqlQuery = null;
        String errorMessage = null;

        int attemptsLeft = maxRetries + 1;
        while (attemptsLeft > 0) {
            attemptsLeft--;

            sqlQuery = generateSqlQuery(naturalLanguageQuery, sqlQuery, errorMessage);

            sqlQuery = clean(sqlQuery);

            if (!isSelect(sqlQuery)) {
                return emptyList();
            }

            try {
                validate(sqlQuery);

                try (Connection connection = dataSource.getConnection();
                     Statement statement = connection.createStatement()) {

                    String result = execute(sqlQuery, statement);
                    Content content = format(result, sqlQuery);
                    return singletonList(content);
                }
            } catch (Exception e) {
                errorMessage = e.getMessage();
            }
        }

        return emptyList();
    }

    protected String generateSqlQuery(Query naturalLanguageQuery, String previousSqlQuery, String previousErrorMessage) {

        List<ChatMessage> messages = new ArrayList<>();
        messages.add(createSystemPrompt().toSystemMessage());
        messages.add(UserMessage.from(naturalLanguageQuery.text()));

        if (previousSqlQuery != null && previousErrorMessage != null) {
            messages.add(AiMessage.from(previousSqlQuery));
            messages.add(UserMessage.from(previousErrorMessage));
        }

        return chatLanguageModel.generate(messages).content().text();
    }

    protected Prompt createSystemPrompt() {
        Map<String, Object> variables = new HashMap<>();
        variables.put("sqlDialect", sqlDialect);
        variables.put("databaseStructure", databaseStructure);
        return promptTemplate.apply(variables);
    }

    protected String clean(String sqlQuery) {
        if (sqlQuery.contains("```sql")) {
            return sqlQuery.substring(sqlQuery.indexOf("```sql") + 6, sqlQuery.lastIndexOf("```"));
        } else if (sqlQuery.contains("```")) {
            return sqlQuery.substring(sqlQuery.indexOf("```") + 3, sqlQuery.lastIndexOf("```"));
        }
        return sqlQuery;
    }

    protected void validate(String sqlQuery) {

    }

    protected boolean isSelect(String sqlQuery) {
        try {
            net.sf.jsqlparser.statement.Statement statement = CCJSqlParserUtil.parse(sqlQuery);
            return statement instanceof Select;
        } catch (JSQLParserException e) {
            return false;
        }
    }

    protected String execute(String sqlQuery, Statement statement) throws SQLException {
        List<String> resultRows = new ArrayList<>();

        try (ResultSet resultSet = statement.executeQuery(sqlQuery)) {
            int columnCount = resultSet.getMetaData().getColumnCount();

            // header
            List<String> columnNames = new ArrayList<>();
            for (int i = 1; i <= columnCount; i++) {
                columnNames.add(resultSet.getMetaData().getColumnName(i));
            }
            resultRows.add(String.join(",", columnNames));

            // rows
            while (resultSet.next()) {
                List<String> columnValues = new ArrayList<>();
                for (int i = 1; i <= columnCount; i++) {

                    String columnValue = resultSet.getObject(i)==null?"":resultSet.getObject(i).toString();

                    if (columnValue.contains(",")) {
                        columnValue = "\"" + columnValue + "\"";
                    }
                    columnValues.add(columnValue);
                }
                resultRows.add(String.join(",", columnValues));
            }
        }

        return String.join("\n", resultRows);
    }

    private static Content format(String result, String sqlQuery) {
        return Content.from(String.format("Result of executing '%s':\n%s", sqlQuery, result));
    }
}
