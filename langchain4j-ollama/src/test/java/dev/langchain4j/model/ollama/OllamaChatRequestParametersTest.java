package dev.langchain4j.model.ollama;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.model.chat.request.ChatRequestParameters;
import org.junit.jupiter.api.Test;

class OllamaChatRequestParametersTest {

    @Test
    void should_build_with_numThread() {
        OllamaChatRequestParameters params =
                OllamaChatRequestParameters.builder().numThread(4).build();

        assertThat(params.numThread()).isEqualTo(4);
    }

    @Test
    void numThread_should_be_null_when_not_set() {
        OllamaChatRequestParameters params =
                OllamaChatRequestParameters.builder().build();

        assertThat(params.numThread()).isNull();
    }

    @Test
    void overrideWith_should_apply_numThread_from_override() {
        OllamaChatRequestParameters original =
                OllamaChatRequestParameters.builder().numThread(2).build();
        OllamaChatRequestParameters override =
                OllamaChatRequestParameters.builder().numThread(8).build();

        ChatRequestParameters result = original.overrideWith(override);

        assertThat(result).isInstanceOf(OllamaChatRequestParameters.class);
        assertThat(((OllamaChatRequestParameters) result).numThread()).isEqualTo(8);
    }

    @Test
    void overrideWith_should_keep_original_numThread_when_override_is_null() {
        OllamaChatRequestParameters original =
                OllamaChatRequestParameters.builder().numThread(4).build();
        OllamaChatRequestParameters override =
                OllamaChatRequestParameters.builder().build();

        ChatRequestParameters result = original.overrideWith(override);

        assertThat(result).isInstanceOf(OllamaChatRequestParameters.class);
        assertThat(((OllamaChatRequestParameters) result).numThread()).isEqualTo(4);
    }

    @Test
    void equals_and_hashCode_should_include_numThread() {
        OllamaChatRequestParameters params1 =
                OllamaChatRequestParameters.builder().numThread(4).build();
        OllamaChatRequestParameters params2 =
                OllamaChatRequestParameters.builder().numThread(4).build();
        OllamaChatRequestParameters params3 =
                OllamaChatRequestParameters.builder().numThread(8).build();

        assertThat(params1).isEqualTo(params2);
        assertThat(params1.hashCode()).isEqualTo(params2.hashCode());
        assertThat(params1).isNotEqualTo(params3);
    }

    @Test
    void toString_should_include_numThread() {
        OllamaChatRequestParameters params =
                OllamaChatRequestParameters.builder().numThread(4).build();

        assertThat(params.toString()).contains("numThread=4");
    }

    @Test
    void should_build_with_new_parameters() {
        OllamaChatRequestParameters params = OllamaChatRequestParameters.builder()
                .numKeep(5)
                .typicalP(0.8)
                .numBatch(16)
                .numGPU(1)
                .mainGPU(0)
                .useMmap(true)
                .build();

        assertThat(params.numKeep()).isEqualTo(5);
        assertThat(params.typicalP()).isEqualTo(0.8);
        assertThat(params.numBatch()).isEqualTo(16);
        assertThat(params.numGPU()).isEqualTo(1);
        assertThat(params.mainGPU()).isEqualTo(0);
        assertThat(params.useMmap()).isTrue();
    }

    @Test
    void new_parameters_should_be_null_when_not_set() {
        OllamaChatRequestParameters params =
                OllamaChatRequestParameters.builder().build();

        assertThat(params.numKeep()).isNull();
        assertThat(params.typicalP()).isNull();
        assertThat(params.numBatch()).isNull();
        assertThat(params.numGPU()).isNull();
        assertThat(params.mainGPU()).isNull();
        assertThat(params.useMmap()).isNull();
    }

    @Test
    void overrideWith_should_apply_new_parameters_from_override() {
        OllamaChatRequestParameters original = OllamaChatRequestParameters.builder()
                .numKeep(5)
                .typicalP(0.8)
                .numBatch(16)
                .numGPU(1)
                .mainGPU(0)
                .useMmap(true)
                .build();
        OllamaChatRequestParameters override = OllamaChatRequestParameters.builder()
                .numKeep(10)
                .typicalP(0.9)
                .numBatch(32)
                .numGPU(2)
                .mainGPU(1)
                .useMmap(false)
                .build();

        ChatRequestParameters result = original.overrideWith(override);

        assertThat(result).isInstanceOf(OllamaChatRequestParameters.class);
        OllamaChatRequestParameters ollamaResult = (OllamaChatRequestParameters) result;
        assertThat(ollamaResult.numKeep()).isEqualTo(10);
        assertThat(ollamaResult.typicalP()).isEqualTo(0.9);
        assertThat(ollamaResult.numBatch()).isEqualTo(32);
        assertThat(ollamaResult.numGPU()).isEqualTo(2);
        assertThat(ollamaResult.mainGPU()).isEqualTo(1);
        assertThat(ollamaResult.useMmap()).isFalse();
    }

    @Test
    void overrideWith_should_keep_original_new_parameters_when_override_is_null() {
        OllamaChatRequestParameters original = OllamaChatRequestParameters.builder()
                .numKeep(5)
                .typicalP(0.8)
                .numBatch(16)
                .numGPU(1)
                .mainGPU(0)
                .useMmap(true)
                .build();
        OllamaChatRequestParameters override =
                OllamaChatRequestParameters.builder().build();

        ChatRequestParameters result = original.overrideWith(override);

        assertThat(result).isInstanceOf(OllamaChatRequestParameters.class);
        OllamaChatRequestParameters ollamaResult = (OllamaChatRequestParameters) result;
        assertThat(ollamaResult.numKeep()).isEqualTo(5);
        assertThat(ollamaResult.typicalP()).isEqualTo(0.8);
        assertThat(ollamaResult.numBatch()).isEqualTo(16);
        assertThat(ollamaResult.numGPU()).isEqualTo(1);
        assertThat(ollamaResult.mainGPU()).isEqualTo(0);
        assertThat(ollamaResult.useMmap()).isTrue();
    }
}
