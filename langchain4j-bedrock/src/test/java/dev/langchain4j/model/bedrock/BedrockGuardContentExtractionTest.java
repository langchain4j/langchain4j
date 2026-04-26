package dev.langchain4j.model.bedrock;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.image.Image;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.PdfFileContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.pdf.PdfFile;
import dev.langchain4j.model.chat.TestStreamingChatResponseHandler;
import dev.langchain4j.model.chat.request.ChatRequest;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.awscore.DefaultAwsResponseMetadata;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeAsyncClient;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.CacheTTL;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ConversationRole;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseOutput;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseRequest;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseResponse;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseStreamRequest;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseStreamResponseHandler;
import software.amazon.awssdk.services.bedrockruntime.model.Message;
import software.amazon.awssdk.services.bedrockruntime.model.StopReason;
import software.amazon.awssdk.services.bedrockruntime.model.TokenUsage;

class BedrockGuardContentExtractionTest {

    private static final String BASE64_IMAGE = "aW1hZ2U=";
    private static final String BASE64_PDF = "JVBERi0xLjQ=";

    private final TestableExtractor extractor = new TestableExtractor();

    @Test
    void should_keep_user_text_and_image_unchanged_without_guard_content_placement() {
        List<Message> messages = extractor.testExtractRegularMessages(
                List.of(userMessage("hello", pngImage())),
                null,
                null,
                null);

        List<ContentBlock> content = messages.get(0).content();
        assertThat(content).hasSize(2);
        assertThat(content.get(0).text()).isEqualTo("hello");
        assertThat(content.get(0).guardContent()).isNull();
        assertThat(content.get(1).image()).isNotNull();
        assertThat(content.get(1).guardContent()).isNull();
    }

    @Test
    void should_wrap_only_last_user_message_text_and_image_in_guard_content() {
        List<Message> messages = extractor.testExtractRegularMessages(
                List.of(
                        userMessage("first", pngImage()),
                        userMessage("last", jpegImage())),
                null,
                null,
                BedrockGuardContentPlacement.LAST_USER_MESSAGE);

        List<ContentBlock> firstUserContent = messages.get(0).content();
        assertThat(firstUserContent.get(0).text()).isEqualTo("first");
        assertThat(firstUserContent.get(1).image()).isNotNull();
        assertThat(firstUserContent)
                .allSatisfy(contentBlock -> assertThat(contentBlock.guardContent()).isNull());

        List<ContentBlock> lastUserContent = messages.get(1).content();
        assertThat(lastUserContent).hasSize(2);
        assertThat(lastUserContent.get(0).text()).isNull();
        assertThat(lastUserContent.get(0).guardContent().text().text()).isEqualTo("last");
        assertThat(lastUserContent.get(1).image()).isNull();
        assertThat(lastUserContent.get(1).guardContent().image().formatAsString()).isEqualTo("jpeg");
        assertThat(lastUserContent.get(1).guardContent().image().source().bytes().asUtf8String())
                .isEqualTo("image");
    }

    @Test
    void should_wrap_all_user_messages_text_and_supported_images_in_guard_content() {
        List<Message> messages = extractor.testExtractRegularMessages(
                List.of(
                        userMessage("first", pngImage()),
                        userMessage("second", jpegImage())),
                null,
                null,
                BedrockGuardContentPlacement.ALL_USER_MESSAGES);

        assertThat(messages.get(0).content().get(0).guardContent().text().text())
                .isEqualTo("first");
        assertThat(messages.get(0).content().get(1).guardContent().image().formatAsString())
                .isEqualTo("png");
        assertThat(messages.get(1).content().get(0).guardContent().text().text())
                .isEqualTo("second");
        assertThat(messages.get(1).content().get(1).guardContent().image().formatAsString())
                .isEqualTo("jpeg");
    }

