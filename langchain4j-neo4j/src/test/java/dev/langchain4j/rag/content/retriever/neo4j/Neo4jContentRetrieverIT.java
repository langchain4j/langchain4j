package dev.langchain4j.rag.content.retriever.neo4j;

import dev.langchain4j.internal.RetryUtils;
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

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

import static dev.langchain4j.model.openai.OpenAiChatModelName.GPT_4_O_MINI;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class Neo4jContentRetrieverIT {

    @Container
    private static final Neo4jContainer<?> neo4jContainer = new Neo4jContainer<>("neo4j:latest")
            .withoutAuthentication()
            .withPlugins("apoc");

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

        graph = Neo4jGraph.builder()
                .driver(driver)
                .build();

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
        when(chatLanguageModel.generate(anyString())).thenReturn("MATCH(book:Book {title: 'Dune'})<-[:WROTE]-(author:Person) RETURN author.name AS output");

        // When
        List<Content> contents = retriever.retrieve(query);

        // Then
        assertThat(contents).hasSize(1);
    }

    @Test
    void shouldRetrieveContentWhenQueryIsValidAndResponseHasBackticks() {
        // Given
        Query query = new Query("Who is the author of the book 'Dune'?");
        when(chatLanguageModel.generate(anyString())).thenReturn("```MATCH(book:Book {title: 'Dune'})<-[:WROTE]-(author:Person) RETURN author.name AS output```");

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
    void shouldRetrieveContentWhenQueryIsValidAndOpenAiChatModelIsUsedWithExample() throws Exception {
        // remove existing `Book` and `Person` entities
        driver.session().run("MATCH (n) DETACH DELETE n");
        // recreate Neo4jGraph instead of reuse `this.graph`, otherwise it remains the old one with (:Book), etc..
        final Neo4jGraph graphStreamer = Neo4jGraph.builder()
                .driver(driver)
                .build();
        
        URI resource =  getClass().getClassLoader().getResource("streamer_dataset.cypher")
                .toURI();
        String datasetEntities = Files.readString( Paths.get(resource) );

        driver.session().executeWriteWithoutResult(tx -> {
            for (String query: datasetEntities.split(";")) {
                System.out.println("query = " + query);
                tx.run(query);
            }
        });

        // With
        ChatLanguageModel openAiChatModel = OpenAiChatModel.builder()
                .baseUrl(System.getenv("OPENAI_BASE_URL"))
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .organizationId(System.getenv("OPENAI_ORGANIZATION_ID"))
                .modelName(GPT_4_O_MINI)
                .logRequests(true)
                .logResponses(true)
                .build();

        String examples = """
            # Which streamer has the most followers?
            MATCH (s:Stream)
            RETURN s.name AS streamer
            ORDER BY s.followers DESC LIMIT 1
            
            # How many streamers are from Norway?
            MATCH (s:Stream)-[:HAS_LANGUAGE]->(:Language {{name: 'Norwegian'}})
            RETURN count(s) AS streamers

            Note: Do not include any explanations or apologies in your responses.
            Do not respond to any questions that might ask anything else than for you to construct a Cypher statement.
            Do not include any text except the generated Cypher statement.
            """;
        final String textQuery = "Which streamer from Italy has the most followers?";
        Query query = new Query(textQuery);

        Neo4jContentRetriever neo4jContentRetrieverWithoutExample = Neo4jContentRetriever.builder()
                .graph(graphStreamer)
                .chatLanguageModel(openAiChatModel)
                .build();
        List<Content> contentsWithoutExample = neo4jContentRetrieverWithoutExample.retrieve(query);
        assertThat(contentsWithoutExample).hasSize(0);

        // When
        Neo4jContentRetriever neo4jContentRetriever = Neo4jContentRetriever.builder()
                .graph(graphStreamer)
                .chatLanguageModel(openAiChatModel)
                .examples(examples)
                .build();

        // Then
        // retry mechanism since the result is not deterministic
        final String text = RetryUtils.withRetry(() -> {
            List<Content> contents = neo4jContentRetriever.retrieve(query);
            assertThat(contents).hasSize(1);
            return contents.get(0).textSegment().text();
        }, 5);

        // check validity of the response
        final String name = driver.session().run("MATCH (s:Stream)-[:HAS_LANGUAGE]->(l:Language {name: 'Italian'}) RETURN s.name ORDER BY s.followers DESC LIMIT 1")
                .single()
                .values()
                .get(0)
                .toString();
        assertThat(text).isEqualTo(name);
    }

    @Test
    void shouldReturnEmptyListWhenQueryIsInvalid() {
        // Given
        Query query = new Query("Who is the author of the movie 'Dune'?");
        when(chatLanguageModel.generate(anyString())).thenReturn("MATCH(movie:Movie {title: 'Dune'})<-[:WROTE]-(author:Person) RETURN author.name AS output");

        // When
        List<Content> contents = retriever.retrieve(query);

        // Then
        assertThat(contents).isEmpty();
    }
}
