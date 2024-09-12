package dev.langchain4j.model.scoring.onnx;

import ai.onnxruntime.OrtSession;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.scoring.ScoringModel;
import dev.langchain4j.model.scoring.onnx.OnnxScoringModel;
import org.junit.jupiter.api.Test;

import java.util.List;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Percentage.withPercentage;

class OnnxScoringModelIT {

    @Test
    void should_score_single_text() {

//        String pathToModel = "D:\\github\\langchat\\model\\reranker\\bge-reranker-large\\onnx\\model.onnx";
//        String pathToTokenizer = "D:\\github\\langchat\\model\\reranker\\bge-reranker-large\\tokenizer.json";
//        ScoringModel model = new OnnxScoringModel(pathToModel, new OrtSession.SessionOptions(), pathToTokenizer, 510,  true);

        String pathToModel = "D:\\github\\langchat\\model\\reranker\\bge-reranker-v2-m3\\onnx\\model.onnx";
        String pathToTokenizer = "D:\\github\\langchat\\model\\reranker\\bge-reranker-v2-m3\\tokenizer.json";
        ScoringModel model = new OnnxScoringModel(pathToModel, new OrtSession.SessionOptions(), pathToTokenizer, 8000,  true);

        String text = "The giant panda (Ailuropoda melanoleuca), sometimes called a panda bear or simply panda, is a bear species endemic to China.";
        String query = "What is panda?";

        // when
        Response<Double> response = model.score(text, query);

        // then
        assertThat(response.content()).isCloseTo(0.99, withPercentage(1));

        assertThat(response.tokenUsage().totalTokenCount()).isEqualTo(39);

        assertThat(response.finishReason()).isNull();
    }

    @Test
    void should_score_multiple_segments_with_all_parameters() {

        String pathToModel = "D:\\github\\langchat\\model\\reranker\\bge-reranker-v2-m3\\onnx\\model.onnx";
        String pathToTokenizer = "D:\\github\\langchat\\model\\reranker\\bge-reranker-v2-m3\\tokenizer.json";

        ScoringModel model = new OnnxScoringModel(pathToModel, new OrtSession.SessionOptions(), pathToTokenizer, 8000,  true);

        TextSegment segment1 = TextSegment.from("hi");
        TextSegment segment2 = TextSegment.from("The giant panda (Ailuropoda melanoleuca), sometimes called a panda bear or simply panda, is a bear species endemic to China.");
        List<TextSegment> segments = asList(segment1, segment2);

        String query = "What is panda?";

        // when
        Response<List<Double>> response = model.scoreAll(segments, query);

        // then
        List<Double> scores = response.content();
        assertThat(scores).hasSize(2);
        assertThat(scores.get(0)).isLessThan(scores.get(1));

        assertThat(response.tokenUsage().totalTokenCount()).isGreaterThan(0);

        assertThat(response.finishReason()).isNull();
    }
}