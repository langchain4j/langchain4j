package dev.langchain4j.service;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.mock.ChatModelMock;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;

class AiServicesConcurrentToolExecutionHooksTest {

    private static final String USER_MESSAGE = "What is the current time and temperature in Munich?";

    @Test
    void should_invoke_before_and_after_tool_execution_hooks_when_tools_execute_concurrently() throws Exception {

        class Tools {

            static final String CURRENT_TIME = "16:28";
            static final String CURRENT_TEMPERATURE = "17";

            final Queue<Thread> getCurrentTimeThreads = new ConcurrentLinkedQueue<>();
            final Queue<Thread> getCurrentTemperatureThreads = new ConcurrentLinkedQueue<>();
            final CountDownLatch latch = new CountDownLatch(2);

            @Tool
            String getCurrentTime(@P("City") String city) throws InterruptedException {
                getCurrentTimeThreads.add(Thread.currentThread());
                latch.countDown();
                if (!latch.await(5, SECONDS)) {
                    throw new IllegalStateException("Timed out waiting for concurrent tool execution");
                }
                return CURRENT_TIME;
            }

            @Tool
            String getCurrentTemperature(@P("City") String city) throws InterruptedException {
                getCurrentTemperatureThreads.add(Thread.currentThread());
                latch.countDown();
                if (!latch.await(5, SECONDS)) {
                    throw new IllegalStateException("Timed out waiting for concurrent tool execution");
                }
                return CURRENT_TEMPERATURE;
            }
        }

        Tools spyTools = spy(new Tools());
        Queue<String> toolCalls = new ConcurrentLinkedQueue<>();
        Map<String, Object> toolResults = new ConcurrentHashMap<>();
        Map<String, Queue<Thread>> beforeToolExecutionThreads = new ConcurrentHashMap<>();
        Map<String, Queue<Thread>> afterToolExecutionThreads = new ConcurrentHashMap<>();

        AiMessage toolRequestMessage = AiMessage.from(
                ToolExecutionRequest.builder()
                        .name("getCurrentTime")
                        .arguments("{\"arg0\": \"Munich\"}")
                        .build(),
                ToolExecutionRequest.builder()
                        .name("getCurrentTemperature")
                        .arguments("{\"arg0\": \"Munich\"}")
                        .build());
        AiMessage toolResponseMessage = AiMessage.from("The current time is 16:28 and the temperature is 17.");

        ChatMemory chatMemory = MessageWindowChatMemory.withMaxMessages(10);
        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(ChatModelMock.thatAlwaysResponds(toolRequestMessage, toolResponseMessage))
                .chatMemory(chatMemory)
                .tools(spyTools)
                .executeToolsConcurrently(Executors.newFixedThreadPool(2))
                .beforeToolExecution(before -> {
                    toolCalls.add(before.request().name());
                    beforeToolExecutionThreads
                            .computeIfAbsent(before.request().name(), ignored -> new ConcurrentLinkedQueue<>())
                            .add(Thread.currentThread());
                })
                .afterToolExecution(exec -> {
                    toolResults.put(exec.request().name(), exec.resultObject());
                    afterToolExecutionThreads
                            .computeIfAbsent(exec.request().name(), ignored -> new ConcurrentLinkedQueue<>())
                            .add(Thread.currentThread());
                })
                .build();

        Result<String> result = assistant.chat(USER_MESSAGE);

        assertThat(result.content()).contains(Tools.CURRENT_TIME, Tools.CURRENT_TEMPERATURE);

        verify(spyTools).getCurrentTime("Munich");
        verify(spyTools).getCurrentTemperature("Munich");
        verifyNoMoreInteractions(spyTools);

        assertThat(toolCalls).hasSize(2).containsExactlyInAnyOrder("getCurrentTime", "getCurrentTemperature");
        assertThat(toolResults)
                .hasSize(2)
                .containsEntry("getCurrentTime", Tools.CURRENT_TIME)
                .containsEntry("getCurrentTemperature", Tools.CURRENT_TEMPERATURE);

        assertThat(beforeToolExecutionThreads).hasSize(2);
        assertThat(beforeToolExecutionThreads.get("getCurrentTime")).hasSize(1);
        assertThat(beforeToolExecutionThreads.get("getCurrentTemperature")).hasSize(1);

        assertThat(afterToolExecutionThreads).hasSize(2);
        assertThat(afterToolExecutionThreads.get("getCurrentTime")).hasSize(1);
        assertThat(afterToolExecutionThreads.get("getCurrentTemperature")).hasSize(1);

        assertThat(spyTools.getCurrentTimeThreads).hasSize(1);
        Thread getCurrentTimeThread = spyTools.getCurrentTimeThreads.poll();
        assertThat(getCurrentTimeThread).isNotEqualTo(Thread.currentThread());
        assertThat(beforeToolExecutionThreads.get("getCurrentTime").poll()).isEqualTo(getCurrentTimeThread);
        assertThat(afterToolExecutionThreads.get("getCurrentTime").poll()).isEqualTo(getCurrentTimeThread);

        assertThat(spyTools.getCurrentTemperatureThreads).hasSize(1);
        Thread getCurrentTemperatureThread = spyTools.getCurrentTemperatureThreads.poll();
        assertThat(getCurrentTemperatureThread).isNotEqualTo(Thread.currentThread());
        assertThat(beforeToolExecutionThreads.get("getCurrentTemperature").poll())
                .isEqualTo(getCurrentTemperatureThread);
        assertThat(afterToolExecutionThreads.get("getCurrentTemperature").poll())
                .isEqualTo(getCurrentTemperatureThread);

        assertThat(getCurrentTimeThread).isNotEqualTo(getCurrentTemperatureThread);
    }

    interface Assistant {

        Result<String> chat(String userMessage);
    }
}
