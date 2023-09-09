package dev.langchain4j.model.openai;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.output.Result;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import static dev.langchain4j.agent.tool.JsonSchemaProperty.INTEGER;
import static dev.langchain4j.data.message.UserMessage.userMessage;
import static dev.langchain4j.model.output.FinishReason.STOP;
import static dev.langchain4j.model.output.FinishReason.TOOL_EXECUTION;
import static java.util.Collections.singletonList;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

class OpenAiStreamingChatModelIT {

    StreamingChatLanguageModel model = OpenAiStreamingChatModel.builder()
            .apiKey(System.getenv("OPENAI_API_KEY"))
            .logRequests(true)
            .logResponses(true)
            .build();

    @Test
    void should_stream_answer() throws ExecutionException, InterruptedException, TimeoutException {

        CompletableFuture<String> futureAnswer = new CompletableFuture<>();
        CompletableFuture<Result<AiMessage>> futureResult = new CompletableFuture<>();

        model.generate("What is the capital of Germany?", new StreamingResponseHandler<AiMessage>() {

            private final StringBuilder answerBuilder = new StringBuilder();

            @Override
            public void onNext(String token) {
                System.out.println("onNext: '" + token + "'");
                answerBuilder.append(token);
            }

            @Override
            public void onComplete(Result<AiMessage> result) {
                System.out.println("onComplete: '" + result + "'");
                futureAnswer.complete(answerBuilder.toString());
                futureResult.complete(result);
            }

            @Override
            public void onError(Throwable error) {
                futureAnswer.completeExceptionally(error);
                futureResult.completeExceptionally(error);
            }
        });

        String answer = futureAnswer.get(30, SECONDS);
        Result<AiMessage> result = futureResult.get(30, SECONDS);

        assertThat(answer).contains("Berlin");
        assertThat(result.get().text()).isEqualTo(answer);

        assertThat(result.tokenUsage().inputTokenCount()).isEqualTo(14);
        assertThat(result.tokenUsage().outputTokenCount()).isGreaterThan(0);
        assertThat(result.tokenUsage().totalTokenCount())
                .isEqualTo(result.tokenUsage().inputTokenCount() + result.tokenUsage().outputTokenCount());

        assertThat(result.finishReason()).isEqualTo(STOP);
    }

    @Test
    void should_return_tool_execution_request() throws Exception {

        ToolSpecification toolSpecification = ToolSpecification.builder()
                .name("calculator")
                .description("returns a sum of two numbers")
                .addParameter("first", INTEGER)
                .addParameter("second", INTEGER)
                .build();

        UserMessage userMessage = userMessage("Two plus two?");

        CompletableFuture<Result<AiMessage>> futureResult = new CompletableFuture<>();

        model.generate(singletonList(userMessage), singletonList(toolSpecification), new StreamingResponseHandler<AiMessage>() {

            @Override
            public void onNext(String token) {
                System.out.println("onNext: '" + token + "'");
                Exception e = new IllegalStateException("onNext() should never be called when tool is executed");
                futureResult.completeExceptionally(e);
            }

            @Override
            public void onComplete(Result<AiMessage> result) {
                System.out.println("onComplete: '" + result + "'");
                futureResult.complete(result);
            }

            @Override
            public void onError(Throwable error) {
                futureResult.completeExceptionally(error);
            }
        });

        Result<AiMessage> result = futureResult.get(30, SECONDS);

        AiMessage aiMessage = result.get();
        assertThat(aiMessage.text()).isNull();

        ToolExecutionRequest toolExecutionRequest = aiMessage.toolExecutionRequest();
        assertThat(toolExecutionRequest.name()).isEqualTo("calculator");
        assertThat(toolExecutionRequest.arguments()).isEqualToIgnoringWhitespace("{\"first\": 2, \"second\": 2}");

        assertThat(result.tokenUsage().inputTokenCount()).isEqualTo(50);
        assertThat(result.tokenUsage().outputTokenCount()).isGreaterThan(0);
        assertThat(result.tokenUsage().totalTokenCount())
                .isEqualTo(result.tokenUsage().inputTokenCount() + result.tokenUsage().outputTokenCount());

        assertThat(result.finishReason()).isEqualTo(TOOL_EXECUTION);
    }
}