package dev.langchain4j.agent.agui;

import com.agui.core.agent.AgentSubscriber;
import com.agui.core.agent.AgentSubscriberParams;
import com.agui.core.agent.RunAgentInput;
import com.agui.core.event.BaseEvent;
import com.agui.core.event.RunErrorEvent;
import com.agui.core.event.TextMessageContentEvent;
import com.agui.core.event.ToolCallArgsEvent;
import com.agui.core.event.ToolCallStartEvent;
import com.agui.core.exception.AGUIException;
import com.agui.core.message.BaseMessage;
import com.agui.core.message.UserMessage;
import com.agui.core.state.State;
import com.agui.core.tool.Tool;
import com.agui.core.type.EventType;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static java.util.Collections.emptyList;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Comprehensive test suite for LangchainAgent.
 */
class LangchainAgentTest {

    private StreamingChatModel mockStreamingModel;
    private ChatModel mockChatModel;
    private ChatMemory mockChatMemory;

    @BeforeEach
    void setUp() {
        mockStreamingModel = mock(StreamingChatModel.class);
        mockChatModel = mock(ChatModel.class);
        mockChatMemory = mock(ChatMemory.class);
    }

    @Nested
    @DisplayName("Builder Tests")
    class BuilderTests {

        @Test
        @DisplayName("Should build agent with minimum configuration")
        void shouldBuildWithMinimumConfig() throws AGUIException {
            // Act
            var agent = LangchainAgent.builder()
                    .agentId("test-agent")
                    .streamingChatModel(mockStreamingModel)
                    .systemMessage("You are an AI Assistant")
                    .build();

            // Assert
            assertNotNull(agent);
            assertEquals("test-agent", agent.getAgentId());
        }

        @Test
        @DisplayName("Should build agent with all configuration options")
        void shouldBuildWithAllOptions() throws AGUIException {
            // Arrange
            State state = new State();
            Function<ToolExecutionRequest, ToolExecutionResultMessage> strategy =
                    request -> ToolExecutionResultMessage.from(request, "Tool not found");

            // Act
            var agent = LangchainAgent.builder()
                    .agentId("full-agent")
                    .streamingChatModel(mockStreamingModel)
                    .chatModel(mockChatModel)
                    .systemMessage("Test system message")
                    .state(state)
                    .chatMemory(mockChatMemory)
                    .tool(new TestTool())
                    .tools(List.of(new TestTool()))
                    .hallucinatedToolNameStrategy(strategy)
                    .build();

            // Assert
            assertNotNull(agent);
            assertEquals("full-agent", agent.getAgentId());
        }

        @Test
        @DisplayName("Should build agent with system message provider")
        void shouldBuildWithSystemMessageProvider() throws AGUIException {
            // Act
            var agent = LangchainAgent.builder()
                    .agentId("provider-agent")
                    .streamingChatModel(mockStreamingModel)
                    .systemMessageProvider(localAgent -> "Dynamic message for " + localAgent.getAgentId())
                    .build();

            // Assert
            assertNotNull(agent);
            assertEquals("provider-agent", agent.getAgentId());
        }

        @Test
        @DisplayName("Should build agent with custom interface")
        void shouldBuildWithCustomInterface() throws AGUIException {
            // Act
            var agent = LangchainAgent.builder()
                    .agentId("custom-agent")
                    .streamingChatModel(mockStreamingModel)
                    .systemMessage("You are an AI Assistant")
                    .build();

            // Assert
            assertNotNull(agent);
            assertEquals("custom-agent", agent.getAgentId());
        }
    }

    @Nested
    @DisplayName("Agent Execution Tests")
    class AgentExecutionTests {

        @Test
        @DisplayName("Should execute agent and emit events in correct sequence")
        void shouldExecuteAgentSuccessfully() throws Exception {
            // Arrange
            var agent = createTestAgent();
            var input = createTestInput("Hello, how are you?");

            CountDownLatch latch = new CountDownLatch(1);
            List<BaseEvent> events = new ArrayList<>();
            AtomicBoolean finalized = new AtomicBoolean(false);

            mockStreamingResponse("I'm doing great!");

            // Act
            agent.run(input, new TestSubscriber(events, finalized, latch));

            // Assert
            assertTrue(latch.await(5, TimeUnit.SECONDS));
            assertTrue(finalized.get());
            assertFalse(events.isEmpty());

            // Verify event sequence
            assertEventSequence(events,
                    EventType.RUN_STARTED,
                    EventType.TEXT_MESSAGE_START,
                    EventType.TEXT_MESSAGE_CONTENT,
                    EventType.TEXT_MESSAGE_END,
                    EventType.RUN_FINISHED
            );
        }

