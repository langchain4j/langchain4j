package dev.langchain4j.store.embedding.azure.search;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreWithRemovalIT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "AZURE_SEARCH_ENDPOINT", matches = ".+")
public class AzureAiSearchEmbeddingStoreRemovalIT extends EmbeddingStoreWithRemovalIT {

    private static final Logger log = LoggerFactory.getLogger(AzureAiSearchEmbeddingStoreIT.class);

    private EmbeddingModel embeddingModel;

    private EmbeddingStore<TextSegment> embeddingStore;

    private int dimensions;

    private String AZURE_SEARCH_ENDPOINT = System.getenv("AZURE_SEARCH_ENDPOINT");

    private String AZURE_SEARCH_KEY = System.getenv("AZURE_SEARCH_KEY");

    public AzureAiSearchEmbeddingStoreRemovalIT() {

        embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();
        dimensions = embeddingModel.embed("test").content().vector().length;

        embeddingStore =  AzureAiSearchEmbeddingStore.builder()
                .endpoint(AZURE_SEARCH_ENDPOINT)
                .apiKey(AZURE_SEARCH_KEY)
                .dimensions(dimensions)
                .build();
    }

    @BeforeEach
    void setUp() {
        clearStore();
    }


    @Test
    void test_add_embeddings_and_remove_document_and_find_relevant(){
        String content1 = "banana";
        String content2 = "apple";
        String content3 = "pizza";
        String content4 = "strawberry";
        List<String> contents = asList(content1, content2, content3, content4);
        Map<String, String> contentIdMap = new HashMap<>();
        for(String content: contents){
            TextSegment textSegment = TextSegment.from(content);
            Embedding embedding = embeddingModel.embed(content).content();
            String id = embeddingStore.add(embedding, textSegment);
            contentIdMap.put(content, id);
        }
        awaitUntilPersisted();

        Embedding relevantEmbedding = embeddingModel.embed("fruit").content();
        List<EmbeddingMatch<TextSegment>> relevant = embeddingStore.findRelevant(relevantEmbedding, 4);
        assertThat(relevant).hasSize(4);

        //removing content3
        String idToRemove = contentIdMap.get(content3);
        embeddingStore.remove(idToRemove);

        awaitUntilPersisted();

        //After removal on one document from embedding store, relevant text semgment match should decrease by 1.
        relevant = embeddingStore.findRelevant(relevantEmbedding, 4);
        assertThat(relevant).hasSize(3);
        for (EmbeddingMatch<TextSegment> textSegmentEmbeddingMatch : relevant)
            assertThat(textSegmentEmbeddingMatch.embedded().text()).isIn(content1, content2, content4).isNotIn(content3);
    }

    @Test
    void test_add_embeddings_and_remove_multiple_documents_and_find_relevant(){
        String content1 = "banana";
        String content2 = "apple";
        String content3 = "pizza";
        String content4 = "strawberry";
        List<String> contents = asList(content1, content2, content3, content4);
        Map<String, String> contentIdMap = new HashMap<>();
        for(String content: contents){
            TextSegment textSegment = TextSegment.from(content);
            Embedding embedding = embeddingModel.embed(content).content();
            String id = embeddingStore.add(embedding, textSegment);
            contentIdMap.put(content, id);
        }
        awaitUntilPersisted();

        Embedding relevantEmbedding = embeddingModel.embed("fruit").content();
        List<EmbeddingMatch<TextSegment>> relevant = embeddingStore.findRelevant(relevantEmbedding, 4);
        assertThat(relevant).hasSize(4);

        //removing content1 and content2
        String id1 = contentIdMap.get(content1);
        String id2 = contentIdMap.get(content2);
        embeddingStore.removeAll(Arrays.asList(id1, id2));

        awaitUntilPersisted();

        //After removal on two document from embedding store, relevant text semgment match should decrease by 2.
        relevant = embeddingStore.findRelevant(relevantEmbedding, 4);
        assertThat(relevant).hasSize(2);
        for (EmbeddingMatch<TextSegment> textSegmentEmbeddingMatch : relevant)
            assertThat(textSegmentEmbeddingMatch.embedded().text()).isNotIn(content1, content2).isIn(content3, content4);
    }

    @Test
    void test_add_embeddings_and_remove_all_documents(){
        String content1 = "banana";
        String content2 = "apple";
        String content3 = "pizza";
        String content4 = "strawberry";
        List<String> contents = asList(content1, content2, content3, content4);
        for(String content: contents){
            TextSegment textSegment = TextSegment.from(content);
            Embedding embedding = embeddingModel.embed(content).content();
            String id = embeddingStore.add(embedding, textSegment);
        }
        awaitUntilPersisted();

        Embedding relevantEmbedding = embeddingModel.embed("fruit").content();
        List<EmbeddingMatch<TextSegment>> relevant = embeddingStore.findRelevant(relevantEmbedding, 4);
        assertThat(relevant).hasSize(4);

        //removing all the embeddings
        embeddingStore.removeAll();

        awaitUntilPersisted();

        //After removal on two document from embedding store, relevant text semgment match should decrease by 2.
        relevant = embeddingStore.findRelevant(relevantEmbedding, 4);
        assertThat(relevant).hasSize(0);
        for (EmbeddingMatch<TextSegment> textSegmentEmbeddingMatch : relevant)
            assertThat(textSegmentEmbeddingMatch.embedded().text()).isNotIn(content1, content2, content3, content4);
    }

    private void clearStore() {
        AzureAiSearchEmbeddingStore azureAiSearchEmbeddingStore = (AzureAiSearchEmbeddingStore) embeddingStore;
        try {
            azureAiSearchEmbeddingStore.deleteIndex();
            azureAiSearchEmbeddingStore.createOrUpdateIndex(dimensions);
        } catch (RuntimeException e) {
            log.error("Failed to clean up the index. You should look at deleting it manually.", e);
        }
    }

    @Override
    protected EmbeddingStore<TextSegment> embeddingStore() {
        return embeddingStore;
    }

    @Override
    protected EmbeddingModel embeddingModel() {
        return embeddingModel;
    }

    @Override
    protected void awaitUntilPersisted() {
        try {
            Thread.sleep(1_000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
