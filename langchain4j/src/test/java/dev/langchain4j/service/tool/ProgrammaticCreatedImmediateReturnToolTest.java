package dev.langchain4j.service.tool;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agent.tool.ReturnBehavior;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.mock.ChatModelMock;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.Result;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

public class ProgrammaticCreatedImmediateReturnToolTest {

    @Test
    void should_execute_dynamic_tool_with_immediate_return() {
        AtomicBoolean toolExecuted = new AtomicBoolean(false);
        AtomicInteger llmCallCount = new AtomicInteger(0);

        // Given
        ToolSpecification tool = ToolSpecification.builder()
                .name("calculate")
                .description("Performs calculation")
                .build();

        ToolExecutor executor = (toolExecutionRequest, memoryId) -> {
            toolExecuted.set(true);
            return "4";
        };

        // Mock model that tracks how many times it's called
        ChatModelMock mockModel = new ChatModelMock(request -> {
            llmCallCount.incrementAndGet();
            return AiMessage.from(ToolExecutionRequest.builder()
                    .id("calc-1")
                    .name("calculate")
                    .arguments("{\"expression\": \"2+2\"}")
                    .build());
        });

        // Using the fluent API with immediate return tool
        ToolProvider provider = request -> ToolProviderResult.builder()
                .add(tool, executor, ReturnBehavior.IMMEDIATE)
                .build();

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(mockModel)
                .toolProvider(provider)
                .build();

        // When
        Result<String> result = assistant.chat("What is 2+2?");

        // Then
        assertThat(toolExecuted.get()).isTrue();
        List<ToolExecution> toolExecutions = result.toolExecutions();
        assertThat(toolExecutions).hasSize(1);
        assertThat(toolExecutions.get(0).result()).isEqualTo("4");
        assertThat(llmCallCount.get()).isEqualTo(1);
        assertThat(result.content()).isNull();
    }

    @Test
    void should_execute_dynamic_tool_without_immediate_return() {

        AtomicBoolean toolExecuted = new AtomicBoolean(false);
        AtomicInteger llmCallCount = new AtomicInteger(0);

        // Given
        ToolSpecification tool = ToolSpecification.builder()
                .name("calculate")
                .description("Performs calculation")
                .build();

        ToolExecutor executor = (toolExecutionRequest, memoryId) -> {
            toolExecuted.set(true);
            return "4";
        };

        // Mock model that tracks calls and returns different responses
        ChatModelMock mockModel = new ChatModelMock(request -> {
            int callNumber = llmCallCount.incrementAndGet();
            if (callNumber == 1) {
                // First call: request tool execution
                return AiMessage.from(ToolExecutionRequest.builder()
                        .id("calc-1")
                        .name("calculate")
                        .arguments("{\"expression\": \"2+2\"}")
                        .build());
            } else {
                // Second call: process tool result and generate final answer
                return AiMessage.from("The calculation result is 4. Therefore, 2+2 equals 4.");
            }
        });

        // Using the fluent API without immediate tool return
        ToolProvider provider =
                request -> ToolProviderResult.builder().add(tool, executor).build();

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(mockModel)
                .toolProvider(provider)
                .build();

        // When
        Result<String> result = assistant.chat("What is 2+2?");

        // Then
        assertThat(toolExecuted.get()).isTrue();
        List<ToolExecution> toolExecutions = result.toolExecutions();
        assertThat(toolExecutions).hasSize(1);
        assertThat(toolExecutions.get(0).result()).isEqualTo("4");
        assertThat(llmCallCount.get()).isEqualTo(2);
        assertThat(result.content()).contains("Therefore");
    }

    interface Assistant {
        Result<String> chat(String userMessage);
    }
}
