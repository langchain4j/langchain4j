package dev.langchain4j.model.jlama;

import dev.langchain4j.model.language.LanguageModel;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;

import static dev.langchain4j.model.output.FinishReason.LENGTH;
import static org.assertj.core.api.Assertions.assertThat;

class JlamaLanguageModelIT {

    static File tmpDir;

    static LanguageModel model;


    @BeforeAll
    static void setup() {
        tmpDir = new File(System.getProperty("java.io.tmpdir") + File.separator + "jlama_tests");
        tmpDir.mkdirs();

        model = JlamaLanguageModel.builder()
                .modelName("tjake/Llama-3.2-1B-Instruct-JQ4")
                .modelCachePath(tmpDir.toPath())
                .temperature(0.0f)
                .maxTokens(64)
                .build();
    }

    @Test
    void should_send_prompt_and_return_response() {

        // given
        String prompt = "When is the best time of year to visit Japan?";

        // when
        Response<String> response = model.generate(prompt);

        // then
        assertThat(response.content()).isNotBlank();

        assertThat(response.tokenUsage()).isNotNull();
        assertThat(response.finishReason()).isEqualTo(LENGTH);
    }
}
