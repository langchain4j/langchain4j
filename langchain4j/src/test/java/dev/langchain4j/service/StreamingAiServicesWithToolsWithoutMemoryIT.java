package dev.langchain4j.service;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageType;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.model.output.TokenUsage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static dev.langchain4j.model.openai.OpenAiChatModelName.GPT_4_O_MINI;
import static dev.langchain4j.model.output.FinishReason.STOP;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class StreamingAiServicesWithToolsWithoutMemoryIT {

    @Spy
    StreamingChatModel spyModel = OpenAiStreamingChatModel.builder()
            .baseUrl(System.getenv("OPENAI_BASE_URL"))
            .apiKey(System.getenv("OPENAI_API_KEY"))
            .organizationId(System.getenv("OPENAI_ORGANIZATION_ID"))
            .modelName(GPT_4_O_MINI)
            .temperature(0.0)
            .logRequests(true)
            .logResponses(true)
            .build();

    @Captor
    ArgumentCaptor<ChatRequest> chatRequestCaptor;

    interface Assistant {

        TokenStream chat(String userMessage);
    }

    static class Calculator {

        @Tool("calculates the square root of the provided number")
        double squareRoot(@P("number to operate on") double number) {
            System.out.printf("called squareRoot(%s)%n", number);
            return Math.sqrt(number);
        }
    }

    @Test
    void should_execute_a_tool_then_answer() throws Exception {

        // given
        Calculator calculator = spy(new Calculator());

        Assistant assistant = AiServices
                .builder(Assistant.class)
                .streamingChatModel(spyModel)
                .tools(calculator)
                .build();

        String userMessage = "What is the square root of 485906798473894056 in scientific notation?";

        // when
        CompletableFuture<ChatResponse> future = new CompletableFuture<>();
        assistant.chat(userMessage)
                .onPartialResponse(ignored -> {})
                .onCompleteResponse(future::complete)
                .onError(future::completeExceptionally)
                .start();
        ChatResponse response = future.get(60, TimeUnit.SECONDS);

        // then
        assertThat(response.aiMessage().text()).contains("6.97");
        assertThat(response.finishReason()).isEqualTo(STOP);

        TokenUsage tokenUsage = response.tokenUsage();
        assertThat(tokenUsage.inputTokenCount()).isPositive();
        assertThat(tokenUsage.outputTokenCount()).isPositive();
        assertThat(tokenUsage.totalTokenCount())
                .isEqualTo(tokenUsage.inputTokenCount() + tokenUsage.outputTokenCount());

        verify(calculator).squareRoot(485906798473894056.0);
        verifyNoMoreInteractions(calculator);

        verify(spyModel, times(2)).chat(chatRequestCaptor.capture(), any());
        List<ChatRequest> allChatRequests = chatRequestCaptor.getAllValues();

        List<ChatMessage> firstGenerateSendMessages = allChatRequests.get(0).messages();
        assertThat(firstGenerateSendMessages).hasSize(1);
        assertThat(firstGenerateSendMessages.get(0).type()).isEqualTo(ChatMessageType.USER);
        assertThat(((UserMessage) firstGenerateSendMessages.get(0)).singleText()).isEqualTo(userMessage);

        List<ChatMessage> secondGenerateSendMessages = allChatRequests.get(1).messages();
        assertThat(secondGenerateSendMessages).hasSize(3);
        assertThat(secondGenerateSendMessages.get(0).type()).isEqualTo(ChatMessageType.USER);
        assertThat(((UserMessage) secondGenerateSendMessages.get(0)).singleText()).isEqualTo(userMessage);
        assertThat(secondGenerateSendMessages.get(1).type()).isEqualTo(ChatMessageType.AI);
        assertThat(secondGenerateSendMessages.get(2).type()).isEqualTo(ChatMessageType.TOOL_EXECUTION_RESULT);
    }

    @Test
    void should_execute_multiple_tools_sequentially_then_answer() throws Exception {

        // given
        Calculator calculator = spy(new Calculator());

        StreamingChatModel model = OpenAiStreamingChatModel
                .builder()
                .baseUrl(System.getenv("OPENAI_BASE_URL"))
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .organizationId(System.getenv("OPENAI_ORGANIZATION_ID"))
                .modelName(GPT_4_O_MINI)
                .parallelToolCalls(false) // to force the model to call tools sequentially
                .temperature(0.0)
                .logRequests(true)
                .logResponses(true)
                .build();

        StreamingChatModel spyModel = spy(model);

        Assistant assistant = AiServices
                .builder(Assistant.class)
                .streamingChatModel(spyModel)
                .tools(calculator)
                .build();

        String userMessage = "What is the square root of 485906798473894056 and 97866249624785 in scientific notation?";

        // when
        CompletableFuture<ChatResponse> future = new CompletableFuture<>();
        assistant.chat(userMessage)
                .onPartialResponse(ignored -> {})
                .onCompleteResponse(future::complete)
                .onError(future::completeExceptionally)
                .start();
        ChatResponse response = future.get(60, TimeUnit.SECONDS);

        // then
        assertThat(response.aiMessage().text()).contains("6.97", "9.89");
        assertThat(response.finishReason()).isEqualTo(STOP);

        TokenUsage tokenUsage = response.tokenUsage();
        assertThat(tokenUsage.inputTokenCount()).isPositive();
        assertThat(tokenUsage.outputTokenCount()).isPositive();
        assertThat(tokenUsage.totalTokenCount())
                .isEqualTo(tokenUsage.inputTokenCount() + tokenUsage.outputTokenCount());

        verify(calculator).squareRoot(485906798473894056.0);
        verify(calculator).squareRoot(97866249624785.0);
        verifyNoMoreInteractions(calculator);

        verify(spyModel, times(3)).chat(chatRequestCaptor.capture(), any());
        List<ChatRequest> allChatRequests = chatRequestCaptor.getAllValues();

        List<ChatMessage> firstGenerateSendMessages = allChatRequests.get(0).messages();
        assertThat(firstGenerateSendMessages).hasSize(1);
        assertThat(firstGenerateSendMessages.get(0).type()).isEqualTo(ChatMessageType.USER);
        assertThat(((UserMessage) firstGenerateSendMessages.get(0)).singleText()).isEqualTo(userMessage);

        List<ChatMessage> secondGenerateSendMessages = allChatRequests.get(1).messages();
        assertThat(secondGenerateSendMessages).hasSize(3);
        assertThat(secondGenerateSendMessages.get(0).type()).isEqualTo(ChatMessageType.USER);
        assertThat(((UserMessage) secondGenerateSendMessages.get(0)).singleText()).isEqualTo(userMessage);
        assertThat(secondGenerateSendMessages.get(1).type()).isEqualTo(ChatMessageType.AI);
        assertThat(secondGenerateSendMessages.get(2).type()).isEqualTo(ChatMessageType.TOOL_EXECUTION_RESULT);

        List<ChatMessage> thirdGenerateSendMessages = allChatRequests.get(2).messages();
        assertThat(thirdGenerateSendMessages).hasSize(5);
        assertThat(thirdGenerateSendMessages.get(0).type()).isEqualTo(ChatMessageType.USER);
        assertThat(((UserMessage) thirdGenerateSendMessages.get(0)).singleText()).isEqualTo(userMessage);
        assertThat(thirdGenerateSendMessages.get(1).type()).isEqualTo(ChatMessageType.AI);
        assertThat(thirdGenerateSendMessages.get(2).type()).isEqualTo(ChatMessageType.TOOL_EXECUTION_RESULT);
        assertThat(thirdGenerateSendMessages.get(3).type()).isEqualTo(ChatMessageType.AI);
        assertThat(thirdGenerateSendMessages.get(4).type()).isEqualTo(ChatMessageType.TOOL_EXECUTION_RESULT);
    }

    @Test
    void should_execute_multiple_tools_in_parallel_then_answer() throws Exception {

        // given
        Calculator calculator = spy(new Calculator());

        Assistant assistant = AiServices
                .builder(Assistant.class)
                .streamingChatModel(spyModel)
                .tools(calculator)
                .build();

        String userMessage = "What is the square root of 485906798473894056 and 97866249624785 in scientific notation?";

        // when
        CompletableFuture<ChatResponse> future = new CompletableFuture<>();
        assistant.chat(userMessage)
                .onPartialResponse(ignored -> {})
                .onCompleteResponse(future::complete)
                .onError(future::completeExceptionally)
                .start();
        ChatResponse response = future.get(60, TimeUnit.SECONDS);

        // then
        assertThat(response.aiMessage().text()).contains("6.97", "9.89");
        assertThat(response.finishReason()).isEqualTo(STOP);

        TokenUsage tokenUsage = response.tokenUsage();
        assertThat(tokenUsage.inputTokenCount()).isPositive();
        assertThat(tokenUsage.outputTokenCount()).isPositive();
        assertThat(tokenUsage.totalTokenCount())
                .isEqualTo(tokenUsage.inputTokenCount() + tokenUsage.outputTokenCount());

        verify(calculator).squareRoot(485906798473894056.0);
        verify(calculator).squareRoot(97866249624785.0);
        verifyNoMoreInteractions(calculator);

        verify(spyModel, times(2)).chat(chatRequestCaptor.capture(), any());
        List<ChatRequest> allChatRequests = chatRequestCaptor.getAllValues();

        List<ChatMessage> firstGenerateSendMessages = allChatRequests.get(0).messages();
        assertThat(firstGenerateSendMessages).hasSize(1);
        assertThat(firstGenerateSendMessages.get(0).type()).isEqualTo(ChatMessageType.USER);
        assertThat(((UserMessage) firstGenerateSendMessages.get(0)).singleText()).isEqualTo(userMessage);

        List<ChatMessage> secondGenerateSendMessages = allChatRequests.get(1).messages();
        assertThat(secondGenerateSendMessages).hasSize(4);
        assertThat(secondGenerateSendMessages.get(0).type()).isEqualTo(ChatMessageType.USER);
        assertThat(((UserMessage) secondGenerateSendMessages.get(0)).singleText()).isEqualTo(userMessage);
        assertThat(secondGenerateSendMessages.get(1).type()).isEqualTo(ChatMessageType.AI);
        assertThat(secondGenerateSendMessages.get(2).type()).isEqualTo(ChatMessageType.TOOL_EXECUTION_RESULT);
        assertThat(secondGenerateSendMessages.get(3).type()).isEqualTo(ChatMessageType.TOOL_EXECUTION_RESULT);
    }
}
