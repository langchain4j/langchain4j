package dev.langchain4j.model.embedding;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.DisabledModelTest;
import org.junit.jupiter.api.Test;

import java.util.Collections;

class DisabledEmbeddingModelTest extends DisabledModelTest<EmbeddingModel> {
    private EmbeddingModel model = new DisabledEmbeddingModel();

    public DisabledEmbeddingModelTest() {
        super(EmbeddingModel.class);
    }

    @Test
    void methodsShouldThrowException() {
        performAssertion(() -> this.model.embed("Hello"));
        performAssertion(() -> this.model.embed((TextSegment) null));
        performAssertion(() -> this.model.embedAll(Collections.emptyList()));
        performAssertion(() -> this.model.dimension());
    }
}