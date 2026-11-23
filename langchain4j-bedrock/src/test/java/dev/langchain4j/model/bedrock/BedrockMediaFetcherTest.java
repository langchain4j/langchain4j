package dev.langchain4j.model.bedrock;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.media.FetchedMedia;
import dev.langchain4j.data.media.MediaFetchRequest;
import dev.langchain4j.data.media.MediaFetcher;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.PdfFileContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.exception.AsyncNotSupportedException;
import dev.langchain4j.exception.InvalidRequestException;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatModelStreamingEvent;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.awscore.DefaultAwsResponseMetadata;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.core.async.SdkPublisher;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeAsyncClient;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ConversationRole;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseOutput;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseRequest;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseResponse;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseStreamMetadataEvent;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseStreamMetrics;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseStreamOutput;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseStreamRequest;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseStreamResponseHandler;
import software.amazon.awssdk.services.bedrockruntime.model.GuardrailConverseImageFormat;
import software.amazon.awssdk.services.bedrockruntime.model.ImageFormat;
import software.amazon.awssdk.services.bedrockruntime.model.Message;
import software.amazon.awssdk.services.bedrockruntime.model.MessageStartEvent;
import software.amazon.awssdk.services.bedrockruntime.model.MessageStopEvent;
import software.amazon.awssdk.services.bedrockruntime.model.StopReason;
import software.amazon.awssdk.services.bedrockruntime.model.TokenUsage;

class BedrockMediaFetcherTest {

    private static final URI IMAGE_URL = URI.create("https://example.com/cat.png");
    private static final URI PDF_URL = URI.create("https://example.com/report.pdf");
    private static final URI TOOL_IMAGE_URL = URI.create("https://example.com/chart.jpg");
    private static final byte[] IMAGE_BYTES = {1, 2, 3};
    private static final byte[] PDF_BYTES = {4, 5, 6};
    private static final byte[] TOOL_IMAGE_BYTES = {7, 8, 9};

    private static final MediaFetcher SYNC_ONLY_FETCHER = request -> FetchedMedia.from(IMAGE_BYTES, "image/png");

    @Test
    void should_download_images_and_pdf_files_through_the_configured_media_fetcher() {
        RecordingMediaFetcher mediaFetcher = new RecordingMediaFetcher(Map.of(
                IMAGE_URL, FetchedMedia.from(IMAGE_BYTES, "image/png"),
                PDF_URL, FetchedMedia.from(PDF_BYTES, "application/pdf"),
                TOOL_IMAGE_URL, FetchedMedia.from(TOOL_IMAGE_BYTES, "image/jpeg")));
        BedrockRuntimeClient client = mock(BedrockRuntimeClient.class);
        when(client.converse(any(ConverseRequest.class))).thenReturn(converseResponse());
        ChatModel model = BedrockChatModel.builder()
                .modelId("test-model")
                .client(client)
                .mediaFetcher(mediaFetcher)
                .build();

        model.chat(ChatRequest.builder().messages(messagesWithMedia()).build());

        ConverseRequest request = capturedRequest(client);
        List<ContentBlock> userContent = request.messages().get(0).content();
        assertThat(userContent.get(1).image().source().bytes()).isEqualTo(SdkBytes.fromByteArray(IMAGE_BYTES));
        assertThat(userContent.get(2).document().source().bytes()).isEqualTo(SdkBytes.fromByteArray(PDF_BYTES));
        ContentBlock toolResult = request.messages().get(2).content().get(0);
        assertThat(toolResult.toolResult().content().get(1).image().source().bytes())
                .isEqualTo(SdkBytes.fromByteArray(TOOL_IMAGE_BYTES));
        assertThat(mediaFetcher.fetched).containsExactlyInAnyOrder(IMAGE_URL, PDF_URL, TOOL_IMAGE_URL);
        assertThat(mediaFetcher.fetchedAsync).isEmpty();
    }

    @Test
    void should_use_the_mime_type_of_the_downloaded_image_when_the_url_has_no_extension() {
        URI url = URI.create("https://example.com/images?id=42");
        MediaFetcher mediaFetcher = new RecordingMediaFetcher(Map.of(url, FetchedMedia.from(IMAGE_BYTES, "image/webp")));
        BedrockRuntimeClient client = mock(BedrockRuntimeClient.class);
        when(client.converse(any(ConverseRequest.class))).thenReturn(converseResponse());
        ChatModel model = BedrockChatModel.builder()
                .modelId("test-model")
                .client(client)
                .mediaFetcher(mediaFetcher)
                .build();

        model.chat(UserMessage.from(TextContent.from("describe"), ImageContent.from(url)));

        assertThat(capturedRequest(client).messages().get(0).content().get(1).image().format())
                .isEqualTo(ImageFormat.WEBP);
    }

