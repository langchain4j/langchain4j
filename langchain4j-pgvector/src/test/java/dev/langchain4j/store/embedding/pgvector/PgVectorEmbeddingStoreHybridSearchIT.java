package dev.langchain4j.store.embedding.pgvector;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author xiaoyang
 **/
@Testcontainers
public class PgVectorEmbeddingStoreHybridSearchIT extends PgVectorEmbeddingStoreIT {

    @Container
    static PostgreSQLContainer<?> pgVector = new PostgreSQLContainer<>("pgvector/pgvector:pg15");

    PgVectorEmbeddingStore embeddingStore;

    EmbeddingModel embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();

    @BeforeEach
    void beforeEach() {
        embeddingStore = PgVectorEmbeddingStore.builder()
                .host(pgVector.getHost())
                .port(pgVector.getFirstMappedPort())
                .user("test")
                .password("test")
                .database("test")
                .table("test")
                .useFullTextIndex(true)
                .dimension(384)
                .dropTableFirst(true)
                .build();
    }

    @Override
    protected EmbeddingStore<TextSegment> embeddingStore() {
        return embeddingStore;
    }

    @Override
    protected EmbeddingModel embeddingModel() {
        return embeddingModel;
    }


    @Test
    void should_return_most_relevant_when_using_full_text_search() {

        List<String> contents = prepareData();

        for (String content : contents) {
            embeddingStore.add(embeddingModel.embed(content).content(), TextSegment.from(content));
        }

        awaitUntilPersisted();

        EmbeddingSearchResult<TextSegment> relevant = embeddingStore.fullTextSearch("Alain", null, 3, 0.0);
        assertThat(relevant.matches()).hasSizeGreaterThan(0);
        assertThat(relevant.matches().get(0).embedded().text()).contains("Émile-Auguste Chartier");


        EmbeddingSearchResult<TextSegment> relevant2 = embeddingStore.fullTextSearch("Heidegger", null, 3, 0.0);
        assertThat(relevant2.matches()).hasSizeGreaterThan(0);
        assertThat(relevant2.matches().get(0).embedded().text()).contains("Maurice Jean Jacques Merleau-Ponty");
    }



    @Test
    void should_return_nothing_when_using_full_text_search_and_word_not_exists() {
        List<String> contents = prepareData();

        for (String content : contents) {
            embeddingStore.add(embeddingModel.embed(content).content(), TextSegment.from(content));
        }
        awaitUntilPersisted();
        EmbeddingSearchResult<TextSegment> relevant = embeddingStore.fullTextSearch("WuKong", null, 3, 0.0);
        assertThat(relevant.matches()).hasSize(0);
    }


    @Test
    void should_return_most_relevant_when_using_hybrid_search() {
        List<String> contents = prepareData();

        for (String content : contents) {
            embeddingStore.add(embeddingModel.embed(content).content(), TextSegment.from(content));
        }

        awaitUntilPersisted();

        EmbeddingSearchResult<TextSegment> relevant = embeddingStore.hybridSearch(embeddingModel.embed("Alain").content(), "Alain", null, 3, 0.0, 60);
        assertThat(relevant.matches()).hasSizeGreaterThan(0);
        assertThat(relevant.matches().get(0).embedded().text()).contains("Émile-Auguste Chartier");


        EmbeddingSearchResult<TextSegment> relevant2 = embeddingStore.hybridSearch(embeddingModel.embed("Heidegger").content(), "Heidegger", null, 3, 0.0, 60);
        assertThat(relevant2.matches()).hasSizeGreaterThan(0);
        assertThat(relevant2.matches().get(0).embedded().text()).contains("Maurice Jean Jacques Merleau-Ponty");
    }


    private List<String> prepareData() {
        String content1 = "Émile-Auguste Chartier (3 March 1868 – 2 June 1951), commonly known as Alain, was a French philosopher, journalist, essayist, pacifist, and teacher of philosophy. He adopted his pseudonym as the most banal he could find. There is no evidence he ever thought in so doing of the 15th century Norman poet Alain Chartier.";
        String content2 = "Emmanuel Levinas (12 January 1906 – 25 December 1995) was a French philosopher of Lithuanian Jewish ancestry who is known for his work within Jewish philosophy, existentialism, and phenomenology, focusing on the relationship of ethics to metaphysics and ontology.";
        String content3 = "Maurice Jean Jacques Merleau-Ponty (14 March 1908 – 3 May 1961) was a French phenomenological philosopher, strongly influenced by Edmund Husserl and Martin Heidegger. The constitution of meaning in human experience was his main interest and he wrote on perception, art, politics, religion, biology, psychology, psychoanalysis, language, nature, and history. He was the lead editor of Les Temps modernes, the leftist magazine he established with Jean-Paul Sartre and Simone de Beauvoir in 1945.";
        return asList(content1, content2, content3);
    }
}
