package dev.langchain4j.model.scoring.onnx;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.scoring.listener.ScoringModelListener;
import dev.langchain4j.model.scoring.listener.ScoringModelRequestContext;
import dev.langchain4j.model.scoring.listener.ScoringModelResponseContext;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;

class InProcessScoringModelListenerTest {

    @Test
    void should_notify_listeners_when_scoring_segments() {
        OnnxScoringBertCrossEncoder encoder = mock(OnnxScoringBertCrossEncoder.class);
        when(encoder.scoreAll("query", List.of("first", "second")))
                .thenReturn(new OnnxScoringBertCrossEncoder.ScoringAndTokenCount(List.of(0.9, 0.1), 7));
        List<Object> notified = new CopyOnWriteArrayList<>();
        ScoringModelListener listener = new ScoringModelListener() {

            @Override
            public void onRequest(ScoringModelRequestContext requestContext) {
                notified.add(requestContext);
            }

            @Override
            public void onResponse(ScoringModelResponseContext responseContext) {
                notified.add(responseContext);
            }
        };
        AbstractInProcessScoringModel model = new AbstractInProcessScoringModel() {

            @Override
            protected OnnxScoringBertCrossEncoder model() {
                return encoder;
            }

            @Override
            public List<ScoringModelListener> listeners() {
                return List.of(listener);
            }
        };

        var response = model.scoreAll(List.of(TextSegment.from("first"), TextSegment.from("second")), "query");

        assertThat(response.content()).containsExactly(0.9, 0.1);
        assertThat(response.tokenUsage().inputTokenCount()).isEqualTo(7);
        assertThat(notified).hasExactlyElementsOfTypes(ScoringModelRequestContext.class, ScoringModelResponseContext.class);
    }
}
