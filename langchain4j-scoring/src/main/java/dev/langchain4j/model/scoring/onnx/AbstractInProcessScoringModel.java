package dev.langchain4j.model.scoring.onnx;

import ai.onnxruntime.OrtSession;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.model.scoring.ScoringModel;

import java.util.List;
import java.util.stream.Collectors;

public abstract class AbstractInProcessScoringModel implements ScoringModel {

    public AbstractInProcessScoringModel() {
    }

    static OnnxScoringBertBiEncoder loadFromFileSystem(String pathToModel, OrtSession.SessionOptions option, String pathToTokenizer, int modelMaxLength, boolean normalize) {
        try {
            return new OnnxScoringBertBiEncoder(pathToModel, option, pathToTokenizer, modelMaxLength, normalize);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    protected abstract OnnxScoringBertBiEncoder model();

    public Response<List<Double>> scoreAll(List<TextSegment> segments, String query) {
        OnnxScoringBertBiEncoder.ScoringAndTokenCount embeddingAndTokenCount = this.model().scoreAll(query,
                segments.stream().map(TextSegment::text).collect(Collectors.toList()));
        return Response.from(embeddingAndTokenCount.scores, new TokenUsage(embeddingAndTokenCount.tokenCount));
    }
}
