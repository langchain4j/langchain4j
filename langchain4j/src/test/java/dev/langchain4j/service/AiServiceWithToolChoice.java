package dev.langchain4j.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.ToolChoice;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.FinishReason;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class AiServiceWithToolChoice {

    @Mock
    ChatModel chatModel;

    static class Calculator {
        @Tool("Adds two given numbers")
        double add(double a, double b) {
            return a + b;
        }
    }

    interface Assistant {
        String chat(String userMessage);
    }

    @Test
    void should_not_loop_when_tool_choice_required() {

        when(chatModel.defaultRequestParameters())
                .thenReturn(ChatRequestParameters.builder()
                        .toolChoice(ToolChoice.REQUIRED)
                        .build());

        ToolExecutionRequest toolExecutionRequest = ToolExecutionRequest.builder()
                .id("id")
                .name("add")
                .arguments("{\"arg0\": 2, \"arg1\": 2}")
                .build();

        ChatResponse response1 = ChatResponse.builder()
                .aiMessage(AiMessage.from(toolExecutionRequest))
                .finishReason(FinishReason.TOOL_EXECUTION)
                .build();

        ChatResponse response2 = ChatResponse.builder()
                .aiMessage(AiMessage.from("4"))
                .finishReason(FinishReason.STOP)
                .build();

        when(chatModel.chat(any(ChatRequest.class))).thenReturn(response1).thenReturn(response2);

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(chatModel)
                .chatMemory(MessageWindowChatMemory.withMaxMessages(10))
                .tools(new Calculator())
                .build();

        assistant.chat("What is the result of 2 + 2");

        ArgumentCaptor<ChatRequest> captor = ArgumentCaptor.forClass(ChatRequest.class);
        verify(chatModel, times(2)).chat(captor.capture());

        List<ChatRequest> capturedRequests = captor.getAllValues();
        assertEquals(ToolChoice.NONE, capturedRequests.get(1).toolChoice());
    }
}
