package dev.langchain4j.service;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.model.chat.mock.ChatModelMock;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.junit.jupiter.api.Test;

/**
 * Verifies that AI Service methods and {@code @Tool} methods support single-value asynchronous return types
 * beyond {@link CompletableFuture}: {@link CompletionStage} (handled natively) and any type plugged in via the
 * {@link dev.langchain4j.spi.services.CompletableFutureAdapter} SPI — exercised here by the test-only
 * {@link FutureBox} type (see {@link FutureBoxAdapter}).
 */
class AsyncReturnTypeAdapterTest {

    private static ToolExecutionRequest temperatureToolRequest() {
        return ToolExecutionRequest.builder()
                .id("1")
                .name("currentTemperature")
                .arguments("{}")
                .build();
    }

    // ===== CompletionStage (handled natively) =====

    interface CompletionStageAssistant {

        CompletionStage<String> chat(String message);
    }

    @Test
    void ai_service_returns_completion_stage() throws Exception {
        CompletionStageAssistant assistant = AiServices.builder(CompletionStageAssistant.class)
                .chatModel(ChatModelMock.thatAlwaysResponds("Berlin"))
                .build();

        CompletionStage<String> stage = assistant.chat("What is the capital of Germany?");

        assertThat(stage.toCompletableFuture().get(10, SECONDS)).isEqualTo("Berlin");
    }

    static class CompletionStageTools {

        @Tool
        CompletionStage<String> currentTemperature() {
            return CompletableFuture.completedFuture("42");
        }
    }

    interface AsyncAssistant {

        CompletableFuture<String> chat(String message);
    }

    @Test
    void async_ai_service_executes_completion_stage_returning_tool() throws Exception {
        ChatModelMock chatModel = ChatModelMock.thatAlwaysResponds(
                AiMessage.from(temperatureToolRequest()), AiMessage.from("It is 42 degrees"));

        AsyncAssistant assistant = AiServices.builder(AsyncAssistant.class)
                .chatModel(chatModel)
                .tools(new CompletionStageTools())
                .build();

        assertThat(assistant.chat("What is the temperature?").get(10, SECONDS)).isEqualTo("It is 42 degrees");
        assertThat(chatModel.requests().get(1).messages())
                .filteredOn(message -> message instanceof ToolExecutionResultMessage)
                .singleElement()
                .satisfies(message -> assertThat(((ToolExecutionResultMessage) message).text())
                        .isEqualTo("42"));
    }

    interface SyncAssistant {

        String chat(String message);
    }

    @Test
    void sync_ai_service_joins_completion_stage_returning_tool() {
        ChatModelMock chatModel = ChatModelMock.thatAlwaysResponds(
                AiMessage.from(temperatureToolRequest()), AiMessage.from("It is 42 degrees"));

        SyncAssistant assistant = AiServices.builder(SyncAssistant.class)
                .chatModel(chatModel)
                .tools(new CompletionStageTools())
                .build();

        assertThat(assistant.chat("What is the temperature?")).isEqualTo("It is 42 degrees");
    }

    // ===== Custom async type via the CompletableFutureAdapter SPI (FutureBoxAdapter) =====

    interface BoxAssistant {

        FutureBox<String> chat(String message);
    }

    @Test
    void ai_service_returns_custom_async_type_via_spi() throws Exception {
        BoxAssistant assistant = AiServices.builder(BoxAssistant.class)
                .chatModel(ChatModelMock.thatAlwaysResponds("Berlin"))
                .build();

        FutureBox<String> box = assistant.chat("What is the capital of Germany?");

        assertThat(box.future().get(10, SECONDS)).isEqualTo("Berlin");
    }

    static class BoxTools {

        @Tool
        FutureBox<String> currentTemperature() {
            return new FutureBox<>(CompletableFuture.completedFuture("42"));
        }
    }

    @Test
    void async_ai_service_executes_custom_async_type_returning_tool_via_spi() throws Exception {
        ChatModelMock chatModel = ChatModelMock.thatAlwaysResponds(
                AiMessage.from(temperatureToolRequest()), AiMessage.from("It is 42 degrees"));

        AsyncAssistant assistant = AiServices.builder(AsyncAssistant.class)
                .chatModel(chatModel)
                .tools(new BoxTools())
                .build();

        assertThat(assistant.chat("What is the temperature?").get(10, SECONDS)).isEqualTo("It is 42 degrees");
        assertThat(chatModel.requests().get(1).messages())
                .filteredOn(message -> message instanceof ToolExecutionResultMessage)
                .singleElement()
                .satisfies(message -> assertThat(((ToolExecutionResultMessage) message).text())
                        .isEqualTo("42"));
    }
}
