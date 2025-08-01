package dev.langchain4j.model.watsonx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.ibm.watsonx.ai.embedding.EmbeddingParameters;
import com.ibm.watsonx.ai.embedding.EmbeddingResponse;
import com.ibm.watsonx.ai.embedding.EmbeddingService;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;

@ExtendWith(MockitoExtension.class)
public class WatsonxEmbeddingModelTest {

    @Mock
    EmbeddingService mockEmbeddingService;

    @Test
    void testEmbeddingAll() {

        List<EmbeddingResponse.Result> results = List.of(
                new EmbeddingResponse.Result(List.of(0f, 1f), "test1"),
                new EmbeddingResponse.Result(List.of(0f, 1f), "test2"));

        when(mockEmbeddingService.embedding(List.of("test1", "test2"), null))
                .thenReturn(new EmbeddingResponse("modelId", "createdAt", results, 10));

        EmbeddingModel embeddingModel =
                WatsonxEmbeddingModel.builder().service(mockEmbeddingService).build();

        var response = embeddingModel.embedAll(List.of(TextSegment.from("test1"), TextSegment.from("test2")));
        assertEquals(2, response.content().size());
        assertEquals(Embedding.from(List.of(0f, 1f)), response.content().get(0));
        assertEquals(Embedding.from(List.of(0f, 1f)), response.content().get(1));
    }

    @Test
    void testEmbeddingAllWithParameters() {

        List<EmbeddingResponse.Result> results = List.of(
                new EmbeddingResponse.Result(List.of(0f, 1f), "test1"),
                new EmbeddingResponse.Result(List.of(0f, 1f), "test2"));

        EmbeddingParameters parameters = EmbeddingParameters.builder()
                .modelId("modelId")
                .inputText(true)
                .projectId("projectId")
                .spaceId("spaceId")
                .truncateInputTokens(512)
                .build();

        WatsonxEmbeddingModel embeddingModel =
                WatsonxEmbeddingModel.builder().service(mockEmbeddingService).build();

        when(mockEmbeddingService.embedding(List.of("test1", "test2"), parameters))
                .thenReturn(new EmbeddingResponse("modelId", "createdAt", results, 10));

        var response =
                embeddingModel.embedAll(List.of(TextSegment.from("test1"), TextSegment.from("test2")), parameters);
        assertEquals(2, response.content().size());
        assertEquals(Embedding.from(List.of(0f, 1f)), response.content().get(0));
        assertEquals(Embedding.from(List.of(0f, 1f)), response.content().get(1));

        assertEquals(0, embeddingModel.embedAll(null).content().size());
        assertEquals(0, embeddingModel.embedAll(List.of()).content().size());
    }
}
