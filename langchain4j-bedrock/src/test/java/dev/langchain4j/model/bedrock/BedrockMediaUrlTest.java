package dev.langchain4j.model.bedrock;

import static dev.langchain4j.model.bedrock.AbstractBedrockChatModel.IMAGE_URL_NOT_SUPPORTED;
import static dev.langchain4j.model.bedrock.AbstractBedrockChatModel.PDF_URL_NOT_SUPPORTED;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.image.Image;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.PdfFileContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.pdf.PdfFile;
import dev.langchain4j.exception.UnsupportedFeatureException;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
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
import software.amazon.awssdk.services.bedrockruntime.model.Message;
import software.amazon.awssdk.services.bedrockruntime.model.MessageStartEvent;
import software.amazon.awssdk.services.bedrockruntime.model.MessageStopEvent;
import software.amazon.awssdk.services.bedrockruntime.model.StopReason;
import software.amazon.awssdk.services.bedrockruntime.model.TokenUsage;

class BedrockMediaUrlTest {

    private static final byte[] IMAGE_BYTES = {1, 2, 3};
    private static final byte[] PDF_BYTES = {4, 5, 6};

    static Stream<Content> mediaGivenByUrl() {
        return Stream.of(
                ImageContent.from("https://example.com/cat.png"),
                ImageContent.from("http://localhost/cat.png"),
                ImageContent.from(URI.create("file:///etc/hosts")),
                ImageContent.from("cat.png"),
                PdfFileContent.from("https://example.com/report.pdf"),
                PdfFileContent.from(URI.create("file:///home/me/report.pdf")),
                PdfFileContent.from("report.pdf"));
    }

    @ParameterizedTest
    @MethodSource("mediaGivenByUrl")
    void chat_should_reject_media_given_by_url(Content content) {

        // given
        BedrockRuntimeClient client = mock(BedrockRuntimeClient.class);
        ChatModel model =
                BedrockChatModel.builder().modelId("test-model").client(client).build();

        // when-then
        assertThatThrownBy(() -> model.chat(UserMessage.from(TextContent.from("describe"), content)))
                .isExactlyInstanceOf(UnsupportedFeatureException.class)
                .hasMessage(expectedMessage(content));
        verify(client, never()).converse(any(ConverseRequest.class));
    }

    @Test
    void chat_should_reject_images_given_by_url_in_tool_results() {

        // given
        BedrockRuntimeClient client = mock(BedrockRuntimeClient.class);
        ChatModel model =
                BedrockChatModel.builder().modelId("test-model").client(client).build();
        ToolExecutionResultMessage toolResult = ToolExecutionResultMessage.builder()
                .id("call-1")
                .toolName("chart")
                .contents(TextContent.from("the chart"), ImageContent.from("https://example.com/chart.png"))
                .build();

        // when-then
        assertThatThrownBy(() -> model.chat(UserMessage.from("draw"), toolCall(), toolResult))
                .isExactlyInstanceOf(UnsupportedFeatureException.class)
                .hasMessage(IMAGE_URL_NOT_SUPPORTED);
        verify(client, never()).converse(any(ConverseRequest.class));
    }

    @Test
    void chat_should_reject_media_with_neither_base64_data_nor_a_url() {

        // given
        BedrockRuntimeClient client = mock(BedrockRuntimeClient.class);
        ChatModel model =
                BedrockChatModel.builder().modelId("test-model").client(client).build();

        // when-then
        assertThatThrownBy(() -> model.chat(UserMessage.from(
                        TextContent.from("describe"),
                        ImageContent.from(Image.builder().mimeType("image/png").build()))))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("The image has neither base64 data nor a URL");
        assertThatThrownBy(() -> model.chat(UserMessage.from(
                        TextContent.from("describe"),
                        PdfFileContent.from(PdfFile.builder().build()))))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("The PDF file has neither base64 data nor a URL");
        verify(client, never()).converse(any(ConverseRequest.class));
    }

    @Test
    void streaming_chat_should_reject_media_given_by_url() {

        // given
        BedrockRuntimeAsyncClient client = mock(BedrockRuntimeAsyncClient.class);
        StreamingChatModel model = BedrockStreamingChatModel.builder()
                .modelId("test-model")
                .client(client)
                .build();
        CompletingHandler handler = new CompletingHandler();

        // when-then
        assertThatThrownBy(() -> model.chat(
                        ChatRequest.builder()
                                .messages(UserMessage.from(PdfFileContent.from("https://example.com/report.pdf")))
                                .build(),
                        handler))
                .isExactlyInstanceOf(UnsupportedFeatureException.class)
                .hasMessage(PDF_URL_NOT_SUPPORTED);
        verify(client, never())
                .converseStream(any(ConverseStreamRequest.class), any(ConverseStreamResponseHandler.class));
    }

    @Test
    void chat_should_send_images_and_pdf_files_read_from_local_files(@TempDir Path directory) throws Exception {

        // given
        Path image = Files.write(directory.resolve("cat.png"), IMAGE_BYTES);
        Path report = Files.write(directory.resolve("report.pdf"), PDF_BYTES);
        BedrockRuntimeClient client = mock(BedrockRuntimeClient.class);
        when(client.converse(any(ConverseRequest.class))).thenReturn(converseResponse());
        ChatModel model =
                BedrockChatModel.builder().modelId("test-model").client(client).build();

        // when
        model.chat(UserMessage.from(
                TextContent.from("describe"), ImageContent.from(image, "image/png"), PdfFileContent.from(report)));

        // then
        List<ContentBlock> content = capturedRequest(client).messages().get(0).content();
        assertThat(content.get(1).image().source().bytes()).isEqualTo(SdkBytes.fromByteArray(IMAGE_BYTES));
        assertThat(content.get(2).document().source().bytes()).isEqualTo(SdkBytes.fromByteArray(PDF_BYTES));
    }