    @Test
    void should_fallback_all_messages_to_regular_content_when_targeted_pdf_content_cannot_be_guarded() {
        List<Message> messages = extractor.testExtractRegularMessages(
                List.of(
                        userMessage("first", pngImage()),
                        new UserMessage(
                                TextContent.from("inspect this"),
                                PdfFileContent.from(PdfFile.builder()
                                        .url("file:///tmp/sample.pdf")
                                        .base64Data(BASE64_PDF)
                                        .mimeType(PdfFile.DEFAULT_MIME_TYPE)
                                        .build()))),
                null,
                null,
                BedrockGuardContentPlacement.ALL_USER_MESSAGES);

        List<ContentBlock> firstContent = messages.get(0).content();
        assertThat(firstContent.get(0).text()).isEqualTo("first");
        assertThat(firstContent.get(1).image()).isNotNull();

        List<ContentBlock> secondContent = messages.get(1).content();
        assertThat(secondContent).hasSize(2);
        assertThat(secondContent.get(0).text()).isEqualTo("inspect this");
        assertThat(secondContent.get(1).document()).isNotNull();

        assertThat(messages)
                .flatExtracting(Message::content)
                .allSatisfy(contentBlock -> assertThat(contentBlock.guardContent()).isNull());
    }

    @Test
    void should_fallback_to_regular_content_when_targeted_image_format_cannot_be_guarded() {
        List<Message> messages = extractor.testExtractRegularMessages(
                List.of(userMessage("look", gifImage())),
                null,
                null,
                BedrockGuardContentPlacement.LAST_USER_MESSAGE);

        List<ContentBlock> content = messages.get(0).content();
        assertThat(content).hasSize(2);
        assertThat(content.get(0).text()).isEqualTo("look");
        assertThat(content.get(0).guardContent()).isNull();
        assertThat(content.get(1).image().formatAsString()).isEqualTo("gif");
        assertThat(content.get(1).guardContent()).isNull();
    }

    @Test
    void should_not_fallback_when_unsupported_content_is_not_targeted_for_guard_content() {
        List<Message> messages = extractor.testExtractRegularMessages(
                List.of(
                        userMessage("historical", gifImage()),
                        userMessage("current", pngImage())),
                null,
                null,
                BedrockGuardContentPlacement.LAST_USER_MESSAGE);

        List<ContentBlock> historicalContent = messages.get(0).content();
        assertThat(historicalContent.get(0).text()).isEqualTo("historical");
        assertThat(historicalContent.get(1).image().formatAsString()).isEqualTo("gif");
        assertThat(historicalContent)
                .allSatisfy(contentBlock -> assertThat(contentBlock.guardContent()).isNull());

        List<ContentBlock> currentContent = messages.get(1).content();
        assertThat(currentContent.get(0).guardContent().text().text()).isEqualTo("current");
        assertThat(currentContent.get(1).guardContent().image().formatAsString()).isEqualTo("png");
    }

    @Test
    void should_keep_cache_point_after_guard_content_when_both_are_enabled() {
        List<Message> messages = extractor.testExtractRegularMessages(
                List.of(userMessage("cache and guard", pngImage())),
                BedrockCachePointPlacement.AFTER_LAST_USER_MESSAGE,
                CacheTTL.VALUE_5_M,
                BedrockGuardContentPlacement.LAST_USER_MESSAGE);

        List<ContentBlock> content = messages.get(0).content();
        assertThat(content).hasSize(3);
        assertThat(content.get(0).guardContent().text().text()).isEqualTo("cache and guard");
        assertThat(content.get(1).guardContent().image().formatAsString()).isEqualTo("png");
        assertThat(content.get(2).cachePoint()).isNotNull();
    }

    @Test
    void should_apply_guard_content_placement_when_building_sync_request() {
        AtomicReference<ConverseRequest> capturedRequest = new AtomicReference<>();
        BedrockChatModel model = BedrockChatModel.builder()
                .modelId("test-model")
                .client(new BedrockRuntimeClient() {

                    @Override
                    public ConverseResponse converse(ConverseRequest request) {
                        capturedRequest.set(request);
                        return minimalConverseResponse();
                    }

                    @Override
                    public String serviceName() {
                        return "bedrock-runtime";
                    }

                    @Override
                    public void close() {}
                })
                .build();

        model.doChat(chatRequestWithGuardContentPlacement(BedrockGuardContentPlacement.LAST_USER_MESSAGE));

        ConverseRequest request = capturedRequest.get();
        assertThat(request.guardrailConfig().guardrailIdentifier()).isEqualTo("guardrail-id");
        assertThat(request.guardrailConfig().guardrailVersion()).isEqualTo("1");

        List<Message> messages = request.messages();
        assertThat(messages).hasSize(2);
        assertThat(messages.get(0).content())
                .allSatisfy(contentBlock -> assertThat(contentBlock.guardContent()).isNull());

        List<ContentBlock> content = messages.get(1).content();
        assertThat(content).hasSize(2);
        assertThat(content.get(0).guardContent().text().text()).isEqualTo("guard me");
        assertThat(content.get(1).guardContent().image().formatAsString()).isEqualTo("png");
    }