    @Test
    void should_prefer_the_extension_of_the_url_to_the_mime_type_of_the_downloaded_image() {
        MediaFetcher mediaFetcher =
                new RecordingMediaFetcher(Map.of(IMAGE_URL, FetchedMedia.from(IMAGE_BYTES, "image/jpeg")));
        BedrockRuntimeClient client = mock(BedrockRuntimeClient.class);
        when(client.converse(any(ConverseRequest.class))).thenReturn(converseResponse());
        ChatModel model = BedrockChatModel.builder()
                .modelId("test-model")
                .client(client)
                .mediaFetcher(mediaFetcher)
                .build();

        model.chat(UserMessage.from(ImageContent.from(IMAGE_URL)));

        assertThat(capturedRequest(client).messages().get(0).content().get(0).image().format())
                .isEqualTo(ImageFormat.PNG);
    }

    @Test
    void should_download_each_url_once_per_request() {
        URI url = URI.create("https://example.com/photo");
        RecordingMediaFetcher mediaFetcher =
                new RecordingMediaFetcher(Map.of(url, FetchedMedia.from(IMAGE_BYTES, "image/png")));
        BedrockRuntimeClient client = mock(BedrockRuntimeClient.class);
        when(client.converse(any(ConverseRequest.class))).thenReturn(converseResponse());
        ChatModel model = BedrockChatModel.builder()
                .modelId("test-model")
                .client(client)
                .mediaFetcher(mediaFetcher)
                .defaultRequestParameters(BedrockChatRequestParameters.builder()
                        .guardrailConfiguration(BedrockGuardrailConfiguration.builder()
                                .guardrailIdentifier("guardrail-id")
                                .guardrailVersion("1")
                                .guardContentPlacement(BedrockGuardContentPlacement.ALL_USER_MESSAGES)
                                .build())
                        .build())
                .build();

        model.chat(
                UserMessage.from(TextContent.from("first"), ImageContent.from(url)),
                AiMessage.from("ok"),
                UserMessage.from(TextContent.from("again"), ImageContent.from(url)));

        ConverseRequest request = capturedRequest(client);
        assertThat(request.messages().get(2).content().get(1).guardContent().image().format())
                .isEqualTo(GuardrailConverseImageFormat.PNG);
        assertThat(mediaFetcher.fetched).containsExactly(url);
    }

    static Stream<Content> localFiles() {
        return Stream.of(
                ImageContent.from(URI.create("file:///etc/passwd")),
                ImageContent.from("/home/me/cat.png"),
                PdfFileContent.from(URI.create("file:///home/me/report.pdf")),
                PdfFileContent.from("report.pdf"));
    }

    @ParameterizedTest
    @MethodSource("localFiles")
    void should_not_read_local_files_by_default(Content localFile) {
        BedrockRuntimeClient client = mock(BedrockRuntimeClient.class);
        ChatModel model =
                BedrockChatModel.builder().modelId("test-model").client(client).build();

        assertThatThrownBy(() -> model.chat(UserMessage.from(TextContent.from("describe"), localFile)))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("from(Path");
        verify(client, never()).converse(any(ConverseRequest.class));
    }

    @Test
    void should_not_read_local_files_in_tool_results_by_default() {
        BedrockRuntimeClient client = mock(BedrockRuntimeClient.class);
        ChatModel model =
                BedrockChatModel.builder().modelId("test-model").client(client).build();
        ToolExecutionResultMessage toolResult = ToolExecutionResultMessage.builder()
                .id("call-1")
                .toolName("chart")
                .contents(TextContent.from("the chart"), ImageContent.from(URI.create("file:///etc/passwd")))
                .build();

        assertThatThrownBy(() -> model.chat(UserMessage.from("draw"), toolCall(), toolResult))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("from(Path");
        verify(client, never()).converse(any(ConverseRequest.class));
    }

