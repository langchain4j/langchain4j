package dev.langchain4j.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.langchain4j.agent.tool.LazyEvaluationConfig;
import dev.langchain4j.agent.tool.LazyEvaluationMode;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.Response;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Integration tests to verify lazy evaluation configuration flows correctly
 * from AiServices builder through to tool execution.
 */
class AiServicesLazyEvaluationIntegrationTest {

    interface TestServiceWithTools {
        String chat(String userMessage);
    }

    static class TestTools {
        @Tool("Get current weather for a location")
        public String getCurrentWeather(String location) {
            return "Sunny, 25°C in " + location;
        }

        @Tool("Calculate sum of two numbers")
        public int calculateSum(int a, int b) {
            return a + b;
        }
    }

    @Test
    void should_pass_lazy_evaluation_config_to_tool_service() {
        // Given
        ChatModel chatModel = mock(ChatModel.class);
        
        // Mock the chat model to return a tool execution request
        AiMessage aiMessageWithToolCall = AiMessage.builder()
                .toolExecutionRequests(List.of(
                        ToolExecutionRequest.builder()
                                .name("getCurrentWeather")
                                .arguments("{\"location\": \"London\"}")
                                .build()
                ))
                .build();
        
        ChatResponse firstResponse = ChatResponse.builder()
                .aiMessage(aiMessageWithToolCall)
                .build();
        
        AiMessage finalResponse = AiMessage.from("The weather in London is sunny, 25°C");
        ChatResponse secondResponse = ChatResponse.builder()
                .aiMessage(finalResponse)
                .build();
        
        when(chatModel.chat(any(ChatRequest.class)))
                .thenReturn(firstResponse)
                .thenReturn(secondResponse);

        LazyEvaluationConfig config = LazyEvaluationConfig.builder()
                .mode(LazyEvaluationMode.ENABLED)
                .addLazyTool("getCurrentWeather")
                .build();

        // When
        TestServiceWithTools service = AiServices.builder(TestServiceWithTools.class)
                .chatModel(chatModel)
                .tools(new TestTools())
                .lazyEvaluationConfig(config)
                .build();

        String result = service.chat("What's the weather in London?");

        // Then
        assertThat(result).isNotNull();
        
        // Verify that the chat model was called with the expected messages
        ArgumentCaptor<ChatRequest> requestCaptor = ArgumentCaptor.forClass(ChatRequest.class);
        verify(chatModel, times(2)).chat(requestCaptor.capture());
        
        List<ChatRequest> capturedRequests = requestCaptor.getAllValues();
        assertThat(capturedRequests).hasSize(2);
        
        // First request should contain the user message
        ChatRequest firstRequest = capturedRequests.get(0);
        assertThat(firstRequest.messages()).hasSize(1);
        assertThat(firstRequest.messages().get(0)).isInstanceOf(UserMessage.class);
        
        // Second request should contain user message, AI message with tool call, and tool result
        if (capturedRequests.size() > 1) {
            ChatRequest secondRequest = capturedRequests.get(1);
            assertThat(secondRequest.messages()).hasSizeGreaterThanOrEqualTo(3);
            
            // Check that tool execution result message is present
            boolean hasToolResultMessage = secondRequest.messages().stream()
                    .anyMatch(msg -> msg instanceof ToolExecutionResultMessage);
            assertThat(hasToolResultMessage).isTrue();
        }
    }

    @Test
    void should_work_with_default_config_when_none_specified() {
        // Given
        ChatModel chatModel = mock(ChatModel.class);
        
        AiMessage simpleResponse = AiMessage.from("Hello there!");
        ChatResponse response = ChatResponse.builder()
                .aiMessage(simpleResponse)
                .build();
        
        when(chatModel.chat(any(ChatRequest.class))).thenReturn(response);

        // When - no lazy evaluation config specified
        TestServiceWithTools service = AiServices.builder(TestServiceWithTools.class)
                .chatModel(chatModel)
                .tools(new TestTools())
                .build();

        String result = service.chat("Hello");

        // Then
        assertThat(result).isEqualTo("Hello there!");
        verify(chatModel).chat(any(ChatRequest.class));
    }

    @Test
    void should_work_with_convenience_method() {
        // Given
        ChatModel chatModel = mock(ChatModel.class);
        
        AiMessage simpleResponse = AiMessage.from("Hello there!");
        ChatResponse response = ChatResponse.builder()
                .aiMessage(simpleResponse)
                .build();
        
        when(chatModel.chat(any(ChatRequest.class))).thenReturn(response);

        // When - using convenience method
        TestServiceWithTools service = AiServices.builder(TestServiceWithTools.class)
                .chatModel(chatModel)
                .tools(new TestTools())
                .enableLazyEvaluation()
                .build();

        String result = service.chat("Hello");

        // Then
        assertThat(result).isEqualTo("Hello there!");
        verify(chatModel).chat(any(ChatRequest.class));
    }

    @Test
    void should_handle_multiple_tool_configurations() {
        // Given
        ChatModel chatModel = mock(ChatModel.class);
        
        AiMessage simpleResponse = AiMessage.from("Calculation complete!");
        ChatResponse response = ChatResponse.builder()
                .aiMessage(simpleResponse)
                .build();
        
        when(chatModel.chat(any(ChatRequest.class))).thenReturn(response);

        LazyEvaluationConfig config = LazyEvaluationConfig.builder()
                .mode(LazyEvaluationMode.AUTO)
                .addLazyTool("getCurrentWeather")
                .addEagerTool("calculateSum")
                .enablePerformanceMonitoring(true)
                .build();

        // When
        TestServiceWithTools service = AiServices.builder(TestServiceWithTools.class)
                .chatModel(chatModel)
                .tools(new TestTools())
                .lazyEvaluationConfig(config)
                .build();

        String result = service.chat("Calculate 5 + 3");

        // Then
        assertThat(result).isEqualTo("Calculation complete!");
        verify(chatModel).chat(any(ChatRequest.class));
    }
}