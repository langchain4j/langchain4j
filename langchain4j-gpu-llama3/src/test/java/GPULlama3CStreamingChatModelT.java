import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;

import dev.langchain4j.model.gpullama3.GPULlama3StreamingChatModel;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.CompletableFuture;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

@Disabled("Requires TornadoVM locally configured with GPU support")
public class GPULlama3CStreamingChatModelT {
    static GPULlama3StreamingChatModel model;

    @BeforeAll
    static void setup() {
        // @formatter:off
        Path modelPath = Paths.get("Phi-3-mini-4k-instruct-fp16.gguf");
         model = GPULlama3StreamingChatModel.builder()
                .modelPath(modelPath)
                .onGPU(Boolean.TRUE) //if false, runs on CPU though a lightweight implementation of llama3.java
                .build();
        // @formatter:on
    }

    @Test
    void should_stream_answer_and_return_response() throws Exception {
        CompletableFuture<ChatResponse> futureResponse = new CompletableFuture<>();

        String prompt;
        StringBuilder answerBuilder = new StringBuilder();

        prompt = "When is the best time of year to visit Japan?";

        // @formatter:off
        ChatRequest request = ChatRequest.builder().messages(
                        UserMessage.from(prompt),
                        SystemMessage.from("reply with extensive sarcasm"))
                .build();
        // @formatter:on

        model.chat(request, new StreamingChatResponseHandler() {

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

        futureResponse.join();

        ChatResponse response = futureResponse.get(30, SECONDS);
        String streamedAnswer = answerBuilder.toString();

        // then
        assertThat(streamedAnswer).isNotBlank();

        AiMessage aiMessage = response.aiMessage();
        assertThat(streamedAnswer).contains(aiMessage.text());
    }
}