    @Test
    void chatAsync_should_download_with_fetchAsync() {
        RecordingMediaFetcher mediaFetcher = new RecordingMediaFetcher(Map.of(
                IMAGE_URL, FetchedMedia.from(IMAGE_BYTES, "image/png"),
                PDF_URL, FetchedMedia.from(PDF_BYTES, "application/pdf"),
                TOOL_IMAGE_URL, FetchedMedia.from(TOOL_IMAGE_BYTES, "image/jpeg")));
        BedrockRuntimeAsyncClient asyncClient = mock(BedrockRuntimeAsyncClient.class);
        when(asyncClient.converse(any(ConverseRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(converseResponse()));
        ChatModel model = BedrockChatModel.builder()
                .modelId("test-model")
                .client(mock(BedrockRuntimeClient.class))
                .asyncClient(asyncClient)
                .mediaFetcher(mediaFetcher)
                .build();

        CompletableFuture<ChatResponse> response =
                model.chatAsync(ChatRequest.builder().messages(messagesWithMedia()).build());

        assertThat(response).succeedsWithin(10, SECONDS);
        ArgumentCaptor<ConverseRequest> request = ArgumentCaptor.forClass(ConverseRequest.class);
        verify(asyncClient).converse(request.capture());
        List<ContentBlock> userContent = request.getValue().messages().get(0).content();
        assertThat(userContent.get(1).image().source().bytes()).isEqualTo(SdkBytes.fromByteArray(IMAGE_BYTES));
        assertThat(userContent.get(2).document().source().bytes()).isEqualTo(SdkBytes.fromByteArray(PDF_BYTES));
        assertThat(mediaFetcher.fetchedAsync).containsExactlyInAnyOrder(IMAGE_URL, PDF_URL, TOOL_IMAGE_URL);
        assertThat(mediaFetcher.fetched).isEmpty();
    }

    @Test
    void chatAsync_should_fail_through_the_future_when_the_media_fetcher_cannot_download_asynchronously() {
        BedrockRuntimeAsyncClient asyncClient = mock(BedrockRuntimeAsyncClient.class);
        ChatModel model = BedrockChatModel.builder()
                .modelId("test-model")
                .client(mock(BedrockRuntimeClient.class))
                .asyncClient(asyncClient)
                .mediaFetcher(SYNC_ONLY_FETCHER)
                .build();

        CompletableFuture<ChatResponse> response = model.chatAsync(
                ChatRequest.builder().messages(UserMessage.from(ImageContent.from(IMAGE_URL))).build());

        assertThat(response)
                .failsWithin(10, SECONDS)
                .withThrowableThat()
                .havingCause()
                .isInstanceOf(AsyncNotSupportedException.class)
                .withMessageContaining("fetchAsync()");
        verify(asyncClient, never()).converse(any(ConverseRequest.class));
    }

    @Test
    void chatAsync_should_not_call_the_media_fetcher_without_media_urls() {
        BedrockRuntimeAsyncClient asyncClient = mock(BedrockRuntimeAsyncClient.class);
        when(asyncClient.converse(any(ConverseRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(converseResponse()));
        ChatModel model = BedrockChatModel.builder()
                .modelId("test-model")
                .client(mock(BedrockRuntimeClient.class))
                .asyncClient(asyncClient)
                .mediaFetcher(SYNC_ONLY_FETCHER)
                .build();

        CompletableFuture<ChatResponse> response = model.chatAsync(ChatRequest.builder()
                .messages(UserMessage.from(TextContent.from("hi"), ImageContent.from("aGVsbG8=", "image/png")))
                .build());

        assertThat(response).succeedsWithin(10, SECONDS);
    }

    @Test
    void chatAsync_should_report_the_rejection_of_a_local_file_through_the_future() {
        BedrockRuntimeAsyncClient asyncClient = mock(BedrockRuntimeAsyncClient.class);
        ChatModel model = BedrockChatModel.builder()
                .modelId("test-model")
                .client(mock(BedrockRuntimeClient.class))
                .asyncClient(asyncClient)
                .build();

        CompletableFuture<ChatResponse> response = model.chatAsync(ChatRequest.builder()
                .messages(UserMessage.from(ImageContent.from(URI.create("file:///etc/passwd"))))
                .build());

        assertThat(response)
                .failsWithin(10, SECONDS)
                .withThrowableThat()
                .havingCause()
                .isInstanceOf(InvalidRequestException.class)
                .withMessageContaining("from(Path");
        verify(asyncClient, never()).converse(any(ConverseRequest.class));
    }

    @Test
    void streaming_should_download_with_fetchAsync() throws Exception {
        RecordingMediaFetcher mediaFetcher = new RecordingMediaFetcher(Map.of(
                IMAGE_URL, FetchedMedia.from(IMAGE_BYTES, "image/png"),
                PDF_URL, FetchedMedia.from(PDF_BYTES, "application/pdf"),
                TOOL_IMAGE_URL, FetchedMedia.from(TOOL_IMAGE_BYTES, "image/jpeg")));
        AtomicReference<ConverseStreamRequest> streamRequest = new AtomicReference<>();
        StreamingChatModel model = BedrockStreamingChatModel.builder()
                .modelId("test-model")
                .client(streamingClient(streamRequest))
                .mediaFetcher(mediaFetcher)
                .build();

        CompletingHandler handler = new CompletingHandler();
        model.chat(ChatRequest.builder().messages(messagesWithMedia()).build(), handler);

        assertThat(handler.response).succeedsWithin(10, SECONDS);
        List<ContentBlock> userContent = streamRequest.get().messages().get(0).content();
        assertThat(userContent.get(1).image().source().bytes()).isEqualTo(SdkBytes.fromByteArray(IMAGE_BYTES));
        assertThat(userContent.get(2).document().source().bytes()).isEqualTo(SdkBytes.fromByteArray(PDF_BYTES));
        assertThat(mediaFetcher.fetchedAsync).containsExactlyInAnyOrder(IMAGE_URL, PDF_URL, TOOL_IMAGE_URL);
        assertThat(mediaFetcher.fetched).isEmpty();
    }

    @Test
    void streaming_should_report_to_onError_when_the_media_fetcher_cannot_download_asynchronously() {
        BedrockRuntimeAsyncClient client = mock(BedrockRuntimeAsyncClient.class);
        StreamingChatModel model = BedrockStreamingChatModel.builder()
                .modelId("test-model")
                .client(client)
                .mediaFetcher(SYNC_ONLY_FETCHER)
                .build();

        CompletingHandler handler = new CompletingHandler();
        model.chat(ChatRequest.builder().messages(UserMessage.from(ImageContent.from(IMAGE_URL))).build(), handler);

        assertThat(handler.response)
                .failsWithin(10, SECONDS)
                .withThrowableThat()
                .havingCause()
                .isInstanceOf(AsyncNotSupportedException.class);
        verify(client, never())
                .converseStream(any(ConverseStreamRequest.class), any(ConverseStreamResponseHandler.class));
    }

    @Test
    void reactive_streaming_should_download_with_fetchAsync() {
        RecordingMediaFetcher mediaFetcher =
                new RecordingMediaFetcher(Map.of(IMAGE_URL, FetchedMedia.from(IMAGE_BYTES, "image/png")));
        AtomicReference<ConverseStreamRequest> streamRequest = new AtomicReference<>();
        StreamingChatModel model = BedrockStreamingChatModel.builder()
                .modelId("test-model")
                .client(streamingClient(streamRequest))
                .mediaFetcher(mediaFetcher)
                .build();

        CompletingSubscriber subscriber = new CompletingSubscriber();
        model.chat(ChatRequest.builder()
                        .messages(UserMessage.from(ImageContent.from(IMAGE_URL)))
                        .build())
                .subscribe(subscriber);

        assertThat(subscriber.completion).succeedsWithin(10, SECONDS);
        assertThat(streamRequest.get().messages().get(0).content().get(0).image().source().bytes())
                .isEqualTo(SdkBytes.fromByteArray(IMAGE_BYTES));
        assertThat(mediaFetcher.fetchedAsync).containsExactly(IMAGE_URL);
        assertThat(mediaFetcher.fetched).isEmpty();
    }

    @Test
    void reactive_streaming_should_report_to_onError_when_the_media_fetcher_cannot_download_asynchronously() {
        BedrockRuntimeAsyncClient client = mock(BedrockRuntimeAsyncClient.class);
        StreamingChatModel model = BedrockStreamingChatModel.builder()
                .modelId("test-model")
                .client(client)
                .mediaFetcher(SYNC_ONLY_FETCHER)
                .build();

        CompletingSubscriber subscriber = new CompletingSubscriber();
        model.chat(ChatRequest.builder()
                        .messages(UserMessage.from(ImageContent.from(IMAGE_URL)))
                        .build())
                .subscribe(subscriber);

        assertThat(subscriber.completion)
                .failsWithin(10, SECONDS)
                .withThrowableThat()
                .havingCause()
                .isInstanceOf(AsyncNotSupportedException.class);
        verify(client, never())
                .converseStream(any(ConverseStreamRequest.class), any(ConverseStreamResponseHandler.class));
    }

    private static List<ChatMessage> messagesWithMedia() {
        return List.of(
                UserMessage.from(
                        TextContent.from("describe"), ImageContent.from(IMAGE_URL), PdfFileContent.from(PDF_URL)),
                toolCall(),
                ToolExecutionResultMessage.builder()
                        .id("call-1")
                        .toolName("chart")
                        .contents(TextContent.from("the chart"), ImageContent.from(TOOL_IMAGE_URL))
                        .build());
    }

    private static AiMessage toolCall() {
        return AiMessage.from(ToolExecutionRequest.builder()
                .id("call-1")
                .name("chart")
                .arguments("{}")
                .build());
    }

    private static ConverseRequest capturedRequest(BedrockRuntimeClient client) {
        ArgumentCaptor<ConverseRequest> request = ArgumentCaptor.forClass(ConverseRequest.class);
        verify(client).converse(request.capture());
        return request.getValue();
    }

    private static ConverseResponse converseResponse() {
        ConverseResponse.Builder builder = ConverseResponse.builder()
                .output(ConverseOutput.fromMessage(Message.builder()
                        .role(ConversationRole.ASSISTANT)
                        .content(ContentBlock.builder().text("ok").build())
                        .build()))
                .stopReason(StopReason.END_TURN)
                .usage(TokenUsage.builder()
                        .inputTokens(1)
                        .outputTokens(1)
                        .totalTokens(2)
                        .build());
        builder.responseMetadata(DefaultAwsResponseMetadata.create(Map.of("AWS_REQUEST_ID", "request-id")));
        return builder.build();
    }

    private static BedrockRuntimeAsyncClient streamingClient(AtomicReference<ConverseStreamRequest> streamRequest) {
        BedrockRuntimeAsyncClient client = mock(BedrockRuntimeAsyncClient.class);
        List<ConverseStreamOutput> events = List.of(
                MessageStartEvent.builder().role(ConversationRole.ASSISTANT).build(),
                MessageStopEvent.builder().stopReason(StopReason.END_TURN).build(),
                ConverseStreamMetadataEvent.builder()
                        .usage(TokenUsage.builder()
                                .inputTokens(1)
                                .outputTokens(1)
                                .totalTokens(2)
                                .build())
                        .metrics(ConverseStreamMetrics.builder().latencyMs(1L).build())
                        .build());
        when(client.converseStream(any(ConverseStreamRequest.class), any(ConverseStreamResponseHandler.class)))
                .thenAnswer(invocation -> {
                    streamRequest.set(invocation.getArgument(0));
                    ConverseStreamResponseHandler responseHandler = invocation.getArgument(1);
                    responseHandler.onEventStream(SdkPublisher.fromIterable(events));
                    return CompletableFuture.completedFuture(null);
                });
        return client;
    }

    /**
     * Returns the given media for each URL and records whether it was asked with {@link #fetch(MediaFetchRequest)} or
     * {@link #fetchAsync(MediaFetchRequest)}.
     */
    private static class RecordingMediaFetcher implements MediaFetcher {

        final List<URI> fetched = new CopyOnWriteArrayList<>();
        final List<URI> fetchedAsync = new CopyOnWriteArrayList<>();
        private final Map<URI, FetchedMedia> media;

        RecordingMediaFetcher(Map<URI, FetchedMedia> media) {
            this.media = media;
        }

        @Override
        public FetchedMedia fetch(MediaFetchRequest request) {
            fetched.add(request.url());
            return media.get(request.url());
        }

        @Override
        public CompletableFuture<FetchedMedia> fetchAsync(MediaFetchRequest request) {
            fetchedAsync.add(request.url());
            return CompletableFuture.supplyAsync(() -> media.get(request.url()));
        }
    }

    private static class CompletingHandler implements StreamingChatResponseHandler {

        final CompletableFuture<ChatResponse> response = new CompletableFuture<>();

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
    }

    private static class CompletingSubscriber implements Flow.Subscriber<ChatModelStreamingEvent> {

        final CompletableFuture<Void> completion = new CompletableFuture<>();

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            subscription.request(Long.MAX_VALUE);
        }

        @Override
        public void onNext(ChatModelStreamingEvent event) {}

        @Override
        public void onError(Throwable error) {
            completion.completeExceptionally(error);
        }

        @Override
        public void onComplete() {
            completion.complete(null);
        }
    }
}
