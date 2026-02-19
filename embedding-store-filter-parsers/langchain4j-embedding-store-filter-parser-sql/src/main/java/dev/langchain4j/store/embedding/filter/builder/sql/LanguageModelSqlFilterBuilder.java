package dev.langchain4j.store.embedding.filter.builder.sql;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.Utils.isNullOrBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.Experimental;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.input.Prompt;
import dev.langchain4j.model.input.PromptTemplate;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.filter.parser.sql.SqlFilterParser;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Given a natural language {@link Query}, this class creates a suitable {@link Filter} using a language model.
 * <br>
 * This approach is also known as
 * <a href="https://python.langchain.com/docs/how_to/self_query/">self-querying</a>.
 * <br>
 * It is useful for improving retrieval from an {@link EmbeddingStore} by narrowing down the search space.
 * <br>
 * For instance, if you have internal company documentation for multiple products in the same {@link EmbeddingStore}
 * and want to search the documentation of a specific product without forcing the user to specify the
 * {@link Filter} manually, you could use {@code LanguageModelSqlFilterBuilder} to automatically create the filter
 * using a language model.
 * <br>
 * <br>
 * First, describe the {@link Metadata} of your {@link TextSegment}
 * as if it were an SQL table using {@link TableDefinition}:
 * <pre>
 * TableDefinition tableDefinition = TableDefinition.builder()
 *     .name("documentation") // table name
 *     .addColumn("product", "VARCHAR", "one of [iPhone, iPad, MacBook]") // column name, column type, comment
 *     ... other relevant metadata keys (columns) ...
 *     .build();
 * </pre>
 * Then, create a {@code LanguageModelSqlFilterBuilder} by providing a language model and a {@link TableDefinition},
 * and use it with {@link EmbeddingStoreContentRetriever}:
 * <pre>
 * LanguageModelSqlFilterBuilder sqlFilterBuilder = new LanguageModelSqlFilterBuilder(model, tableDefinition);
 * ContentRetriever contentRetriever = EmbeddingStoreContentRetriever.builder()
 *                 .embeddingStore(embeddingStore)
 *                 .embeddingModel(embeddingModel)
 *                 .dynamicFilter(sqlFilterBuilder::build)
 *                 .build();
 * </pre>
 * When the user asks, for example, "How to make the screen of my phone brighter?", the language model will generate
 * an SQL query like {@code SELECT * from documentation WHERE product = 'iPhone'}.
 * <br>
 * Then, {@link SqlFilterParser} will parse the generated SQL into the following {@link Filter} object:
 * {@code metadataKey("product").isEqualTo("iPhone")}.
 * <br>
 * This filter will be applied during similarity search in the {@link EmbeddingStore}.
 * This means that only those {@link TextSegment}s with a {@link Metadata} entry {@code product = "iPhone"}
 * will be considered for the search.
 * <br>
 * <br>
 * It is recommended to use a capable language model, such as gpt-3.5-turbo,
 * or the smaller one but fine-tuned for the text-to-SQL task, such as <a href="https://huggingface.co/defog">SQLCoder</a>.
 * SQLCoder is also available via <a href="https://ollama.com/library/sqlcoder">Ollama</a>.
 * <br>
 * The default {@link PromptTemplate} in this class is suited for SQLCoder, but should work fine with
 * capable language models like gpt-3.5-turbo and better.
 * <br>
 * You can override the default {@link PromptTemplate} using builder.
 * <br>
 * <br>
 * In case SQL parsing fails (e.g., the generated SQL is invalid or contains text in addition to the SQL statement),
 * {@code LanguageModelSqlFilterBuilder} will first try to extract the valid SQL from the input string.
 * If parsing fails again, it will return {@code null}, meaning no filtering will be applied during the search.
 */
@Experimental
public class LanguageModelSqlFilterBuilder {

    private static final Logger log = LoggerFactory.getLogger(LanguageModelSqlFilterBuilder.class);

    private static final PromptTemplate DEFAULT_PROMPT_TEMPLATE = PromptTemplate.from("### Instructions:\n"
            + "Your task is to convert a question into a SQL query, given a Postgres database schema.\n"
            + "Adhere to these rules:\n"
            + "- **Deliberately go through the question and database schema word by word** to appropriately answer the question\n"
            + "- **Use Table Aliases** to prevent ambiguity. For example, `SELECT table1.col1, table2.col1 FROM table1 JOIN table2 ON table1.id = table2.id`.\n"
            + "- When creating a ratio, always cast the numerator as float\n"
            + "\n"
            + "### Input:\n"
            + "Generate a SQL query that answers the question `{{query}}`.\n"
            + "This query will run on a database whose schema is represented in this string:\n"
            + "{{create_table_statement}}\n"
            + "\n"
            + "### Response:\n"
            + "Based on your instructions, here is the SQL query I have generated to answer the question `{{query}}`:\n"
            + "```sql");