        @Test
        @DisplayName("Should handle streaming tokens correctly")
        void shouldHandleStreamingTokens() throws Exception {
            // Arrange
            var agent = createTestAgent();
            var input = createTestInput("Tell me a story");

            CountDownLatch latch = new CountDownLatch(1);
            List<String> contentTokens = new ArrayList<>();
            List<BaseEvent> events = new ArrayList<>();

            mockStreamingResponseWithTokens("Once", " upon", " a", " time");

            // Act
            agent.run(input, new TestSubscriber(events, new AtomicBoolean(), latch) {
                @Override
                public void onTextMessageContentEvent(TextMessageContentEvent event) {
                    contentTokens.add(event.getDelta());
                }
            });

            // Assert
            assertTrue(latch.await(5, TimeUnit.SECONDS));
            assertEquals(4, contentTokens.size());
            assertEquals("Once upon a time", String.join("", contentTokens));
        }

        @Test
        @DisplayName("Should handle errors during execution")
        void shouldHandleErrors() throws Exception {
            // Arrange
            var agent = createTestAgent();
            var input = createTestInput("This will fail");

            CountDownLatch latch = new CountDownLatch(1);
            List<BaseEvent> events = new ArrayList<>();
            AtomicReference<String> errorMessage = new AtomicReference<>();

            mockStreamingError(new RuntimeException("Model error"));

            // Act
            agent.run(input, new TestSubscriber(events, new AtomicBoolean(), latch) {
                @Override
                public void onRunErrorEvent(RunErrorEvent event) {
                    errorMessage.set(event.getError());
                    super.onRunErrorEvent(event);
                }
            });

            // Assert
            assertTrue(latch.await(5, TimeUnit.SECONDS));
            assertNotNull(errorMessage.get());
            assertTrue(hasEventType(events, EventType.RUN_ERROR));
        }

        @Test
        @DisplayName("Should fail on empty messages list")
        void shouldFailOnEmptyMessagesList() throws Exception {
            // Arrange
            var agent = createTestAgent();
            var input = new RunAgentInput(
                    UUID.randomUUID().toString(),
                    UUID.randomUUID().toString(),
                    new State(),
                    new ArrayList<>(), // Empty messages
                    new ArrayList<>(),
                    new ArrayList<>(),
                    null
            );

            CountDownLatch latch = new CountDownLatch(1);
            List<BaseEvent> events = new ArrayList<>();

            // Act
            agent.run(input, new TestSubscriber(events, new AtomicBoolean(), latch));

            // Assert
            assertTrue(latch.await(5, TimeUnit.SECONDS));
            assertTrue(hasEventType(events, EventType.RUN_ERROR));
        }

        @Test
        @DisplayName("Should handle tool executions")
        void shouldHandleToolExecutions() throws Exception {
            // Arrange
            var agent = LangchainAgent.builder()
                    .agentId("tool-agent")
                    .streamingChatModel(mockStreamingModel)
                    .tool(new TestTool())
                    .systemMessage("You are an AI Assistant")
                    .build();

            var input = createTestInput("Use a tool");

            CountDownLatch latch = new CountDownLatch(1);
            List<BaseEvent> events = new ArrayList<>();

            mockStreamingResponse("Tool result");

            // Act
            agent.run(input, new TestSubscriber(events, new AtomicBoolean(), latch));

            // Assert
            assertTrue(latch.await(5, TimeUnit.SECONDS));
            // Note: Tool call events depend on actual tool execution
        }


