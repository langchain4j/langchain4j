package dev.langchain4j.model.googleai;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class GeminiGenerationConfigTest {

    @Nested
    class Builder {

        @Test
        void settingSeedProducesConfigWithSeed() {
            GeminiGenerationConfig result =
                    GeminiGenerationConfig.builder().seed(42).build();

            assertThat(result.getSeed()).isEqualTo(42);
        }

        @Test
        void defaultValues() {
            GeminiGenerationConfig result = GeminiGenerationConfig.builder().build();

            assertThat(result.getSeed()).isNull();
        }
    }
}
