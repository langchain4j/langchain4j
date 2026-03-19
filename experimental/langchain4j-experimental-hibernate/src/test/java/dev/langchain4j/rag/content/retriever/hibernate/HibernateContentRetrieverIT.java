package dev.langchain4j.rag.content.retriever.hibernate;

import static dev.langchain4j.model.mistralai.MistralAiChatModelName.MISTRAL_SMALL_LATEST;
import static dev.langchain4j.model.openai.OpenAiChatModelName.GPT_4_1_NANO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.mistralai.MistralAiChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.hibernate.cfg.SchemaToolingSettings;
import org.hibernate.tool.schema.Action;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
@EnabledIfEnvironmentVariable(named = "MISTRAL_AI_API_KEY", matches = ".+")
class HibernateContentRetrieverIT {

    static ChatModel openAiChatModel = OpenAiChatModel.builder()
            .baseUrl(System.getenv("OPENAI_BASE_URL"))
            .apiKey(System.getenv("OPENAI_API_KEY"))
            .organizationId(System.getenv("OPENAI_ORGANIZATION_ID"))
            .modelName(GPT_4_1_NANO)
            .temperature(0.0)
            .logRequests(true)
            .logResponses(true)
            .build();

    static ChatModel mistralAiChatModel = MistralAiChatModel.builder()
            .apiKey(System.getenv("MISTRAL_AI_API_KEY"))
            .modelName(MISTRAL_SMALL_LATEST)
            .temperature(0.0)
            .logRequests(true)
            .logResponses(true)
            .build();

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:latest");

    static SessionFactory sessionFactory;

    @BeforeAll
    static void setup() {
        sessionFactory = new Configuration()
                .addAnnotatedClass(CustomerEntity.class)
                .addAnnotatedClass(ProductEntity.class)
                .addAnnotatedClass(OrderEntity.class)
                .setJdbcUrl(postgres.getJdbcUrl())
                .setCredentials(postgres.getUsername(), postgres.getPassword())
                .setSchemaExportAction(Action.CREATE_DROP)
                .setProperty(SchemaToolingSettings.JAKARTA_HBM2DDL_LOAD_SCRIPT_SOURCE, "/import-content-retriever.sql")
                .buildSessionFactory();
    }

    @AfterAll
    static void tearDown() {
        if (sessionFactory != null) {
            sessionFactory.close();
        }
    }

    @ParameterizedTest
    @MethodSource("contentRetrieverProviders")
    void should_answer_query_1(Function<SessionFactory, ContentRetriever> contentRetrieverProvider) {

        // given
        ContentRetriever contentRetriever = contentRetrieverProvider.apply(sessionFactory);

        // when
        List<Content> retrieved = contentRetriever.retrieve(Query.from("How many customers do we have?"));

        // then
        assertThat(retrieved).hasSize(1);

        assertThat(retrieved.get(0).textSegment().text()).contains("5");
    }

    @ParameterizedTest
    @MethodSource("contentRetrieverProviders")
    void should_answer_query_2(Function<SessionFactory, ContentRetriever> contentRetrieverProvider) {

        // given
        ContentRetriever contentRetriever = contentRetrieverProvider.apply(sessionFactory);

        // when
        List<Content> retrieved = contentRetriever.retrieve(Query.from("Who is our top customer by total spend?"));

        // then
        assertThat(retrieved).hasSize(1);

        assertThat(retrieved.get(0).textSegment().text())
                .contains("Carol")
                .doesNotContain("John", "Jane", "Alice", "Bob");
    }

