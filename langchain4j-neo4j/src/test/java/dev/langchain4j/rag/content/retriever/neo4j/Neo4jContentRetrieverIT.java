package dev.langchain4j.rag.content.retriever.neo4j;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.store.graph.neo4j.Neo4jGraph;
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
import org.testcontainers.utility.DockerImageName;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class Neo4jContentRetrieverIT {

    @Container
    private static final Neo4jContainer<?> neo4jContainer = new Neo4jContainer<>(DockerImageName.parse("neo4j:5.16.0"))
            .withoutAuthentication()
            .withLabsPlugins("apoc");

    private static Driver driver;

    private static Neo4jGraph graph;

    private static Neo4jContentRetriever retriever;

    @Mock
    private ChatLanguageModel chatLanguageModel;

    @BeforeAll
    static void beforeAll() {

        neo4jContainer.start();
        driver = GraphDatabase.driver(neo4jContainer.getBoltUrl(), AuthTokens.none());
        graph = Neo4jGraph.builder().driver(driver).build();
    }

    @AfterAll
    static void afterAll() {

        graph.close();
        driver.close();
        neo4jContainer.stop();
    }

    @BeforeEach
    void setUp() {

        try (Session session = driver.session()) {
            session.run("CREATE (book:Book {title: 'Dune'})<-[:WROTE]-(author:Person {name: 'Frank Herbert'})");
        }
        retriever = Neo4jContentRetriever.builder()
                .graph(graph)
                .chatLanguageModel(chatLanguageModel)
                .build();
    }

    @AfterEach
    void tearDown() {

        try (Session session = driver.session()) {
            session.run("MATCH (n) DETACH DELETE n");
        }
    }

    @Test
    void shouldRetrieveContentWhenQueryIsValid() {
        // Given
        Query query = new Query("Who is the author of the book 'Dune'?");
        when(chatLanguageModel.generate(anyString())).thenReturn("MATCH(book:Book {title: 'Dune'})<-[:WROTE]-(author:Person) RETURN author.name AS output");

        // When
        List<Content> contents = retriever.retrieve(query);

        // Then
        assertEquals(1, contents.size());
    }

    @Test
    void shouldRetrieveContentWhenQueryIsValidAndResponseHasBackticks() {
        // Given
        Query query = new Query("Who is the author of the book 'Dune'?");
        when(chatLanguageModel.generate(anyString())).thenReturn("```MATCH(book:Book {title: 'Dune'})<-[:WROTE]-(author:Person) RETURN author.name AS output```");

        // When
        List<Content> contents = retriever.retrieve(query);

        // Then
        assertEquals(1, contents.size());
    }

    @Test
    void shouldRetrieveContentWhenQueryIsValidAndOpenAiChatModelIsUsed() {

        // With
        ChatLanguageModel openAiChatModel = OpenAiChatModel.builder()
                .baseUrl(System.getenv("OPENAI_BASE_URL"))
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .organizationId(System.getenv("OPENAI_ORGANIZATION_ID"))
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
        assertEquals(1, contents.size());
    }

    @Test
    void shouldReturnEmptyListWhenQueryIsInvalid() {
        // Given
        Query query = new Query("Who is the author of the movie 'Dune'?");
        when(chatLanguageModel.generate(anyString())).thenReturn("MATCH(movie:Movie {title: 'Dune'})<-[:WROTE]-(author:Person) RETURN author.name AS output");

        // When
        List<Content> contents = retriever.retrieve(query);

        // Then
        assertEquals(0, contents.size());
    }
}