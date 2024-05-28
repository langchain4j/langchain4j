package dev.langchain4j.experimental.rag.content.retriever.sql;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.mistralai.MistralAiChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import javax.sql.DataSource;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.*;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

@Testcontainers
class SqlDatabaseContentRetrieverIT {

    static ChatLanguageModel openAiChatModel = OpenAiChatModel.builder()
            .baseUrl(System.getenv("OPENAI_BASE_URL"))
            .apiKey(System.getenv("OPENAI_API_KEY"))
            .organizationId(System.getenv("OPENAI_ORGANIZATION_ID"))
            .logRequests(true)
            .logResponses(true)
            .build();

    static ChatLanguageModel mistralAiChatModel = MistralAiChatModel.builder()
            .apiKey(System.getenv("MISTRAL_AI_API_KEY"))
            .logRequests(true)
            .logResponses(true)
            .build();

    @Container
    PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:12.18"));

    DataSource dataSource;

    @BeforeEach
    void beforeEach() {
        dataSource = createDataSource();

        String createTablesScript = read("sql/create_tables.sql");
        execute(createTablesScript, dataSource);

        String prefillTablesScript = read("sql/prefill_tables.sql");
        execute(prefillTablesScript, dataSource);
    }

    private PGSimpleDataSource createDataSource() {
        PGSimpleDataSource dataSource = new PGSimpleDataSource();
        dataSource.setUrl(postgres.getJdbcUrl());
        dataSource.setUser(postgres.getUsername());
        dataSource.setPassword(postgres.getPassword());
        return dataSource;
    }

    @AfterEach
    void afterEach() {
        execute("DROP TABLE orders;", dataSource);
        execute("DROP TABLE products;", dataSource);
        execute("DROP TABLE customers;", dataSource);
    }

    @ParameterizedTest
    @MethodSource("contentRetrieverProviders")
    void should_answer_query_1(Function<DataSource, ContentRetriever> contentRetrieverProvider) {

        // given
        ContentRetriever contentRetriever = contentRetrieverProvider.apply(dataSource);

        // when
        List<Content> retrieved = contentRetriever.retrieve(Query.from("How many customers do we have?"));

        // then
        assertThat(retrieved).hasSize(1);

        assertThat(retrieved.get(0).textSegment().text())
                .contains("SELECT")
                .contains("5");
    }

    @ParameterizedTest
    @MethodSource("contentRetrieverProviders")
    void should_answer_query_2(Function<DataSource, ContentRetriever> contentRetrieverProvider) {

        // given
        ContentRetriever contentRetriever = contentRetrieverProvider.apply(dataSource);

        // when
        List<Content> retrieved = contentRetriever.retrieve(Query.from("What is the total sales in dollars for each product?"));

        // then
        assertThat(retrieved).hasSize(1);

        assertThat(retrieved.get(0).textSegment().text())
                .contains("SELECT")
                .contains("99.98", "71.97", "64.95", "22.50", "23.97");
    }

    @ParameterizedTest
    @MethodSource("contentRetrieverProviders")
    void should_answer_query_3(Function<DataSource, ContentRetriever> contentRetrieverProvider) {

        // given
        ContentRetriever contentRetriever = contentRetrieverProvider.apply(dataSource);

        // when
        List<Content> retrieved = contentRetriever.retrieve(Query.from("Which quarters show the highest sales?"));

        // then
        assertThat(retrieved).hasSize(1);

        assertThat(retrieved.get(0).textSegment().text())
                .contains("SELECT")
                .containsAnyOf("2,283.37", "2.0,283.37");
    }

    @ParameterizedTest
    @MethodSource("contentRetrieverProviders")
    void should_answer_query_4(Function<DataSource, ContentRetriever> contentRetrieverProvider) {

        // given
        ContentRetriever contentRetriever = contentRetrieverProvider.apply(dataSource);

        // when
        List<Content> retrieved = contentRetriever.retrieve(Query.from("Who is our top customer by total spend?"));

        // then
        assertThat(retrieved).hasSize(1);

        assertThat(retrieved.get(0).textSegment().text())
                .contains("SELECT")
                .contains("Carol")
                .doesNotContain("John", "Jane", "Alice", "Bob");
    }