    @ParameterizedTest
    @MethodSource("contentRetrieverProviders")
    void should_not_fail_for_unrelated_query(Function<SessionFactory, ContentRetriever> contentRetrieverProvider) {

        // given
        ContentRetriever contentRetriever = contentRetrieverProvider.apply(sessionFactory);

        // when-then
        assertThatCode(() -> contentRetriever.retrieve(Query.from("hello"))).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @MethodSource("contentRetrieverProviders")
    void should_not_drop_table(Function<SessionFactory, ContentRetriever> contentRetrieverProvider) {

        // given
        ContentRetriever contentRetriever = contentRetrieverProvider.apply(sessionFactory);

        long customersHash = getTableHash("customers");
        long productsHash = getTableHash("products");
        long ordersHash = getTableHash("orders");

        // when
        contentRetriever.retrieve(Query.from("Drop table with orders"));

        // then - data must remain intact
        assertThat(getTableHash("customers")).isEqualTo(customersHash);
        assertThat(getTableHash("products")).isEqualTo(productsHash);
        assertThat(getTableHash("orders")).isEqualTo(ordersHash);
    }

    @ParameterizedTest
    @MethodSource("contentRetrieverProviders")
    void should_not_delete_existing_rows(Function<SessionFactory, ContentRetriever> contentRetrieverProvider) {

        // given
        ContentRetriever contentRetriever = contentRetrieverProvider.apply(sessionFactory);

        long customersHash = getTableHash("customers");
        long productsHash = getTableHash("products");
        long ordersHash = getTableHash("orders");

        // when
        contentRetriever.retrieve(Query.from("Delete customer with ID=1"));

        // then - data must remain intact
        assertThat(getTableHash("customers")).isEqualTo(customersHash);
        assertThat(getTableHash("products")).isEqualTo(productsHash);
        assertThat(getTableHash("orders")).isEqualTo(ordersHash);
    }

    @ParameterizedTest
    @MethodSource("contentRetrieverProviders")
    void should_not_insert_new_rows(Function<SessionFactory, ContentRetriever> contentRetrieverProvider) {

        // given
        ContentRetriever contentRetriever = contentRetrieverProvider.apply(sessionFactory);

        long customersHash = getTableHash("customers");
        long productsHash = getTableHash("products");
        long ordersHash = getTableHash("orders");

        // when
        contentRetriever.retrieve(Query.from("Insert new customer James Bond with ID=7"));

        // then - data must remain intact
        assertThat(getTableHash("customers")).isEqualTo(customersHash);
        assertThat(getTableHash("products")).isEqualTo(productsHash);
        assertThat(getTableHash("orders")).isEqualTo(ordersHash);
    }

    @ParameterizedTest
    @MethodSource("contentRetrieverProviders")
    void should_not_update_existing_rows(Function<SessionFactory, ContentRetriever> contentRetrieverProvider) {

        // given
        ContentRetriever contentRetriever = contentRetrieverProvider.apply(sessionFactory);

        long customersHash = getTableHash("customers");
        long productsHash = getTableHash("products");
        long ordersHash = getTableHash("orders");

        // when
        contentRetriever.retrieve(Query.from("Update email of customer with ID=1 to bad@guy.com"));

        // then - data must remain intact
        assertThat(getTableHash("customers")).isEqualTo(customersHash);
        assertThat(getTableHash("products")).isEqualTo(productsHash);
        assertThat(getTableHash("orders")).isEqualTo(ordersHash);
    }

    private long getTableHash(String tableName) {
        return sessionFactory.fromStatelessSession(session -> {
            StringBuilder dataBuilder = new StringBuilder();
            session.createNativeQuery("SELECT * FROM " + tableName, Object[].class)
                    .getResultList()
                    .forEach(row -> {
                        for (Object col : row) {
                            dataBuilder.append(col);
                        }
                    });
            return (long) dataBuilder.toString().hashCode();
        });
    }

    static Stream<Function<SessionFactory, ContentRetriever>> contentRetrieverProviders() {
        return Stream.of(

                // OpenAI
                sf -> HibernateContentRetriever.builder()
                        .sessionFactory(sf)
                        .chatModel(openAiChatModel)
                        .build(),
                sf -> HibernateContentRetriever.builder()
                        .sessionFactory(sf)
                        .chatModel(openAiChatModel)
                        .maxRetries(1)
                        .build(),

                // Mistral
                sf -> HibernateContentRetriever.builder()
                        .sessionFactory(sf)
                        .chatModel(mistralAiChatModel)
                        .build(),
                sf -> HibernateContentRetriever.builder()
                        .sessionFactory(sf)
                        .chatModel(mistralAiChatModel)
                        .maxRetries(1)
                        .build());
    }
}
