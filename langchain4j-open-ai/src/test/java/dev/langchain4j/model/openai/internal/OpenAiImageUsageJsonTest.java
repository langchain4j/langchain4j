package dev.langchain4j.model.openai.internal;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.model.openai.internal.image.GenerateImagesResponse;
import dev.langchain4j.model.openai.internal.image.ImageUsage;
import org.junit.jupiter.api.Test;

class OpenAiImageUsageJsonTest {

    @Test
    void should_deserialize_image_token_details() {
        String json = """
                {
                  "usage": {
                    "input_tokens": 10,
                    "output_tokens": 20,
                    "total_tokens": 30,
                    "input_tokens_details": {"image_tokens": 7, "text_tokens": 3},
                    "output_tokens_details": {"image_tokens": 18, "text_tokens": 2}
                  }
                }
                """;

        GenerateImagesResponse response = Json.fromJson(json, GenerateImagesResponse.class);

        assertThat(response.usage())
                .isEqualTo(ImageUsage.builder()
                        .inputTokens(10)
                        .outputTokens(20)
                        .totalTokens(30)
                        .inputTokensDetails(ImageUsage.TokensDetails.builder()
                                .imageTokens(7)
                                .textTokens(3)
                                .build())
                        .outputTokensDetails(ImageUsage.TokensDetails.builder()
                                .imageTokens(18)
                                .textTokens(2)
                                .build())
                        .build());
    }
}
