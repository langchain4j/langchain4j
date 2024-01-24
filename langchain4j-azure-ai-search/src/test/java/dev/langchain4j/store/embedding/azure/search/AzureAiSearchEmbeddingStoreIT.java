package dev.langchain4j.store.embedding.azure.search;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.azure.AzureOpenAiEmbeddingModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIT;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.List;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "AZURE_SEARCH_ENDPOINT", matches = ".+")
public class AzureAiSearchEmbeddingStoreIT extends EmbeddingStoreIT {

    private EmbeddingModel embeddingModel;

    private EmbeddingStore<TextSegment> embeddingStore;

    public AzureAiSearchEmbeddingStoreIT() {
        embeddingModel = AzureOpenAiEmbeddingModel.builder()
                .endpoint(System.getenv("AZURE_OPENAI_ENDPOINT"))
                .serviceVersion(System.getenv("AZURE_OPENAI_SERVICE_VERSION"))
                .apiKey(System.getenv("AZURE_OPENAI_KEY"))
                .deploymentName(System.getenv("AZURE_OPENAI_DEPLOYMENT_NAME"))
                .logRequestsAndResponses(true)
                .build();

        embeddingStore =  AzureAiSearchEmbeddingStore.builder()
                .endpoint(System.getenv("AZURE_SEARCH_ENDPOINT"))
                .apiKey(System.getenv("AZURE_SEARCH_KEY"))
                .embeddingModel(embeddingModel)
                .build();
    }

    @Test
    void testAddEmbeddingsAndFindRelevant() {
        String content1 = "Chihuahua";
        String content2 = "Poodle";
        String content3 = "Golden retriever";
        String content4 = "Chocolate cookie";
        List<String> contents = asList(content1, content2, content3, content4);

        for (String content : contents) {
            TextSegment textSegment = TextSegment.from(content);
            Embedding embedding = embeddingModel.embed(content).content();
            embeddingStore.add(embedding, textSegment);
        }

        Embedding relevantEmbedding = embeddingModel.embed("Cake").content();
        List<EmbeddingMatch<TextSegment>> relevant = embeddingStore.findRelevant(relevantEmbedding, 10);
        assertThat(relevant).hasSize(3);
        assertThat(relevant.get(0).embedding()).isNotNull();
        assertThat(relevant.get(1).embedding()).isNotNull();
        assertThat(relevant.get(2).embedding()).isNotNull();
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
    protected void clearStore() {
        AzureAiSearchEmbeddingStore azureAiSearchEmbeddingStore = (AzureAiSearchEmbeddingStore) embeddingStore;
        azureAiSearchEmbeddingStore.deleteIndex();
        azureAiSearchEmbeddingStore.createOrUpdateIndex(embeddingModel);
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
