package dev.langchain4j.model.ollama;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.ResponseFormatType;
import dev.langchain4j.model.chat.request.json.JsonArraySchema;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchema;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import static dev.langchain4j.data.message.UserMessage.userMessage;
import static dev.langchain4j.model.ollama.OllamaImage.TOOL_MODEL;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

class OllamaStructuredOutputIT extends AbstractOllamaStructuredOutputLanguageModelInfrastructure {

    JsonObjectSchema schema = JsonObjectSchema.builder()
            .addProperty("name", JsonStringSchema.builder().build())
            .addProperty("capital", JsonStringSchema.builder().build())
            .addProperty("languages", JsonArraySchema.builder().items(JsonStringSchema.builder().build()).build())
            .required("name", "capital", "languages")
            .build();

    ChatLanguageModel ollamaChatModel = OllamaChatModel.builder()
            .baseUrl(ollamaBaseUrl())
            .modelName(TOOL_MODEL)
            .temperature(0.0)
            .build();

    StreamingChatLanguageModel streamingOllamaChatModel = OllamaStreamingChatModel.builder()
            .baseUrl(ollamaBaseUrl())
            .modelName(TOOL_MODEL)
            .temperature(0.0)
            .format(ResponseFormat.builder().type(ResponseFormatType.JSON)
                    .jsonSchema(JsonSchema.builder().rootElement(schema).build())
                    .build())
            .logRequests(true)
            .logResponses(true)
            .build();

    record CountryInfo(String name, String capital, List<String> languages) {
    }

    @Test
    void should_generate_structured_output_using_chat_request_api() {

        // when
        final ChatResponse chatResponse = ollamaChatModel.chat(ChatRequest.builder()
                .messages(userMessage("Tell me about Canada."))
                .responseFormat(ResponseFormat.builder()
                        .type(ResponseFormatType.JSON)
                        .jsonSchema(JsonSchema.builder()
                                .rootElement(schema)
                                .build())
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
    void should_generate_structured_output_streaming() throws ExecutionException, InterruptedException, TimeoutException {

        // when
        CompletableFuture<Response<AiMessage>> secondFutureResponse = new CompletableFuture<>();
        streamingOllamaChatModel.generate("Tell me about Canada.", new StreamingResponseHandler<>() {

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
}
