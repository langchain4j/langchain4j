package dev.langchain4j.model.workersai;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "WORKERS_AI_API_KEY", matches = ".*")
@EnabledIfEnvironmentVariable(named = "WORKERS_AI_ACCOUNT_ID", matches = ".*")
class WorkersAiEmbeddingModelIT {

    static WorkersAiEmbeddingModel embeddingModel;

    @BeforeAll
    static void initializeModel() {
        embeddingModel = WorkersAiEmbeddingModel.builder()
                .modelName(WorkersAiEmbeddingModelName.BAAI_EMBEDDING_BASE.toString())
                .accountId(System.getenv("WORKERS_AI_ACCOUNT_ID"))
                .apiToken(System.getenv("WORKERS_AI_API_KEY"))
                .build();
    }

    @Test
    void generateEmbeddingSimple() {
        Response<Embedding> out = embeddingModel.embed("Sentence1");
        assertThat(out.content()).isNotNull();
    }

    @Test
    void generateEmbeddings() {
        List<TextSegment> data = new ArrayList<>();
        data.add(new TextSegment("Sentence1", new Metadata()));
        data.add(new TextSegment("Sentence2", new Metadata()));
        Response<List<Embedding>> out = embeddingModel.embedAll(data);
        assertThat(out.content()).isNotNull();
        assertThat(out.content()).hasSize(2);
    }
}
