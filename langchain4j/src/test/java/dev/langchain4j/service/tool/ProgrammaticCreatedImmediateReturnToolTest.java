package dev.langchain4j.service.tool;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agent.tool.ReturnBehavior;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.mock.ChatModelMock;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.Result;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

    @Test
    void should_use_return_behavior_from_tool_specification_via_map_api() {
        AtomicBoolean toolExecuted = new AtomicBoolean(false);
        AtomicInteger llmCallCount = new AtomicInteger(0);

        ToolSpecification tool = ToolSpecification.builder()
                .name("calculate")
                .description("Performs calculation")
                .returnBehavior(ReturnBehavior.IMMEDIATE)
                .build();

        ToolExecutor executor = (toolExecutionRequest, memoryId) -> {
            toolExecuted.set(true);
            return "4";
        };

        ChatModelMock mockModel = new ChatModelMock(request -> {
            llmCallCount.incrementAndGet();
            return AiMessage.from(ToolExecutionRequest.builder()
                    .id("calc-1")
                    .name("calculate")
                    .arguments("{\"expression\": \"2+2\"}")
                    .build());
        });

        Map<ToolSpecification, ToolExecutor> tools = new LinkedHashMap<>();
        tools.put(tool, executor);

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(mockModel)
                .tools(tools)
                .build();

        Result<String> result = assistant.chat("What is 2+2?");

        assertThat(toolExecuted.get()).isTrue();
        assertThat(llmCallCount.get()).isEqualTo(1);
        assertThat(result.content()).isNull();
        assertThat(result.toolExecutions()).hasSize(1);
        assertThat(result.toolExecutions().get(0).result()).isEqualTo("4");
    }

    @Test
    void should_default_to_TO_LLM_when_return_behavior_not_set_on_tool_specification() {
        AtomicBoolean toolExecuted = new AtomicBoolean(false);
        AtomicInteger llmCallCount = new AtomicInteger(0);

        ToolSpecification tool = ToolSpecification.builder()
                .name("calculate")
                .description("Performs calculation")
                .build();

        assertThat(tool.returnBehavior()).isNull();

        ToolExecutor executor = (toolExecutionRequest, memoryId) -> {
            toolExecuted.set(true);
            return "4";
        };

        ChatModelMock mockModel = new ChatModelMock(request -> {
            int callNumber = llmCallCount.incrementAndGet();
            if (callNumber == 1) {
                return AiMessage.from(ToolExecutionRequest.builder()
                        .id("calc-1")
                        .name("calculate")
                        .arguments("{\"expression\": \"2+2\"}")
                        .build());
            } else {
                return AiMessage.from("The answer is 4.");
            }
        });

        Map<ToolSpecification, ToolExecutor> tools = new LinkedHashMap<>();
        tools.put(tool, executor);

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(mockModel)
                .tools(tools)
                .build();

        Result<String> result = assistant.chat("What is 2+2?");

        assertThat(toolExecuted.get()).isTrue();
        assertThat(llmCallCount.get()).isEqualTo(2);
        assertThat(result.content()).isEqualTo("The answer is 4.");
    }

    interface Assistant {
        Result<String> chat(String userMessage);
    }
}
