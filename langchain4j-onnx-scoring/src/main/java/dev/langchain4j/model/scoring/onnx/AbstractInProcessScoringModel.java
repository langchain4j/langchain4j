package dev.langchain4j.model.scoring.onnx;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import ai.onnxruntime.OrtSession;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.model.scoring.ScoringModel;
import dev.langchain4j.model.scoring.request.ScoringRequest;
import dev.langchain4j.model.scoring.response.ScoringResponse;
import java.util.List;
import java.util.stream.Collectors;

abstract class AbstractInProcessScoringModel implements ScoringModel, AutoCloseable {

    public AbstractInProcessScoringModel() {}

    static OnnxScoringBertCrossEncoder loadFromFileSystem(
            String pathToModel,
            OrtSession.SessionOptions options,
            String pathToTokenizer,
            int modelMaxLength,
            boolean normalize) {
        try {
            return new OnnxScoringBertCrossEncoder(pathToModel, options, pathToTokenizer, modelMaxLength, normalize);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    protected abstract OnnxScoringBertCrossEncoder model();

    public Response<List<Double>> scoreAll(List<TextSegment> segments, String query) {
        ensureNotNull(segments, "segments");
        ScoringResponse response = score(ScoringRequest.builder()
                .documents(segments.stream().map(TextSegment::text).collect(Collectors.toList()))
                .query(query)
                .build());
        return Response.from(response.scores(), response.tokenUsage());
    }

    @Override
    public ScoringResponse doScore(ScoringRequest request) {
        OnnxScoringBertCrossEncoder.ScoringAndTokenCount scoresAndTokenCount =
                this.model().scoreAll(request.query(), request.documents());
        return ScoringResponse.builder()
                .scores(scoresAndTokenCount.scores)
                .tokenUsage(new TokenUsage(scoresAndTokenCount.tokenCount))
                .build();
    }

    @Override
    public void close() {
        OnnxScoringBertCrossEncoder m = model();
        if (m != null) m.close();
    }
}
