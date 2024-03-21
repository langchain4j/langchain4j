package dev.langchain4j.model.ollama;

import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.chat.TestStreamingResponseHandler;
import dev.langchain4j.model.language.StreamingLanguageModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

class OllamaStreamingLanguageModelIT extends AbstractOllamaLanguageModelInfrastructure {

    @Test
    void should_stream_answer() {

        // given
        String prompt = "What is the capital of Germany?";

        StreamingLanguageModel model = OllamaStreamingLanguageModel.builder()
                .baseUrl(ollama.getEndpoint())
                .modelName(OllamaImage.TINY_DOLPHIN_MODEL)
                .temperature(0.0)
                .build();

        // when
        TestStreamingResponseHandler<String> handler = new TestStreamingResponseHandler<>();
        model.generate(prompt, handler);
        Response<String> response = handler.get();
        String answer = response.content();

        // then
        assertThat(answer).contains("Berlin");
        assertThat(response.content()).isEqualTo(answer);

        TokenUsage tokenUsage = response.tokenUsage();
        assertThat(tokenUsage.inputTokenCount()).isEqualTo(13);
        assertThat(tokenUsage.outputTokenCount()).isGreaterThan(0);
        assertThat(tokenUsage.totalTokenCount())
                .isEqualTo(tokenUsage.inputTokenCount() + tokenUsage.outputTokenCount());

        assertThat(response.finishReason()).isNull();
    }

    @Test
    void should_respect_numPredict() {

        // given
        int numPredict = 1; // max output tokens

        StreamingLanguageModel model = OllamaStreamingLanguageModel.builder()
                .baseUrl(ollama.getEndpoint())
                .modelName(OllamaImage.TINY_DOLPHIN_MODEL)
                .numPredict(numPredict)
                .temperature(0.0)
                .build();

        String prompt = "What is the capital of Germany?";

        // when
        TestStreamingResponseHandler<String> handler = new TestStreamingResponseHandler<>();
        model.generate(prompt, handler);
        Response<String> response = handler.get();
        String answer = response.content();

        // then
        assertThat(answer).doesNotContain("Berlin");
        assertThat(response.content()).isEqualTo(answer);

        assertThat(response.tokenUsage().outputTokenCount()).isBetween(numPredict, numPredict + 2); // bug in Ollama
    }

    @Test
    void should_stream_valid_json() {

        // given
        StreamingLanguageModel model = OllamaStreamingLanguageModel.builder()
                .baseUrl(ollama.getEndpoint())
                .modelName(OllamaImage.TINY_DOLPHIN_MODEL)
                .format("json")
                .temperature(0.0)
                .build();

        String prompt = "Return JSON with two fields: name and age of John Doe, 42 years old.";

        // when
        TestStreamingResponseHandler<String> handler = new TestStreamingResponseHandler<>();
        model.generate(prompt, handler);
        Response<String> response = handler.get();
        String answer = response.content();

        // then
        assertThat(answer).isEqualToIgnoringWhitespace("{\"name\": \"John Doe\", \"age\": 42}");
        assertThat(response.content()).isEqualTo(answer);
    }
}
