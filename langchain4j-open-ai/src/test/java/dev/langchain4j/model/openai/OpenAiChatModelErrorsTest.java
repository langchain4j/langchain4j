package dev.langchain4j.model.openai;

import static dev.langchain4j.model.openai.OpenAiChatModelName.GPT_4_O_MINI;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.model.chat.common.AbstractChatModelErrorsTest;
import io.ktor.http.HttpStatusCode;
import java.time.Duration;
import java.util.Random;
import me.kpavlov.aimocks.core.AbstractBuildingStep;
import me.kpavlov.aimocks.openai.MockOpenai;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class OpenAiChatModelErrorsTest extends AbstractChatModelErrorsTest<OpenAiChatModel, MockOpenai> {

    OpenAiChatModelErrorsTest() {
        super(new MockOpenai());
    }

    @Override
    protected OpenAiChatModel createModel(final double temperature, @Nullable final Duration timeout) {
        final var modelBuilder = OpenAiChatModel.builder()
                .baseUrl(mock.baseUrl())
                .modelName(GPT_4_O_MINI)
                .maxRetries(0)
                .logRequests(true)
                .logResponses(true);
        if (timeout != null) {
            modelBuilder.timeout(timeout);
        }
        return modelBuilder.build();
    }

    @Override
    protected AbstractBuildingStep<?, ?> whenMockMatched(final String question, final double temperature) {
        return mock.completion(req -> {
            req.userMessageContains(question);
            req.temperature(temperature);
        });
    }

    @Test
    void should_handle_refusal() {

        // given
        double temperature = 0.1 + new Random().nextDouble() * 0.9;

        final var userMessage = "does not matter";
        mock.completion(req -> req.userMessageContains(userMessage)).respondsError(res -> {
            res.setHttpStatus(HttpStatusCode.Companion.fromValue(200));
            // copied from https://platform.openai.com/docs/guides/structured-outputs/refusals?api-mode=chat#refusals
            // language=json
            res.setBody(
                    """
                    {
                      "id": "chatcmpl-9nYAG9LPNonX8DAyrkwYfemr3C8HC",
                      "object": "chat.completion",
                      "created": 1721596428,
                      "model": "gpt-4o-2024-08-06",
                      "choices": [
                        {
                          "index": 0,
                          "message": {
                            "role": "assistant",
                            "refusal": "I'm sorry, I cannot assist with that request."
                          },
                          "logprobs": null,
                          "finish_reason": "stop"
                        }
                      ],
                      "usage": {
                        "prompt_tokens": 81,
                        "completion_tokens": 11,
                        "total_tokens": 92,
                        "completion_tokens_details": {
                          "reasoning_tokens": 0,
                          "accepted_prediction_tokens": 0,
                          "rejected_prediction_tokens": 0
                        }
                      },
                      "system_fingerprint": "fp_3407719c7f"
                    }
                    """);
        });

        final var model = createModel(temperature, null);

        // when-then
        assertThatThrownBy(() -> model.chat(userMessage))
                .isExactlyInstanceOf(dev.langchain4j.exception.ContentFilteredException.class)
                .hasMessage("I'm sorry, I cannot assist with that request.");
    }
}
