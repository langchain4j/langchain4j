package dev.langchain4j.store.embedding.filter.builder.sql;

import dev.langchain4j.Experimental;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.input.Prompt;
import dev.langchain4j.model.input.PromptTemplate;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.filter.parser.sql.SqlFilterParser;
import lombok.Builder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.Utils.isNullOrBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

/**
 * Given a natural language {@link Query}, this class creates a suitable {@link Filter} using a language model.
 * <br>
 * This approach is also known as
 * <a href="https://python.langchain.com/docs/modules/data_connection/retrievers/self_query">self-querying</a>.
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

    private static final PromptTemplate DEFAULT_PROMPT_TEMPLATE = PromptTemplate.from(
            "### Instructions:\n" +
                    "Your task is to convert a question into a SQL query, given a Postgres database schema.\n" +
                    "Adhere to these rules:\n" +
                    "- **Deliberately go through the question and database schema word by word** to appropriately answer the question\n" +
                    "- **Use Table Aliases** to prevent ambiguity. For example, `SELECT table1.col1, table2.col1 FROM table1 JOIN table2 ON table1.id = table2.id`.\n" +
                    "- When creating a ratio, always cast the numerator as float\n" +
                    "\n" +
                    "### Input:\n" +
                    "Generate a SQL query that answers the question `{{query}}`.\n" +
                    "This query will run on a database whose schema is represented in this string:\n" +
                    "{{create_table_statement}}\n" +
                    "\n" +
                    "### Response:\n" +
                    "Based on your instructions, here is the SQL query I have generated to answer the question `{{query}}`:\n" +
                    "```sql"
    );

    protected final ChatLanguageModel chatLanguageModel;
    protected final TableDefinition tableDefinition;
    protected final String createTableStatement;
    protected final PromptTemplate promptTemplate;
    protected final SqlFilterParser sqlFilterParser;

    public LanguageModelSqlFilterBuilder(ChatLanguageModel chatLanguageModel,
                                         TableDefinition tableDefinition) {
        this(chatLanguageModel, tableDefinition, DEFAULT_PROMPT_TEMPLATE, new SqlFilterParser());
    }

    @Builder
    private LanguageModelSqlFilterBuilder(ChatLanguageModel chatLanguageModel,
                                          TableDefinition tableDefinition,
                                          PromptTemplate promptTemplate,
                                          SqlFilterParser sqlFilterParser) {
        this.chatLanguageModel = ensureNotNull(chatLanguageModel, "chatLanguageModel");
        this.tableDefinition = ensureNotNull(tableDefinition, "tableDefinition");
        this.createTableStatement = format(tableDefinition);
        this.promptTemplate = getOrDefault(promptTemplate, DEFAULT_PROMPT_TEMPLATE);
        this.sqlFilterParser = getOrDefault(sqlFilterParser, SqlFilterParser::new);
    }

    public Filter build(Query query) {

        Prompt prompt = createPrompt(query);

        Response<AiMessage> response = chatLanguageModel.generate(prompt.toUserMessage());

        String generatedSql = response.content().text();

        String cleanedSql = clean(generatedSql);
        log.trace("Cleaned SQL: '{}'", cleanedSql);

        try {
            return sqlFilterParser.parse(cleanedSql);
        } catch (Exception e) {
            log.warn("Failed parsing the following SQL: '{}'", cleanedSql, e);
            // TODO implement additional strategies (configurable):
            //  - feed the error to the LLM and retry
            //  - return predefined filter
            //  - return partial filter if the filter is composite and some parts were parsed successfully
            //  - etc
            return fallback(query, generatedSql, cleanedSql, e);
        }
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

    protected Filter fallback(Query query, String generatedSql, String cleanedSql, Exception e) {

        String extractedSql = extractSelectStatement(generatedSql);
        if (isNullOrBlank(extractedSql)) {
            log.trace("Cannot extract SQL, giving up");
            return null;
        }

        try {
            log.trace("Extracted SQL: '{}'", extractedSql);
            return sqlFilterParser.parse(extractedSql);
        } catch (Exception e2) {
            log.warn("Failed parsing the following SQL, giving up: '{}'", extractedSql, e2);
            return null;
        }
    }

    protected String extractSelectStatement(String dirtySql) {
        // TODO improve
        if (dirtySql.contains("```sql")) {
            for (String part : dirtySql.split("```sql")) {
                if (part.toUpperCase().contains("SELECT") && part.toUpperCase().contains("WHERE")) {
                    return part.split("```")[0].trim();
                }
            }
        } else if (dirtySql.contains("```")) {
            for (String part : dirtySql.split("```")) {
                if (part.toUpperCase().contains("SELECT") && part.toUpperCase().contains("WHERE")) {
                    return part.split("```")[0].trim();
                }
            }
        } else {
            for (String part : dirtySql.split("SELECT")) {
                if (part.toUpperCase().contains("WHERE")) {
                    if (part.contains("\n")) {
                        for (String part2 : part.split("\n")) {
                            if (part2.toUpperCase().contains("WHERE")) {
                                return "SELECT " + part2.trim();
                            }
                        }
                    } else {
                        return "SELECT " + part.trim();
                    }
                }
            }
        }
        return null;
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
}
