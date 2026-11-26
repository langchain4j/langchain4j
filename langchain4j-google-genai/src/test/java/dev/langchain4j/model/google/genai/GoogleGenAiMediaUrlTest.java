package dev.langchain4j.model.google.genai;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.google.genai.Client;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.exception.UnsupportedFeatureException;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

class GoogleGenAiMediaUrlTest {

    private static final ChatRequest REQUEST_WITH_LOCAL_FILE_URL = ChatRequest.builder()
            .messages(UserMessage.from(TextContent.from("What is in the image?"), ImageContent.from("file:///etc/cat.png")))
            .build();

    @Test
    void chat_should_reject_media_given_by_a_local_file_url() {

        // given
        GoogleGenAiChatModel model = GoogleGenAiChatModel.builder()
                .client(mock(Client.class))
                .modelName("gemini-test-model")
                .build();

        // when-then
        assertThatThrownBy(() -> model.chat(REQUEST_WITH_LOCAL_FILE_URL))
                .isExactlyInstanceOf(UnsupportedFeatureException.class)
                .hasMessage(GoogleGenAiContentMapper.URL_NOT_SUPPORTED);
    }

    @Test
    void chatAsync_should_report_media_given_by_a_local_file_url_through_the_future() {

        // given
        GoogleGenAiChatModel model = GoogleGenAiChatModel.builder()
                .client(mock(Client.class))
                .modelName("gemini-test-model")
                .build();

        // when
        CompletableFuture<ChatResponse> response = model.chatAsync(REQUEST_WITH_LOCAL_FILE_URL);

        // then
        assertThat(response)
                .failsWithin(10, SECONDS)
                .withThrowableThat()
                .havingCause()
                .isExactlyInstanceOf(UnsupportedFeatureException.class)
                .withMessage(GoogleGenAiContentMapper.URL_NOT_SUPPORTED);
    }

    @Test
    void streaming_chat_should_reject_media_given_by_a_local_file_url() {

        // given
        GoogleGenAiStreamingChatModel model = GoogleGenAiStreamingChatModel.builder()
                .client(mock(Client.class))
                .modelName("gemini-test-model")
                .build();
        StreamingChatResponseHandler handler = new StreamingChatResponseHandler() {

            @Override
            public void onPartialResponse(String partialResponse) {}

            @Override
            public void onCompleteResponse(ChatResponse completeResponse) {}

            @Override
            public void onError(Throwable error) {}
        };

        // when-then
        assertThatThrownBy(() -> model.chat(REQUEST_WITH_LOCAL_FILE_URL, handler))
                .isExactlyInstanceOf(UnsupportedFeatureException.class)
                .hasMessage(GoogleGenAiContentMapper.URL_NOT_SUPPORTED);
    }
}
