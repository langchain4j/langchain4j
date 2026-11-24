package dev.langchain4j.model.google.genai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static java.util.concurrent.TimeUnit.SECONDS;

import com.google.genai.AsyncModels;
import com.google.genai.Client;
import com.google.genai.Models;
import com.google.genai.ResponseStream;
import com.google.genai.types.Candidate;
import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.Part;
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
import java.lang.reflect.Field;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class GoogleGenAiMediaFetcherTest {

    private static final String MODEL_NAME = "gemini-test-model";
    private static final URI IMAGE_URL = URI.create("https://example.com/cat.png");
    private static final byte[] IMAGE_BYTES = "image bytes".getBytes(StandardCharsets.UTF_8);

    private static final ChatRequest REQUEST_WITH_IMAGE_URL = ChatRequest.builder()
            .messages(UserMessage.from(TextContent.from("What is in the image?"), ImageContent.from(IMAGE_URL)))
            .build();

    private static final GenerateContentResponse RESPONSE = GenerateContentResponse.builder()
            .candidates(List.of(Candidate.builder()
                    .content(Content.builder()
                            .role("model")
                            .parts(List.of(Part.builder().text("A cat").build()))
                            .build())
                    .build()))
            .build();

    @Test
    void chat_model_downloads_media_urls_with_configured_media_fetcher() throws Exception {
        Models models = mock(Models.class);
        when(models.generateContent(anyString(), anyList(), any(GenerateContentConfig.class)))
                .thenReturn(RESPONSE);
        RecordingMediaFetcher mediaFetcher = new RecordingMediaFetcher();
        GoogleGenAiChatModel model = GoogleGenAiChatModel.builder()
                .client(clientWith(models))
                .modelName(MODEL_NAME)
                .mediaFetcher(mediaFetcher)
                .build();

        ChatResponse response = model.chat(REQUEST_WITH_IMAGE_URL);

        assertThat(response.aiMessage().text()).isEqualTo("A cat");
        assertThat(mediaFetcher.fetched).containsExactly(IMAGE_URL);
        assertThat(mediaFetcher.fetchedAsync).isEmpty();
        assertThat(sentImage(models)).isEqualTo(IMAGE_BYTES);
    }

    @Test
    void chat_model_rejects_media_urls_of_non_public_hosts_by_default() throws Exception {
        Models models = mock(Models.class);
        GoogleGenAiChatModel model = GoogleGenAiChatModel.builder()
                .client(clientWith(models))
                .modelName(MODEL_NAME)
                .build();

        assertThatThrownBy(() -> model.chat(UserMessage.from(ImageContent.from("http://127.0.0.1/cat.png"))))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("non-public address");
        verify(models, never()).generateContent(anyString(), anyList(), any(GenerateContentConfig.class));
    }

    @Test
    void chatAsync_downloads_media_urls_with_fetchAsync() throws Exception {
        AsyncModels asyncModels = mock(AsyncModels.class);
        when(asyncModels.generateContent(anyString(), anyList(), any(GenerateContentConfig.class)))
                .thenReturn(CompletableFuture.completedFuture(RESPONSE));
        RecordingMediaFetcher mediaFetcher = new RecordingMediaFetcher();
        GoogleGenAiChatModel model = GoogleGenAiChatModel.builder()
                .client(clientWith(asyncModels))
                .modelName(MODEL_NAME)
                .mediaFetcher(mediaFetcher)
                .build();

        ChatResponse response = model.chatAsync(REQUEST_WITH_IMAGE_URL).get(10, SECONDS);

        assertThat(response.aiMessage().text()).isEqualTo("A cat");
        assertThat(mediaFetcher.fetchedAsync).containsExactly(IMAGE_URL);
        assertThat(mediaFetcher.fetched).isEmpty();
        assertThat(sentImage(asyncModels)).isEqualTo(IMAGE_BYTES);
    }

    @Test
    void chatAsync_does_not_block_the_caller_while_downloading() throws Exception {
        AsyncModels asyncModels = mock(AsyncModels.class);
        when(asyncModels.generateContent(anyString(), anyList(), any(GenerateContentConfig.class)))
                .thenReturn(CompletableFuture.completedFuture(RESPONSE));
        CompletableFuture<FetchedMedia> download = new CompletableFuture<>();
        GoogleGenAiChatModel model = GoogleGenAiChatModel.builder()
                .client(clientWith(asyncModels))
                .modelName(MODEL_NAME)
                .mediaFetcher(asyncMediaFetcher(download))
                .build();

        CompletableFuture<ChatResponse> response = model.chatAsync(REQUEST_WITH_IMAGE_URL);

        assertThat(response).isNotDone();
        verify(asyncModels, never()).generateContent(anyString(), anyList(), any(GenerateContentConfig.class));

        download.complete(FetchedMedia.from(IMAGE_BYTES, "image/png"));

        assertThat(response.get(10, SECONDS).aiMessage().text()).isEqualTo("A cat");
        assertThat(sentImage(asyncModels)).isEqualTo(IMAGE_BYTES);
    }

    @Test
    void cancelling_chatAsync_cancels_the_download() throws Exception {
        AsyncModels asyncModels = mock(AsyncModels.class);
        CompletableFuture<FetchedMedia> download = new CompletableFuture<>();
        GoogleGenAiChatModel model = GoogleGenAiChatModel.builder()
                .client(clientWith(asyncModels))
                .modelName(MODEL_NAME)
                .mediaFetcher(asyncMediaFetcher(download))
                .build();

        CompletableFuture<ChatResponse> response = model.chatAsync(REQUEST_WITH_IMAGE_URL);
        response.cancel(true);

        assertThat(download).isCancelled();
        verify(asyncModels, never()).generateContent(anyString(), anyList(), any(GenerateContentConfig.class));
    }

    @Test
    void chatAsync_fails_through_the_future_when_media_fetcher_does_not_implement_fetchAsync() throws Exception {
        AsyncModels asyncModels = mock(AsyncModels.class);
        MediaFetcher syncOnlyMediaFetcher = request -> FetchedMedia.from(IMAGE_BYTES, "image/png");
        GoogleGenAiChatModel model = GoogleGenAiChatModel.builder()
                .client(clientWith(asyncModels))
                .modelName(MODEL_NAME)
                .mediaFetcher(syncOnlyMediaFetcher)
                .build();

        CompletableFuture<ChatResponse> response = model.chatAsync(REQUEST_WITH_IMAGE_URL);

        assertThat(response)
                .failsWithin(Duration.ofSeconds(10))
                .withThrowableThat()
                .havingCause()
                .isInstanceOf(AsyncNotSupportedException.class)
                .withMessageContaining("fetchAsync()");
        verify(asyncModels, never()).generateContent(anyString(), anyList(), any(GenerateContentConfig.class));
    }

    @Test
    void chatAsync_rejects_media_urls_of_non_public_hosts_by_default_through_the_future() throws Exception {
        AsyncModels asyncModels = mock(AsyncModels.class);
        GoogleGenAiChatModel model = GoogleGenAiChatModel.builder()
                .client(clientWith(asyncModels))
                .modelName(MODEL_NAME)
                .build();

        CompletableFuture<ChatResponse> response =
                model.chatAsync(UserMessage.from(ImageContent.from("http://127.0.0.1/cat.png")));

        assertThat(response)
                .failsWithin(Duration.ofSeconds(10))
                .withThrowableThat()
                .havingCause()
                .isInstanceOf(InvalidRequestException.class)
                .withMessageContaining("non-public address");
        verify(asyncModels, never()).generateContent(anyString(), anyList(), any(GenerateContentConfig.class));
    }

    @Test
    void streaming_chat_model_downloads_media_urls_with_fetchAsync() throws Exception {
        Models models = streamingModels();
        RecordingMediaFetcher mediaFetcher = new RecordingMediaFetcher();
        GoogleGenAiStreamingChatModel model = GoogleGenAiStreamingChatModel.builder()
                .client(clientWith(models))
                .modelName(MODEL_NAME)
                .mediaFetcher(mediaFetcher)
                .build();

        ChatResponse response = stream(model, REQUEST_WITH_IMAGE_URL).get(10, SECONDS);

        assertThat(response.aiMessage().text()).isEqualTo("A cat");
        assertThat(mediaFetcher.fetchedAsync).containsExactly(IMAGE_URL);
        assertThat(mediaFetcher.fetched).isEmpty();
        assertThat(sentStreamedImage(models)).isEqualTo(IMAGE_BYTES);
    }

    @Test
    void streaming_chat_model_fails_through_onError_when_media_fetcher_does_not_implement_fetchAsync()
            throws Exception {
        Models models = streamingModels();
        MediaFetcher syncOnlyMediaFetcher = request -> FetchedMedia.from(IMAGE_BYTES, "image/png");
        GoogleGenAiStreamingChatModel model = GoogleGenAiStreamingChatModel.builder()
                .client(clientWith(models))
                .modelName(MODEL_NAME)
                .mediaFetcher(syncOnlyMediaFetcher)
                .build();

        CompletableFuture<ChatResponse> response = stream(model, REQUEST_WITH_IMAGE_URL);

        assertThat(response)
                .failsWithin(Duration.ofSeconds(10))
                .withThrowableThat()
                .havingCause()
                .isInstanceOf(AsyncNotSupportedException.class)
                .withMessageContaining("fetchAsync()");
        verify(models, never()).generateContentStream(anyString(), anyList(), any(GenerateContentConfig.class));
    }

    @Test
    void streaming_chat_model_submits_to_its_executor_on_the_calling_thread_and_waits_there_for_the_download()
            throws Exception {
        Models models = streamingModels();
        CompletableFuture<FetchedMedia> download = new CompletableFuture<>();
        List<Thread> submittingThreads = new CopyOnWriteArrayList<>();
        ExecutorService executor = new ThreadPoolExecutor(0, 4, 1, SECONDS, new SynchronousQueue<>()) {
            @Override
            public void execute(Runnable task) {
                submittingThreads.add(Thread.currentThread());
                super.execute(task);
            }
        };
        try {
            GoogleGenAiStreamingChatModel model = GoogleGenAiStreamingChatModel.builder()
                    .client(clientWith(models))
                    .modelName(MODEL_NAME)
                    .executor(executor)
                    .mediaFetcher(asyncMediaFetcher(download))
                    .build();

            CompletableFuture<ChatResponse> response = stream(model, REQUEST_WITH_IMAGE_URL);

            assertThat(submittingThreads).containsExactly(Thread.currentThread());
            assertThat(response).isNotDone();
            Thread completing = new Thread(() -> download.complete(FetchedMedia.from(IMAGE_BYTES, "image/png")));
            completing.start();
            completing.join(10_000);

            assertThat(response.get(10, SECONDS).aiMessage().text()).isEqualTo("A cat");
            assertThat(submittingThreads).containsExactly(Thread.currentThread());
            assertThat(sentStreamedImage(models)).isEqualTo(IMAGE_BYTES);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void streaming_chat_model_reports_a_rejected_task_to_onError_and_cancels_the_download() throws Exception {
        Models models = streamingModels();
        CompletableFuture<FetchedMedia> download = new CompletableFuture<>();
        RejectedExecutionException rejected = new RejectedExecutionException("busy");
        ExecutorService rejecting = new ThreadPoolExecutor(0, 1, 1, SECONDS, new SynchronousQueue<>()) {
            @Override
            public void execute(Runnable task) {
                throw rejected;
            }
        };
        GoogleGenAiStreamingChatModel model = GoogleGenAiStreamingChatModel.builder()
                .client(clientWith(models))
                .modelName(MODEL_NAME)
                .executor(rejecting)
                .mediaFetcher(asyncMediaFetcher(download))
                .build();

        CompletableFuture<ChatResponse> response = stream(model, REQUEST_WITH_IMAGE_URL);

        assertThat(response)
                .failsWithin(Duration.ofSeconds(10))
                .withThrowableThat()
                .havingCause()
                .isSameAs(rejected);
        assertThat(download).isCancelled();
        verify(models, never()).generateContentStream(anyString(), anyList(), any(GenerateContentConfig.class));
    }

    @Test
    void streaming_chat_model_without_media_urls_does_not_use_media_fetcher() throws Exception {
        Models models = streamingModels();
        MediaFetcher syncOnlyMediaFetcher = request -> {
            throw new AssertionError("Unexpected download of " + request.url());
        };
        GoogleGenAiStreamingChatModel model = GoogleGenAiStreamingChatModel.builder()
                .client(clientWith(models))
                .modelName(MODEL_NAME)
                .mediaFetcher(syncOnlyMediaFetcher)
                .build();

        ChatResponse response = stream(
                        model, ChatRequest.builder().messages(UserMessage.from("Hi")).build())
                .get(10, SECONDS);

        assertThat(response.aiMessage().text()).isEqualTo("A cat");
    }

    @Test
    void batch_chat_model_downloads_media_urls_with_configured_media_fetcher() {
        RuntimeException stop = new RuntimeException("stop after the download");
        List<URI> fetched = new CopyOnWriteArrayList<>();
        GoogleGenAiBatchChatModel model = GoogleGenAiBatchChatModel.builder()
                .apiKey("does not matter")
                .modelName(MODEL_NAME)
                .mediaFetcher(request -> {
                    fetched.add(request.url());
                    throw stop;
                })
                .build();

        assertThatThrownBy(() -> model.submit("batch", List.of(REQUEST_WITH_IMAGE_URL)))
                .isSameAs(stop);
        assertThat(fetched).containsExactly(IMAGE_URL);
    }

    @Test
    void token_count_estimator_downloads_media_urls_with_configured_media_fetcher() {
        RuntimeException stop = new RuntimeException("stop after the download");
        List<URI> fetched = new CopyOnWriteArrayList<>();
        GoogleGenAiTokenCountEstimator estimator = GoogleGenAiTokenCountEstimator.builder()
                .apiKey("does not matter")
                .modelName(MODEL_NAME)
                .mediaFetcher(request -> {
                    fetched.add(request.url());
                    throw stop;
                })
                .build();

        assertThatThrownBy(() -> estimator.estimateTokenCountInMessage(UserMessage.from(ImageContent.from(IMAGE_URL))))
                .isSameAs(stop);
        assertThat(fetched).containsExactly(IMAGE_URL);
    }

    @Test
    void caches_download_media_urls_with_configured_media_fetcher() {
        RuntimeException stop = new RuntimeException("stop after the download");
        List<URI> fetched = new CopyOnWriteArrayList<>();
        GoogleGenAiCaches caches = GoogleGenAiCaches.builder()
                .apiKey("does not matter")
                .mediaFetcher(request -> {
                    fetched.add(request.url());
                    throw stop;
                })
                .build();

        assertThatThrownBy(() ->
                        caches.createCache(MODEL_NAME, List.of(UserMessage.from(ImageContent.from(IMAGE_URL))), null))
                .isSameAs(stop);
        assertThat(fetched).containsExactly(IMAGE_URL);
    }

    private static MediaFetcher asyncMediaFetcher(CompletableFuture<FetchedMedia> download) {
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

    private static CompletableFuture<ChatResponse> stream(GoogleGenAiStreamingChatModel model, ChatRequest request) {
        CompletableFuture<ChatResponse> response = new CompletableFuture<>();
        model.chat(request, new StreamingChatResponseHandler() {

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
        });
        return response;
    }

    @SuppressWarnings("unchecked")
    private static Models streamingModels() {
        Models models = mock(Models.class);
        ResponseStream<GenerateContentResponse> stream = mock(ResponseStream.class);
        when(stream.iterator()).thenReturn(List.of(RESPONSE).iterator());
        when(models.generateContentStream(anyString(), anyList(), any(GenerateContentConfig.class)))
                .thenReturn(stream);
        return models;
    }

    @SuppressWarnings("unchecked")
    private static byte[] sentImage(Models models) {
        ArgumentCaptor<List<Content>> contents = ArgumentCaptor.forClass(List.class);
        verify(models).generateContent(anyString(), contents.capture(), any(GenerateContentConfig.class));
        return imageOf(contents.getValue());
    }

    @SuppressWarnings("unchecked")
    private static byte[] sentImage(AsyncModels asyncModels) {
        ArgumentCaptor<List<Content>> contents = ArgumentCaptor.forClass(List.class);
        verify(asyncModels).generateContent(anyString(), contents.capture(), any(GenerateContentConfig.class));
        return imageOf(contents.getValue());
    }

    @SuppressWarnings("unchecked")
    private static byte[] sentStreamedImage(Models models) {
        ArgumentCaptor<List<Content>> contents = ArgumentCaptor.forClass(List.class);
        verify(models).generateContentStream(anyString(), contents.capture(), any(GenerateContentConfig.class));
        return imageOf(contents.getValue());
    }

    private static byte[] imageOf(List<Content> contents) {
        Part image = contents.get(0).parts().orElseThrow().get(1);
        assertThat(image.inlineData().orElseThrow().mimeType()).hasValue("image/png");
        return image.inlineData().orElseThrow().data().orElseThrow();
    }

    private static Client clientWith(Models models) throws Exception {
        Client client = mock(Client.class);
        Field modelsField = Client.class.getDeclaredField("models");
        modelsField.setAccessible(true);
        modelsField.set(client, models);
        return client;
    }

    private static Client clientWith(AsyncModels asyncModels) throws Exception {
        Client client = mock(Client.class);
        Client.Async async = mock(Client.Async.class);
        Field asyncField = Client.class.getDeclaredField("async");
        asyncField.setAccessible(true);
        asyncField.set(client, async);
        Field asyncModelsField = Client.Async.class.getDeclaredField("models");
        asyncModelsField.setAccessible(true);
        asyncModelsField.set(async, asyncModels);
        return client;
    }

    private static final class RecordingMediaFetcher implements MediaFetcher {

        private final List<URI> fetched = new CopyOnWriteArrayList<>();
        private final List<URI> fetchedAsync = new CopyOnWriteArrayList<>();

        @Override
        public FetchedMedia fetch(MediaFetchRequest request) {
            fetched.add(request.url());
            return FetchedMedia.from(IMAGE_BYTES, "image/webp");
        }

        @Override
        public CompletableFuture<FetchedMedia> fetchAsync(MediaFetchRequest request) {
            fetchedAsync.add(request.url());
            return CompletableFuture.completedFuture(FetchedMedia.from(IMAGE_BYTES, "image/webp"));
        }
    }
}
