package dev.langchain4j.model.xai;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.output.FinishReason;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "XAI_API_KEY", matches = ".+")
class XAiStreamingChatModelIT {

    XAiStreamingChatModel model = XAiStreamingChatModel.builder()
            .apiKey(System.getenv("XAI_API_KEY"))
            .modelName(XAiChatModelName.GROK_4_7)
            .store(false)
            .logRequests(true)
            .logResponses(true)
            .build();

    @Test
    void should_stream_answer() throws Exception {
        List<String> partialResponses = new CopyOnWriteArrayList<>();
        CompletableFuture<ChatResponse> future = new CompletableFuture<>();

        model.chat("What is the capital of France? Answer in one word.", new StreamingChatResponseHandler() {
            @Override
            public void onPartialResponse(String partialResponse) {
                partialResponses.add(partialResponse);
            }

            @Override
            public void onCompleteResponse(ChatResponse completeResponse) {
                future.complete(completeResponse);
            }

            @Override
            public void onError(Throwable error) {
                future.completeExceptionally(error);
            }
        });

        ChatResponse response = future.get(120, SECONDS);
        assertThat(String.join("", partialResponses))
                .isEqualTo(response.aiMessage().text());
        assertThat(response.aiMessage().text()).containsIgnoringCase("Paris");
        assertThat(response.finishReason()).isEqualTo(FinishReason.STOP);
    }

    @Test
    void should_stream_tool_call() throws Exception {
        CompletableFuture<ChatResponse> future = new CompletableFuture<>();

        model.chat(
                ChatRequest.builder()
                        .messages(UserMessage.from("What is the weather in Paris?"))
                        .toolSpecifications(ToolSpecification.builder()
                                .name("getWeather")
                                .description("Returns the current weather in the given city")
                                .parameters(JsonObjectSchema.builder()
                                        .addStringProperty("city")
                                        .required("city")
                                        .build())
                                .build())
                        .build(),
                new StreamingChatResponseHandler() {
                    @Override
                    public void onPartialResponse(String partialResponse) {}

                    @Override
                    public void onCompleteResponse(ChatResponse completeResponse) {
                        future.complete(completeResponse);
                    }

                    @Override
                    public void onError(Throwable error) {
                        future.completeExceptionally(error);
                    }
                });

        ChatResponse response = future.get(120, SECONDS);
        assertThat(response.aiMessage().toolExecutionRequests()).hasSize(1);
        assertThat(response.aiMessage().toolExecutionRequests().get(0).name()).isEqualTo("getWeather");
        assertThat(response.finishReason()).isEqualTo(FinishReason.TOOL_EXECUTION);
    }
}