    private static final PromptTemplate SIMPLE_PROMPT_TEMPLATE = PromptTemplate.from("### Instructions:\n"
            + "Generate a simple SQL SELECT query with a WHERE clause for the question below.\n"
            + "IMPORTANT: Only use simple comparisons (=, !=, <, >, <=, >=, IN, NOT IN, BETWEEN).\n"
            + "Do NOT use JOINs, subqueries, or complex expressions.\n"
            + "Return ONLY the SQL query, nothing else.\n"
            + "\n"
            + "### Schema:\n"
            + "{{create_table_statement}}\n"
            + "\n"
            + "### Question:\n"
            + "{{query}}\n"
            + "\n"
            + "### SQL:\n"
            + "```sql");

    private static final PromptTemplate RETRY_PROMPT_TEMPLATE = PromptTemplate.from("### Instructions:\n"
            + "The following SQL query failed to parse. Please fix it.\n"
            + "\n"
            + "### Original Query:\n"
            + "{{original_sql}}\n"
            + "\n"
            + "### Error:\n"
            + "{{error_message}}\n"
            + "\n"
            + "### Schema:\n"
            + "{{create_table_statement}}\n"
            + "\n"
            + "### Fixed SQL:\n"
            + "```sql");

    protected final ChatModel chatModel;
    protected final TableDefinition tableDefinition;
    protected final String createTableStatement;
    protected final PromptTemplate promptTemplate;
    protected final SqlFilterParser sqlFilterParser;
    protected final RetryStrategy retryStrategy;
    protected final int maxRetries;

    public LanguageModelSqlFilterBuilder(ChatModel chatModel, TableDefinition tableDefinition) {
        this(chatModel, tableDefinition, DEFAULT_PROMPT_TEMPLATE, new SqlFilterParser(), RetryStrategy.NONE, 1);
    }

    private LanguageModelSqlFilterBuilder(
            ChatModel chatModel,
            TableDefinition tableDefinition,
            PromptTemplate promptTemplate,
            SqlFilterParser sqlFilterParser,
            RetryStrategy retryStrategy,
            int maxRetries) {
        this.chatModel = ensureNotNull(chatModel, "chatModel");
        this.tableDefinition = ensureNotNull(tableDefinition, "tableDefinition");
        this.createTableStatement = format(tableDefinition);
        this.promptTemplate = getOrDefault(promptTemplate, DEFAULT_PROMPT_TEMPLATE);
        this.sqlFilterParser = getOrDefault(sqlFilterParser, SqlFilterParser::new);
        this.retryStrategy = getOrDefault(retryStrategy, RetryStrategy.NONE);
        this.maxRetries = maxRetries > 0 ? maxRetries : 1;
    }

    public static LanguageModelSqlFilterBuilderBuilder builder() {
        return new LanguageModelSqlFilterBuilderBuilder();
    }

    public Filter build(Query query) {

        Prompt prompt = createPrompt(query);

        ChatResponse response = chatModel.chat(prompt.toUserMessage());

        String generatedSql = response.aiMessage().text();

        String cleanedSql = clean(generatedSql);
        log.trace("Cleaned SQL: '{}'", cleanedSql);

        try {
            return sqlFilterParser.parse(cleanedSql);
        } catch (Exception e) {
            log.warn("Failed parsing the following SQL: '{}'", cleanedSql, e);
            return handleParsingFailure(query, generatedSql, cleanedSql, e, 0);
        }
    }

    /**
     * Handles parsing failure with configurable retry strategies.
     */
    protected Filter handleParsingFailure(
            Query query, String generatedSql, String cleanedSql, Exception e, int attempt) {
        // First, try to extract SQL from the response
        String extractedSql = extractSelectStatement(generatedSql);
        if (!isNullOrBlank(extractedSql) && !extractedSql.equals(cleanedSql)) {
            try {
                log.trace("Extracted SQL: '{}'", extractedSql);
                return sqlFilterParser.parse(extractedSql);
            } catch (Exception e2) {
                log.warn("Failed parsing extracted SQL: '{}'", extractedSql, e2);
            }
        }

        // If extraction failed and we have retries left, apply retry strategy
        if (attempt < maxRetries && retryStrategy != RetryStrategy.NONE) {
            return applyRetryStrategy(query, generatedSql, e, attempt);
        }

        // All attempts exhausted
        log.trace("All retry attempts exhausted, giving up");
        return null;
    }

    /**
     * Applies the configured retry strategy.
     */
    protected Filter applyRetryStrategy(Query query, String generatedSql, Exception e, int attempt) {
        switch (retryStrategy) {
            case RETRY_WITH_ERROR_FEEDBACK:
                return retryWithErrorFeedback(query, generatedSql, e, attempt);
            case RETRY_SIMPLIFIED:
                return retrySimplified(query, generatedSql, attempt);
            case RETRY_WITH_SIMPLE_PROMPT:
                return retryWithSimplePrompt(query, attempt);
            default:
                return null;
        }
    }

