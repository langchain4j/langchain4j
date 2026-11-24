package dev.langchain4j.model.vertexai.gemini;

import static java.util.concurrent.TimeUnit.SECONDS;
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
import com.google.cloud.vertexai.generativeai.ResponseStream;
import com.google.cloud.vertexai.generativeai.ResponseStreamIteratorWithHistory;
import dev.langchain4j.data.media.FetchedMedia;
import dev.langchain4j.data.media.MediaFetchRequest;
import dev.langchain4j.data.media.MediaFetcher;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.exception.AsyncNotSupportedException;
import dev.langchain4j.exception.InvalidRequestException;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class VertexAiGeminiMediaFetcherTest {

    private static final URI IMAGE_URL = URI.create("https://example.com/cat.png");

    private static final ChatRequest REQUEST_WITH_IMAGE_URL = ChatRequest.builder()
            .messages(UserMessage.from(TextContent.from("What is in the image?"), ImageContent.from(IMAGE_URL)))
            .build();

    @Test
    void chat_model_downloads_media_urls_with_configured_media_fetcher() {
        RuntimeException stop = new RuntimeException("stop after the download");
        List<URI> fetched = new CopyOnWriteArrayList<>();
        VertexAiGeminiChatModel model = VertexAiGeminiChatModel.builder()
                .project("does-not-matter")
                .location("does-not-matter")
                .modelName("does-not-matter")
                .mediaFetcher(request -> {
                    fetched.add(request.url());
                    throw stop;
                })
                .build();

        assertThatThrownBy(() -> model.chat(REQUEST_WITH_IMAGE_URL)).isSameAs(stop);
        assertThat(fetched).containsExactly(IMAGE_URL);
    }

    @Test
    void chat_model_rejects_media_urls_of_non_public_hosts_by_default() throws IOException {
        GenerativeModel generativeModel = generativeModel();
        VertexAiGeminiChatModel model = new VertexAiGeminiChatModel(generativeModel, GenerationConfig.newBuilder().build());

        assertThatThrownBy(() -> model.chat(UserMessage.from(ImageContent.from("http://127.0.0.1/cat.png"))))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("non-public address");
        verify(generativeModel, never()).generateContent(anyList());
    }

    @Test
    void streaming_chat_model_downloads_media_urls_with_fetchAsync_without_blocking_the_caller() {
        InvalidRequestException downloadFailure = new InvalidRequestException("download failed");
        CompletableFuture<FetchedMedia> download = new CompletableFuture<>();
        List<URI> fetchedAsync = new CopyOnWriteArrayList<>();
        VertexAiGeminiStreamingChatModel model = VertexAiGeminiStreamingChatModel.builder()
                .project("does-not-matter")
                .location("does-not-matter")
                .modelName("does-not-matter")
                .mediaFetcher(new MediaFetcher() {

                    @Override
                    public FetchedMedia fetch(MediaFetchRequest request) {
                        throw new AssertionError("fetch() must not be called");
                    }

                    @Override
                    public CompletableFuture<FetchedMedia> fetchAsync(MediaFetchRequest request) {
                        fetchedAsync.add(request.url());
                        return download;
                    }
                })
                .build();

        CompletableFuture<ChatResponse> response = stream(model, REQUEST_WITH_IMAGE_URL);

        assertThat(fetchedAsync).containsExactly(IMAGE_URL);
        assertThat(response).isNotDone();

        download.completeExceptionally(downloadFailure);

        assertThat(response).failsWithin(5, SECONDS).withThrowableThat().havingCause().isSameAs(downloadFailure);
    }

    @Test
    @SuppressWarnings("unchecked")
    void streaming_chat_model_sends_the_media_downloaded_with_fetchAsync() throws Exception {
        byte[] imageBytes = {1, 2, 3};
        GenerativeModel generativeModel = generativeModel();
        when(generativeModel.generateContentStream(anyList())).thenReturn(responseStream(response("A cat")));
        CompletableFuture<FetchedMedia> download = new CompletableFuture<>();
        VertexAiGeminiStreamingChatModel model = new VertexAiGeminiStreamingChatModel(
                generativeModel, GenerationConfig.newBuilder().build(), new MediaFetcher() {

                    @Override
                    public FetchedMedia fetch(MediaFetchRequest request) {
                        throw new AssertionError("fetch() must not be called");
                    }

                    @Override
                    public CompletableFuture<FetchedMedia> fetchAsync(MediaFetchRequest request) {
                        return download;
                    }
                });

        CompletableFuture<ChatResponse> response = stream(model, REQUEST_WITH_IMAGE_URL);

        assertThat(response).isNotDone();
        verify(generativeModel, never()).generateContentStream(anyList());

        download.complete(FetchedMedia.from(imageBytes, "image/png"));

        assertThat(response.get(10, SECONDS).aiMessage().text()).isEqualTo("A cat");
        ArgumentCaptor<List<Content>> contents = ArgumentCaptor.forClass(List.class);
        verify(generativeModel).generateContentStream(contents.capture());
        Part image = contents.getValue().get(0).getParts(1);
        assertThat(image.getInlineData().getMimeType()).isEqualTo("image/png");
        assertThat(image.getInlineData().getData().toByteArray()).isEqualTo(imageBytes);
    }

    @Test
    void streaming_chat_model_submits_to_its_executor_on_the_calling_thread_and_waits_there_for_the_download()
            throws Exception {
        GenerativeModel generativeModel = generativeModel();
        when(generativeModel.generateContentStream(anyList())).thenReturn(responseStream(response("A cat")));
        CompletableFuture<FetchedMedia> download = new CompletableFuture<>();
        List<Thread> submittingThreads = new CopyOnWriteArrayList<>();
        ExecutorService threads = Executors.newCachedThreadPool();
        try {
            Executor executor = task -> {
                submittingThreads.add(Thread.currentThread());
                threads.execute(task);
            };
            VertexAiGeminiStreamingChatModel model = new VertexAiGeminiStreamingChatModel(
                    generativeModel, GenerationConfig.newBuilder().build(), executor, pendingMediaFetcher(download));

            CompletableFuture<ChatResponse> response = stream(model, REQUEST_WITH_IMAGE_URL);

            assertThat(submittingThreads).containsExactly(Thread.currentThread());
            assertThat(response).isNotDone();
            Thread completing =
                    new Thread(() -> download.complete(FetchedMedia.from(new byte[] {1, 2, 3}, "image/png")));
            completing.start();
            completing.join(10_000);

            assertThat(response.get(10, SECONDS).aiMessage().text()).isEqualTo("A cat");
            assertThat(submittingThreads).containsExactly(Thread.currentThread());
        } finally {
            threads.shutdownNow();
        }
    }

    @Test
    void streaming_chat_model_reports_a_rejected_task_to_onError_and_cancels_the_download() throws IOException {
        GenerativeModel generativeModel = generativeModel();
        CompletableFuture<FetchedMedia> download = new CompletableFuture<>();
        RejectedExecutionException rejected = new RejectedExecutionException("busy");
        VertexAiGeminiStreamingChatModel model = new VertexAiGeminiStreamingChatModel(
                generativeModel,
                GenerationConfig.newBuilder().build(),
                task -> {
                    throw rejected;
                },
                pendingMediaFetcher(download));

        CompletableFuture<ChatResponse> response = stream(model, REQUEST_WITH_IMAGE_URL);

        assertThat(response).failsWithin(5, SECONDS).withThrowableThat().havingCause().isSameAs(rejected);
        assertThat(download).isCancelled();
        verify(generativeModel, never()).generateContentStream(anyList());
    }

    private static MediaFetcher pendingMediaFetcher(CompletableFuture<FetchedMedia> download) {
        return new MediaFetcher() {

            @Override
            public FetchedMedia fetch(MediaFetchRequest request) {
                throw new AssertionError("fetch() must not be called");
            }

            @Override
            public CompletableFuture<FetchedMedia> fetchAsync(MediaFetchRequest request) {
                return download;
            }
        };
    }

    @Test
    void streaming_chat_model_fails_through_onError_when_media_fetcher_does_not_implement_fetchAsync() {
        MediaFetcher syncOnlyMediaFetcher = request -> FetchedMedia.from(new byte[] {1, 2, 3}, "image/png");
        VertexAiGeminiStreamingChatModel model = VertexAiGeminiStreamingChatModel.builder()
                .project("does-not-matter")
                .location("does-not-matter")
                .modelName("does-not-matter")
                .mediaFetcher(syncOnlyMediaFetcher)
                .build();

        CompletableFuture<ChatResponse> response = stream(model, REQUEST_WITH_IMAGE_URL);

        assertThat(response)
                .failsWithin(5, SECONDS)
                .withThrowableThat()
                .havingCause()
                .isInstanceOf(AsyncNotSupportedException.class)
                .withMessageContaining("fetchAsync()");
    }

    @Test
    void streaming_chat_model_rejects_media_urls_of_non_public_hosts_by_default_through_onError() throws IOException {
        GenerativeModel generativeModel = generativeModel();
        VertexAiGeminiStreamingChatModel model =
                new VertexAiGeminiStreamingChatModel(generativeModel, GenerationConfig.newBuilder().build());

        CompletableFuture<ChatResponse> response = stream(
                model,
                ChatRequest.builder()
                        .messages(UserMessage.from(ImageContent.from("http://127.0.0.1/cat.png")))
                        .build());

        assertThat(response)
                .failsWithin(5, SECONDS)
                .withThrowableThat()
                .havingCause()
                .isInstanceOf(InvalidRequestException.class)
                .withMessageContaining("non-public address");
        verify(generativeModel, never()).generateContentStream(anyList());
    }

    private static GenerativeModel generativeModel() {
        GenerativeModel generativeModel = mock(GenerativeModel.class);
        when(generativeModel.getModelName()).thenReturn("gemini-test");
        when(generativeModel.withGenerationConfig(any())).thenReturn(generativeModel);
        when(generativeModel.withTools(anyList())).thenReturn(generativeModel);
        when(generativeModel.withToolConfig(any())).thenReturn(generativeModel);
        return generativeModel;
    }

    private static GenerateContentResponse response(String text) {
        return GenerateContentResponse.newBuilder()
                .addCandidates(Candidate.newBuilder()
                        .setContent(Content.newBuilder()
                                .setRole("model")
                                .addParts(Part.newBuilder().setText(text)))
                        .setFinishReason(Candidate.FinishReason.STOP))
                .build();
    }

    private static ResponseStream<GenerateContentResponse> responseStream(GenerateContentResponse response) {
        return new ResponseStream<>(new ResponseStreamIteratorWithHistory<>(List.of(response).iterator()));
    }

    private static CompletableFuture<ChatResponse> stream(VertexAiGeminiStreamingChatModel model, ChatRequest request) {
        CompletableFuture<ChatResponse> response = new CompletableFuture<>();
        model.chat(request, new StreamingChatResponseHandler() {

            @Override
            public void onCompleteResponse(ChatResponse completeResponse) {
                response.complete(completeResponse);
            }

            @Override
            public void onError(Throwable error) {
                response.completeExceptionally(error);
            }
        });
        return response;
    }
}
