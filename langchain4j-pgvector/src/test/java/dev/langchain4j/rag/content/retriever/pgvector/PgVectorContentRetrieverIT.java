package dev.langchain4j.rag.content.retriever.pgvector;


import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.store.embedding.pgvector.FullTextIndexType;
import org.awaitility.Awaitility;
import org.awaitility.core.ThrowingRunnable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
public class PgVectorContentRetrieverIT{

    @Container
    static PostgreSQLContainer<?> pgVector = new PostgreSQLContainer<>("pgvector/pgvector:pg15");

    PgVectorContentRetriever contentRetrieverWithVector;

    PgVectorContentRetriever contentRetrieverWithFullTextUsingGin;

    PgVectorContentRetriever contentRetrieverWithFullTextWithoutIndex;

    PgVectorContentRetriever contentRetrieverWithHybrid;

    EmbeddingModel embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();

    @BeforeEach
    void beforeEach() {
        contentRetrieverWithVector = PgVectorContentRetriever.builder()
                .host(pgVector.getHost())
                .port(pgVector.getFirstMappedPort())
                .user("test")
                .password("test")
                .database("test")
                .table("testVector")
                .dimension(384)
                .dropTableFirst(true)
                .pgQueryType(PgQueryType.VECTOR)
                .embeddingModel(embeddingModel)
                .maxResults(100)
                .build();
        contentRetrieverWithFullTextUsingGin = PgVectorContentRetriever.builder()
                .host(pgVector.getHost())
                .port(pgVector.getFirstMappedPort())
                .user("test")
                .password("test")
                .database("test")
                .table("testFullTextGin")
                .dimension(384)
                .dropTableFirst(true)
                .pgQueryType(PgQueryType.FULL_TEXT)
                .fullTextIndexType(FullTextIndexType.GIN)
                .embeddingModel(embeddingModel)
                .maxResults(100)
                .build();
        contentRetrieverWithFullTextWithoutIndex = PgVectorContentRetriever.builder()
                .host(pgVector.getHost())
                .port(pgVector.getFirstMappedPort())
                .user("test")
                .password("test")
                .database("test")
                .table("testFullText")
                .dimension(384)
                .dropTableFirst(true)
                .pgQueryType(PgQueryType.FULL_TEXT)
                .embeddingModel(embeddingModel)
                .maxResults(100)
                .build();
        contentRetrieverWithHybrid = PgVectorContentRetriever.builder()
                .host(pgVector.getHost())
                .port(pgVector.getFirstMappedPort())
                .user("test")
                .password("test")
                .database("test")
                .table("testHybrid")
                .dimension(384)
                .dropTableFirst(true)
                .pgQueryType(PgQueryType.HYBRID)
                .embeddingModel(embeddingModel)
                .maxResults(100)
                .build();
    }


    @Test
    void should_return_most_relevant_when_using_full_text_search_without_index() {

        List<TextSegment> contents = prepareData();

        contentRetrieverWithFullTextWithoutIndex.add(contents);

        awaitUntilAsserted(() -> assertThat(getAllContent(contentRetrieverWithFullTextWithoutIndex)).hasSize(contents.size()));

        List<Content> relevant = contentRetrieverWithFullTextWithoutIndex.retrieve(Query.from("Alain"));
        assertThat(relevant).hasSizeGreaterThan(0);
        assertThat(relevant.get(0).textSegment().text()).contains("Émile-Auguste Chartier");


        List<Content> relevant2 = contentRetrieverWithFullTextWithoutIndex.retrieve(Query.from("Heidegger"));
        assertThat(relevant2).hasSizeGreaterThan(0);
        assertThat(relevant2.get(0).textSegment().text()).contains("Maurice Jean Jacques Merleau-Ponty");
    }


    @Test
    void should_return_most_relevant_when_using_full_text_search_and_gin() {

        List<TextSegment> contents = prepareData();

        contentRetrieverWithFullTextUsingGin.add(contents);

        awaitUntilAsserted(() -> assertThat(getAllContent(contentRetrieverWithFullTextUsingGin)).hasSize(contents.size()));

        List<Content> relevant = contentRetrieverWithFullTextUsingGin.retrieve(Query.from("Alain"));
        assertThat(relevant).hasSizeGreaterThan(0);
        assertThat(relevant.get(0).textSegment().text()).contains("Émile-Auguste Chartier");


        List<Content> relevant2 = contentRetrieverWithFullTextUsingGin.retrieve(Query.from("Heidegger"));
        assertThat(relevant2).hasSizeGreaterThan(0);
        assertThat(relevant2.get(0).textSegment().text()).contains("Maurice Jean Jacques Merleau-Ponty");
    }



