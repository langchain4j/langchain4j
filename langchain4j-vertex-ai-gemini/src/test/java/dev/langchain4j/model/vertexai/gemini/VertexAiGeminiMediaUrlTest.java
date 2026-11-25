package dev.langchain4j.model.vertexai.gemini;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.cloud.vertexai.api.Candidate;
import com.google.cloud.vertexai.api.Content;
import com.google.cloud.vertexai.api.GenerateContentResponse;
import com.google.cloud.vertexai.api.GenerationConfig;
import com.google.cloud.vertexai.api.Part;
import com.google.cloud.vertexai.generativeai.GenerativeModel;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.exception.UnsupportedFeatureException;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class VertexAiGeminiMediaUrlTest {

    private static final ChatRequest REQUEST_WITH_LOCAL_FILE_URL = ChatRequest.builder()
            .messages(UserMessage.from(TextContent.from("What is in the image?"), ImageContent.from("file:///etc/cat.png")))
            .build();

    @Test
    @SuppressWarnings("unchecked")
    void chat_should_pass_image_urls_on_to_vertex_ai() throws Exception {

        // given
        GenerativeModel generativeModel = generativeModel();
        when(generativeModel.generateContent(anyList())).thenReturn(response("A cat"));
        VertexAiGeminiChatModel model =
                new VertexAiGeminiChatModel(generativeModel, GenerationConfig.newBuilder().build());

        // when
        ChatResponse response = model.chat(UserMessage.from(
                TextContent.from("What is in the image?"), ImageContent.from("https://example.com/cat.png")));

        // then
        assertThat(response.aiMessage().text()).isEqualTo("A cat");
        ArgumentCaptor<List<Content>> contents = ArgumentCaptor.forClass(List.class);
        verify(generativeModel).generateContent(contents.capture());
        Part image = contents.getValue().get(0).getParts(1);
        assertThat(image.hasInlineData()).isFalse();
        assertThat(image.getFileData().getFileUri()).isEqualTo("https://example.com/cat.png");
        assertThat(image.getFileData().getMimeType()).isEqualTo("image/png");
    }

    @Test
    void chat_should_reject_media_given_by_a_local_file_url() throws Exception {

        // given
        GenerativeModel generativeModel = generativeModel();
        VertexAiGeminiChatModel model =
                new VertexAiGeminiChatModel(generativeModel, GenerationConfig.newBuilder().build());

        // when-then
        assertThatThrownBy(() -> model.chat(REQUEST_WITH_LOCAL_FILE_URL))
                .isExactlyInstanceOf(UnsupportedFeatureException.class)
                .hasMessage(PartsMapper.URL_NOT_SUPPORTED);
        verify(generativeModel, never()).generateContent(anyList());
    }

    @Test
    void streaming_chat_should_reject_media_given_by_a_local_file_url() throws Exception {

        // given
        GenerativeModel generativeModel = generativeModel();
        VertexAiGeminiStreamingChatModel model =
                new VertexAiGeminiStreamingChatModel(generativeModel, GenerationConfig.newBuilder().build());
        CompletableFuture<ChatResponse> response = new CompletableFuture<>();

        // when-then
        assertThatThrownBy(() -> model.chat(REQUEST_WITH_LOCAL_FILE_URL, new StreamingChatResponseHandler() {

                    @Override
                    public void onPartialResponse(String partialResponse) {}

                    @Override
                    public void onCompleteResponse(ChatResponse completeResponse) {
                        response.complete(completeResponse);
                    }

                    @Override
                    public void onError(Throwable error) {
                        response.completeExceptionally(error);
                    }
                }))
                .isExactlyInstanceOf(UnsupportedFeatureException.class)
                .hasMessage(PartsMapper.URL_NOT_SUPPORTED);
        verify(generativeModel, never()).generateContentStream(anyList());
    }

    private static GenerativeModel generativeModel() {
        GenerativeModel generativeModel = mock(GenerativeModel.class);
        when(generativeModel.getModelName()).thenReturn("gemini-test");
        when(generativeModel.withGenerationConfig(any())).thenReturn(generativeModel);
        when(generativeModel.withTools(anyList())).thenReturn(generativeModel);
        when(generativeModel.withToolConfig(any())).thenReturn(generativeModel);
        when(generativeModel.withSystemInstruction(any())).thenReturn(generativeModel);
        when(generativeModel.withSafetySettings(anyList())).thenReturn(generativeModel);
        return generativeModel;
    }

    private static GenerateContentResponse response(String text) {
        return GenerateContentResponse.newBuilder()
                .addCandidates(Candidate.newBuilder()
                        .setContent(Content.newBuilder().setRole("model").addParts(Part.newBuilder()
                                .setText(text)))
                        .setFinishReason(Candidate.FinishReason.STOP))
                .build();
    }
}