    /**
     * Retries by sending the error back to the LLM.
     */
    protected Filter retryWithErrorFeedback(Query query, String generatedSql, Exception e, int attempt) {
        log.debug("Retrying with error feedback (attempt {})", attempt + 1);
        Map<String, Object> variables = new HashMap<>();
        variables.put("original_sql", generatedSql);
        variables.put("error_message", e.getMessage());
        variables.put("create_table_statement", createTableStatement);

        Prompt retryPrompt = RETRY_PROMPT_TEMPLATE.apply(variables);
        ChatResponse response = chatModel.chat(retryPrompt.toUserMessage());
        String newSql = clean(response.aiMessage().text());

        try {
            return sqlFilterParser.parse(newSql);
        } catch (Exception e2) {
            log.warn("Retry with error feedback failed: '{}'", newSql, e2);
            return handleParsingFailure(query, newSql, newSql, e2, attempt + 1);
        }
    }

    /**
     * Retries with a simplified prompt.
     */
    protected Filter retryWithSimplePrompt(Query query, int attempt) {
        log.debug("Retrying with simple prompt (attempt {})", attempt + 1);
        Map<String, Object> variables = new HashMap<>();
        variables.put("create_table_statement", createTableStatement);
        variables.put("query", query.text());

        Prompt simplePrompt = SIMPLE_PROMPT_TEMPLATE.apply(variables);
        ChatResponse response = chatModel.chat(simplePrompt.toUserMessage());
        String newSql = clean(response.aiMessage().text());

        try {
            return sqlFilterParser.parse(newSql);
        } catch (Exception e2) {
            log.warn("Retry with simple prompt failed: '{}'", newSql, e2);
            return handleParsingFailure(query, newSql, newSql, e2, attempt + 1);
        }
    }

    /**
     * Retries by simplifying the generated SQL.
     */
    protected Filter retrySimplified(Query query, String generatedSql, int attempt) {
        log.debug("Retrying with simplified SQL (attempt {})", attempt + 1);
        String simplifiedSql = simplifySql(generatedSql);
        if (isNullOrBlank(simplifiedSql)) {
            return null;
        }

        try {
            return sqlFilterParser.parse(simplifiedSql);
        } catch (Exception e2) {
            log.warn("Retry with simplified SQL failed: '{}'", simplifiedSql, e2);
            return handleParsingFailure(query, simplifiedSql, simplifiedSql, e2, attempt + 1);
        }
    }

    /**
     * Attempts to simplify SQL by removing complex parts like JOINs and subqueries.
     */
    protected String simplifySql(String sql) {
        if (isNullOrBlank(sql)) {
            return null;
        }

        // Remove JOINs
        String simplified = sql.replaceAll(
                "(?i)\\s+(INNER|LEFT|RIGHT|FULL|CROSS)?\\s*JOIN\\s+.+?\\s+ON\\s+.+?(?=\\s+WHERE|\\s+GROUP|\\s+ORDER|\\s*$)",
                " ");

        // Remove ORDER BY and GROUP BY clauses
        simplified = simplified.replaceAll("(?i)\\s+ORDER\\s+BY\\s+.+?(?=\\s+LIMIT|\\s+OFFSET|;|$)", "");
        simplified = simplified.replaceAll("(?i)\\s+GROUP\\s+BY\\s+.+?(?=\\s+HAVING|\\s+ORDER|\\s+LIMIT|;|$)", "");
        simplified = simplified.replaceAll("(?i)\\s+HAVING\\s+.+?(?=\\s+ORDER|\\s+LIMIT|;|$)", "");
        simplified = simplified.replaceAll("(?i)\\s+LIMIT\\s+\\d+", "");
        simplified = simplified.replaceAll("(?i)\\s+OFFSET\\s+\\d+", "");

        return simplified.trim();
    }