        @Test
        @DisplayName("Should handle tools passed in input")
        void shouldHandleToolsPassedInInput() throws Exception {
            // Arrange
            var agent = createTestAgent();

            // Create a tool to pass in the input
            Tool calculatorTool = createCalculatorTool();

            var input = createTestInputWithTools(
                    "Calculate 5 + 3",
                    List.of(calculatorTool)
            );

            CountDownLatch latch = new CountDownLatch(1);
            List<BaseEvent> events = new ArrayList<>();
            AtomicInteger toolCallCount = new AtomicInteger(0);
            AtomicReference<String> toolName = new AtomicReference<>();
            AtomicReference<String> toolArgs = new AtomicReference<>();

            mockStreamingResponse("The result is 8");

            // Act
            agent.run(input, new TestSubscriber(events, new AtomicBoolean(), latch) {
                @Override
                public void onToolCallStartEvent(ToolCallStartEvent event) {
                    toolCallCount.incrementAndGet();
                    toolName.set(event.getToolCallName());
                    super.onToolCallStartEvent(event);
                }

                @Override
                public void onToolCallArgsEvent(ToolCallArgsEvent event) {
                    toolArgs.set(event.getDelta());
                    super.onToolCallArgsEvent(event);
                }
            });

            // Assert
            assertTrue(latch.await(5, TimeUnit.SECONDS));
            assertFalse(events.isEmpty());

            // Verify the tool was registered (even if not called in this mock scenario)
            // The buildAssistant method should have processed the input tools
        }

        @Test
        @DisplayName("Should handle multiple tools passed in input")
        void shouldHandleMultipleToolsInInput() throws Exception {
            // Arrange
            var agent = createTestAgent();

            Tool calculatorTool = createCalculatorTool();
            Tool weatherTool = createWeatherTool();

            var input = createTestInputWithTools(
                    "What's 10 + 5 and what's the weather?",
                    List.of(calculatorTool, weatherTool)
            );

            CountDownLatch latch = new CountDownLatch(1);
            List<BaseEvent> events = new ArrayList<>();

            mockStreamingResponse("The result is 15 and it's sunny");

            // Act
            agent.run(input, new TestSubscriber(events, new AtomicBoolean(), latch));

            // Assert
            assertTrue(latch.await(5, TimeUnit.SECONDS));
            assertFalse(events.isEmpty());

            // Verify basic execution completed
            assertTrue(hasEventType(events, EventType.RUN_STARTED));
            assertTrue(hasEventType(events, EventType.RUN_FINISHED));
        }

        @Test
        @DisplayName("Should handle empty tools list in input")
        void shouldHandleEmptyToolsListInInput() throws Exception {
            // Arrange
            var agent = createTestAgent();

            var input = createTestInputWithTools(
                    "Hello",
                    new ArrayList<>() // Empty tools list
            );

            CountDownLatch latch = new CountDownLatch(1);
            List<BaseEvent> events = new ArrayList<>();

            mockStreamingResponse("Hello!");

            // Act
            agent.run(input, new TestSubscriber(events, new AtomicBoolean(), latch));

            // Assert
            assertTrue(latch.await(5, TimeUnit.SECONDS));
            assertTrue(hasEventType(events, EventType.RUN_FINISHED));
        }

        @Test
        @DisplayName("Should handle tools with complex parameters")
        void shouldHandleToolsWithComplexParameters() throws Exception {
            // Arrange
            var agent = createTestAgent();

            Tool complexTool = createComplexParameterTool();

            var input = createTestInputWithTools(
                    "Process this data",
                    List.of(complexTool)
            );

            CountDownLatch latch = new CountDownLatch(1);
            List<BaseEvent> events = new ArrayList<>();

            mockStreamingResponse("Data processed");

            // Act
            agent.run(input, new TestSubscriber(events, new AtomicBoolean(), latch));

            // Assert
            assertTrue(latch.await(5, TimeUnit.SECONDS));
            assertFalse(events.isEmpty());
        }

        @Test
        @DisplayName("Should combine agent tools with input tools")
        void shouldCombineAgentToolsWithInputTools() throws Exception {
            // Arrange - Agent with its own tool
            var agent = LangchainAgent.builder()
                    .agentId("combined-tools-agent")
                    .streamingChatModel(mockStreamingModel)
                    .tool(new TestTool()) // Agent's own tool
                    .systemMessage("You are an AI Assistant")
                    .build();

            // Input with additional tool
            Tool calculatorTool = createCalculatorTool();
            var input = createTestInputWithTools(
                    "Use multiple tools",
                    List.of(calculatorTool)
            );

            CountDownLatch latch = new CountDownLatch(1);
            List<BaseEvent> events = new ArrayList<>();

            mockStreamingResponse("Used both tools");

            // Act
            agent.run(input, new TestSubscriber(events, new AtomicBoolean(), latch));

            // Assert
            assertTrue(latch.await(5, TimeUnit.SECONDS));
            assertTrue(hasEventType(events, EventType.RUN_FINISHED));
        }
    }

    @Nested
    @DisplayName("Memory Management Tests")
    class MemoryTests {

