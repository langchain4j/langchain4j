package dev.langchain4j.model.workerai;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.ArrayList;
import java.util.List;

@Disabled("requires a worker ai account")
@EnabledIfEnvironmentVariable(named = "WORKERAI_API_KEY", matches = ".*")
@EnabledIfEnvironmentVariable(named = "WORKERAI_ACCOUNT_ID", matches = ".*")
public class WorkerAIEmbeddingModelIT {

    static WorkerAiEmbeddingModel embeddingModel;

    @BeforeAll
    public static void initializeModel() {
        embeddingModel = WorkerAiEmbeddingModel.builder()
                .modelName(WorkerAiModelName.BAAI_EMBEDDING_BASE)
                .accountIdentifier(System.getenv("WORKERAI_ACCOUNT_ID"))
                .token(System.getenv("WORKERAI_API_KEY"))
                .buildEmbeddingModel();
    }

    @Test
    public void generateEmbeddingSimple() {
        Response<Embedding> out = embeddingModel.embed("Sentence1");
        Assertions.assertNotNull(out.content());
    }

    @Test
    public void generateEmbeddings() {
        List<TextSegment> data = new ArrayList<>();
        data.add(new TextSegment("Sentence1", new Metadata()));
        data.add(new TextSegment("Sentence2", new Metadata()));
        Response<List<Embedding>> out = embeddingModel.embedAll(data);
        Assertions.assertNotNull(out.content());
    }
}