    @ParameterizedTest
    @MethodSource("contentRetrieverProviders")
    void should_not_fail_for_unrelated_query(Function<DataSource, ContentRetriever> contentRetrieverProvider) {

        // given
        ContentRetriever contentRetriever = contentRetrieverProvider.apply(dataSource);

        // when-then
        assertThatCode(() -> contentRetriever.retrieve(Query.from("hello")))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @MethodSource("contentRetrieverProviders")
    void should_not_drop_table(Function<DataSource, ContentRetriever> contentRetrieverProvider) {

        // given
        ContentRetriever contentRetriever = contentRetrieverProvider.apply(dataSource);

        long customersHash = getTableHash(dataSource, "customers");
        long productsHash = getTableHash(dataSource, "products");
        long ordersHash = getTableHash(dataSource, "orders");

        // when
        List<Content> retrieved = contentRetriever.retrieve(Query.from("Drop table with orders"));

        // then
        assertThat(retrieved).isEmpty();

        assertThat(getTableHash(dataSource, "customers")).isEqualTo(customersHash);
        assertThat(getTableHash(dataSource, "products")).isEqualTo(productsHash);
        assertThat(getTableHash(dataSource, "orders")).isEqualTo(ordersHash);
    }

    @ParameterizedTest
    @MethodSource("contentRetrieverProviders")
    void should_not_delete_existing_rows(Function<DataSource, ContentRetriever> contentRetrieverProvider) {

        // given
        ContentRetriever contentRetriever = contentRetrieverProvider.apply(dataSource);

        long customersHash = getTableHash(dataSource, "customers");
        long productsHash = getTableHash(dataSource, "products");
        long ordersHash = getTableHash(dataSource, "orders");

        // when
        List<Content> retrieved = contentRetriever.retrieve(Query.from("Delete customer with ID=1"));

        // then
        assertThat(retrieved).isEmpty();

        assertThat(getTableHash(dataSource, "customers")).isEqualTo(customersHash);
        assertThat(getTableHash(dataSource, "products")).isEqualTo(productsHash);
        assertThat(getTableHash(dataSource, "orders")).isEqualTo(ordersHash);
    }

    @ParameterizedTest
    @MethodSource("contentRetrieverProviders")
    void should_not_insert_new_rows(Function<DataSource, ContentRetriever> contentRetrieverProvider) {

        // given
        ContentRetriever contentRetriever = contentRetrieverProvider.apply(dataSource);

        long customersHash = getTableHash(dataSource, "customers");
        long productsHash = getTableHash(dataSource, "products");
        long ordersHash = getTableHash(dataSource, "orders");

        // when
        List<Content> retrieved = contentRetriever.retrieve(Query.from("Insert new customer James Bond with ID=7"));

        // then
        assertThat(retrieved).isEmpty();

        assertThat(getTableHash(dataSource, "customers")).isEqualTo(customersHash);
        assertThat(getTableHash(dataSource, "products")).isEqualTo(productsHash);
        assertThat(getTableHash(dataSource, "orders")).isEqualTo(ordersHash);
    }

    @ParameterizedTest
    @MethodSource("contentRetrieverProviders")
    void should_not_update_existing_rows(Function<DataSource, ContentRetriever> contentRetrieverProvider) {

        // given
        ContentRetriever contentRetriever = contentRetrieverProvider.apply(dataSource);

        long customersHash = getTableHash(dataSource, "customers");
        long productsHash = getTableHash(dataSource, "products");
        long ordersHash = getTableHash(dataSource, "orders");

        // when
        List<Content> retrieved = contentRetriever.retrieve(Query.from("Update email of customer with ID=1 to bad@guy.com"));

        // then
        assertThat(retrieved).isEmpty();

        assertThat(getTableHash(dataSource, "customers")).isEqualTo(customersHash);
        assertThat(getTableHash(dataSource, "products")).isEqualTo(productsHash);
        assertThat(getTableHash(dataSource, "orders")).isEqualTo(ordersHash);
    }

    private static void execute(String sql, DataSource dataSource) {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            for (String sqlStatement : sql.split(";")) {
                statement.execute(sqlStatement.trim());
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private static long getTableHash(DataSource dataSource, String tableName) {
        String query = "SELECT * FROM " + tableName;
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(query)) {
            StringBuilder dataBuilder = new StringBuilder();
            ResultSetMetaData metaData = rs.getMetaData();
            while (rs.next()) {
                for (int i = 1; i <= metaData.getColumnCount(); i++) {
                    dataBuilder.append(rs.getString(i));
                }
            }
            return dataBuilder.toString().hashCode();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    static Stream<Function<DataSource, ContentRetriever>> contentRetrieverProviders() {
        return Stream.of(

                // OpenAI
                dataSource -> SqlDatabaseContentRetriever.builder()
                        .dataSource(dataSource)
                        .sqlDialect("PostgreSQL")
                        .databaseStructure(read("sql/create_tables.sql"))
                        .chatLanguageModel(openAiChatModel)
                        .build(),
                dataSource -> SqlDatabaseContentRetriever.builder()
                        .dataSource(dataSource)
                        .chatLanguageModel(openAiChatModel)
                        .build(),

                // Mistral
                dataSource -> SqlDatabaseContentRetriever.builder()
                        .dataSource(dataSource)
                        .sqlDialect("PostgreSQL")
                        .databaseStructure(read("sql/create_tables.sql"))
                        .chatLanguageModel(mistralAiChatModel)
                        .build(),
                dataSource -> SqlDatabaseContentRetriever.builder()
                        .dataSource(dataSource)
                        .chatLanguageModel(mistralAiChatModel)
                        .build()
        );
    }

    private static String read(String path) {
        try {
            return new String(Files.readAllBytes(toPath(path)));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static Path toPath(String fileName) {
        try {
            return Paths.get(SqlDatabaseContentRetrieverIT.class.getClassLoader().getResource(fileName).toURI());
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }
}