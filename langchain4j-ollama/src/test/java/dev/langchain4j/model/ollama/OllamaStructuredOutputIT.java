package dev.langchain4j.model.ollama;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.chat.TestStreamingResponseHandler;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.ResponseFormatType;
import dev.langchain4j.model.chat.request.json.JsonArraySchema;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchema;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.language.LanguageModel;
import dev.langchain4j.model.language.StreamingLanguageModel;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static dev.langchain4j.data.message.UserMessage.userMessage;
import static dev.langchain4j.model.ollama.AbstractOllamaLanguageModelInfrastructure.ollamaBaseUrl;
import static dev.langchain4j.model.ollama.OllamaImage.LLAMA_3_1;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OllamaStructuredOutputIT extends AbstractOllamaStructuredOutputLanguageModelInfrastructure {

    JsonObjectSchema schema = JsonObjectSchema.builder()
            .addProperty("name", JsonStringSchema.builder().build())
            .addProperty("capital", JsonStringSchema.builder().build())
            .addProperty(
                    "languages",
                    JsonArraySchema.builder()
                            .items(JsonStringSchema.builder().build())
                            .build())
            .required("name", "capital", "languages")
            .build();

    record CountryInfo(String name, String capital, List<String> languages) {
    }

    @Test
    void should_generate_structured_output_using_chat_request_api() {
        // given
        ChatLanguageModel ollamaChatModel = OllamaChatModel.builder()
                .baseUrl(ollamaBaseUrl(ollama))
                .modelName(LLAMA_3_1)
                .temperature(0.0)
                .build();

        // when
        final ChatResponse chatResponse = ollamaChatModel.chat(ChatRequest.builder()
                .messages(userMessage("Tell me about Canada."))
                .responseFormat(ResponseFormat.builder()
                        .type(ResponseFormatType.JSON)
                        .jsonSchema(JsonSchema.builder().rootElement(schema).build())
                        .build())
                .build());

        final String response = chatResponse.aiMessage().text();

        // then
        CountryInfo countryInfo = OllamaJsonUtils.toObject(response, CountryInfo.class);

        assertThat(countryInfo.name()).isEqualTo("Canada");
        assertThat(countryInfo.capital()).isEqualTo("Ottawa");
        assertThat(countryInfo.languages()).contains("English", "French");
    }

    @Test
    void should_generate_structured_output_using_response_format() {

        // given
        ChatLanguageModel ollamaChatModel = OllamaChatModel.builder()
                .baseUrl(ollamaBaseUrl(ollama))
                .modelName(LLAMA_3_1)
                .temperature(0.0)
                .responseFormat(ResponseFormat.builder()
                        .type(ResponseFormatType.JSON)
                        .jsonSchema(JsonSchema.builder().rootElement(schema).build())
                        .build())
                .logRequests(true)
                .logResponses(true)
                .build();

        // when
        final Response<AiMessage> chatResponse = ollamaChatModel.generate(UserMessage.from("Tell me about Canada."));
        final String response = chatResponse.content().text();

        // then
        CountryInfo countryInfo = OllamaJsonUtils.toObject(response, CountryInfo.class);

        assertThat(countryInfo.name()).isEqualTo("Canada");
        assertThat(countryInfo.capital()).isEqualTo("Ottawa");
        assertThat(countryInfo.languages()).contains("English", "French");
    }

    @Test
    void should_generate_structured_output_using_response_format_streaming() throws Exception {

        // given
        StreamingChatLanguageModel streamingOllamaChatModelWithResponseFormat = OllamaStreamingChatModel.builder()
                .baseUrl(ollamaBaseUrl(ollama))
                .modelName(LLAMA_3_1)
                .temperature(0.0)
                .responseFormat(ResponseFormat.builder()
                        .type(ResponseFormatType.JSON)
                        .jsonSchema(JsonSchema.builder().rootElement(schema).build())
                        .build())
                .logRequests(true)
                .logResponses(true)
                .build();

        // when
        CompletableFuture<Response<AiMessage>> secondFutureResponse = new CompletableFuture<>();
        streamingOllamaChatModelWithResponseFormat.generate("Tell me about Canada.", new StreamingResponseHandler<>() {

            @Override
            public void onNext(String token) {
            }

            @Override
            public void onComplete(Response<AiMessage> response) {
                secondFutureResponse.complete(response);
            }

            @Override
            public void onError(Throwable error) {
                secondFutureResponse.completeExceptionally(error);
            }
        });

        // then
        Response<AiMessage> secondResponse = secondFutureResponse.get(30, SECONDS);
        AiMessage secondAiMessage = secondResponse.content();
        CountryInfo countryInfo = OllamaJsonUtils.toObject(secondAiMessage.text(), CountryInfo.class);

        assertThat(countryInfo.name()).isEqualTo("Canada");
        assertThat(countryInfo.capital()).isEqualTo("Ottawa");
        assertThat(countryInfo.languages()).contains("English", "French");
    }

    @Test
    void should_throw_exception_when_both_format_parameters_are_set_for_ollama_chat_model() {
        assertThatThrownBy(() -> OllamaChatModel.builder()
                .format("json")
                .responseFormat(ResponseFormat.JSON)
                .build())
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void should_throw_exception_when_both_format_parameters_are_set_for_ollama_streaming_chat_model() {
        assertThatThrownBy(() -> OllamaStreamingChatModel.builder()
                .format("json")
                .responseFormat(ResponseFormat.JSON)
                .build())
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void language_model_should_generate_structured_output() {
        // given
        final LanguageModel languageModel = OllamaLanguageModel.builder()
                .baseUrl(ollamaBaseUrl(ollama))
                .modelName(LLAMA_3_1)
                .temperature(0.0)
                .responseFormat(ResponseFormat.builder()
                        .type(ResponseFormatType.JSON)
                        .jsonSchema(JsonSchema.builder().rootElement(schema).build())
                        .build())
                .logRequests(true)
                .logResponses(true)
                .build();

        // when
        final Response<String> generated = languageModel.generate("Tell me about Canada.");

        // then
        CountryInfo countryInfo = OllamaJsonUtils.toObject(generated.content(), CountryInfo.class);

        assertThat(countryInfo.name()).isEqualTo("Canada");
        assertThat(countryInfo.capital()).isEqualTo("Ottawa");
        assertThat(countryInfo.languages()).contains("English", "French");
    }

    @Test
    void streaming_language_model_should_generate_structured_output() {
        // given
        final StreamingLanguageModel languageModel = OllamaStreamingLanguageModel.builder()
                .baseUrl(ollamaBaseUrl(ollama))
                .modelName(LLAMA_3_1)
                .temperature(0.0)
                .responseFormat(ResponseFormat.builder()
                        .type(ResponseFormatType.JSON)
                        .jsonSchema(JsonSchema.builder().rootElement(schema).build())
                        .build())
                .logRequests(true)
                .logResponses(true)
                .build();

        TestStreamingResponseHandler<String> handler = new TestStreamingResponseHandler<>();

        // when
        languageModel.generate("Tell me about Canada.", handler);
        final Response<String> generated = handler.get();

        CountryInfo countryInfo = OllamaJsonUtils.toObject(generated.content(), CountryInfo.class);

        assertThat(countryInfo.name()).isEqualTo("Canada");
        assertThat(countryInfo.capital()).isEqualTo("Ottawa");
        assertThat(countryInfo.languages()).contains("English", "French");
    }
}
