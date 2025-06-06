package dev.langchain4j.model.ollama;

import static dev.langchain4j.data.message.UserMessage.userMessage;
import static dev.langchain4j.model.ollama.AbstractOllamaLanguageModelInfrastructure.ollamaBaseUrl;
import static dev.langchain4j.model.ollama.OllamaJsonUtils.fromJson;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.TestStreamingResponseHandler;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.ResponseFormatType;
import dev.langchain4j.model.chat.request.json.JsonArraySchema;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchema;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.language.LanguageModel;
import dev.langchain4j.model.language.StreamingLanguageModel;
import dev.langchain4j.model.output.Response;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

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

    record CountryInfo(String name, String capital, List<String> languages) {}

    @Test
    void should_generate_structured_output_using_chat_request_api() {
        // given
        ChatModel ollamaChatModel = OllamaChatModel.builder()
                .baseUrl(ollamaBaseUrl(ollama))
                .modelName(MODEL_NAME)
                .temperature(0.0)
                .logRequests(true)
                .logResponses(true)
                .build();

        // when
        ChatResponse chatResponse = ollamaChatModel.chat(ChatRequest.builder()
                .messages(userMessage("Tell me about Canada."))
                .responseFormat(ResponseFormat.builder()
                        .type(ResponseFormatType.JSON)
                        .jsonSchema(JsonSchema.builder().rootElement(schema).build())
                        .build())
                .build());

        String response = chatResponse.aiMessage().text();

        // then
        CountryInfo countryInfo = fromJson(response, CountryInfo.class);

        assertThat(countryInfo.name()).isEqualTo("Canada");
        assertThat(countryInfo.capital()).isEqualTo("Ottawa");
        assertThat(countryInfo.languages()).contains("English", "French");
    }

    @Test
    void should_generate_structured_output_using_response_format() {

        // given
        ChatModel ollamaChatModel = OllamaChatModel.builder()
                .baseUrl(ollamaBaseUrl(ollama))
                .modelName(MODEL_NAME)
                .temperature(0.0)
                .responseFormat(ResponseFormat.builder()
                        .type(ResponseFormatType.JSON)
                        .jsonSchema(JsonSchema.builder().rootElement(schema).build())
                        .build())
                .logRequests(true)
                .logResponses(true)
                .build();

        // when
        ChatResponse chatResponse = ollamaChatModel.chat(UserMessage.from("Tell me about Canada."));
        String response = chatResponse.aiMessage().text();

        // then
        CountryInfo countryInfo = fromJson(response, CountryInfo.class);

        assertThat(countryInfo.name()).isEqualTo("Canada");
        assertThat(countryInfo.capital()).isEqualTo("Ottawa");
        assertThat(countryInfo.languages()).contains("English", "French");
    }

    @Test
    void should_generate_structured_output_using_response_format_streaming() throws Exception {

        // given
        StreamingChatModel streamingOllamaChatModelWithResponseFormat = OllamaStreamingChatModel.builder()
                .baseUrl(ollamaBaseUrl(ollama))
                .modelName(MODEL_NAME)
                .temperature(0.0)
                .responseFormat(ResponseFormat.builder()
                        .type(ResponseFormatType.JSON)
                        .jsonSchema(JsonSchema.builder().rootElement(schema).build())
                        .build())
                .logRequests(true)
                .logResponses(true)
                .build();

        // when
        CompletableFuture<ChatResponse> secondFutureResponse = new CompletableFuture<>();
        streamingOllamaChatModelWithResponseFormat.chat("Tell me about Canada.", new StreamingChatResponseHandler() {

            @Override
            public void onPartialResponse(String partialResponse) {}

            @Override
            public void onCompleteResponse(ChatResponse completeResponse) {
                secondFutureResponse.complete(completeResponse);
            }

            @Override
            public void onError(Throwable error) {
                secondFutureResponse.completeExceptionally(error);
            }
        });

        // then
        ChatResponse secondResponse = secondFutureResponse.get(30, SECONDS);
        AiMessage secondAiMessage = secondResponse.aiMessage();
        CountryInfo countryInfo = fromJson(secondAiMessage.text(), CountryInfo.class);

        assertThat(countryInfo.name()).isEqualTo("Canada");
        assertThat(countryInfo.capital()).isEqualTo("Ottawa");
        assertThat(countryInfo.languages()).contains("English", "French");
    }

    @Test
    void language_model_should_generate_structured_output() {
        // given
        LanguageModel languageModel = OllamaLanguageModel.builder()
                .baseUrl(ollamaBaseUrl(ollama))
                .modelName(MODEL_NAME)
                .temperature(0.0)
                .responseFormat(ResponseFormat.builder()
                        .type(ResponseFormatType.JSON)
                        .jsonSchema(JsonSchema.builder().rootElement(schema).build())
                        .build())
                .logRequests(true)
                .logResponses(true)
                .build();

        // when
        Response<String> generated = languageModel.generate("Tell me about Canada.");

        // then
        CountryInfo countryInfo = fromJson(generated.content(), CountryInfo.class);

        assertThat(countryInfo.name()).isEqualTo("Canada");
        assertThat(countryInfo.capital()).isEqualTo("Ottawa");
        assertThat(countryInfo.languages()).contains("English", "French");
    }

    @Test
    void streaming_language_model_should_generate_structured_output() {
        // given
        StreamingLanguageModel languageModel = OllamaStreamingLanguageModel.builder()
                .baseUrl(ollamaBaseUrl(ollama))
                .modelName(MODEL_NAME)
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
        Response<String> generated = handler.get();

        CountryInfo countryInfo = fromJson(generated.content(), CountryInfo.class);

        assertThat(countryInfo.name()).isEqualTo("Canada");
        assertThat(countryInfo.capital()).isEqualTo("Ottawa");
        assertThat(countryInfo.languages()).contains("English", "French");
    }
}