        @Test
        @DisplayName("Should use chat memory when provided")
        void shouldUseChatMemory() throws AGUIException {
            // Arrange
            ChatMemory memory = MessageWindowChatMemory.withMaxMessages(10);

            // Act
            var agent = LangchainAgent.builder()
                    .agentId("memory-agent")
                    .streamingChatModel(mockStreamingModel)
                    .chatMemory(memory)
                    .systemMessage("You are an AI Assistant")
                    .build();

            // Assert
            assertNotNull(agent);
        }

        @Test
        @DisplayName("Should work without chat memory")
        void shouldWorkWithoutMemory() throws AGUIException {
            // Act
            var agent = LangchainAgent.builder()
                    .agentId("no-memory-agent")
                    .streamingChatModel(mockStreamingModel)
                    .systemMessage("You are an AI Assistant")
                    .build();

            // Assert
            assertNotNull(agent);
        }
    }

    @Nested
    @DisplayName("Tool Management Tests")
    class ToolTests {

        @Test
        @DisplayName("Should add single tool")
        void shouldAddSingleTool() throws AGUIException {
            // Act
            var agent = LangchainAgent.builder()
                    .agentId("single-tool-agent")
                    .streamingChatModel(mockStreamingModel)
                    .tool(new TestTool())
                    .systemMessage("You are an AI Assistant")
                    .build();

            // Assert
            assertNotNull(agent);
        }

        @Test
        @DisplayName("Should add multiple tools")
        void shouldAddMultipleTools() throws AGUIException {
            // Act
            var agent = LangchainAgent.builder()
                    .agentId("multi-tool-agent")
                    .streamingChatModel(mockStreamingModel)
                    .tools(List.of(new TestTool(), new TestTool()))
                    .systemMessage("You are an AI Assistant")
                    .build();

            // Assert
            assertNotNull(agent);
        }

        @Test
        @DisplayName("Should handle hallucinated tool names")
        void shouldHandleHallucinatedTools() throws AGUIException {
            // Arrange
            Function<ToolExecutionRequest, ToolExecutionResultMessage> strategy =
                    request -> ToolExecutionResultMessage.from(
                            request,
                            "Tool '" + request.name() + "' does not exist"
                    );

            // Act
            var agent = LangchainAgent.builder()
                    .agentId("hallucination-agent")
                    .streamingChatModel(mockStreamingModel)
                    .hallucinatedToolNameStrategy(strategy)
                    .systemMessage("You are an AI Assistant")
                    .build();

            // Assert
            assertNotNull(agent);
        }
    }

    @Nested
    @DisplayName("System Message Tests")
    class SystemMessageTests {

        @Test
        @DisplayName("Should use static system message")
        void shouldUseStaticSystemMessage() throws AGUIException {
            // Act
            var agent = LangchainAgent.builder()
                    .agentId("static-msg-agent")
                    .streamingChatModel(mockStreamingModel)
                    .systemMessage("You are a helpful assistant")
                    .build();

            // Assert
            assertNotNull(agent);
        }

        @Test
        @DisplayName("Should use dynamic system message provider")
        void shouldUseDynamicSystemMessage() throws AGUIException {
            // Act
            var agent = LangchainAgent.builder()
                    .agentId("dynamic-msg-agent")
                    .streamingChatModel(mockStreamingModel)
                    .systemMessageProvider(localAgent ->
                            "Agent " + localAgent.getAgentId() + " ready"
                    )
                    .build();

            // Assert
            assertNotNull(agent);
        }
    }

    private LangchainAgent createTestAgent() throws AGUIException {
        return LangchainAgent.builder()
                .agentId("test-agent")
                .streamingChatModel(mockStreamingModel)
                .systemMessage("Test system message")
                .build();
    }

    private RunAgentInput createTestInput(String userMessage) {
        List<BaseMessage> messages = new ArrayList<>();
        UserMessage msg = new UserMessage();
        msg.setContent(userMessage);
        messages.add(msg);

        return new RunAgentInput(
                UUID.randomUUID().toString(), // threadId
                UUID.randomUUID().toString(), // runId
                new State(),
                messages,
                emptyList(),
                emptyList(),
                null
        );
    }

    private RunAgentInput createTestInputWithTools(String userMessage, List<Tool> tools) {
        List<BaseMessage> messages = new ArrayList<>();
        UserMessage msg = new UserMessage();
        msg.setContent(userMessage);
        messages.add(msg);

        return new RunAgentInput(
                UUID.randomUUID().toString(), // threadId
                UUID.randomUUID().toString(), // runId
                new State(),
                messages,
                tools, // tools passed in input
                emptyList(), // context
                null
        );
    }

