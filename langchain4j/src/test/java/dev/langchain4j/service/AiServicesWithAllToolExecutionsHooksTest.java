package dev.langchain4j.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.mock.ChatModelMock;
import dev.langchain4j.service.tool.BeforeAllToolExecutions;
import dev.langchain4j.service.tool.ToolExecution;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AiServicesWithAllToolExecutionsHooksTest {

    private static final ToolExecutionRequest GET_TIME_REQUEST = ToolExecutionRequest.builder()
            .id("1")
            .name("getCurrentTime")
            .arguments("{\"arg0\":\"Munich\"}")
            .build();

    private static final ToolExecutionRequest GET_TEMPERATURE_REQUEST = ToolExecutionRequest.builder()
            .id("2")
            .name("getCurrentTemperature")
            .arguments("{\"arg0\":\"Munich\"}")
            .build();

    interface Assistant {

        String chat(String userMessage);
    }

    static class Tools {

        static final String CURRENT_TIME = "16:35";
        static final String CURRENT_TEMPERATURE = "17 degrees";

        final AtomicInteger executedToolCount = new AtomicInteger();

        @Tool
        String getCurrentTime(String city) {
            executedToolCount.incrementAndGet();
            return CURRENT_TIME;
        }

        @Tool
        String getCurrentTemperature(String city) {
            executedToolCount.incrementAndGet();
            return CURRENT_TEMPERATURE;
        }
    }

    static class FailingTools {

        static final RuntimeException TIME_FAILURE = new RuntimeException("time service is down");
        static final String CURRENT_TEMPERATURE = "17 degrees";

        @Tool
        String getCurrentTime(String city) {
            throw TIME_FAILURE;
        }

        @Tool
        String getCurrentTemperature(String city) {
            return CURRENT_TEMPERATURE;
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void should_invoke_beforeAllToolExecutions_with_all_requests_before_any_tool_is_executed(
            boolean executeToolsConcurrently) {

        // given
        Tools tools = new Tools();

        List<BeforeAllToolExecutions> hookInvocations = new CopyOnWriteArrayList<>();
        AtomicInteger executedToolsWhenHookFired = new AtomicInteger(-1);
        AtomicReference<Thread> hookThread = new AtomicReference<>();

        ChatModel model = ChatModelMock.thatAlwaysResponds(
                AiMessage.from(GET_TIME_REQUEST, GET_TEMPERATURE_REQUEST), AiMessage.from("done"));

        AiServices<Assistant> assistantBuilder = AiServices.builder(Assistant.class)
                .chatModel(model)
                .tools(tools)
                .beforeAllToolExecutions(before -> {
                    hookInvocations.add(before);
                    executedToolsWhenHookFired.set(tools.executedToolCount.get());
                    hookThread.set(Thread.currentThread());
                });
        if (executeToolsConcurrently) {
            assistantBuilder.executeToolsConcurrently();
        }
        Assistant assistant = assistantBuilder.build();

        // when
        assistant.chat("What is the current time and temperature in Munich?");

        // then
        assertThat(hookInvocations).hasSize(1);

        BeforeAllToolExecutions beforeAllToolExecutions = hookInvocations.get(0);
        assertThat(beforeAllToolExecutions.requests()).containsExactly(GET_TIME_REQUEST, GET_TEMPERATURE_REQUEST);
        assertThat(beforeAllToolExecutions.invocationContext()).isNotNull();

        assertThat(executedToolsWhenHookFired).hasValue(0);
        assertThat(tools.executedToolCount).hasValue(2);
        assertThat(hookThread).hasValue(Thread.currentThread());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void should_invoke_afterAllToolExecutions_once_after_all_tools_are_executed(boolean executeToolsConcurrently) {

        // given
        Tools tools = new Tools();

        List<List<ToolExecution>> hookInvocations = new CopyOnWriteArrayList<>();
        AtomicInteger executedToolsWhenHookFired = new AtomicInteger(-1);
        AtomicReference<Thread> hookThread = new AtomicReference<>();

        ChatModel model = ChatModelMock.thatAlwaysResponds(
                AiMessage.from(GET_TIME_REQUEST, GET_TEMPERATURE_REQUEST), AiMessage.from("done"));

        AiServices<Assistant> assistantBuilder = AiServices.builder(Assistant.class)
                .chatModel(model)
                .tools(tools)
                .afterAllToolExecutions(executions -> {
                    hookInvocations.add(executions);
                    executedToolsWhenHookFired.set(tools.executedToolCount.get());
                    hookThread.set(Thread.currentThread());
                });
        if (executeToolsConcurrently) {
            assistantBuilder.executeToolsConcurrently();
        }
        Assistant assistant = assistantBuilder.build();

        // when
        assistant.chat("What is the current time and temperature in Munich?");

        // then
        assertThat(hookInvocations).hasSize(1);

        List<ToolExecution> toolExecutions = hookInvocations.get(0);
        assertThat(toolExecutions).hasSize(2);
        assertThat(toolExecutions.get(0).request()).isEqualTo(GET_TIME_REQUEST);
        assertThat(toolExecutions.get(0).result()).isEqualTo(Tools.CURRENT_TIME);
        assertThat(toolExecutions.get(1).request()).isEqualTo(GET_TEMPERATURE_REQUEST);
        assertThat(toolExecutions.get(1).result()).isEqualTo(Tools.CURRENT_TEMPERATURE);

        assertThat(executedToolsWhenHookFired).hasValue(2);
        assertThat(hookThread).hasValue(Thread.currentThread());
    }

    @Test
    void should_invoke_batch_hooks_and_per_call_hooks_in_order() {

        // given
        Tools tools = new Tools();

        List<String> events = new ArrayList<>();

        ChatModel model = ChatModelMock.thatAlwaysResponds(
                AiMessage.from(GET_TIME_REQUEST, GET_TEMPERATURE_REQUEST), AiMessage.from("done"));

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(model)
                .tools(tools)
                .beforeAllToolExecutions(before -> events.add("beforeAll"))
                .beforeToolExecution(
                        before -> events.add("before " + before.request().name()))
                .afterToolExecution(
                        after -> events.add("after " + after.request().name()))
                .afterAllToolExecutions(executions -> events.add("afterAll"))
                .build();

        // when
        assistant.chat("What is the current time and temperature in Munich?");

        // then
        assertThat(events)
                .containsExactly(
                        "beforeAll",
                        "before getCurrentTime",
                        "after getCurrentTime",
                        "before getCurrentTemperature",
                        "after getCurrentTemperature",
                        "afterAll");
    }

    @Test
    void should_invoke_batch_hooks_around_per_call_hooks_when_executing_tools_concurrently() {

        // given
        Tools tools = new Tools();

        List<String> events = new CopyOnWriteArrayList<>();

        ChatModel model = ChatModelMock.thatAlwaysResponds(
                AiMessage.from(GET_TIME_REQUEST, GET_TEMPERATURE_REQUEST), AiMessage.from("done"));

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(model)
                .tools(tools)
                .executeToolsConcurrently()
                .beforeAllToolExecutions(before -> events.add("beforeAll"))
                .beforeToolExecution(
                        before -> events.add("before " + before.request().name()))
                .afterToolExecution(
                        after -> events.add("after " + after.request().name()))
                .afterAllToolExecutions(executions -> events.add("afterAll"))
                .build();

        // when
        assistant.chat("What is the current time and temperature in Munich?");

        // then
        assertThat(events).hasSize(6);
        assertThat(events.get(0)).isEqualTo("beforeAll");
        assertThat(events.get(5)).isEqualTo("afterAll");
        assertThat(events.subList(1, 5))
                .containsExactlyInAnyOrder(
                        "before getCurrentTime",
                        "after getCurrentTime",
                        "before getCurrentTemperature",
                        "after getCurrentTemperature");
    }

    @Test
    void should_invoke_batch_hooks_once_per_tool_calling_round() {

        // given
        Tools tools = new Tools();

        List<BeforeAllToolExecutions> beforeInvocations = new ArrayList<>();
        List<List<ToolExecution>> afterInvocations = new ArrayList<>();

        ChatModel model = ChatModelMock.thatAlwaysResponds(
                AiMessage.from(GET_TIME_REQUEST), AiMessage.from(GET_TEMPERATURE_REQUEST), AiMessage.from("done"));

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(model)
                .tools(tools)
                .beforeAllToolExecutions(beforeInvocations::add)
                .afterAllToolExecutions(afterInvocations::add)
                .build();

        // when
        assistant.chat("What is the current time and temperature in Munich?");

        // then
        assertThat(beforeInvocations).hasSize(2);
        assertThat(beforeInvocations.get(0).requests()).containsExactly(GET_TIME_REQUEST);
        assertThat(beforeInvocations.get(1).requests()).containsExactly(GET_TEMPERATURE_REQUEST);

        assertThat(afterInvocations).hasSize(2);
        assertThat(afterInvocations.get(0)).hasSize(1);
        assertThat(afterInvocations.get(0).get(0).request()).isEqualTo(GET_TIME_REQUEST);
        assertThat(afterInvocations.get(1)).hasSize(1);
        assertThat(afterInvocations.get(1).get(0).request()).isEqualTo(GET_TEMPERATURE_REQUEST);
    }

    @Test
    void should_not_invoke_batch_hooks_when_no_tools_are_requested() {

        // given
        Tools tools = new Tools();

        List<BeforeAllToolExecutions> beforeInvocations = new ArrayList<>();
        List<List<ToolExecution>> afterInvocations = new ArrayList<>();

        ChatModel model = ChatModelMock.thatAlwaysResponds(AiMessage.from("done"));

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(model)
                .tools(tools)
                .beforeAllToolExecutions(beforeInvocations::add)
                .afterAllToolExecutions(afterInvocations::add)
                .build();

        // when
        assistant.chat("Hello");

        // then
        assertThat(beforeInvocations).isEmpty();
        assertThat(afterInvocations).isEmpty();
        assertThat(tools.executedToolCount).hasValue(0);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void should_propagate_exception_thrown_from_beforeAllToolExecutions(boolean executeToolsConcurrently) {

        // given
        Tools tools = new Tools();

        RuntimeException hookException = new RuntimeException("beforeAllToolExecutions failed");

        ChatModel model = ChatModelMock.thatAlwaysResponds(
                AiMessage.from(GET_TIME_REQUEST, GET_TEMPERATURE_REQUEST), AiMessage.from("done"));

        AiServices<Assistant> assistantBuilder = AiServices.builder(Assistant.class)
                .chatModel(model)
                .tools(tools)
                .beforeAllToolExecutions(before -> {
                    throw hookException;
                });
        if (executeToolsConcurrently) {
            assistantBuilder.executeToolsConcurrently();
        }
        Assistant assistant = assistantBuilder.build();

        // when-then
        assertThatThrownBy(() -> assistant.chat("What is the current time and temperature in Munich?"))
                .isSameAs(hookException);
        assertThat(tools.executedToolCount).hasValue(0);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void should_propagate_exception_thrown_from_afterAllToolExecutions(boolean executeToolsConcurrently) {

        // given
        Tools tools = new Tools();

        RuntimeException hookException = new RuntimeException("afterAllToolExecutions failed");

        ChatModel model = ChatModelMock.thatAlwaysResponds(
                AiMessage.from(GET_TIME_REQUEST, GET_TEMPERATURE_REQUEST), AiMessage.from("done"));

        AiServices<Assistant> assistantBuilder = AiServices.builder(Assistant.class)
                .chatModel(model)
                .tools(tools)
                .afterAllToolExecutions(executions -> {
                    throw hookException;
                });
        if (executeToolsConcurrently) {
            assistantBuilder.executeToolsConcurrently();
        }
        Assistant assistant = assistantBuilder.build();

        // when-then
        assertThatThrownBy(() -> assistant.chat("What is the current time and temperature in Munich?"))
                .isSameAs(hookException);
        assertThat(tools.executedToolCount).hasValue(2);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void should_invoke_afterAllToolExecutions_with_error_result_when_tool_fails(boolean executeToolsConcurrently) {

        // given
        List<List<ToolExecution>> hookInvocations = new CopyOnWriteArrayList<>();

        ChatModel model = ChatModelMock.thatAlwaysResponds(
                AiMessage.from(GET_TIME_REQUEST, GET_TEMPERATURE_REQUEST), AiMessage.from("done"));

        AiServices<Assistant> assistantBuilder = AiServices.builder(Assistant.class)
                .chatModel(model)
                .tools(new FailingTools())
                .afterAllToolExecutions(hookInvocations::add);
        if (executeToolsConcurrently) {
            assistantBuilder.executeToolsConcurrently();
        }
        Assistant assistant = assistantBuilder.build();

        // when
        assistant.chat("What is the current time and temperature in Munich?");

        // then
        assertThat(hookInvocations).hasSize(1);

        List<ToolExecution> toolExecutions = hookInvocations.get(0);
        assertThat(toolExecutions).hasSize(2);
        assertThat(toolExecutions.get(0).request()).isEqualTo(GET_TIME_REQUEST);
        assertThat(toolExecutions.get(0).hasFailed()).isTrue();
        assertThat(toolExecutions.get(0).result()).isEqualTo(FailingTools.TIME_FAILURE.getMessage());
        assertThat(toolExecutions.get(1).request()).isEqualTo(GET_TEMPERATURE_REQUEST);
        assertThat(toolExecutions.get(1).hasFailed()).isFalse();
        assertThat(toolExecutions.get(1).result()).isEqualTo(FailingTools.CURRENT_TEMPERATURE);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void should_not_invoke_afterAllToolExecutions_when_tool_exception_propagates(boolean executeToolsConcurrently) {

        // given
        List<BeforeAllToolExecutions> beforeInvocations = new CopyOnWriteArrayList<>();
        List<List<ToolExecution>> afterInvocations = new CopyOnWriteArrayList<>();

        ChatModel model = ChatModelMock.thatAlwaysResponds(
                AiMessage.from(GET_TIME_REQUEST, GET_TEMPERATURE_REQUEST), AiMessage.from("done"));

        AiServices<Assistant> assistantBuilder = AiServices.builder(Assistant.class)
                .chatModel(model)
                .tools(new FailingTools())
                .toolExecutionErrorHandler((error, context) -> {
                    throw (RuntimeException) error;
                })
                .beforeAllToolExecutions(beforeInvocations::add)
                .afterAllToolExecutions(afterInvocations::add);
        if (executeToolsConcurrently) {
            assistantBuilder.executeToolsConcurrently();
        }
        Assistant assistant = assistantBuilder.build();

        // when-then
        assertThatThrownBy(() -> assistant.chat("What is the current time and temperature in Munich?"))
                .isSameAs(FailingTools.TIME_FAILURE);

        assertThat(beforeInvocations).hasSize(1);
        assertThat(afterInvocations).isEmpty();
    }
}
