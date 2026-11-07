package dev.langchain4j.model.jitllm;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.exception.UnsupportedFeatureException;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class JitLLMChatModelBuilderTest {

    private static final Path MODEL_PATH = Path.of("model.gguf");

    @Test
    void should_fail_when_model_path_is_missing() {
        assertThatThrownBy(() -> JitLLMChatModel.builder().build())
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("modelPath");
    }

    @Test
    void should_fail_when_context_length_is_not_positive() {
        assertThatThrownBy(() -> JitLLMStreamingChatModel.builder()
                        .modelPath(MODEL_PATH)
                        .contextLength(0)
                        .build())
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("contextLength");
    }

    @Test
    void should_fail_when_on_gpu_is_requested_without_tornadovm() {
        assertThatThrownBy(() -> JitLLMChatModel.builder()
                        .modelPath(MODEL_PATH)
                        .onGPU(true)
                        .build())
                .isExactlyInstanceOf(IllegalStateException.class)
                .hasMessageContaining("-Duse.tornadovm=true");
    }

    @Test
    void should_fail_when_default_request_parameters_are_not_supported() {
        assertThatThrownBy(() -> JitLLMStreamingChatModel.builder()
                        .modelPath(MODEL_PATH)
                        .defaultRequestParameters(
                                ChatRequestParameters.builder().modelName("other").build())
                        .build())
                .isExactlyInstanceOf(UnsupportedFeatureException.class)
                .hasMessageContaining("modelName");
    }
}
