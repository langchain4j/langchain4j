package dev.langchain4j.rag.content.retriever.neo4j;

import static dev.langchain4j.Neo4jTestUtils.getNeo4jContainer;
import static dev.langchain4j.model.openai.OpenAiChatModelName.GPT_4_O_MINI;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.store.graph.neo4j.Neo4jGraph;
import java.util.List;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Session;
import org.testcontainers.containers.Neo4jContainer;
import org.testcontainers.junit.jupiter.Container;

@ExtendWith(MockitoExtension.class)
class Neo4jContentRetrieverIT {

    @Container
    private static final Neo4jContainer<?> neo4jContainer =
            getNeo4jContainer().withoutAuthentication().withPlugins("apoc");

    private Driver driver;
    private Neo4jGraph graph;
    private Neo4jContentRetriever retriever;

    @Mock
    private ChatLanguageModel chatLanguageModel;

    @BeforeAll
    static void beforeAll() {
        neo4jContainer.start();
    }

    @BeforeEach
    void beforeEach() {

        driver = GraphDatabase.driver(neo4jContainer.getBoltUrl(), AuthTokens.none());

        try (Session session = driver.session()) {
            session.run("CREATE (book:Book {title: 'Dune'})<-[:WROTE]-(author:Person {name: 'Frank Herbert'})");
        }

        graph = Neo4jGraph.builder().driver(driver).build();

        retriever = Neo4jContentRetriever.builder()
                .graph(graph)
                .chatLanguageModel(chatLanguageModel)
                .build();
    }

    @AfterEach
    void afterEach() {
        try (Session session = driver.session()) {
            session.run("MATCH (n) DETACH DELETE n");
        }
        graph.close();
        driver.close();
    }

    @AfterAll
    static void afterAll() {
        neo4jContainer.stop();
    }

    @Test
    void shouldRetrieveContentWhenQueryIsValid() {
        // Given
        Query query = new Query("Who is the author of the book 'Dune'?");
        when(chatLanguageModel.generate(anyString()))
                .thenReturn("MATCH(book:Book {title: 'Dune'})<-[:WROTE]-(author:Person) RETURN author.name AS output");

        // When
        List<Content> contents = retriever.retrieve(query);

        // Then
        assertThat(contents).hasSize(1);
    }

    @Test
    void shouldRetrieveContentWhenQueryIsValidAndResponseHasBackticks() {
        // Given
        Query query = new Query("Who is the author of the book 'Dune'?");
        when(chatLanguageModel.generate(anyString()))
                .thenReturn(
                        "```MATCH(book:Book {title: 'Dune'})<-[:WROTE]-(author:Person) RETURN author.name AS output```");

        // When
        List<Content> contents = retriever.retrieve(query);

        // Then
        assertThat(contents).hasSize(1);
    }

    @Test
    void shouldRetrieveContentWhenQueryIsValidAndOpenAiChatModelIsUsed() {

        // With
        ChatLanguageModel openAiChatModel = OpenAiChatModel.builder()
                .baseUrl(System.getenv("OPENAI_BASE_URL"))
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .organizationId(System.getenv("OPENAI_ORGANIZATION_ID"))
                .modelName(GPT_4_O_MINI)
                .logRequests(true)
                .logResponses(true)
                .build();

        Neo4jContentRetriever neo4jContentRetriever = Neo4jContentRetriever.builder()
                .graph(graph)
                .chatLanguageModel(openAiChatModel)
                .build();

        // Given
        Query query = new Query("Who is the author of the book 'Dune'?");

        // When
        List<Content> contents = neo4jContentRetriever.retrieve(query);

        // Then
        assertThat(contents).hasSize(1);
    }

    @Test
    void shouldReturnEmptyListWhenQueryIsInvalid() {
        // Given
        Query query = new Query("Who is the author of the movie 'Dune'?");
        when(chatLanguageModel.generate(anyString()))
                .thenReturn(
                        "MATCH(movie:Movie {title: 'Dune'})<-[:WROTE]-(author:Person) RETURN author.name AS output");

        // When
        List<Content> contents = retriever.retrieve(query);

        // Then
        assertThat(contents).isEmpty();
    }
}
