package dev.langchain4j.model.scoring.onnx;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class OnnxScoringBertCrossEncoderTest {

    @Test
    void extractLogits_should_handle_2d_output() {
        float[][] output = {{0.1f}, {0.2f}, {0.3f}};

        float[] logits = OnnxScoringBertCrossEncoder.extractLogits(output);

        assertThat(logits).containsExactly(0.1f, 0.2f, 0.3f);
    }

    @Test
    void extractLogits_should_handle_3d_output_from_bge_reranker() {
        // BAAI/bge-reranker-base exported to ONNX (e.g. via Optimum) produces logits with shape
        // [batch, 1, 1] (float[][][] / "[[[F"), which previously triggered:
        //   ClassCastException: class [[[F cannot be cast to class [[F
        float[][][] output = {{{0.5f}}, {{-0.2f}}, {{0.9f}}};

        float[] logits = OnnxScoringBertCrossEncoder.extractLogits(output);

        assertThat(logits).containsExactly(0.5f, -0.2f, 0.9f);
    }

    @Test
    void extractLogits_should_take_first_value_when_multiple_logits_per_item() {
        // historical behaviour: only the first logit per item is used
        float[][] output = {{0.4f, 0.5f, 0.6f}};

        float[] logits = OnnxScoringBertCrossEncoder.extractLogits(output);

        assertThat(logits).containsExactly(0.4f);
    }

    @Test
    void extractLogits_should_throw_on_unsupported_shape() {
        float[] unsupported = {0.1f};

        assertThatThrownBy(() -> OnnxScoringBertCrossEncoder.extractLogits(unsupported))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Unsupported ONNX scoring output shape");
    }
}