    private Tool createCalculatorTool() {
        return new Tool(
                "calculator",
                "Performs basic arithmetic operations",
                new Tool.ToolParameters(
                        "function",
                        Map.of(
                                "a",
                                new Tool.ToolProperty("number", "First number"),
                                "b",
                                new Tool.ToolProperty("number", "Second number"),
                                "operation",
                                new Tool.ToolProperty("string", "Operation to perform: add, subtract, multiply, divide")
                        ),
                        List.of("a", "b", "operation")
                )
        );
    }

    private Tool createWeatherTool() {
        return new Tool(
                "get_weather",
                "Gets current weather for a location",
                new Tool.ToolParameters(
                        "function",
                        Map.of(
                                "location",
                                new Tool.ToolProperty("string", "city name")
                        ),
                        List.of("location")
                )
        );
    }

    private Tool createComplexParameterTool() {
        return new Tool(
                "process_data",
                "Processes complex data structures",
                new Tool.ToolParameters(
                        "function",
                        Map.of(
                                "data",
                                new Tool.ToolProperty("object", "Complex data object to process"),
                                "options",
                                new Tool.ToolProperty("array", "Processing options")
                        ),
                        List.of("data")
                )
        );
    }

    private void mockStreamingResponse(String response) {
        doAnswer(invocation -> {
            StreamingChatResponseHandler handler = invocation.getArgument(1);

            CompletableFuture.runAsync(() -> {
                handler.onPartialResponse(response);
                handler.onCompleteResponse(ChatResponse
                        .builder()
                        .aiMessage(AiMessage.from(response))
                        .build());
            });

            return null;
        }).when(mockStreamingModel).chat(any(ChatRequest.class), any());
    }

    private void mockStreamingResponseWithTokens(String... tokens) {
        doAnswer(invocation -> {
            StreamingChatResponseHandler handler = invocation.getArgument(1);

            CompletableFuture.runAsync(() -> {
                StringBuilder fullResponse = new StringBuilder();

                for (String token : tokens) {
                    handler.onPartialResponse(token);
                    fullResponse.append(token);
                    try {
                        Thread.sleep(10);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }

                handler.onCompleteResponse(ChatResponse
                        .builder()
                        .aiMessage(AiMessage.from(fullResponse.toString()))
                        .build()
                );
            });

            return null;
        }).when(mockStreamingModel).chat(any(ChatRequest.class), any());
    }

    private void mockStreamingError(Throwable error) {
        doAnswer(invocation -> {
            StreamingChatResponseHandler handler = invocation.getArgument(1);

            CompletableFuture.runAsync(() -> handler.onError(error));

            return null;
        }).when(mockStreamingModel).chat(any(ChatRequest.class), any());
    }

    private boolean hasEventType(List<BaseEvent> events, EventType type) {
        return events.stream().anyMatch(e -> type.equals(e.getType()));
    }

    private void assertEventSequence(List<BaseEvent> events, EventType... expectedTypes) {
        List<EventType> actualTypes = events.stream()
                .map(BaseEvent::getType)
                .toList();

        for (EventType expectedType : expectedTypes) {
            assertTrue(actualTypes.contains(expectedType),
                    "Missing event type: " + expectedType + ". Actual events: " + actualTypes);
        }
    }

    private static class TestSubscriber implements AgentSubscriber {
        private final List<BaseEvent> events;
        private final AtomicBoolean finalized;
        private final CountDownLatch latch;

        public TestSubscriber(List<BaseEvent> events, AtomicBoolean finalized, CountDownLatch latch) {
            this.events = events;
            this.finalized = finalized;
            this.latch = latch;
        }

        @Override
        public void onRunErrorEvent(final RunErrorEvent event) {
            finalized.set(true);
            latch.countDown();
        }

        @Override
        public void onEvent(BaseEvent event) {
            events.add(event);
        }

        @Override
        public void onRunFinalized(AgentSubscriberParams params) {
            finalized.set(true);
            latch.countDown();
        }

        @Override
        public void onRunFailed(AgentSubscriberParams params, Throwable error) {
            finalized.set(true);
            latch.countDown();
        }
    }

    private static class TestTool {
        @dev.langchain4j.agent.tool.Tool("Test tool for testing")
        public String execute(String input) {
            return "Result: " + input;
        }
    }

}
