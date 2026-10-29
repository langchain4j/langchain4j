package dev.langchain4j.model.openai;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.image.Image;
import dev.langchain4j.http.client.MockHttpClient;
import dev.langchain4j.http.client.MockHttpClientBuilder;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import dev.langchain4j.model.output.Response;
import java.util.List;
import org.junit.jupiter.api.Test;

class OpenAiImageModelTokenUsageTest {

    private static final OpenAiImageTokenUsage EXPECTED_TOKEN_USAGE = OpenAiImageTokenUsage.builder()
            .inputTokenCount(10)
            .inputTokensDetails(OpenAiImageTokenUsage.TokensDetails.builder()
                    .imageTokens(7)
                    .textTokens(3)
                    .build())
            .outputTokenCount(20)
            .outputTokensDetails(OpenAiImageTokenUsage.TokensDetails.builder()
                    .imageTokens(18)
                    .textTokens(2)
                    .build())
            .totalTokenCount(30)
            .build();

    @Test
    void should_return_token_usage_with_image_and_text_details() {
        OpenAiImageModel model = modelRespondingWith(
                """
                {
                  "created": 1,
                  "data": [{"b64_json": "aW1hZ2U="}],
                  "usage": {
                    "input_tokens": 10,
                    "output_tokens": 20,
                    "total_tokens": 30,
                    "input_tokens_details": {"image_tokens": 7, "text_tokens": 3},
                    "output_tokens_details": {"image_tokens": 18, "text_tokens": 2}
                  }
                }
                """);

        Response<Image> response = model.generate("a cat");

        assertThat(response.tokenUsage()).isEqualTo(EXPECTED_TOKEN_USAGE);
    }

    @Test
    void should_return_token_usage_when_generating_multiple_images() {
        OpenAiImageModel model = modelRespondingWith(
                """
                {
                  "created": 1,
                  "data": [{"b64_json": "aW1hZ2U="}, {"b64_json": "aW1hZ2U="}],
                  "usage": {
                    "input_tokens": 10,
                    "output_tokens": 20,
                    "total_tokens": 30,
                    "input_tokens_details": {"image_tokens": 7, "text_tokens": 3},
                    "output_tokens_details": {"image_tokens": 18, "text_tokens": 2}
                  }
                }
                """);

        Response<List<Image>> response = model.generate("a cat", 2);

        assertThat(response.content()).hasSize(2);
        assertThat(response.tokenUsage()).isEqualTo(EXPECTED_TOKEN_USAGE);
    }

    @Test
    void should_return_null_token_usage_when_model_does_not_report_usage() {
        // dall-e models do not return usage
        OpenAiImageModel model = modelRespondingWith(
                """
                {
                  "created": 1,
                  "data": [{"url": "https://example.com/image.png"}]
                }
                """);

        Response<Image> response = model.generate("a cat");

        assertThat(response.tokenUsage()).isNull();
    }

    private static OpenAiImageModel modelRespondingWith(String body) {
        MockHttpClient mockHttpClient = MockHttpClient.thatAlwaysResponds(
                SuccessfulHttpResponse.builder().statusCode(200).body(body).build());
        return OpenAiImageModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(mockHttpClient))
                .apiKey("banana")
                .modelName("gpt-image-1")
                .build();
    }
}
