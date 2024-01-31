package dev.langchain4j.service;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import org.assertj.core.data.Percentage;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static dev.langchain4j.model.openai.OpenAiModelName.GPT_3_5_TURBO_1106;
import static dev.langchain4j.model.output.FinishReason.STOP;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Percentage.withPercentage;
import static org.mockito.Mockito.*;

public class StreamingAiServicesIT {

    StreamingChatLanguageModel streamingChatModel = OpenAiStreamingChatModel.builder()
            .baseUrl(System.getenv("OPENAI_BASE_URL"))
            .apiKey(System.getenv("OPENAI_API_KEY"))
            .organizationId(System.getenv("OPENAI_ORGANIZATION_ID"))
            .temperature(0.0)
            .logRequests(true)
            .logResponses(true)
            .build();

    Percentage tokenizerPrecision = withPercentage(5);

    interface Assistant {

        TokenStream chat(String userMessage);
    }

    @Test
    void should_stream_answer() throws Exception {

        Assistant assistant = AiServices.create(Assistant.class, streamingChatModel);

        StringBuilder answerBuilder = new StringBuilder();
        CompletableFuture<String> futureAnswer = new CompletableFuture<>();
        CompletableFuture<Response<AiMessage>> futureResponse = new CompletableFuture<>();

        assistant.chat("What is the capital of Germany?")
                .onNext(answerBuilder::append)
                .onComplete(response -> {
                    futureAnswer.complete(answerBuilder.toString());
                    futureResponse.complete(response);
                })
                .onError(futureAnswer::completeExceptionally)
                .start();

        String answer = futureAnswer.get(30, SECONDS);
        Response<AiMessage> response = futureResponse.get(30, SECONDS);

        assertThat(answer).contains("Berlin");
        assertThat(response.content().text()).isEqualTo(answer);

        assertThat(response.tokenUsage().inputTokenCount()).isEqualTo(14);
        assertThat(response.tokenUsage().outputTokenCount()).isGreaterThan(1);
        assertThat(response.tokenUsage().totalTokenCount()).isGreaterThan(15);

        assertThat(response.finishReason()).isEqualTo(STOP);
    }

    @Test
    void should_stream_answers_with_memory() throws Exception {

        ChatMemory chatMemory = MessageWindowChatMemory.withMaxMessages(10);

        Assistant assistant = AiServices.builder(Assistant.class)
                .streamingChatLanguageModel(streamingChatModel)
                .chatMemory(chatMemory)
                .build();


        String firstUserMessage = "Hi, my name is Klaus";
        CompletableFuture<Response<AiMessage>> firstResultFuture = new CompletableFuture<>();

        assistant.chat(firstUserMessage)
                .onNext(System.out::println)
                .onComplete(firstResultFuture::complete)
                .onError(firstResultFuture::completeExceptionally)
                .start();

        Response<AiMessage> firstResponse = firstResultFuture.get(30, SECONDS);
        assertThat(firstResponse.content().text()).contains("Klaus");


        String secondUserMessage = "What is my name?";
        CompletableFuture<Response<AiMessage>> secondResultFuture = new CompletableFuture<>();

        assistant.chat(secondUserMessage)
                .onNext(System.out::println)
                .onComplete(secondResultFuture::complete)
                .onError(secondResultFuture::completeExceptionally)
                .start();

        Response<AiMessage> secondResponse = secondResultFuture.get(30, SECONDS);
        assertThat(secondResponse.content().text()).contains("Klaus");


        List<ChatMessage> messages = chatMemory.messages();
        assertThat(messages).hasSize(4);

        assertThat(messages.get(0)).isInstanceOf(UserMessage.class);
        assertThat(messages.get(0).text()).isEqualTo(firstUserMessage);

        assertThat(messages.get(1)).isInstanceOf(AiMessage.class);
        assertThat(messages.get(1)).isEqualTo(firstResponse.content());

        assertThat(messages.get(2)).isInstanceOf(UserMessage.class);
        assertThat(messages.get(2).text()).isEqualTo(secondUserMessage);

        assertThat(messages.get(3)).isInstanceOf(AiMessage.class);
        assertThat(messages.get(3)).isEqualTo(secondResponse.content());
    }

