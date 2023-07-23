package dev.langchain4j.model.openai;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import static dev.langchain4j.agent.tool.JsonSchemaProperty.INTEGER;
import static dev.langchain4j.data.message.UserMessage.userMessage;
import static java.util.Collections.singletonList;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

class OpenAiStreamingChatModelIT {

    StreamingChatLanguageModel model
            = OpenAiStreamingChatModel.withApiKey(System.getenv("OPENAI_API_KEY"));

    @Test
    void should_stream_answer() throws ExecutionException, InterruptedException, TimeoutException {

        CompletableFuture<String> future = new CompletableFuture<>();

        model.sendUserMessage(
                "What is the capital of Germany?",
                new StreamingResponseHandler() {

                    final StringBuilder answerBuilder = new StringBuilder();

                    @Override
                    public void onNext(String partialResult) {
                        answerBuilder.append(partialResult);
                        System.out.println("onPartialResult: '" + partialResult + "'");
                    }

                    @Override
                    public void onComplete() {
                        future.complete(answerBuilder.toString());
                        System.out.println("onComplete");
                    }

                    @Override
                    public void onError(Throwable error) {
                        future.completeExceptionally(error);
                    }
                });

        String answer = future.get(30, SECONDS);

        assertThat(answer).contains("Berlin");
    }

    @Test
    void should_stream_tool_execution_request() throws Exception {

        ToolSpecification toolSpecification = ToolSpecification.builder()
                .name("calculator")
                .description("returns a sum of two numbers")
                .addParameter("first", INTEGER)
                .addParameter("second", INTEGER)
                .build();

        UserMessage userMessage = userMessage("Two plus two?");

        CompletableFuture<String> future = new CompletableFuture<>();

        model.sendMessages(
                singletonList(userMessage),
                singletonList(toolSpecification),
                new StreamingResponseHandler() {

                    final StringBuilder answerBuilder = new StringBuilder();

                    @Override
                    public void onNext(String partialResult) {
                        answerBuilder.append(partialResult);
                        System.out.println("onPartialResult: '" + partialResult + "'");
                    }

                    @Override
                    public void onToolName(String name) {
                        answerBuilder.append(name);
                        System.out.println("onToolName: '" + name + "'");
                    }

                    @Override
                    public void onToolArguments(String arguments) {
                        answerBuilder.append(arguments);
                        System.out.println("onToolArguments: '" + arguments + "'");
                    }

                    @Override
                    public void onComplete() {
                        future.complete(answerBuilder.toString());
                        System.out.println("onComplete");
                    }

                    @Override
                    public void onError(Throwable error) {
                        future.completeExceptionally(error);
                    }
                });

        String answer = future.get(30, SECONDS);

        assertThat(answer).isEqualToIgnoringWhitespace("calculator {\"first\": 2, \"second\": 2}");
    }
}