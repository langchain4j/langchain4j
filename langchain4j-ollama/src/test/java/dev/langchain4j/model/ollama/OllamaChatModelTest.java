package dev.langchain4j.model.ollama;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class OllamaChatModelTest {

    @Test
    void default_request_parameters_should_preserve_all_ollama_specific_parameters() {
        OllamaChatModel model = OllamaChatModel.builder()
                .baseUrl("http://localhost:11434")
                .modelName("llama3")
                .defaultRequestParameters(OllamaChatRequestParameters.builder()
                        .mirostat(1)
                        .mirostatEta(0.1)
                        .mirostatTau(5.0)
                        .numCtx(2048)
                        .numThread(4)
                        .numKeep(5)
                        .typicalP(0.8)
                        .numBatch(16)
                        .numGPU(1)
                        .mainGPU(0)
                        .useMmap(true)
                        .repeatLastN(64)
                        .repeatPenalty(1.1)
                        .seed(42)
                        .minP(0.05)
                        .keepAlive(300)
                        .think(true)
                        .build())
                .build();

        OllamaChatRequestParameters parameters = model.defaultRequestParameters();

        // Already-copied parameters (guard against regressions)
        assertThat(parameters.mirostat()).isEqualTo(1);
        assertThat(parameters.mirostatEta()).isEqualTo(0.1);
        assertThat(parameters.mirostatTau()).isEqualTo(5.0);
        assertThat(parameters.numCtx()).isEqualTo(2048);
        assertThat(parameters.repeatLastN()).isEqualTo(64);
        assertThat(parameters.repeatPenalty()).isEqualTo(1.1);
        assertThat(parameters.seed()).isEqualTo(42);
        assertThat(parameters.minP()).isEqualTo(0.05);
        assertThat(parameters.keepAlive()).isEqualTo(300);
        assertThat(parameters.think()).isTrue();

        // Parameters that were previously dropped by init()
        assertThat(parameters.numThread()).isEqualTo(4);
        assertThat(parameters.numKeep()).isEqualTo(5);
        assertThat(parameters.typicalP()).isEqualTo(0.8);
        assertThat(parameters.numBatch()).isEqualTo(16);
        assertThat(parameters.numGPU()).isEqualTo(1);
        assertThat(parameters.mainGPU()).isEqualTo(0);
        assertThat(parameters.useMmap()).isTrue();
    }
}
