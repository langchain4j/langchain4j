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
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.rag.AugmentationResult;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.rag.content.Content;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import static dev.langchain4j.model.openai.OpenAiChatModelName.GPT_4_O_MINI;
import static dev.langchain4j.model.output.FinishReason.STOP;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class StreamingAiServicesIT {

    static Stream<StreamingChatModel> models() {
        return Stream.of(
                OpenAiStreamingChatModel.builder()
                        .baseUrl(System.getenv("OPENAI_BASE_URL"))
                        .apiKey(System.getenv("OPENAI_API_KEY"))
                        .organizationId(System.getenv("OPENAI_ORGANIZATION_ID"))
                        .modelName(GPT_4_O_MINI)
                        .logRequests(true)
                        .logResponses(true)
                        .build()
                // TODO add more models
        );
    }

    interface Assistant {

        TokenStream chat(String userMessage);
    }

    @ParameterizedTest
    @MethodSource("models")
    void should_stream_answer(StreamingChatModel model) throws Exception {

        Assistant assistant = AiServices.create(Assistant.class, model);

        StringBuilder answerBuilder = new StringBuilder();
        CompletableFuture<String> futureAnswer = new CompletableFuture<>();
        CompletableFuture<ChatResponse> futureResponse = new CompletableFuture<>();

        assistant.chat("What is the capital of Germany?")
                .onPartialResponse(answerBuilder::append)
                .onCompleteResponse(response -> {
                    futureAnswer.complete(answerBuilder.toString());
                    futureResponse.complete(response);
                })
                .onError(futureAnswer::completeExceptionally)
                .start();

        String answer = futureAnswer.get(30, SECONDS);
        ChatResponse response = futureResponse.get(30, SECONDS);

        assertThat(answer).contains("Berlin");
        assertThat(response.aiMessage().text()).isEqualTo(answer);

        TokenUsage tokenUsage = response.tokenUsage();
        assertThat(tokenUsage.inputTokenCount()).isPositive();
        assertThat(tokenUsage.outputTokenCount()).isPositive();
        assertThat(tokenUsage.totalTokenCount())
                .isEqualTo(tokenUsage.inputTokenCount() + tokenUsage.outputTokenCount());

        assertThat(response.finishReason()).isEqualTo(STOP);
    }

    @ParameterizedTest
    @MethodSource("models")
    void should_callback_with_content(StreamingChatModel model) throws Exception {

        List<Content> contents = new ArrayList<>();
        contents.add(Content.from("This is additional content"));

        RetrievalAugmentor retrievalAugmentor = mock(RetrievalAugmentor.class);
        ChatMessage message = UserMessage.from("What is the capital of Germany?");

        AugmentationResult result = AugmentationResult.builder()
                .chatMessage(message)
                .contents(contents)
                .build();
        when(retrievalAugmentor.augment(any())).thenReturn(result);

        Assistant assistant = AiServices.builder(Assistant.class)
                .streamingChatModel(model)
                .retrievalAugmentor(retrievalAugmentor)
                .build();

        CompletableFuture<List<Content>> futureContent = new CompletableFuture<>();

        assistant.chat("What is the capital of Germany?")
                .onPartialResponse(ignored -> {})
                .onRetrieved(futureContent::complete)
                .ignoreErrors()
                .start();

        List<Content> returnedContents = futureContent.get(30, SECONDS);

        assertThat(returnedContents)
                .hasSize(1)
                .anySatisfy(c -> assertThat(c.textSegment().text()).isEqualTo("This is additional content"));
    }

    @ParameterizedTest
    @MethodSource("models")
    void should_stream_answers_with_memory(StreamingChatModel model) throws Exception {

        ChatMemory chatMemory = MessageWindowChatMemory.withMaxMessages(10);

        Assistant assistant = AiServices.builder(Assistant.class)
                .streamingChatModel(model)
                .chatMemory(chatMemory)
                .build();


        String firstUserMessage = "Hi, my name is Klaus";
        CompletableFuture<ChatResponse> firstResponseFuture = new CompletableFuture<>();

        assistant.chat(firstUserMessage)
                .onPartialResponse(System.out::println)
                .onCompleteResponse(firstResponseFuture::complete)
                .onError(firstResponseFuture::completeExceptionally)
                .start();

        ChatResponse firstResponse = firstResponseFuture.get(30, SECONDS);
        assertThat(firstResponse.aiMessage().text()).contains("Klaus");


        String secondUserMessage = "What is my name?";
        CompletableFuture<ChatResponse> secondResponseFuture = new CompletableFuture<>();

        assistant.chat(secondUserMessage)
                .onPartialResponse(System.out::println)
                .onCompleteResponse(secondResponseFuture::complete)
                .onError(secondResponseFuture::completeExceptionally)
                .start();

        ChatResponse secondResponse = secondResponseFuture.get(30, SECONDS);
        assertThat(secondResponse.aiMessage().text()).contains("Klaus");


        List<ChatMessage> messages = chatMemory.messages();
        assertThat(messages).hasSize(4);

        assertThat(messages.get(0)).isInstanceOf(UserMessage.class);
        assertThat(((UserMessage) messages.get(0)).singleText()).isEqualTo(firstUserMessage);

        assertThat(messages.get(1)).isInstanceOf(AiMessage.class);
        assertThat(messages.get(1)).isEqualTo(firstResponse.aiMessage());

        assertThat(messages.get(2)).isInstanceOf(UserMessage.class);
        assertThat(((UserMessage) messages.get(2)).singleText()).isEqualTo(secondUserMessage);

        assertThat(messages.get(3)).isInstanceOf(AiMessage.class);
        assertThat(messages.get(3)).isEqualTo(secondResponse.aiMessage());
    }

    static class Calculator {

        @Tool("calculates the square root of the provided number")
        double squareRoot(@P("number to operate on") double number) {
            return Math.sqrt(number);
        }
    }

    @ParameterizedTest
    @MethodSource("models")
    void should_execute_a_tool_then_stream_answer(StreamingChatModel model) throws Exception {

        Calculator calculator = spy(new Calculator());

        ChatMemory chatMemory = MessageWindowChatMemory.withMaxMessages(10);

        Assistant assistant = AiServices.builder(Assistant.class)
                .streamingChatModel(model)
                .chatMemory(chatMemory)
                .tools(calculator)
                .build();

        StringBuilder answerBuilder = new StringBuilder();
        CompletableFuture<String> futureAnswer = new CompletableFuture<>();
        CompletableFuture<ChatResponse> futureResponse = new CompletableFuture<>();

        String userMessage = "What is the square root of 485906798473894056 in scientific notation?";

        assistant.chat(userMessage)
                .onPartialResponse(answerBuilder::append)
                .onCompleteResponse(response -> {
                    futureAnswer.complete(answerBuilder.toString());
                    futureResponse.complete(response);
                })
                .onError(futureAnswer::completeExceptionally)
                .start();

        String answer = futureAnswer.get(30, SECONDS);
        ChatResponse response = futureResponse.get(30, SECONDS);

        assertThat(answer).contains("6.97");
        assertThat(response.aiMessage().text()).isEqualTo(answer);

        TokenUsage tokenUsage = response.tokenUsage();
        assertThat(tokenUsage.inputTokenCount()).isPositive();
        assertThat(tokenUsage.outputTokenCount()).isPositive();
        assertThat(tokenUsage.totalTokenCount())
                .isEqualTo(tokenUsage.inputTokenCount() + tokenUsage.outputTokenCount());

        assertThat(response.finishReason()).isEqualTo(STOP);


        verify(calculator).squareRoot(485906798473894056.0);
        verifyNoMoreInteractions(calculator);


        List<ChatMessage> messages = chatMemory.messages();
        assertThat(messages).hasSize(4);

        assertThat(messages.get(0)).isInstanceOf(UserMessage.class);
        assertThat(((UserMessage) messages.get(0)).singleText()).isEqualTo(userMessage);

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
        assertThat(((AiMessage) messages.get(3)).text()).contains("6.97");
    }

    @Test
    void should_execute_multiple_tools_sequentially_then_answer() throws Exception {

        // TODO test more models
        StreamingChatModel streamingChatModel = OpenAiStreamingChatModel.builder()
                .baseUrl(System.getenv("OPENAI_BASE_URL"))
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .organizationId(System.getenv("OPENAI_ORGANIZATION_ID"))
                .modelName(GPT_4_O_MINI)
                .parallelToolCalls(false) // to force the model to call tools sequentially
                .temperature(0.0)
                .logRequests(true)
                .logResponses(true)
                .build();

        Calculator calculator = spy(new Calculator());

        ChatMemory chatMemory = MessageWindowChatMemory.withMaxMessages(10);

        Assistant assistant = AiServices.builder(Assistant.class)
                .streamingChatModel(streamingChatModel)
                .chatMemory(chatMemory)
                .tools(calculator)
                .build();

        StringBuilder answerBuilder = new StringBuilder();
        CompletableFuture<String> futureAnswer = new CompletableFuture<>();
        CompletableFuture<ChatResponse> futureResponse = new CompletableFuture<>();

        String userMessage = "What is the square root of 485906798473894056 and 97866249624785 in scientific notation?";

        assistant.chat(userMessage)
                .onPartialResponse(answerBuilder::append)
                .onCompleteResponse(response -> {
                    futureAnswer.complete(answerBuilder.toString());
                    futureResponse.complete(response);
                })
                .onError(futureAnswer::completeExceptionally)
                .start();

        String answer = futureAnswer.get(30, SECONDS);
        ChatResponse response = futureResponse.get(30, SECONDS);

        assertThat(answer).contains("6.97", "9.89");
        assertThat(response.aiMessage().text()).isEqualTo(answer);

        TokenUsage tokenUsage = response.tokenUsage();
        assertThat(tokenUsage.inputTokenCount()).isPositive(); // TODO
        assertThat(tokenUsage.outputTokenCount()).isPositive();
        assertThat(tokenUsage.totalTokenCount())
                .isEqualTo(tokenUsage.inputTokenCount() + tokenUsage.outputTokenCount());

        assertThat(response.finishReason()).isEqualTo(STOP);


        verify(calculator).squareRoot(485906798473894056.0);
        verify(calculator).squareRoot(97866249624785.0);
        verifyNoMoreInteractions(calculator);


        List<ChatMessage> messages = chatMemory.messages();
        assertThat(messages).hasSize(6);

        assertThat(messages.get(0)).isInstanceOf(dev.langchain4j.data.message.UserMessage.class);
        assertThat(((UserMessage) messages.get(0)).singleText()).isEqualTo(userMessage);

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
        assertThat(((AiMessage) messages.get(5)).text()).contains("6.97", "9.89");
    }

    @Test
    void should_execute_multiple_tools_in_parallel_then_answer() throws Exception {

        Calculator calculator = spy(new Calculator());

        // TODO test more models
        StreamingChatModel streamingChatModel = OpenAiStreamingChatModel.builder()
                .baseUrl(System.getenv("OPENAI_BASE_URL"))
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .organizationId(System.getenv("OPENAI_ORGANIZATION_ID"))
                .modelName(GPT_4_O_MINI)
                .temperature(0.0)
                .logRequests(true)
                .logResponses(true)
                .build();

        ChatMemory chatMemory = MessageWindowChatMemory.withMaxMessages(10);

        Assistant assistant = AiServices.builder(Assistant.class)
                .streamingChatModel(streamingChatModel)
                .chatMemory(chatMemory)
                .tools(calculator)
                .build();

        StringBuilder answerBuilder = new StringBuilder();
        CompletableFuture<String> futureAnswer = new CompletableFuture<>();
        CompletableFuture<ChatResponse> futureResponse = new CompletableFuture<>();

        String userMessage = "What is the square root of 485906798473894056 and 97866249624785 in scientific notation?";

        assistant.chat(userMessage)
                .onPartialResponse(answerBuilder::append)
                .onCompleteResponse(response -> {
                    futureAnswer.complete(answerBuilder.toString());
                    futureResponse.complete(response);
                })
                .onError(futureAnswer::completeExceptionally)
                .start();

        String answer = futureAnswer.get(30, SECONDS);
        ChatResponse response = futureResponse.get(30, SECONDS);

        assertThat(answer).contains("6.97", "9.89");
        assertThat(response.aiMessage().text()).isEqualTo(answer);

        TokenUsage tokenUsage = response.tokenUsage();
        assertThat(tokenUsage.inputTokenCount()).isPositive(); // TODO
        assertThat(tokenUsage.outputTokenCount()).isPositive();
        assertThat(tokenUsage.totalTokenCount())
                .isEqualTo(tokenUsage.inputTokenCount() + tokenUsage.outputTokenCount());

        assertThat(response.finishReason()).isEqualTo(STOP);


        verify(calculator).squareRoot(485906798473894056.0);
        verify(calculator).squareRoot(97866249624785.0);
        verifyNoMoreInteractions(calculator);


        List<ChatMessage> messages = chatMemory.messages();
        assertThat(messages).hasSize(5);

        assertThat(messages.get(0)).isInstanceOf(dev.langchain4j.data.message.UserMessage.class);
        assertThat(((UserMessage) messages.get(0)).singleText()).isEqualTo(userMessage);

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
        assertThat(((AiMessage) messages.get(4)).text()).contains("6.97", "9.89");
    }
}
