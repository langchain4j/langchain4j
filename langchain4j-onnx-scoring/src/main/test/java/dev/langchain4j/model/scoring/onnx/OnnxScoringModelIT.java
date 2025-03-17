package dev.langchain4j.model.scoring.onnx;

import ai.onnxruntime.OrtSession;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.scoring.ScoringModel;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Percentage.withPercentage;

class OnnxScoringModelIT {

    @TempDir
    private static Path tempDir;

    private static ScoringModel model;

    @BeforeAll
    static void initModel() throws IOException {

        // I set up a local proxy to download the model
        // System.setProperty("https.proxyHost","127.0.0.1" );
        // System.setProperty("https.proxyPort","7890" );

        URL modelUrl = new URL("https://huggingface.co/Xenova/ms-marco-MiniLM-L-6-v2/resolve/main/onnx/model_quantized.onnx?download=true");
        Path modelPath = tempDir.resolve("model_quantized.onnx");
        Files.copy(modelUrl.openStream(), modelPath, REPLACE_EXISTING);

        URL tokenizerUrl = new URL("https://huggingface.co/Xenova/ms-marco-MiniLM-L-6-v2/resolve/main/tokenizer.json?download=true");
        Path tokenizerPath = tempDir.resolve("tokenizer.json");
        Files.copy(tokenizerUrl.openStream(), tokenizerPath, REPLACE_EXISTING);

        // To check the modelMaxLength parameter, refer to the model configuration file at  https://huggingface.co/Xenova/ms-marco-MiniLM-L-6-v2/resolve/main/tokenizer_config.json
        model = new OnnxScoringModel(modelPath.toString(), new OrtSession.SessionOptions(), tokenizerPath.toString(), 512, false);
    }

    @Test
    void should_score_multiple_segments_with_all_parameters() {
        List<TextSegment> segments = new ArrayList<>();
        segments.add(TextSegment.from("Berlin has a population of 3,520,031 registered inhabitants in an area of 891.82 square kilometers."));
        segments.add(TextSegment.from("New York City is famous for the Metropolitan Museum of Art."));

        String query = "How many people live in Berlin?";

        // when
        Response<List<Double>> response = model.scoreAll(segments, query);

        // then
        List<Double> scores = response.content();
        assertThat(scores).hasSize(2);

        // python output results on https://huggingface.co/Xenova/ms-marco-MiniLM-L-6-v2: [ 8.663132667541504, -11.245542526245117 ]
        assertThat(scores.get(0)).isCloseTo(8.663132667541504, withPercentage(0.1));
        assertThat(scores.get(1)).isCloseTo(-11.245542526245117, withPercentage(0.1));

        assertThat(response.tokenUsage().totalTokenCount()).isGreaterThan(0);

        assertThat(response.finishReason()).isNull();
    }
}