    static class Calculator {

        @Tool("calculates the square root of the provided number")
        double squareRoot(@P("number to operate on") double number) {
            return Math.sqrt(number);
        }
    }

    @Test
    void should_execute_a_tool_then_stream_answer() throws Exception {

        Calculator calculator = spy(new Calculator());

        ChatMemory chatMemory = MessageWindowChatMemory.withMaxMessages(10);

        Assistant assistant = AiServices.builder(Assistant.class)
                .streamingChatLanguageModel(streamingChatModel)
                .chatMemory(chatMemory)
                .tools(calculator)
                .build();

        StringBuilder answerBuilder = new StringBuilder();
        CompletableFuture<String> futureAnswer = new CompletableFuture<>();
        CompletableFuture<Response<AiMessage>> futureResponse = new CompletableFuture<>();

        String userMessage = "What is the square root of 485906798473894056 in scientific notation?";

        assistant.chat(userMessage)
                .onNext(answerBuilder::append)
                .onComplete(response -> {
                    futureAnswer.complete(answerBuilder.toString());
                    futureResponse.complete(response);
                })
                .onError(futureAnswer::completeExceptionally)
                .start();

        String answer = futureAnswer.get(30, SECONDS);
        Response<AiMessage> response = futureResponse.get(30, SECONDS);

        assertThat(answer).contains("6.97");
        assertThat(response.content().text()).isEqualTo(answer);

        TokenUsage tokenUsage = response.tokenUsage();
        assertThat(tokenUsage.inputTokenCount()).isCloseTo(72 + 110, tokenizerPrecision);
        assertThat(tokenUsage.outputTokenCount()).isCloseTo(21 + 28, tokenizerPrecision);
        assertThat(tokenUsage.totalTokenCount())
                .isEqualTo(tokenUsage.inputTokenCount() + tokenUsage.outputTokenCount());

        assertThat(response.finishReason()).isEqualTo(STOP);


        verify(calculator).squareRoot(485906798473894056.0);
        verifyNoMoreInteractions(calculator);


        List<ChatMessage> messages = chatMemory.messages();
        assertThat(messages).hasSize(4);

        assertThat(messages.get(0)).isInstanceOf(UserMessage.class);
        assertThat(messages.get(0).text()).isEqualTo(userMessage);

        AiMessage aiMessage = (AiMessage) messages.get(1);
        assertThat(aiMessage.text()).isNull();
        assertThat(aiMessage.toolExecutionRequests()).hasSize(1);
        ToolExecutionRequest toolExecutionRequest = aiMessage.toolExecutionRequests().get(0);
        assertThat(toolExecutionRequest.id()).isNotBlank();
        assertThat(toolExecutionRequest.name()).isEqualTo("squareRoot");
        assertThat(toolExecutionRequest.arguments())
                .isEqualToIgnoringWhitespace("{\"arg0\": 485906798473894056}");

        ToolExecutionResultMessage toolExecutionResultMessage = (ToolExecutionResultMessage) messages.get(2);
        assertThat(toolExecutionResultMessage.id()).isEqualTo(toolExecutionRequest.id());
        assertThat(toolExecutionResultMessage.toolName()).isEqualTo("squareRoot");
        assertThat(toolExecutionResultMessage.text()).isEqualTo("6.97070153193991E8");

        assertThat(messages.get(3)).isInstanceOf(AiMessage.class);
        assertThat(messages.get(3).text()).contains("6.97");
    }

