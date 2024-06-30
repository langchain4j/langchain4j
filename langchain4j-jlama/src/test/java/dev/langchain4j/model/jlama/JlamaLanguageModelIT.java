package dev.langchain4j.model.jlama;

import dev.langchain4j.model.language.LanguageModel;
import dev.langchain4j.model.output.Response;
import org.assertj.core.util.Files;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;

import static dev.langchain4j.model.output.FinishReason.LENGTH;
import static org.assertj.core.api.Assertions.assertThat;

public class JlamaLanguageModelIT {

    static File tmpDir;

    static LanguageModel model;


    @BeforeAll
    static void setup() {
        tmpDir = Files.newTemporaryFolder();

        model = JlamaLanguageModel.builder()
                .modelName("openai-community/gpt2")
                .modelCachePath(tmpDir.toPath())
                .maxTokens(10)
                .build();
    }

    @Test
    void should_send_prompt_and_return_response() {

        // given
        String prompt = "hello";

        // when
        Response<String> response = model.generate(prompt);
        System.out.println(response);

        // then
        assertThat(response.content()).isNotBlank();

        assertThat(response.tokenUsage()).isNotNull();
        assertThat(response.finishReason()).isEqualTo(LENGTH);
    }
}
