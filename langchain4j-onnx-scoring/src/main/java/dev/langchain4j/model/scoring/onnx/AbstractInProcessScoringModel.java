package dev.langchain4j.model.scoring.onnx;

import ai.onnxruntime.OrtSession;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.model.scoring.ScoringModel;

import java.util.List;
import java.util.stream.Collectors;

abstract class AbstractInProcessScoringModel implements ScoringModel {

    public AbstractInProcessScoringModel() {
    }

    static OnnxScoringBertCrossEncoder loadFromFileSystem(String pathToModel, OrtSession.SessionOptions options, String pathToTokenizer, int modelMaxLength, boolean normalize) {
        try {
            return new OnnxScoringBertCrossEncoder(pathToModel, options, pathToTokenizer, modelMaxLength, normalize);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    protected abstract OnnxScoringBertCrossEncoder model();

    public Response<List<Double>> scoreAll(List<TextSegment> segments, String query) {
        OnnxScoringBertCrossEncoder.ScoringAndTokenCount scoresAndTokenCount = this.model().scoreAll(query,
                segments.stream().map(TextSegment::text).collect(Collectors.toList()));
        return Response.from(scoresAndTokenCount.scores, new TokenUsage(scoresAndTokenCount.tokenCount));
    }
}