    @Test
    void should_execute_multiple_tools_sequentially_then_answer() throws Exception {

        Calculator calculator = spy(new Calculator());

        ChatMemory chatMemory = MessageWindowChatMemory.withMaxMessages(10);

        Assistant assistant = AiServices.builder(Assistant.class)
                .streamingChatLanguageModel(streamingChatModel)
                .chatMemory(chatMemory)
                .tools(calculator)
                .build();

        StringBuilder answerBuilder = new StringBuilder();
        CompletableFuture<String> futureAnswer = new CompletableFuture<>();
        CompletableFuture<Response<AiMessage>> futureResponse = new CompletableFuture<>();

        String userMessage = "What is the square root of 485906798473894056 and 97866249624785 in scientific notation?";

        assistant.chat(userMessage)
                .onNext(answerBuilder::append)
                .onComplete(response -> {
                    futureAnswer.complete(answerBuilder.toString());
                    futureResponse.complete(response);
                })
                .onError(futureAnswer::completeExceptionally)
                .start();

        String answer = futureAnswer.get(30, SECONDS);
        Response<AiMessage> response = futureResponse.get(30, SECONDS);

        assertThat(answer).contains("6.97", "9.89");
        assertThat(response.content().text()).isEqualTo(answer);

        TokenUsage tokenUsage = response.tokenUsage();
        assertThat(tokenUsage.inputTokenCount()).isCloseTo(79 + 117 + 152, tokenizerPrecision);
        assertThat(tokenUsage.outputTokenCount()).isCloseTo(21 + 20 + 53, tokenizerPrecision);
        assertThat(tokenUsage.totalTokenCount())
                .isEqualTo(tokenUsage.inputTokenCount() + tokenUsage.outputTokenCount());

        assertThat(response.finishReason()).isEqualTo(STOP);


        verify(calculator).squareRoot(485906798473894056.0);
        verify(calculator).squareRoot(97866249624785.0);
        verifyNoMoreInteractions(calculator);


        List<ChatMessage> messages = chatMemory.messages();
        assertThat(messages).hasSize(6);

        assertThat(messages.get(0)).isInstanceOf(dev.langchain4j.data.message.UserMessage.class);
        assertThat(messages.get(0).text()).isEqualTo(userMessage);

        AiMessage aiMessage = (AiMessage) messages.get(1);
        assertThat(aiMessage.text()).isNull();
        assertThat(aiMessage.toolExecutionRequests()).hasSize(1);
        ToolExecutionRequest toolExecutionRequest = aiMessage.toolExecutionRequests().get(0);
        assertThat(toolExecutionRequest.id()).isNotBlank();
        assertThat(toolExecutionRequest.name()).isEqualTo("squareRoot");
        assertThat(toolExecutionRequest.arguments())
                .isEqualToIgnoringWhitespace("{\"arg0\": 485906798473894056}");

        ToolExecutionResultMessage toolExecutionResultMessage = (ToolExecutionResultMessage) messages.get(2);
        assertThat(toolExecutionResultMessage.id()).isEqualTo(toolExecutionRequest.id());
        assertThat(toolExecutionResultMessage.toolName()).isEqualTo("squareRoot");
        assertThat(toolExecutionResultMessage.text()).isEqualTo("6.97070153193991E8");

        AiMessage secondAiMessage = (AiMessage) messages.get(3);
        assertThat(secondAiMessage.text()).isNull();
        assertThat(secondAiMessage.toolExecutionRequests()).hasSize(1);
        ToolExecutionRequest secondToolExecutionRequest = secondAiMessage.toolExecutionRequests().get(0);
        assertThat(secondToolExecutionRequest.id()).isNotBlank();
        assertThat(secondToolExecutionRequest.name()).isEqualTo("squareRoot");
        assertThat(secondToolExecutionRequest.arguments())
                .isEqualToIgnoringWhitespace("{\"arg0\": 97866249624785}");

        ToolExecutionResultMessage secondToolExecutionResultMessage = (ToolExecutionResultMessage) messages.get(4);
        assertThat(secondToolExecutionResultMessage.id()).isEqualTo(secondToolExecutionRequest.id());
        assertThat(secondToolExecutionResultMessage.toolName()).isEqualTo("squareRoot");
        assertThat(secondToolExecutionResultMessage.text()).isEqualTo("9892737.215997653");

        assertThat(messages.get(5)).isInstanceOf(AiMessage.class);
        assertThat(messages.get(5).text()).contains("6.97", "9.89");
    }

