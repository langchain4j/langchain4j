package dev.langchain4j.model.jlama;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.concurrent.CompletableFuture;

import static dev.langchain4j.model.output.FinishReason.LENGTH;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

class JlamaStreamingChatModelIT {

    static File tmpDir;
    static StreamingChatModel model;

    @BeforeAll
    static void setup() {
        tmpDir = new File(System.getProperty("java.io.tmpdir") + File.separator + "jlama_tests");
        tmpDir.mkdirs();

        model = JlamaStreamingChatModel.builder()
                .modelName("tjake/Llama-3.2-1B-Instruct-JQ4")
                .modelCachePath(tmpDir.toPath())
                .maxTokens(64)
                .temperature(0.0f)
                .build();
    }

    @Test
    void should_stream_answer_and_return_response() throws Exception {

        // given
        String userMessage = "When is the best time of year to visit Japan?";

        // when
        StringBuilder answerBuilder = new StringBuilder();
        CompletableFuture<ChatResponse> futureResponse = new CompletableFuture<>();

        model.chat(userMessage, new StreamingChatResponseHandler() {

            @Override
            public void onPartialResponse(String partialResponse) {
                answerBuilder.append(partialResponse);
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
        String streamedAnswer = answerBuilder.toString();

        // then
        assertThat(streamedAnswer).isNotBlank();

        AiMessage aiMessage = response.aiMessage();
        assertThat(streamedAnswer).contains(aiMessage.text());

        assertThat(response.tokenUsage()).isNotNull();
        assertThat(response.finishReason()).isEqualTo(LENGTH);
    }
}
