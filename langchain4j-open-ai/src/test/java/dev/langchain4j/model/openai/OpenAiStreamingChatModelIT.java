package dev.langchain4j.model.openai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.TestStreamingChatResponseHandler;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.output.TokenUsage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Base64;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static dev.langchain4j.data.message.ToolExecutionResultMessage.from;
import static dev.langchain4j.data.message.UserMessage.userMessage;
import static dev.langchain4j.internal.Utils.readBytes;
import static dev.langchain4j.model.chat.request.ToolChoice.REQUIRED;
import static dev.langchain4j.model.openai.OpenAiChatModelIT.CAT_IMAGE_URL;
import static dev.langchain4j.model.openai.OpenAiChatModelIT.DICE_IMAGE_URL;
import static dev.langchain4j.model.openai.OpenAiChatModelName.GPT_4_O_MINI;
import static dev.langchain4j.model.output.FinishReason.LENGTH;
import static dev.langchain4j.model.output.FinishReason.STOP;
import static dev.langchain4j.model.output.FinishReason.TOOL_EXECUTION;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.EnumSource.Mode.EXCLUDE;

@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class OpenAiStreamingChatModelIT {

    OpenAiStreamingChatModel model = OpenAiStreamingChatModel.builder()
            .baseUrl(System.getenv("OPENAI_BASE_URL"))
            .apiKey(System.getenv("OPENAI_API_KEY"))
            .organizationId(System.getenv("OPENAI_ORGANIZATION_ID"))
            .modelName(GPT_4_O_MINI)
            .temperature(0.0)
            .logRequests(true)
            .logResponses(true)
            .build();

    ToolSpecification calculator = ToolSpecification.builder()
            .name("calculator")
            .description("returns a sum of two numbers")
            .parameters(JsonObjectSchema.builder()
                    .addIntegerProperty("first")
                    .addIntegerProperty("second")
                    .required("first", "second")
                    .build())
            .build();

    @Test
    void should_stream_answer() throws Exception {

        CompletableFuture<String> futureAnswer = new CompletableFuture<>();
        CompletableFuture<ChatResponse> futureResponse = new CompletableFuture<>();

        model.chat("What is the capital of Germany?", new StreamingChatResponseHandler() {

            private final StringBuilder answerBuilder = new StringBuilder();

            @Override
            public void onPartialResponse(String partialResponse) {
                answerBuilder.append(partialResponse);
            }

            @Override
            public void onCompleteResponse(ChatResponse completeResponse) {
                futureAnswer.complete(answerBuilder.toString());
                futureResponse.complete(completeResponse);
            }

            @Override
            public void onError(Throwable error) {
                futureAnswer.completeExceptionally(error);
                futureResponse.completeExceptionally(error);
            }
        });

        String answer = futureAnswer.get(30, SECONDS);
        ChatResponse response = futureResponse.get(30, SECONDS);

        assertThat(answer).contains("Berlin");
        assertThat(response.aiMessage().text()).isEqualTo(answer);

        assertTokenUsage(response.tokenUsage());

        assertThat(response.finishReason()).isEqualTo(STOP);
    }

    @Test
    void should_respoct_maxTokens() throws Exception {

        // given
        int maxTokens = 1;

        OpenAiStreamingChatModel model = OpenAiStreamingChatModel.builder()
                .baseUrl(System.getenv("OPENAI_BASE_URL"))
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .organizationId(System.getenv("OPENAI_ORGANIZATION_ID"))
                .modelName(GPT_4_O_MINI)
                .maxTokens(maxTokens)
                .temperature(0.0)
                .logRequests(true)
                .logResponses(true)
                .build();

        CompletableFuture<ChatResponse> futureResponse = new CompletableFuture<>();

        // when
        model.chat("Tell me a long story", new StreamingChatResponseHandler() {

            @Override
            public void onPartialResponse(String partialResponse) {
            }

            @Override
            public void onCompleteResponse(ChatResponse completeResponse) {
                futureResponse.complete(completeResponse);
            }

            @Override
            public void onError(Throwable error) {
                futureResponse.completeExceptionally(error);
            }
        });

        ChatResponse response = futureResponse.get(30, SECONDS);

        // then
        assertThat(response.aiMessage().text()).isNotBlank();
        assertThat(response.tokenUsage().outputTokenCount()).isEqualTo(maxTokens);
        assertThat(response.finishReason()).isEqualTo(LENGTH);
    }

    @Test
    void should_respoct_maxCompletionTokens() throws Exception {

        // given
        int maxCompletionTokens = 1;

        OpenAiStreamingChatModel model = OpenAiStreamingChatModel.builder()
                .baseUrl(System.getenv("OPENAI_BASE_URL"))
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .organizationId(System.getenv("OPENAI_ORGANIZATION_ID"))
                .modelName(GPT_4_O_MINI)
                .maxCompletionTokens(maxCompletionTokens)
                .temperature(0.0)
                .logRequests(true)
                .logResponses(true)
                .build();

        CompletableFuture<ChatResponse> futureResponse = new CompletableFuture<>();

        // when
        model.chat("Tell me a long story", new StreamingChatResponseHandler() {

            @Override
            public void onPartialResponse(String partialResponse) {
            }

            @Override
            public void onCompleteResponse(ChatResponse completeResponse) {
                futureResponse.complete(completeResponse);
            }

            @Override
            public void onError(Throwable error) {
                futureResponse.completeExceptionally(error);
            }
        });

        ChatResponse response = futureResponse.get(30, SECONDS);

        // then
        assertThat(response.aiMessage().text()).isNotBlank();
        assertThat(response.tokenUsage().outputTokenCount()).isEqualTo(maxCompletionTokens);
        assertThat(response.finishReason()).isEqualTo(LENGTH);
    }

    @Test
    void should_execute_a_tool_then_stream_answer() throws Exception {

        // given
        UserMessage userMessage = userMessage("2+2=?");

        ChatRequest chatRequest = ChatRequest.builder()
                .messages(userMessage)
                .toolSpecifications(calculator)
                .build();

        // when
        CompletableFuture<ChatResponse> futureResponse = new CompletableFuture<>();

        model.chat(chatRequest, new StreamingChatResponseHandler() {

            @Override
            public void onPartialResponse(String partialResponse) {
                Exception e = new IllegalStateException("onPartialResponse() should never be called when tool is executed");
                futureResponse.completeExceptionally(e);
            }

            @Override
            public void onCompleteResponse(ChatResponse completeResponse) {
                futureResponse.complete(completeResponse);
            }

            @Override
            public void onError(Throwable error) {
                futureResponse.completeExceptionally(error);
            }
        });

        ChatResponse response = futureResponse.get(30, SECONDS);
        AiMessage aiMessage = response.aiMessage();

        // then
        assertThat(aiMessage.text()).isNull();

        List<ToolExecutionRequest> toolExecutionRequests = aiMessage.toolExecutionRequests();
        assertThat(toolExecutionRequests).hasSize(1);

        ToolExecutionRequest toolExecutionRequest = toolExecutionRequests.get(0);
        assertThat(toolExecutionRequest.name()).isEqualTo("calculator");
        assertThat(toolExecutionRequest.arguments()).isEqualToIgnoringWhitespace("{\"first\": 2, \"second\": 2}");

        assertTokenUsage(response.tokenUsage());

        assertThat(response.finishReason()).isEqualTo(TOOL_EXECUTION);

        // given
        ToolExecutionResultMessage toolExecutionResultMessage = from(toolExecutionRequest, "4");

        List<ChatMessage> messages = asList(userMessage, aiMessage, toolExecutionResultMessage);

        // when
        CompletableFuture<ChatResponse> secondFutureResponse = new CompletableFuture<>();

        model.chat(messages, new StreamingChatResponseHandler() {

            @Override
            public void onPartialResponse(String partialResponse) {
            }

            @Override
            public void onCompleteResponse(ChatResponse completeResponse) {
                secondFutureResponse.complete(completeResponse);
            }

            @Override
            public void onError(Throwable error) {
                secondFutureResponse.completeExceptionally(error);
            }
        });

        ChatResponse secondResponse = secondFutureResponse.get(30, SECONDS);
        AiMessage secondAiMessage = secondResponse.aiMessage();

        // then
        assertThat(secondAiMessage.text()).contains("4");
        assertThat(secondAiMessage.toolExecutionRequests()).isEmpty();

        assertTokenUsage(secondResponse.tokenUsage());

        assertThat(secondResponse.finishReason()).isEqualTo(STOP);
    }

    @Test
    void should_execute_tool_forcefully_then_stream_answer() throws Exception {

        // given
        UserMessage userMessage = userMessage("2+2=?");

        ChatRequest chatRequest = ChatRequest.builder()
                .messages(userMessage)
                .parameters(ChatRequestParameters.builder()
                        .toolSpecifications(calculator)
                        .toolChoice(REQUIRED)
                        .build())
                .build();

        // when
        CompletableFuture<ChatResponse> futureResponse = new CompletableFuture<>();

        model.chat(chatRequest, new StreamingChatResponseHandler() {

            @Override
            public void onPartialResponse(String partialResponse) {
                Exception e = new IllegalStateException("onPartialResponse() should never be called when tool is executed");
                futureResponse.completeExceptionally(e);
            }

            @Override
            public void onCompleteResponse(ChatResponse completeResponse) {
                futureResponse.complete(completeResponse);
            }

            @Override
            public void onError(Throwable error) {
                futureResponse.completeExceptionally(error);
            }
        });

        ChatResponse response = futureResponse.get(30, SECONDS);
        AiMessage aiMessage = response.aiMessage();

        // then
        assertThat(aiMessage.text()).isNull();

        List<ToolExecutionRequest> toolExecutionRequests = aiMessage.toolExecutionRequests();
        assertThat(toolExecutionRequests).hasSize(1);

        ToolExecutionRequest toolExecutionRequest = toolExecutionRequests.get(0);
        assertThat(toolExecutionRequest.name()).isEqualTo("calculator");
        assertThat(toolExecutionRequest.arguments()).isEqualToIgnoringWhitespace("{\"first\": 2, \"second\": 2}");

        assertTokenUsage(response.tokenUsage());

        assertThat(response.finishReason()).isEqualTo(TOOL_EXECUTION);

        // given
        ToolExecutionResultMessage toolExecutionResultMessage = from(toolExecutionRequest, "4");

        List<ChatMessage> messages = asList(userMessage, aiMessage, toolExecutionResultMessage);

        // when
        CompletableFuture<ChatResponse> secondFutureResponse = new CompletableFuture<>();

        model.chat(messages, new StreamingChatResponseHandler() {

            @Override
            public void onPartialResponse(String partialResponse) {
            }

            @Override
            public void onCompleteResponse(ChatResponse completeResponse) {
                secondFutureResponse.complete(completeResponse);
            }

            @Override
            public void onError(Throwable error) {
                secondFutureResponse.completeExceptionally(error);
            }
        });

        ChatResponse secondResponse = secondFutureResponse.get(30, SECONDS);
        AiMessage secondAiMessage = secondResponse.aiMessage();

        // then
        assertThat(secondAiMessage.text()).contains("4");
        assertThat(secondAiMessage.toolExecutionRequests()).isEmpty();

        assertTokenUsage(secondResponse.tokenUsage());

        assertThat(secondResponse.finishReason()).isEqualTo(STOP);
    }

    @Test
    void should_execute_multiple_tools_in_parallel_then_stream_answer() throws Exception {

        // given
        StreamingChatModel model = OpenAiStreamingChatModel.builder()
                .baseUrl(System.getenv("OPENAI_BASE_URL"))
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .organizationId(System.getenv("OPENAI_ORGANIZATION_ID"))
                .modelName(GPT_4_O_MINI)
                .temperature(0.0)
                .logRequests(true)
                .logResponses(true)
                .build();

        UserMessage userMessage = userMessage("2+2=? 3+3=?");

        ChatRequest chatRequest = ChatRequest.builder()
                .messages(userMessage)
                .toolSpecifications(calculator)
                .build();

        // when
        CompletableFuture<ChatResponse> futureResponse = new CompletableFuture<>();

        model.chat(chatRequest, new StreamingChatResponseHandler() {

            @Override
            public void onPartialResponse(String partialResponse) {
                Exception e = new IllegalStateException("onPartialResponse() should never be called when tool is executed");
                futureResponse.completeExceptionally(e);
            }

            @Override
            public void onCompleteResponse(ChatResponse completeResponse) {
                futureResponse.complete(completeResponse);
            }

            @Override
            public void onError(Throwable error) {
                futureResponse.completeExceptionally(error);
            }
        });

        ChatResponse response = futureResponse.get(30, SECONDS);
        AiMessage aiMessage = response.aiMessage();

        // then
        assertThat(aiMessage.text()).isNull();
        assertThat(aiMessage.toolExecutionRequests()).hasSize(2);

        ToolExecutionRequest toolExecutionRequest1 =
                aiMessage.toolExecutionRequests().get(0);
        assertThat(toolExecutionRequest1.name()).isEqualTo("calculator");
        assertThat(toolExecutionRequest1.arguments()).isEqualToIgnoringWhitespace("{\"first\": 2, \"second\": 2}");

        ToolExecutionRequest toolExecutionRequest2 =
                aiMessage.toolExecutionRequests().get(1);
        assertThat(toolExecutionRequest2.name()).isEqualTo("calculator");
        assertThat(toolExecutionRequest2.arguments()).isEqualToIgnoringWhitespace("{\"first\": 3, \"second\": 3}");

        assertTokenUsage(response.tokenUsage());

        assertThat(response.finishReason()).isEqualTo(TOOL_EXECUTION);

        // given
        ToolExecutionResultMessage toolExecutionResultMessage1 = from(toolExecutionRequest1, "4");
        ToolExecutionResultMessage toolExecutionResultMessage2 = from(toolExecutionRequest2, "6");

        List<ChatMessage> messages =
                asList(userMessage, aiMessage, toolExecutionResultMessage1, toolExecutionResultMessage2);

        // when
        CompletableFuture<ChatResponse> secondFutureResponse = new CompletableFuture<>();

        model.chat(messages, new StreamingChatResponseHandler() {

            @Override
            public void onPartialResponse(String partialResponse) {
            }

            @Override
            public void onCompleteResponse(ChatResponse completeResponse) {
                secondFutureResponse.complete(completeResponse);
            }

            @Override
            public void onError(Throwable error) {
                secondFutureResponse.completeExceptionally(error);
            }
        });

        ChatResponse secondResponse = secondFutureResponse.get(30, SECONDS);
        AiMessage secondAiMessage = secondResponse.aiMessage();

        // then
        assertThat(secondAiMessage.text()).contains("4", "6");
        assertThat(secondAiMessage.toolExecutionRequests()).isEmpty();

        assertTokenUsage(secondResponse.tokenUsage());

        assertThat(secondResponse.finishReason()).isEqualTo(STOP);
    }

    @Test
    void should_stream_valid_json() throws JsonProcessingException {

        // given
        @JsonIgnoreProperties(ignoreUnknown = true) // to ignore the "joke" field
        record Person(String name, String surname) {
        }

        String responseFormat = "json_object";

        String userMessage = "Return JSON with two fields: name and surname of Klaus Heisler. "
                + "Before returning, tell me a joke."; // nudging it to say something additionally to json

        StreamingChatModel model = OpenAiStreamingChatModel.builder()
                .baseUrl(System.getenv("OPENAI_BASE_URL"))
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .organizationId(System.getenv("OPENAI_ORGANIZATION_ID"))
                .modelName(GPT_4_O_MINI)
                .responseFormat(responseFormat)
                .temperature(0.0)
                .logRequests(true)
                .logResponses(true)
                .build();

        // when
        TestStreamingChatResponseHandler handler = new TestStreamingChatResponseHandler();
        model.chat(userMessage, handler);
        ChatResponse response = handler.get();

        // then
        Person person = new ObjectMapper().readValue(response.aiMessage().text(), Person.class);
        assertThat(person.name).isEqualTo("Klaus");
        assertThat(person.surname).isEqualTo("Heisler");
    }

    @Test
    void should_accept_image_url() {

        // given
        ImageContent imageContent = ImageContent.from(CAT_IMAGE_URL);
        UserMessage userMessage = UserMessage.from(imageContent);

        // when
        TestStreamingChatResponseHandler handler = new TestStreamingChatResponseHandler();
        model.chat(singletonList(userMessage), handler);
        ChatResponse response = handler.get();

        // then
        assertThat(response.aiMessage().text()).containsIgnoringCase("cat");
    }

    @Test
    void should_accept_base64_image() {

        // given
        String base64Data = Base64.getEncoder().encodeToString(readBytes(CAT_IMAGE_URL));
        ImageContent imageContent = ImageContent.from(base64Data, "image/png");
        UserMessage userMessage = UserMessage.from(imageContent);

        // when
        TestStreamingChatResponseHandler handler = new TestStreamingChatResponseHandler();
        model.chat(singletonList(userMessage), handler);
        ChatResponse response = handler.get();

        // then
        assertThat(response.aiMessage().text()).containsIgnoringCase("cat");
    }

    @Test
    void should_accept_text_and_image() {

        // given
        UserMessage userMessage = UserMessage.from(
                TextContent.from("What do you see? Reply in one word."), ImageContent.from(CAT_IMAGE_URL));

        // when
        TestStreamingChatResponseHandler handler = new TestStreamingChatResponseHandler();
        model.chat(singletonList(userMessage), handler);
        ChatResponse response = handler.get();

        // then
        assertThat(response.aiMessage().text()).containsIgnoringCase("cat");
    }

    @Test
    void should_accept_text_and_multiple_images() {

        // given
        UserMessage userMessage = UserMessage.from(
                TextContent.from("What do you see? Reply with one word per image."),
                ImageContent.from(CAT_IMAGE_URL),
                ImageContent.from(DICE_IMAGE_URL));

        // when
        TestStreamingChatResponseHandler handler = new TestStreamingChatResponseHandler();
        model.chat(singletonList(userMessage), handler);
        ChatResponse response = handler.get();

        // then
        assertThat(response.aiMessage().text()).containsIgnoringCase("cat").containsIgnoringCase("dice");
    }

    @Test
    void should_accept_text_and_multiple_images_from_different_sources() {

        // given
        UserMessage userMessage = UserMessage.from(
                ImageContent.from(CAT_IMAGE_URL),
                ImageContent.from(Base64.getEncoder().encodeToString(readBytes(DICE_IMAGE_URL)), "image/png"),
                TextContent.from("What do you see? Reply with one word per image."));

        // when
        TestStreamingChatResponseHandler handler = new TestStreamingChatResponseHandler();
        model.chat(singletonList(userMessage), handler);
        ChatResponse response = handler.get();

        // then
        assertThat(response.aiMessage().text()).containsIgnoringCase("cat").containsIgnoringCase("dice");
    }

    @ParameterizedTest
    @EnumSource(
            value = OpenAiChatModelName.class,
            mode = EXCLUDE,
            names = {
                    "GPT_4_32K", // don't have access
                    "GPT_4_32K_0613", // don't have access
                    "O1", // don't have access
                    "O1_2024_12_17", // don't have access
                    "O3", // don't have access
                    "O3_2025_04_16", // don't have access
            })
    void should_support_all_model_names(OpenAiChatModelName modelName) {

        // given
        OpenAiStreamingChatModel model = OpenAiStreamingChatModel.builder()
                .baseUrl(System.getenv("OPENAI_BASE_URL"))
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .organizationId(System.getenv("OPENAI_ORGANIZATION_ID"))
                .modelName(modelName)
                .logRequests(true)
                .logResponses(true)
                .build();

        String question = "What is the capital of Germany?";

        // when
        TestStreamingChatResponseHandler handler = new TestStreamingChatResponseHandler();
        model.chat(question, handler);
        ChatResponse response = handler.get();

        // then
        assertThat(response.aiMessage().text()).containsIgnoringCase("Berlin");
    }

    private static void assertTokenUsage(TokenUsage tokenUsage) {
        assertThat(tokenUsage.inputTokenCount()).isPositive();
        assertThat(tokenUsage.outputTokenCount()).isPositive();
        assertThat(tokenUsage.totalTokenCount())
                .isEqualTo(tokenUsage.inputTokenCount() + tokenUsage.outputTokenCount());
    }
}