    @Test
    void should_execute_multiple_tools_in_parallel_then_answer() throws Exception {

        Calculator calculator = spy(new Calculator());

        StreamingChatLanguageModel streamingChatModel = OpenAiStreamingChatModel.builder()
                .baseUrl(System.getenv("OPENAI_BASE_URL"))
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .organizationId(System.getenv("OPENAI_ORGANIZATION_ID"))
                .modelName(GPT_3_5_TURBO_1106)
                .temperature(0.0)
                .logRequests(true)
                .logResponses(true)
                .build();

        ChatMemory chatMemory = MessageWindowChatMemory.withMaxMessages(10);

        Assistant assistant = AiServices.builder(Assistant.class)
                .streamingChatLanguageModel(streamingChatModel)
                .chatMemory(chatMemory)
                .tools(calculator)
                .build();

        StringBuilder answerBuilder = new StringBuilder();
        CompletableFuture<String> futureAnswer = new CompletableFuture<>();
        CompletableFuture<Response<AiMessage>> futureResponse = new CompletableFuture<>();

        String userMessage = "What is the square root of 485906798473894056 and 97866249624785 in scientific notation?";

        assistant.chat(userMessage)
                .onNext(answerBuilder::append)
                .onComplete(response -> {
                    futureAnswer.complete(answerBuilder.toString());
                    futureResponse.complete(response);
                })
                .onError(futureAnswer::completeExceptionally)
                .start();

        String answer = futureAnswer.get(30, SECONDS);
        Response<AiMessage> response = futureResponse.get(30, SECONDS);

        assertThat(answer).contains("6.97", "9.89");
        assertThat(response.content().text()).isEqualTo(answer);

        TokenUsage tokenUsage = response.tokenUsage();
        assertThat(tokenUsage.inputTokenCount()).isCloseTo(79 + 160, tokenizerPrecision);
        assertThat(tokenUsage.outputTokenCount()).isCloseTo(54 + 58, tokenizerPrecision);
        assertThat(tokenUsage.totalTokenCount())
                .isEqualTo(tokenUsage.inputTokenCount() + tokenUsage.outputTokenCount());

        assertThat(response.finishReason()).isEqualTo(STOP);


        verify(calculator).squareRoot(485906798473894056.0);
        verify(calculator).squareRoot(97866249624785.0);
        verifyNoMoreInteractions(calculator);


        List<ChatMessage> messages = chatMemory.messages();
        assertThat(messages).hasSize(5);

        assertThat(messages.get(0)).isInstanceOf(dev.langchain4j.data.message.UserMessage.class);
        assertThat(messages.get(0).text()).isEqualTo(userMessage);

        AiMessage aiMessage = (AiMessage) messages.get(1);
        assertThat(aiMessage.text()).isNull();
        assertThat(aiMessage.toolExecutionRequests()).hasSize(2);

        ToolExecutionRequest firstToolExecutionRequest = aiMessage.toolExecutionRequests().get(0);
        assertThat(firstToolExecutionRequest.id()).isNotBlank();
        assertThat(firstToolExecutionRequest.name()).isEqualTo("squareRoot");
        assertThat(firstToolExecutionRequest.arguments())
                .isEqualToIgnoringWhitespace("{\"arg0\": 485906798473894056}");

        ToolExecutionRequest secondToolExecutionRequest = aiMessage.toolExecutionRequests().get(1);
        assertThat(secondToolExecutionRequest.id()).isNotBlank();
        assertThat(secondToolExecutionRequest.name()).isEqualTo("squareRoot");
        assertThat(secondToolExecutionRequest.arguments())
                .isEqualToIgnoringWhitespace("{\"arg0\": 97866249624785}");

        ToolExecutionResultMessage firstToolExecutionResultMessage = (ToolExecutionResultMessage) messages.get(2);
        assertThat(firstToolExecutionResultMessage.id()).isEqualTo(firstToolExecutionRequest.id());
        assertThat(firstToolExecutionResultMessage.toolName()).isEqualTo("squareRoot");
        assertThat(firstToolExecutionResultMessage.text()).isEqualTo("6.97070153193991E8");

        ToolExecutionResultMessage secondToolExecutionResultMessage = (ToolExecutionResultMessage) messages.get(3);
        assertThat(secondToolExecutionResultMessage.id()).isEqualTo(secondToolExecutionRequest.id());
        assertThat(secondToolExecutionResultMessage.toolName()).isEqualTo("squareRoot");
        assertThat(secondToolExecutionResultMessage.text()).isEqualTo("9892737.215997653");

        assertThat(messages.get(4)).isInstanceOf(AiMessage.class);
        assertThat(messages.get(4).text()).contains("6.97", "9.89");
    }
}
