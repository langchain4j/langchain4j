package dev.langchain4j.model.ollama;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import org.junit.jupiter.api.Test;

import java.util.List;

import static dev.langchain4j.model.chat.Capability.RESPONSE_FORMAT_JSON_SCHEMA;
import static dev.langchain4j.model.ollama.OllamaImage.TINY_DOLPHIN_MODEL;
import static org.assertj.core.api.Assertions.assertThat;

class OllamaChatModelIT extends AbstractOllamaLanguageModelInfrastructure {

    ChatLanguageModel model = OllamaChatModel.builder()
            .baseUrl(ollamaBaseUrl(ollama))
            .modelName(TINY_DOLPHIN_MODEL)
            .temperature(0.0)
            .logRequests(true)
            .logResponses(true)
            .build();

    // TODO test timeouts?

    public static void main(String[] args) {

//        HttpClientBuilder httpClientBuilder = new Jdk11HttpClientBuilder() // java.net.http.HttpTimeoutException: request timed out / java.net.http.HttpConnectTimeoutException: HTTP connect timed out
//        HttpClientBuilder httpClientBuilder = new OkHttpHttpClientBuilder() // java.net.SocketTimeoutException: timeout / java.net.SocketTimeoutException: Connect timed out
//        HttpClientBuilder httpClientBuilder = new SpringRestClientHttpClientBuilder() // org.springframework.web.client.ResourceAccessException: I/O error on POST request for "http://192.168.178.55:11434/api/chat": timeout / org.springframework.web.client.ResourceAccessException: I/O error on POST request for "http://192.168.0.44:12345/api/chat": Connect timed out
//                .httpClientBuilder(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)))
//                .connectTimeout(Duration.ofSeconds(1))
//                .readTimeout(Duration.ofSeconds(5))
//                .logRequests(true)
//                .logResponses(true);

        ChatLanguageModel model = OllamaChatModel.builder()
//                .httpClientBuilder(httpClientBuilder)
                .baseUrl(ollamaBaseUrl(ollama))
//                .baseUrl("http://192.168.0.44:12345")
                .modelName(TINY_DOLPHIN_MODEL)
                .temperature(0.0)
//                .timeout(Duration.ofSeconds(1))
                .maxRetries(1)
                .logRequests(true)
                .logResponses(true)
                .build();

        String answer = model.chat("tell me a long story");

        System.out.println(answer);
    }

    // TODO add test that HttpException is thrown

    @Test
    void should_generate_response() {

        // given
        UserMessage userMessage = UserMessage.from("What is the capital of Germany?");

        // when
        Response<AiMessage> response = model.generate(userMessage);

        // then
        AiMessage aiMessage = response.content();
        assertThat(aiMessage.text()).contains("Berlin");
        assertThat(aiMessage.toolExecutionRequests()).isNull();

        TokenUsage tokenUsage = response.tokenUsage();
        assertThat(tokenUsage.inputTokenCount()).isGreaterThan(0);
        assertThat(tokenUsage.outputTokenCount()).isGreaterThan(0);
        assertThat(tokenUsage.totalTokenCount())
                .isEqualTo(tokenUsage.inputTokenCount() + tokenUsage.outputTokenCount());

        assertThat(response.finishReason()).isNull();
    }

    @Test
    void should_respect_numPredict() {

        // given
        int numPredict = 1; // max output tokens

        OllamaChatModel model = OllamaChatModel.builder()
                .baseUrl(ollamaBaseUrl(ollama))
                .modelName(TINY_DOLPHIN_MODEL)
                .numPredict(numPredict)
                .temperature(0.0)
                .build();

        UserMessage userMessage = UserMessage.from("What is the capital of Germany?");

        // when
        Response<AiMessage> response = model.generate(userMessage);

        // then
        assertThat(response.content().text()).doesNotContain("Berlin");
        assertThat(response.tokenUsage().outputTokenCount()).isBetween(numPredict, numPredict + 2); // bug in Ollama
    }

    @Test
    void should_respect_system_message() {

        // given
        SystemMessage systemMessage = SystemMessage.from("Translate messages from user into German");
        UserMessage userMessage = UserMessage.from("I love you");

        // when
        Response<AiMessage> response = model.generate(systemMessage, userMessage);

        // then
        assertThat(response.content().text()).containsIgnoringCase("liebe");
    }

    @Test
    void should_respond_to_few_shot() {

        // given
        List<ChatMessage> messages = List.of(
                UserMessage.from("1 + 1 ="),
                AiMessage.from(">>> 2"),
                UserMessage.from("2 + 2 ="),
                AiMessage.from(">>> 4"),
                UserMessage.from("4 + 4 ="));

        // when
        Response<AiMessage> response = model.generate(messages);

        // then
        assertThat(response.content().text()).startsWith(">>> 8");
    }

    @Test
    void should_generate_valid_json() {

        // given
        ChatLanguageModel model = OllamaChatModel.builder()
                .baseUrl(ollamaBaseUrl(ollama))
                .modelName(TINY_DOLPHIN_MODEL)
                .format("json")
                .temperature(0.0)
                .logRequests(true)
                .logResponses(true)
                .build();

        String userMessage = "Return JSON with two fields: name and age of John Doe, 42 years old.";

        // when
        String json = model.generate(userMessage);

        // then
        assertThat(json).isEqualToIgnoringWhitespace("{\"name\": \"John Doe\", \"age\": 42}");
    }

    @Test
    void should_return_set_capabilities() {
        ChatLanguageModel model = OllamaChatModel.builder()
                .baseUrl(ollamaBaseUrl(ollama))
                .modelName(TINY_DOLPHIN_MODEL)
                .supportedCapabilities(RESPONSE_FORMAT_JSON_SCHEMA)
                .build();

        assertThat(model.supportedCapabilities()).contains(RESPONSE_FORMAT_JSON_SCHEMA);
    }
}