    @Test
    void should_apply_guard_content_placement_when_building_streaming_request() {
        AtomicReference<ConverseStreamRequest> capturedRequest = new AtomicReference<>();
        BedrockStreamingChatModel model = BedrockStreamingChatModel.builder()
                .modelId("test-model")
                .client(new BedrockRuntimeAsyncClient() {

                    @Override
                    public CompletableFuture<Void> converseStream(
                            ConverseStreamRequest request, ConverseStreamResponseHandler responseHandler) {
                        capturedRequest.set(request);
                        return CompletableFuture.completedFuture(null);
                    }

                    @Override
                    public String serviceName() {
                        return "bedrock-runtime";
                    }

                    @Override
                    public void close() {}
                })
                .build();

        model.doChat(
                chatRequestWithGuardContentPlacement(BedrockGuardContentPlacement.LAST_USER_MESSAGE),
                new TestStreamingChatResponseHandler());

        ConverseStreamRequest request = capturedRequest.get();
        assertThat(request.guardrailConfig().guardrailIdentifier()).isEqualTo("guardrail-id");
        assertThat(request.guardrailConfig().guardrailVersion()).isEqualTo("1");

        List<Message> messages = request.messages();
        assertThat(messages).hasSize(2);
        assertThat(messages.get(0).content())
                .allSatisfy(contentBlock -> assertThat(contentBlock.guardContent()).isNull());

        List<ContentBlock> content = messages.get(1).content();
        assertThat(content).hasSize(2);
        assertThat(content.get(0).guardContent().text().text()).isEqualTo("guard me");
        assertThat(content.get(1).guardContent().image().formatAsString()).isEqualTo("png");
    }

    private static UserMessage userMessage(String text, ImageContent imageContent) {
        return new UserMessage(TextContent.from(text), imageContent);
    }

    private static ImageContent pngImage() {
        return imageContent("image/png");
    }

    private static ImageContent jpegImage() {
        return imageContent("image/jpeg");
    }

    private static ImageContent gifImage() {
        return imageContent("image/gif");
    }

    private static ImageContent imageContent(String mimeType) {
        return ImageContent.from(Image.builder()
                .base64Data(BASE64_IMAGE)
                .mimeType(mimeType)
                .build());
    }

    private static ChatRequest chatRequestWithGuardContentPlacement(BedrockGuardContentPlacement placement) {
        return ChatRequest.builder()
                // Keep one historical user message so LAST_USER_MESSAGE can prove only the last user message is guarded.
                .messages(
                        userMessage("history", jpegImage()),
                        userMessage("guard me", pngImage()))
                .parameters(BedrockChatRequestParameters.builder()
                        .modelName("test-model")
                        .guardrailConfiguration(BedrockGuardrailConfiguration.builder()
                                .guardrailIdentifier("guardrail-id")
                                .guardrailVersion("1")
                                .guardContentPlacement(placement)
                                .build())
                        .build())
                .build();
    }

    private static ConverseResponse minimalConverseResponse() {
        ConverseResponse.Builder builder = ConverseResponse.builder();
        builder.output(ConverseOutput.fromMessage(Message.builder()
                .role(ConversationRole.ASSISTANT)
                .content(ContentBlock.builder().text("ok").build())
                .build()));
        builder.stopReason(StopReason.END_TURN);
        builder.usage(TokenUsage.builder()
                .inputTokens(1)
                .outputTokens(1)
                .totalTokens(2)
                .build());
        builder.responseMetadata(DefaultAwsResponseMetadata.create(Map.of("AWS_REQUEST_ID", "request-id")));
        return builder.build();
    }

    private static class TestableExtractor extends AbstractBedrockChatModel {

        TestableExtractor() {
            super(new TestBuilder());
        }

        List<Message> testExtractRegularMessages(
                List<ChatMessage> messages,
                BedrockCachePointPlacement cachePointPlacement,
                CacheTTL cacheTtl,
                BedrockGuardContentPlacement guardContentPlacement) {
            return extractRegularMessages(messages, cachePointPlacement, cacheTtl, guardContentPlacement);
        }

        private static class TestBuilder extends AbstractBuilder<TestBuilder> {

            @Override
            public TestBuilder self() {
                return this;
            }
        }
    }
}
