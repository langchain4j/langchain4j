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
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

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
 * Optionally, {@link #sqlDialect}, {@link #databaseStructure}, {@link #promptTemplate}, {@link #maxRetries} and {@link #maxTableRows}  can be specified
 * to customize the behavior. See the javadoc of the constructor for more details.
 * Most methods can be overridden to customize the behavior further.
 * <br>
 * The default prompt template is not highly optimized,
 * so it is advised to experiment with it and see what works best for your use case.
 */
@Experimental
public class SqlDatabaseContentRetriever implements ContentRetriever {
    private static final String ERROR_RESULT_PREFIX = "Error of executing";
    private static final String RESULT_PREFIX = "Result of executing";
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
    private final int maxTableRows;
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
                                       Integer maxRetries,
                                       Integer maxTableRows) {
        this.dataSource = ensureNotNull(dataSource, "dataSource");
        this.sqlDialect = getOrDefault(sqlDialect, () -> getSqlDialect(dataSource));
        this.databaseStructure = getOrDefault(databaseStructure, () -> generateDDL(dataSource));
        this.promptTemplate = getOrDefault(promptTemplate, DEFAULT_PROMPT_TEMPLATE);
        this.chatLanguageModel = ensureNotNull(chatLanguageModel, "chatLanguageModel");
        this.maxRetries = getOrDefault(maxRetries, 1);
        this.maxTableRows = getOrDefault(maxTableRows, 50);
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
        List<Content> contents = new ArrayList<>();

        while (attemptsLeft > 0) {
            attemptsLeft--;

            sqlQuery = generateSqlQuery(naturalLanguageQuery, sqlQuery, errorMessage);
            sqlQuery = clean(sqlQuery);

            List<String> sqlList = getSplitSql(sqlQuery);
            contents = getContents(sqlList);
            if (!isExistError(contents)) {
                break;
            }
            errorMessage = getSqlErrors(contents);

        }
        return contents;
    }

    protected String getSqlErrors(List<Content> contents) {
        List<String> errors = new ArrayList<>();
        for (Content content : contents) {
            String text = content.textSegment().text();
            String sqlStr;
            if (getMessageType(text) == MessageType.ERROR) {
                sqlStr = text.replace(ERROR_RESULT_PREFIX, "").trim();
            } else if (getMessageType(text) == MessageType.RESULT) {
                sqlStr = text.replace(RESULT_PREFIX, "").trim();
            } else {
                continue;
            }
            String[] sqlError = sqlStr.split(":");
            if (sqlError.length == 2) {
                errors.add(sqlError[1].trim());
            } else {
                errors.add(sqlStr);
            }
        }
        return String.join(";", errors);
    }

    protected Boolean isExistError(List<Content> contents) {
        for (Content content : contents) {
            String text = content.textSegment().text();
            if (getMessageType(text) == MessageType.ERROR) {
                return true;
            }
        }
        return false;
    }

    protected List<Content> getContents(List<String> sqlQueries) {
        ArrayList<Content> contents = new ArrayList<>();
        for (int i = 0; i < sqlQueries.size(); i++) {
            String sqlQuery = sqlQueries.get(i);
            if (!isSelect(sqlQuery.trim())) {
                continue;
            }
            SqlResult result;
            Content content;
            try {
                try (Connection connection = this.dataSource.getConnection(); Statement statement = connection.createStatement()) {
                    result = execute(sqlQuery, statement);
                    content = format(result, sqlQuery, false, i);
                }
            } catch (Exception e) {
                result = new SqlResult(e.getMessage(), 0);
                content = format(result, sqlQuery, true, i);
            }
            contents.add(content);
        }

        return contents;
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

    protected List<String> getSplitSql(String sqlQuery) {
        ArrayList<String> sqlList = new ArrayList<>();
        if (sqlQuery != null && sqlQuery.contains(";")) {
            String[] splitSqlArray = sqlQuery.split(";");
            sqlList.addAll(Arrays.asList(splitSqlArray));
        }
        return sqlList;
    }

    protected boolean isSelect(String sqlQuery) {
        try {
            net.sf.jsqlparser.statement.Statement statement = CCJSqlParserUtil.parse(sqlQuery);
            return statement instanceof Select;
        } catch (JSQLParserException e) {
            return false;
        }
    }

    protected SqlResult execute(String sqlQuery, Statement statement) throws SQLException {
        StringBuilder markdownBuilder = new StringBuilder();
        int rowCount = 0;
        try (ResultSet resultSet = statement.executeQuery(sqlQuery)) {
            int columnCount = resultSet.getMetaData().getColumnCount();

            // Header
            List<String> columnNames = new ArrayList<>();
            for (int i = 1; i <= columnCount; i++) {
                columnNames.add(resultSet.getMetaData().getColumnName(i));
            }
            markdownBuilder.append("| ")
                    .append(String.join(" | ", columnNames))
                    .append(" |\n");

            // Separator (custom code for repeat behavior)
            markdownBuilder.append("| --- ".repeat(Math.max(0, columnCount)));
            markdownBuilder.append("|\n");

            // Rows
            while (resultSet.next()) {
                if (rowCount >= this.maxTableRows) {
                    markdownBuilder.insert(0, "The retrieved table is too large, " +
                            "displaying the first %s rows by default:\n".formatted(this.maxTableRows));
                    break;
                }
                List<String> columnValues = new ArrayList<>();
                for (int i = 1; i <= columnCount; i++) {
                    String columnValue = resultSet.getObject(i) == null ? "" : resultSet.getObject(i).toString();
                    columnValues.add(columnValue.replace("|", "\\|")); // Escape pipes
                }
                markdownBuilder.append("| ")
                        .append(String.join(" | ", columnValues))
                        .append(" |\n");
                rowCount++;
            }
        }
        return new SqlResult(markdownBuilder.toString(), rowCount);
    }

    protected Content format(SqlResult result, String sqlQuery, Boolean isError, Integer order) {
        Content content;
        String executeResult = result.getResult();
        if (!isError) {
            content = Content.from(String.format(RESULT_PREFIX + " '%s':\n%s", sqlQuery, executeResult));
        } else {
            content = Content.from(String.format(ERROR_RESULT_PREFIX + " %s:%s", sqlQuery, executeResult));
        }
        int rowCount = result.getRowCount();
        content.textSegment().metadata().put("rowCount", rowCount);
        content.textSegment().metadata().put("sql", sqlQuery);
        content.textSegment().metadata().put("order", order);
        return content;
    }

    private MessageType getMessageType(String text) {
        if (text.startsWith("Error")) {
            return MessageType.ERROR;
        } else if (text.startsWith("Result")) {
            return MessageType.RESULT;
        } else {
            return MessageType.UNKNOWN;
        }
    }

    protected static class SqlResult {
        private final String result;
        private final int rowCount;

        public SqlResult(String result, int rowCount) {
            this.result = result;
            this.rowCount = rowCount;
        }

        public String getResult() {
            return result;
        }

        public int getRowCount() {
            return rowCount;
        }
    }
    
    protected enum MessageType {
        ERROR,
        RESULT,
        UNKNOWN
    }
}
