package dev.langchain4j.model.watsonx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.ibm.watsonx.ai.rerank.RerankParameters;
import com.ibm.watsonx.ai.rerank.RerankResponse;
import com.ibm.watsonx.ai.rerank.RerankResponse.RerankInputResult;
import com.ibm.watsonx.ai.rerank.RerankResponse.RerankResult;
import com.ibm.watsonx.ai.rerank.RerankService;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.scoring.ScoringModel;

@ExtendWith(MockitoExtension.class)
public class WatsonxScoringModelTest {

    @Mock
    RerankService mockRerankService;

    @Test
    void testScoreAll() {

        List<RerankResult> rerankResults = List.of(
                new RerankResult(0, 0.0, new RerankInputResult("test1")),
                new RerankResult(1, 0.1, new RerankInputResult("test2")));
        RerankResponse rerankResponse =
                new RerankResponse("modelId", rerankResults, "createdAt", 10, "modelVersion", "query");

        when(mockRerankService.rerank("query", List.of("test1", "test2"), null)).thenReturn(rerankResponse);

        ScoringModel scoringModel =
                WatsonxScoringModel.builder().service(mockRerankService).build();

        var result = scoringModel.scoreAll(List.of(TextSegment.from("test1"), TextSegment.from("test2")), "query");
        assertEquals(2, result.content().size());
        assertEquals(0.0, result.content().get(0));
        assertEquals(0.1, result.content().get(1));
    }

    @Test
    void testScoreAllWithParameters() {

        List<RerankResult> rerankResults = List.of(
                new RerankResult(0, 0.0, new RerankInputResult("test1")),
                new RerankResult(1, 0.1, new RerankInputResult("test2")));
        RerankResponse rerankResponse =
                new RerankResponse("modelId", rerankResults, "createdAt", 10, "modelVersion", "query");
        RerankParameters parameters = RerankParameters.builder()
                .modelId("modelId")
                .projectId("projectId")
                .spaceId("spaceId")
                .truncateInputTokens(512)
                .query(true)
                .inputs(true)
                .build();

        WatsonxScoringModel scoringModel =
                WatsonxScoringModel.builder().service(mockRerankService).build();

        when(mockRerankService.rerank("query", List.of("test1", "test2"), parameters))
                .thenReturn(rerankResponse);

        var result = scoringModel.scoreAll(
                List.of(TextSegment.from("test1"), TextSegment.from("test2")), "query", parameters);
        assertEquals(2, result.content().size());
        assertEquals(0.0, result.content().get(0));
        assertEquals(0.1, result.content().get(1));

        assertEquals(0, scoringModel.scoreAll(null, "query").content().size());
        assertEquals(0, scoringModel.scoreAll(List.of(), "query").content().size());
        assertEquals(
                0,
                scoringModel
                        .scoreAll(List.of(TextSegment.from("test1")), null)
                        .content()
                        .size());
        assertEquals(
                0,
                scoringModel
                        .scoreAll(List.of(TextSegment.from("test1")), "")
                        .content()
                        .size());
    }
}