    protected Prompt createPrompt(Query query) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("create_table_statement", createTableStatement);
        variables.put("query", query.text());
        return promptTemplate.apply(variables);
    }

    protected String clean(String sql) {
        return sql.trim();
    }

    /**
     * Extracts a valid SQL SELECT statement from potentially noisy LLM output.
     * <br>
     * Uses multiple strategies to find valid SQL:
     * <ol>
     *     <li>Look for SQL in code blocks (```sql ... ```)</li>
     *     <li>Split by SELECT keyword and find WHERE clause</li>
     * </ol>
     *
     * @param dirtySql the potentially noisy SQL string from LLM
     * @return extracted SQL statement, or null if extraction fails
     */
    protected String extractSelectStatement(String dirtySql) {
        if (isNullOrBlank(dirtySql)) {
            return null;
        }

        // Strategy 1: Handle code blocks
        if (dirtySql.contains("```sql")) {
            for (String part : dirtySql.split("```sql")) {
                if (part.toUpperCase().contains("SELECT") && part.toUpperCase().contains("WHERE")) {
                    return cleanExtractedSql(part.split("```")[0].trim());
                }
            }
        } else if (dirtySql.contains("```")) {
            for (String part : dirtySql.split("```")) {
                if (part.toUpperCase().contains("SELECT") && part.toUpperCase().contains("WHERE")) {
                    return cleanExtractedSql(part.split("```")[0].trim());
                }
            }
        } else {
            // Strategy 2: Split by SELECT and find the part with WHERE
            for (String part : dirtySql.split("SELECT")) {
                if (part.toUpperCase().contains("WHERE")) {
                    if (part.contains("\n")) {
                        for (String part2 : part.split("\n")) {
                            if (part2.toUpperCase().contains("WHERE")) {
                                return cleanExtractedSql("SELECT " + part2.trim());
                            }
                        }
                    } else {
                        return cleanExtractedSql("SELECT " + part.trim());
                    }
                }
            }
        }

        log.trace("Could not extract SQL from: '{}'", dirtySql);
        return null;
    }

    /**
     * Cleans extracted SQL by removing trailing semicolons and extra whitespace.
     */
    private String cleanExtractedSql(String sql) {
        if (isNullOrBlank(sql)) {
            return null;
        }
        return sql.replaceAll(";\\s*$", "").trim();
    }

    protected String format(TableDefinition tableDefinition) {
        StringBuilder createTableStatement = new StringBuilder();
        createTableStatement.append(String.format("CREATE TABLE %s (\n", tableDefinition.name()));
        for (ColumnDefinition columnDefinition : tableDefinition.columns()) {
            createTableStatement.append(String.format("    %s %s,", columnDefinition.name(), columnDefinition.type()));
            if (!isNullOrBlank(columnDefinition.description())) {
                createTableStatement.append(String.format(" -- %s", columnDefinition.description()));
            }
            createTableStatement.append("\n");
        }
        createTableStatement.append(")");
        if (!isNullOrBlank(tableDefinition.description())) {
            createTableStatement.append(String.format(" COMMENT='%s'", tableDefinition.description()));
        }
        createTableStatement.append(";");
        return createTableStatement.toString();
    }

    public static class LanguageModelSqlFilterBuilderBuilder {
        private ChatModel chatModel;
        private TableDefinition tableDefinition;
        private PromptTemplate promptTemplate;
        private SqlFilterParser sqlFilterParser;
        private RetryStrategy retryStrategy;
        private int maxRetries = 1;

        LanguageModelSqlFilterBuilderBuilder() {}

        public LanguageModelSqlFilterBuilderBuilder chatModel(ChatModel chatModel) {
            this.chatModel = chatModel;
            return this;
        }

        public LanguageModelSqlFilterBuilderBuilder tableDefinition(TableDefinition tableDefinition) {
            this.tableDefinition = tableDefinition;
            return this;
        }

        public LanguageModelSqlFilterBuilderBuilder promptTemplate(PromptTemplate promptTemplate) {
            this.promptTemplate = promptTemplate;
            return this;
        }

        public LanguageModelSqlFilterBuilderBuilder sqlFilterParser(SqlFilterParser sqlFilterParser) {
            this.sqlFilterParser = sqlFilterParser;
            return this;
        }

        /**
         * Sets the retry strategy to use when SQL parsing fails.
         *
         * @param retryStrategy the retry strategy
         * @return this builder
         */
        public LanguageModelSqlFilterBuilderBuilder retryStrategy(RetryStrategy retryStrategy) {
            this.retryStrategy = retryStrategy;
            return this;
        }

        /**
         * Sets the maximum number of retry attempts when using a retry strategy.
         *
         * @param maxRetries the maximum number of retries (default is 1)
         * @return this builder
         */
        public LanguageModelSqlFilterBuilderBuilder maxRetries(int maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        public LanguageModelSqlFilterBuilder build() {
            return new LanguageModelSqlFilterBuilder(
                    this.chatModel,
                    this.tableDefinition,
                    this.promptTemplate,
                    this.sqlFilterParser,
                    this.retryStrategy,
                    this.maxRetries);
        }

        public String toString() {
            return "LanguageModelSqlFilterBuilder.LanguageModelSqlFilterBuilderBuilder(chatModel=" + this.chatModel
                    + ", tableDefinition=" + this.tableDefinition + ", promptTemplate=" + this.promptTemplate
                    + ", sqlFilterParser=" + this.sqlFilterParser + ", retryStrategy=" + this.retryStrategy
                    + ", maxRetries=" + this.maxRetries + ")";
        }
    }
}