    @Test
    void should_return_nothing_when_using_full_text_search_and_word_not_exists() {
        List<TextSegment> contents = prepareData();

        contentRetrieverWithFullTextUsingGin.add(contents);

        awaitUntilAsserted(() -> assertThat(getAllContent(contentRetrieverWithFullTextUsingGin)).hasSize(contents.size()));
        List<Content> relevant = contentRetrieverWithFullTextUsingGin.retrieve(Query.from("WuKong"));
        assertThat(relevant).hasSize(0);
    }


    @Test
    void should_return_most_relevant_when_using_hybrid_search() {
        List<TextSegment> contents = prepareData();

        contentRetrieverWithHybrid.add(contents);

        awaitUntilAsserted(() -> assertThat(getAllContent(contentRetrieverWithHybrid)).hasSize(contents.size()));

        List<Content> relevant = contentRetrieverWithHybrid.retrieve(Query.from("Alain"));
        assertThat(relevant).hasSizeGreaterThan(0);
        assertThat(relevant.get(0).textSegment().text()).contains("Émile-Auguste Chartier");


        List<Content> relevant2 = contentRetrieverWithHybrid.retrieve(Query.from("Heidegger"));
        assertThat(relevant2).hasSizeGreaterThan(0);
        assertThat(relevant2.get(0).textSegment().text()).contains("Maurice Jean Jacques Merleau-Ponty");
    }

    @Test
    void should_return_most_relevant_when_using_vector() {
        List<TextSegment> contents = prepareData();

        contentRetrieverWithVector.add(contents);

        awaitUntilAsserted(() -> assertThat(getAllContent(contentRetrieverWithVector)).hasSize(contents.size()));

        List<Content> relevant = contentRetrieverWithVector.retrieve(Query.from("Alain"));
        assertThat(relevant).hasSizeGreaterThan(0);
        assertThat(relevant.get(0).textSegment().text()).contains("Émile-Auguste Chartier");


        List<Content> relevant2 = contentRetrieverWithVector.retrieve(Query.from("Heidegger"));
        assertThat(relevant2).hasSizeGreaterThan(0);
        assertThat(relevant2.get(0).textSegment().text()).contains("Maurice Jean Jacques Merleau-Ponty");
    }




    private List<TextSegment> prepareData() {
        String content1 = "Émile-Auguste Chartier (3 March 1868 – 2 June 1951), commonly known as Alain, was a French philosopher, journalist, essayist, pacifist, and teacher of philosophy. He adopted his pseudonym as the most banal he could find. There is no evidence he ever thought in so doing of the 15th century Norman poet Alain Chartier.";
        String content2 = "Emmanuel Levinas (12 January 1906 – 25 December 1995) was a French philosopher of Lithuanian Jewish ancestry who is known for his work within Jewish philosophy, existentialism, and phenomenology, focusing on the relationship of ethics to metaphysics and ontology.";
        String content3 = "Maurice Jean Jacques Merleau-Ponty (14 March 1908 – 3 May 1961) was a French phenomenological philosopher, strongly influenced by Edmund Husserl and Martin Heidegger. The constitution of meaning in human experience was his main interest and he wrote on perception, art, politics, religion, biology, psychology, psychoanalysis, language, nature, and history. He was the lead editor of Les Temps modernes, the leftist magazine he established with Jean-Paul Sartre and Simone de Beauvoir in 1945.";
        return Stream.of(content1, content2, content3).map(TextSegment::from).collect(Collectors.toList());
    }

    protected void awaitUntilAsserted(ThrowingRunnable assertion) {
        Awaitility.await()
                .atMost(Duration.ofSeconds(60))
                .pollDelay(Duration.ofSeconds(0))
                .pollInterval(Duration.ofMillis(300))
                .untilAsserted(assertion);
    }

    protected List<Content> getAllContent(ContentRetriever contentRetriever) {

        Query query = Query.from("French");

        return contentRetriever.retrieve(query);

    }

}
