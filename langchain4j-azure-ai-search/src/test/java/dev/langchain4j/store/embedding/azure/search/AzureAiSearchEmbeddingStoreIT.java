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

    EmbeddingModel embeddingModel = AzureOpenAiEmbeddingModel.builder()
            .endpoint(System.getenv("AZURE_OPENAI_ENDPOINT"))
            .serviceVersion(System.getenv("AZURE_OPENAI_SERVICE_VERSION"))
            .apiKey(System.getenv("AZURE_OPENAI_KEY"))
            .deploymentName(System.getenv("AZURE_OPENAI_DEPLOYMENT_NAME"))
            .logRequestsAndResponses(true)
            .build();

    EmbeddingStore<TextSegment> embeddingStore = AzureAiSearchEmbeddingStore.builder()
            .endpoint(System.getenv("AZURE_SEARCH_ENDPOINT"))
            .apiKey(System.getenv("AZURE_SEARCH_KEY"))
            .embeddingModel(embeddingModel)
            .build();

    @Test
    void testAddEmbeddingAndFindRelevant() {
        Embedding firstEmbedding = embeddingModel.embed("hello").content();
        Embedding secondEmbedding = embeddingModel.embed("hi").content();
        embeddingStore.addAll(asList(firstEmbedding, secondEmbedding));

        List<EmbeddingMatch<TextSegment>> relevant = embeddingStore.findRelevant(firstEmbedding, 10);
        assertThat(relevant).hasSize(2);
        assertThat(relevant.get(0).embedding()).isNull();
        assertThat(relevant.get(1).embedding()).isNull();
    }

    @Override
    protected EmbeddingStore<TextSegment> embeddingStore() {
        return embeddingStore;
    }

    @Override
    protected EmbeddingModel embeddingModel() {
        return embeddingModel;
    }
}