    // names of documents

    @Test
    void should_name_a_document_without_a_url_after_its_content_in_every_request(@TempDir Path directory)
            throws Exception {

        // given
        Path report = Files.write(directory.resolve("report.pdf"), PDF_BYTES);
        String pdfBase64 = Base64.getEncoder().encodeToString(PDF_BYTES);
        BedrockRuntimeClient client = mock(BedrockRuntimeClient.class);
        when(client.converse(any(ConverseRequest.class))).thenReturn(converseResponse());
        ChatModel model =
                BedrockChatModel.builder().modelId("test-model").client(client).build();

        // when
        model.chat(UserMessage.from(TextContent.from("summarize"), PdfFileContent.from(report)));
        model.chat(UserMessage.from(TextContent.from("summarize"), PdfFileContent.from(pdfBase64, "application/pdf")));

        // then
        ArgumentCaptor<ConverseRequest> requests = ArgumentCaptor.forClass(ConverseRequest.class);
        verify(client, times(2)).converse(requests.capture());
        String expected = expectedDocumentName(PDF_BYTES);
        assertThat(documentNames(requests.getAllValues().get(0).messages())).containsExactly(expected);
        assertThat(documentNames(requests.getAllValues().get(1).messages())).containsExactly(expected);
    }

    @Test
    void should_give_documents_distinct_names_in_one_request() throws Exception {

        // given
        String pdfBase64 = Base64.getEncoder().encodeToString(PDF_BYTES);
        String otherBase64 = Base64.getEncoder().encodeToString(IMAGE_BYTES);
        PdfFile namedAfterItsUrl = PdfFile.builder()
                .url("https://example.com/reports/annual.pdf")
                .base64Data(otherBase64)
                .build();
        BedrockRuntimeClient client = mock(BedrockRuntimeClient.class);
        when(client.converse(any(ConverseRequest.class))).thenReturn(converseResponse());
        ChatModel model =
                BedrockChatModel.builder().modelId("test-model").client(client).build();

        // when
        model.chat(ChatRequest.builder()
                .messages(
                        UserMessage.from(
                                PdfFileContent.from(pdfBase64, "application/pdf"),
                                PdfFileContent.from(otherBase64, "application/pdf"),
                                PdfFileContent.from(pdfBase64, "application/pdf"),
                                PdfFileContent.from(namedAfterItsUrl)),
                        AiMessage.from("ok"),
                        UserMessage.from(
                                PdfFileContent.from(pdfBase64, "application/pdf"),
                                PdfFileContent.from(namedAfterItsUrl)))
                .build());

        // then
        String expected = expectedDocumentName(PDF_BYTES);
        assertThat(documentNames(capturedRequest(client).messages()))
                .containsExactly(
                        expected,
                        expectedDocumentName(IMAGE_BYTES),
                        expected + "-2",
                        "annual",
                        expected + "-3",
                        "annual-2");
    }

    @Test
    void should_give_documents_distinct_names_when_a_suffix_matches_another_name() {

        // given
        BedrockRuntimeClient client = mock(BedrockRuntimeClient.class);
        when(client.converse(any(ConverseRequest.class))).thenReturn(converseResponse());
        ChatModel model =
                BedrockChatModel.builder().modelId("test-model").client(client).build();

        // when
        model.chat(UserMessage.from(
                pdf("https://example.com/annual.pdf", PDF_BYTES),
                pdf("https://example.com/annual.pdf", IMAGE_BYTES),
                pdf("https://example.com/annual-2.pdf", new byte[] {7})));

        // then
        assertThat(documentNames(capturedRequest(client).messages())).containsExactly("annual", "annual-2", "annual-2-2");
    }

    @Test
    void streaming_should_give_documents_distinct_names_after_their_content() throws Exception {

        // given
        AtomicReference<ConverseStreamRequest> streamRequest = new AtomicReference<>();
        StreamingChatModel model = BedrockStreamingChatModel.builder()
                .modelId("test-model")
                .client(streamingClient(streamRequest))
                .build();
        String pdfBase64 = Base64.getEncoder().encodeToString(PDF_BYTES);
        CompletingHandler handler = new CompletingHandler();

        // when
        model.chat(
                ChatRequest.builder()
                        .messages(UserMessage.from(
                                PdfFileContent.from(pdfBase64, "application/pdf"),
                                PdfFileContent.from(pdfBase64, "application/pdf")))
                        .build(),
                handler);

        // then
        assertThat(handler.response).succeedsWithin(10, SECONDS);
        String expected = expectedDocumentName(PDF_BYTES);
        assertThat(documentNames(streamRequest.get().messages())).containsExactly(expected, expected + "-2");
    }

    private static PdfFileContent pdf(String url, byte[] content) {
        return PdfFileContent.from(PdfFile.builder()
                .url(url)
                .base64Data(Base64.getEncoder().encodeToString(content))
                .build());
    }

    private static String expectedMessage(Content content) {
        return content instanceof ImageContent ? IMAGE_URL_NOT_SUPPORTED : PDF_URL_NOT_SUPPORTED;
    }

    private static String expectedDocumentName(byte[] content) throws Exception {
        return "document-"
                + HexFormat.of()
                        .formatHex(MessageDigest.getInstance("SHA-256").digest(content))
                        .substring(0, 12);
    }

    private static List<String> documentNames(List<Message> messages) {
        return messages.stream()
                .flatMap(message -> message.content().stream())
                .filter(block -> block.document() != null)
                .map(block -> block.document().name())
                .toList();
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
}
