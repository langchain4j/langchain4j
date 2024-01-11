package dev.langchain4j.model.wenxin;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import static dev.langchain4j.agent.tool.JsonSchemaProperty.INTEGER;
import static dev.langchain4j.data.message.ToolExecutionResultMessage.from;
import static dev.langchain4j.data.message.UserMessage.userMessage;
import static dev.langchain4j.model.output.FinishReason.STOP;
import static dev.langchain4j.model.output.FinishReason.TOOL_EXECUTION;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;


class WenxinStreamingChatModelIT {

    //see your client id and secret key here: https://console.bce.baidu.com/qianfan/ais/console/applicationConsole/application
    private String clientId ="your client id";
    private String secretKey ="your secret key";
    WenxinStreamingChatModel model = WenxinStreamingChatModel.builder().modelName("ERNIE-Bot 4.0").temperature(0.7).topP(1.0f)
            .clientId(clientId)
            .secretKey(secretKey)
            .build();
    ToolSpecification calculator = ToolSpecification.builder()
            .name("calculator")
            .description("returns a sum of two numbers")
            .addParameter("first", INTEGER)
            .addParameter("second", INTEGER)
            .build();



  @Test
    void should_stream_answer() throws ExecutionException, InterruptedException, TimeoutException {


        CompletableFuture<Response<AiMessage>> futureResponse = new CompletableFuture<>();

        model.generate("中国首都是哪里", new StreamingResponseHandler<AiMessage>() {

            private final StringBuilder answerBuilder = new StringBuilder();

            @Override
            public void onNext(String token) {
                System.out.println("onNext: '" + token + "'");
                answerBuilder.append(token);
            }

            @Override
            public void onComplete(Response<AiMessage> response) {
                System.out.println("onComplete: '" + response.content().text() + "'");
                futureResponse.complete(response);
            }

            @Override
            public void onError(Throwable error) {
                futureResponse.completeExceptionally(error);
            }
        });


        Response<AiMessage> response = futureResponse.get(50,SECONDS);

        assertThat(response.content().text()).containsIgnoringCase("北京");
        TokenUsage tokenUsage = response.tokenUsage();
        assertThat(tokenUsage.totalTokenCount())
                .isEqualTo(tokenUsage.inputTokenCount() + tokenUsage.outputTokenCount());

        assertThat(response.finishReason()).isEqualTo(STOP);
    }

    @Test
    void should_execute_a_tool_then_stream_answer() throws Exception {

        // given
        UserMessage userMessage = userMessage("2+2=?");
        List<ToolSpecification> toolSpecifications = singletonList(calculator);

        // when
        CompletableFuture<Response<AiMessage>> futureResponse = new CompletableFuture<>();

        model.generate(singletonList(userMessage), toolSpecifications, new StreamingResponseHandler<AiMessage>() {

            @Override
            public void onNext(String token) {
                System.out.println("onNext: '" + token + "'");
                Exception e = new IllegalStateException("onNext() should never be called when tool is executed");
                futureResponse.completeExceptionally(e);
            }

            @Override
            public void onComplete(Response<AiMessage> response) {
                System.out.println("onComplete: '" + response + "'");
                futureResponse.complete(response);
            }

            @Override
            public void onError(Throwable error) {
                futureResponse.completeExceptionally(error);
            }
        });

        Response<AiMessage> response = futureResponse.get(30, SECONDS);
        AiMessage aiMessage = response.content();

        // then
        assertThat(aiMessage.text()).isNull();

        List<ToolExecutionRequest> toolExecutionRequests = aiMessage.toolExecutionRequests();
        assertThat(toolExecutionRequests).hasSize(1);

        ToolExecutionRequest toolExecutionRequest = toolExecutionRequests.get(0);
        assertThat(toolExecutionRequest.name()).isEqualTo("calculator");
        assertThat(toolExecutionRequest.arguments()).isEqualToIgnoringWhitespace("{\"first\": 2, \"second\": 2}");

        TokenUsage tokenUsage = response.tokenUsage();
        assertThat(tokenUsage.totalTokenCount())
                .isEqualTo(tokenUsage.inputTokenCount() + tokenUsage.outputTokenCount());

        assertThat(response.finishReason()).isEqualTo(TOOL_EXECUTION);

        // given
        ToolExecutionResultMessage toolExecutionResultMessage = from(toolExecutionRequest, "4");

        List<ChatMessage> messages = asList(userMessage, aiMessage, toolExecutionResultMessage);

        // when
        CompletableFuture<Response<AiMessage>> secondFutureResponse = new CompletableFuture<>();

        model.generate(messages, new StreamingResponseHandler<AiMessage>() {

            @Override
            public void onNext(String token) {
                System.out.println("onNext: '" + token + "'");
            }

            @Override
            public void onComplete(Response<AiMessage> response) {
                System.out.println("onComplete: '" + response + "'");
                secondFutureResponse.complete(response);
            }

            @Override
            public void onError(Throwable error) {
                secondFutureResponse.completeExceptionally(error);
            }
        });

        Response<AiMessage> secondResponse = secondFutureResponse.get(30, SECONDS);
        AiMessage secondAiMessage = secondResponse.content();

        // then
        assertThat(secondAiMessage.text()).contains("4");
        assertThat(secondAiMessage.toolExecutionRequests()).isNull();

        TokenUsage secondTokenUsage = secondResponse.tokenUsage();
        assertThat(secondTokenUsage.totalTokenCount())
                .isEqualTo(secondTokenUsage.inputTokenCount() + secondTokenUsage.outputTokenCount());

        assertThat(secondResponse.finishReason()).isEqualTo(STOP);
    }


    @Test
    void should_stream_valid_json() throws ExecutionException, InterruptedException, TimeoutException {

        //given
        String userMessage = "Return JSON with two fields: name and surname of Klaus Heisler. ";
        // nudging it to say something additionally to json
        WenxinStreamingChatModel model = WenxinStreamingChatModel.builder().modelName("ERNIE-Bot 4.0").temperature(0.7).topP(1.0f)
                .clientId("dLX2hbGGGSTcR8XIBmvtAUZm")
                .secretKey("MVVLs15ITDUTySuGCmZsdu9H9oBuqdXf")
                .responseFormat("json_object")
                .build();

        // when
        CompletableFuture<String> futureAnswer = new CompletableFuture<>();
        CompletableFuture<Response<AiMessage>> futureResponse = new CompletableFuture<>();

        model.generate(userMessage, new StreamingResponseHandler<AiMessage>() {

            private final StringBuilder answerBuilder = new StringBuilder();

            @Override
            public void onNext(String token) {
                System.out.println("onNext: '" + token + "'");
                answerBuilder.append(token);
            }

            @Override
            public void onComplete(Response<AiMessage> response) {
                System.out.println("onComplete: '" + response + "'");
                futureAnswer.complete(answerBuilder.toString());
                futureResponse.complete(response);
            }

            @Override
            public void onError(Throwable error) {
                futureAnswer.completeExceptionally(error);
                futureResponse.completeExceptionally(error);
            }
        });

        String json = futureAnswer.get(30, SECONDS);
        Response<AiMessage> response = futureResponse.get(30, SECONDS);

        // then
        assertThat(json).isEqualToIgnoringWhitespace("{\"name\": \"Klaus\", \"surname\": \"Heisler\"}");
        assertThat(response.content().text()).isEqualTo(json);
    }
}