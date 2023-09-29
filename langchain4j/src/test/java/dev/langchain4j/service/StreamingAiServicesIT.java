package dev.langchain4j.service;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static dev.langchain4j.model.output.FinishReason.STOP;
import static java.time.Duration.ofSeconds;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

public class StreamingAiServicesIT {

    StreamingChatLanguageModel streamingChatModel = OpenAiStreamingChatModel.builder()
            .apiKey(System.getenv("OPENAI_API_KEY"))
            .timeout(ofSeconds(30))
            .logRequests(true)
            .logResponses(true)
            .build();

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

        @Tool
        double squareRoot(double number) {
            return Math.sqrt(number);
        }
    }

    @Test
    void should_execute_tool_then_stream_answer() throws Exception {

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

        // tool arguments provided by the model (which contribute to the input token count)
        // can have different formatting (extra spaces) which results in one extra/missing token
        assertThat(response.tokenUsage().inputTokenCount()).isBetween(146, 147);
        assertThat(response.tokenUsage().outputTokenCount()).isGreaterThan(1);
        assertThat(response.tokenUsage().totalTokenCount()).isGreaterThan(148);

        assertThat(response.finishReason()).isEqualTo(STOP);


        verify(calculator).squareRoot(485906798473894056.0);
        verifyNoMoreInteractions(calculator);


        List<ChatMessage> messages = chatMemory.messages();
        assertThat(messages).hasSize(4);

        assertThat(messages.get(0)).isInstanceOf(UserMessage.class);
        assertThat(messages.get(0).text()).isEqualTo(userMessage);

        assertThat(messages.get(1)).isInstanceOf(AiMessage.class);
        AiMessage aiMessage = (AiMessage) messages.get(1);
        assertThat(aiMessage.toolExecutionRequest().name()).isEqualTo("squareRoot");
        assertThat(aiMessage.toolExecutionRequest().arguments())
                .isEqualToIgnoringWhitespace("{\"arg0\": 485906798473894056}");
        assertThat(messages.get(1).text()).isNull();

        assertThat(messages.get(2)).isInstanceOf(ToolExecutionResultMessage.class);
        assertThat(messages.get(2).text()).isEqualTo("6.97070153193991E8");

        assertThat(messages.get(3)).isInstanceOf(AiMessage.class);
        assertThat(messages.get(3).text()).contains("6.97");
    }
}
