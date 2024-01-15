package dev.langchain4j.model.mistralai;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import static dev.langchain4j.data.message.UserMessage.userMessage;
import static dev.langchain4j.model.output.FinishReason.LENGTH;
import static dev.langchain4j.model.output.FinishReason.STOP;
import static java.util.Arrays.asList;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

class MistralAiStreamingChatModelIT {

    StreamingChatLanguageModel model =  MistralAiStreamingChatModel.builder()
            .apiKey(System.getenv("MISTRAL_AI_API_KEY"))
            .temperature(0.1)
            .build();

    @Test
    void should_stream_answer_and_return_token_usage_and_finish_reason_stop() throws ExecutionException, InterruptedException, TimeoutException {

        CompletableFuture<String> futureAnswer = new CompletableFuture<>();
        CompletableFuture<Response<AiMessage>> futureResponse = new CompletableFuture<>();

        // given
        UserMessage userMessage = userMessage("What is the capital of Peru?");

        // when
        model.generate(userMessage.text(), new StreamingResponseHandler<AiMessage>() {

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

        String chunk = futureAnswer.get(10, SECONDS);
        Response<AiMessage> response = futureResponse.get(10, SECONDS);

        // then
        assertThat(chunk).contains("Lima");
        assertThat(response.content().text()).isEqualTo(chunk);

        TokenUsage tokenUsage = response.tokenUsage();
        assertThat(tokenUsage.inputTokenCount()).isEqualTo(15);
        assertThat(tokenUsage.outputTokenCount()).isGreaterThan(0);
        assertThat(tokenUsage.totalTokenCount())
                .isEqualTo(tokenUsage.inputTokenCount() + tokenUsage.outputTokenCount());

        assertThat(response.finishReason()).isEqualTo(STOP);
    }

    @Test
    void should_stream_answer_and_return_token_usage_and_finish_reason_length() throws ExecutionException, InterruptedException, TimeoutException {

        CompletableFuture<String> futureAnswer = new CompletableFuture<>();
        CompletableFuture<Response<AiMessage>> futureResponse = new CompletableFuture<>();

        // given
        StreamingChatLanguageModel model =  MistralAiStreamingChatModel.builder()
                .apiKey(System.getenv("MISTRAL_AI_API_KEY"))
                .maxNewTokens(10)
                .build();

        // given
        UserMessage userMessage = userMessage("What is the capital of Peru?");

        // when
        model.generate(userMessage.text(), new StreamingResponseHandler<AiMessage>() {

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

        String chunk = futureAnswer.get(10, SECONDS);
        Response<AiMessage> response = futureResponse.get(10, SECONDS);

        // then
        assertThat(chunk).contains("Lima");
        assertThat(response.content().text()).isEqualTo(chunk);

        TokenUsage tokenUsage = response.tokenUsage();
        assertThat(tokenUsage.inputTokenCount()).isEqualTo(15);
        assertThat(tokenUsage.outputTokenCount()).isEqualTo(10);
        assertThat(tokenUsage.totalTokenCount())
                .isEqualTo(tokenUsage.inputTokenCount() + tokenUsage.outputTokenCount());

        assertThat(response.finishReason()).isEqualTo(LENGTH);
    }

    //https://docs.mistral.ai/platform/guardrailing/
    @Test
    void should_stream_answer_and_system_prompt_to_enforce_guardrails() throws ExecutionException, InterruptedException, TimeoutException {

        CompletableFuture<String> futureAnswer = new CompletableFuture<>();
        CompletableFuture<Response<AiMessage>> futureResponse = new CompletableFuture<>();

        // given
        StreamingChatLanguageModel model = MistralAiStreamingChatModel.builder()
                .apiKey(System.getenv("MISTRAL_AI_API_KEY"))
                .safePrompt(true)
                .build();

        // given
        UserMessage userMessage = userMessage("Hello, my name is Carlos");

        // then
         model.generate(userMessage.text(), new StreamingResponseHandler<AiMessage>() {
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

        String chunk = futureAnswer.get(10, SECONDS);
        Response<AiMessage> response = futureResponse.get(10, SECONDS);

        // then
        assertThat(chunk).contains("respect");
        assertThat(chunk).contains("truth");
        assertThat(response.content().text()).isEqualTo(chunk);

        TokenUsage tokenUsage = response.tokenUsage();
        assertThat(tokenUsage.inputTokenCount()).isGreaterThan(50);
        assertThat(tokenUsage.outputTokenCount()).isGreaterThan(0);
        assertThat(tokenUsage.totalTokenCount())
                .isEqualTo(tokenUsage.inputTokenCount() + tokenUsage.outputTokenCount());

        assertThat(response.finishReason()).isEqualTo(STOP);

    }

    @Test
    void should_stream_answer_and_return_token_usage_and_finish_reason_stop_with_multiple_messages() throws ExecutionException, InterruptedException, TimeoutException {

        CompletableFuture<String> futureAnswer = new CompletableFuture<>();
        CompletableFuture<Response<AiMessage>> futureResponse = new CompletableFuture<>();

        // given
        UserMessage userMessage1 = userMessage("What is the capital of Peru?");
        UserMessage userMessage2 = userMessage("What is the capital of France?");
        UserMessage userMessage3 = userMessage("What is the capital of Canada?");

        model.generate(asList(userMessage1,userMessage2,userMessage3), new StreamingResponseHandler<AiMessage>(){
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

        String chunk = futureAnswer.get(10, SECONDS);
        Response<AiMessage> response = futureResponse.get(10, SECONDS);

        // then
        assertThat(chunk).contains("Lima");
        assertThat(chunk).contains("Paris");
        assertThat(chunk).contains("Ottawa");
        assertThat(response.content().text()).isEqualTo(chunk);

        TokenUsage tokenUsage = response.tokenUsage();
        assertThat(tokenUsage.inputTokenCount()).isEqualTo(11 + 11 + 11);
        assertThat(tokenUsage.outputTokenCount()).isGreaterThan(0);
        assertThat(tokenUsage.totalTokenCount())
                .isEqualTo(tokenUsage.inputTokenCount() + tokenUsage.outputTokenCount());

        assertThat(response.finishReason()).isEqualTo(STOP);


    }

    @Test
    void should_stream_answer_in_french_using_model_small_and_return_token_usage_and_finish_reason_stop() throws ExecutionException, InterruptedException, TimeoutException {

        CompletableFuture<String> futureAnswer = new CompletableFuture<>();
        CompletableFuture<Response<AiMessage>> futureResponse = new CompletableFuture<>();

        // given - Mistral Small = Mistral-8X7B
        StreamingChatLanguageModel model = MistralAiStreamingChatModel.builder()
                .apiKey(System.getenv("MISTRAL_AI_API_KEY"))
                .modelName(ChatCompletionModel.MISTRAL_SMALL.toString())
                .temperature(0.1)
                .build();

        UserMessage userMessage = userMessage("Quelle est la capitale du Pérou?");

        model.generate(userMessage.text(), new StreamingResponseHandler<AiMessage>() {
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

        String chunk = futureAnswer.get(10, SECONDS);
        Response<AiMessage> response = futureResponse.get(10, SECONDS);

        // then
        assertThat(chunk).contains("Lima");
        assertThat(response.content().text()).isEqualTo(chunk);

        TokenUsage tokenUsage = response.tokenUsage();
        assertThat(tokenUsage.inputTokenCount()).isEqualTo(18);
        assertThat(tokenUsage.outputTokenCount()).isGreaterThan(0);
        assertThat(tokenUsage.totalTokenCount())
                .isEqualTo(tokenUsage.inputTokenCount() + tokenUsage.outputTokenCount());

        assertThat(response.finishReason()).isEqualTo(STOP);
    }

    @Test
    void should_stream_answer_in_spanish_using_model_small_and_return_token_usage_and_finish_reason_stop() throws ExecutionException, InterruptedException, TimeoutException {

        CompletableFuture<String> futureAnswer = new CompletableFuture<>();
        CompletableFuture<Response<AiMessage>> futureResponse = new CompletableFuture<>();

        // given - Mistral Small = Mistral-8X7B
        StreamingChatLanguageModel model = MistralAiStreamingChatModel.builder()
                .apiKey(System.getenv("MISTRAL_AI_API_KEY"))
                .modelName(ChatCompletionModel.MISTRAL_SMALL.toString())
                .temperature(0.1)
                .build();

        UserMessage userMessage = userMessage("¿Cuál es la capital de Perú?");

        model.generate(userMessage.text(), new StreamingResponseHandler<AiMessage>() {
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

        String chunk = futureAnswer.get(10, SECONDS);
        Response<AiMessage> response = futureResponse.get(10, SECONDS);

        // then
        assertThat(chunk).contains("Lima");
        assertThat(response.content().text()).isEqualTo(chunk);

        TokenUsage tokenUsage = response.tokenUsage();
        assertThat(tokenUsage.inputTokenCount()).isEqualTo(19);
        assertThat(tokenUsage.outputTokenCount()).isGreaterThan(0);
        assertThat(tokenUsage.totalTokenCount())
                .isEqualTo(tokenUsage.inputTokenCount() + tokenUsage.outputTokenCount());

        assertThat(response.finishReason()).isEqualTo(STOP);
    }

    @Test
    void should_stream_answer_using_model_medium_and_return_token_usage_and_finish_reason_length() throws ExecutionException, InterruptedException, TimeoutException {

        CompletableFuture<String> futureAnswer = new CompletableFuture<>();
        CompletableFuture<Response<AiMessage>> futureResponse = new CompletableFuture<>();

        // given - Mistral Medium = currently relies on an internal prototype model.
        StreamingChatLanguageModel model = MistralAiStreamingChatModel.builder()
                .apiKey(System.getenv("MISTRAL_AI_API_KEY"))
                .modelName(ChatCompletionModel.MISTRAL_MEDIUM.toString())
                .maxNewTokens(10)
                .build();

        UserMessage userMessage = userMessage("What is the capital of Peru?");

        model.generate(userMessage.text(), new StreamingResponseHandler<AiMessage>() {
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

        String chunk = futureAnswer.get(10, SECONDS);
        Response<AiMessage> response = futureResponse.get(10, SECONDS);

        // then
        assertThat(chunk).contains("Lima");
        assertThat(response.content().text()).isEqualTo(chunk);

        TokenUsage tokenUsage = response.tokenUsage();
        assertThat(tokenUsage.inputTokenCount()).isEqualTo(15);
        assertThat(tokenUsage.outputTokenCount()).isEqualTo(10);
        assertThat(tokenUsage.totalTokenCount())
                .isEqualTo(tokenUsage.inputTokenCount() + tokenUsage.outputTokenCount());

        assertThat(response.finishReason()).isEqualTo(LENGTH);
    }
}
