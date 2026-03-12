package dev.langchain4j.model.ollama;

import static dev.langchain4j.model.chat.Capability.RESPONSE_FORMAT_JSON_SCHEMA;
import static dev.langchain4j.model.chat.request.ResponseFormat.JSON;
import static dev.langchain4j.model.ollama.OllamaImage.TINY_DOLPHIN_MODEL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

import dev.langchain4j.data.message.AudioContent;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.ContentType;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.message.VideoContent;
import dev.langchain4j.exception.UnsupportedFeatureException;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import dev.langchain4j.model.output.FinishReason;

import java.time.Duration;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class OllamaChatModelIT extends AbstractOllamaLanguageModelInfrastructure {

    static final String MODEL_NAME = TINY_DOLPHIN_MODEL;

    @Test
    void should_respect_numPredict() {

        // given
        int numPredict = 1; // max output tokens

        OllamaChatModel model = OllamaChatModel.builder()
                .baseUrl(ollamaBaseUrl(ollama))
                .modelName(MODEL_NAME)
                .numPredict(numPredict)
                .temperature(0.0)
                .logRequests(true)
                .logResponses(true)
                .build();

        UserMessage userMessage = UserMessage.from("What is the capital of Germany?");

        // when
        ChatResponse response = model.chat(userMessage);

        // then
        assertThat(response.aiMessage().text()).doesNotContain("Berlin");

        ChatResponseMetadata metadata = response.metadata();
        assertThat(metadata.modelName()).isEqualTo(MODEL_NAME);
        assertThat(metadata.finishReason()).isEqualTo(FinishReason.LENGTH);
        assertThat(metadata.tokenUsage().outputTokenCount()).isBetween(numPredict, numPredict + 2); // bug in Ollama
    }

    @Test
    void should_generate_valid_json() {

        // given
        ChatModel model = OllamaChatModel.builder()
                .baseUrl(ollamaBaseUrl(ollama))
                .modelName(MODEL_NAME)
                .responseFormat(JSON)
                .temperature(0.0)
                .logRequests(true)
                .logResponses(true)
                .build();

        String userMessage = "Return JSON with two fields: name and age of John Doe, 42 years old.";

        // when
        ChatResponse response = model.chat(UserMessage.from(userMessage));

        // then
        String json = response.aiMessage().text();
        assertThat(json).isEqualToIgnoringWhitespace("{\"name\": \"John Doe\", \"age\": 42}");
        ChatResponseMetadata metadata = response.metadata();
        assertThat(metadata.modelName()).isEqualTo(MODEL_NAME);
        assertThat(metadata.finishReason()).isEqualTo(FinishReason.STOP);
    }

    @Test
    void should_return_set_capabilities() {
        ChatModel model = OllamaChatModel.builder()
                .baseUrl(ollamaBaseUrl(ollama))
                .modelName(MODEL_NAME)
                .supportedCapabilities(RESPONSE_FORMAT_JSON_SCHEMA)
                .build();

        assertThat(model.supportedCapabilities()).contains(RESPONSE_FORMAT_JSON_SCHEMA);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 10, 100})
    void should_handle_timeout(int millis) {

        // given
        Duration timeout = Duration.ofMillis(millis);

        ChatModel model = OllamaChatModel.builder()
                .baseUrl(ollamaBaseUrl(ollama))
                .modelName(MODEL_NAME)
                .maxRetries(0)
                .timeout(timeout)
                .build();

        // when-then
        assertThatThrownBy(() -> model.chat("hi"))
                .isExactlyInstanceOf(dev.langchain4j.exception.TimeoutException.class);
    }

    @ParameterizedTest
    @MethodSource("notSupportedContentTypesProvider")
    void should_throw_when_not_supported_content_types_used(List<ContentType> contentTypes) {

        // given
        OllamaChatModel model = OllamaChatModel.builder()
                .baseUrl(ollamaBaseUrl(ollama))
                .modelName(MODEL_NAME)
                .maxRetries(0)
                .logRequests(true)
                .logResponses(true)
                .build();
        final UserMessage userMessage = createUserMessageBasedOnContentTypes(contentTypes);

        // when-then
        assertThrows(UnsupportedFeatureException.class, () -> model.chat(userMessage));
    }

    @ParameterizedTest
    @MethodSource("supportedContentTypesProvider")
    void should_not_throw_when_supported_content_types_used(List<ContentType> contentTypes) {

        // given

        OllamaChatModel model = OllamaChatModel.builder()
                .baseUrl(ollamaBaseUrl(ollama))
                .modelName(MODEL_NAME)
                .maxRetries(0)
                .timeout(Duration.ofMillis(1))
                .logRequests(true)
                .logResponses(true)
                .build();
        final UserMessage userMessage = createUserMessageBasedOnContentTypes(contentTypes);

        // when-then
        // check that chat() times out, ergo, did not throw the UnsupportedFeatureException
        assertThrows(dev.langchain4j.exception.TimeoutException.class, () -> model.chat(userMessage));
    }

    static UserMessage createUserMessageBasedOnContentTypes(List<ContentType> contentTypes) {
        return UserMessage.from(contentTypes.stream()
                .map(OllamaChatModelIT::createContentBasedOnType)
                .toList());
    }

    static Content createContentBasedOnType(ContentType contentType) {
        return switch (contentType) {
            case AUDIO -> AudioContent.from("VGhpcyBpcyBhIHRlc3QgY29udGVudHM=", "audio/mpeg");
            case IMAGE -> ImageContent.from("VGhpcyBpcyBhIHRlc3QgY29udGVudHM=", "image/jpeg");
            case PDF -> VideoContent.from("VGhpcyBpcyBhIHRlc3QgY29udGVudHM=", "application/pdf");
            case TEXT -> TextContent.from("Test text content.");
            case VIDEO -> VideoContent.from("VGhpcyBpcyBhIHRlc3QgY29udGVudHM=", "video/mp4");
            default -> {
                // keep defaults case to alert when ContentType gets extended
                fail("Cannot create user message from content type: " + contentType);
                yield TextContent.from("Text text content.");
            }
        };
    }

    static Stream<List<ContentType>> notSupportedContentTypesProvider() {
        return Stream.of(
                List.of(ContentType.AUDIO),
                List.of(ContentType.PDF),
                List.of(ContentType.VIDEO),
                List.of(ContentType.TEXT, ContentType.AUDIO),
                List.of(ContentType.TEXT, ContentType.PDF),
                List.of(ContentType.TEXT, ContentType.VIDEO),
                List.of(ContentType.IMAGE, ContentType.AUDIO),
                List.of(ContentType.IMAGE, ContentType.PDF),
                List.of(ContentType.IMAGE, ContentType.VIDEO),
                List.of(ContentType.TEXT, ContentType.AUDIO, ContentType.IMAGE),
                List.of(ContentType.TEXT, ContentType.PDF, ContentType.IMAGE),
                List.of(ContentType.TEXT, ContentType.VIDEO, ContentType.IMAGE),
                List.of(ContentType.AUDIO, ContentType.IMAGE, ContentType.PDF, ContentType.TEXT, ContentType.VIDEO)
        );
    }

    static Stream<List<ContentType>> supportedContentTypesProvider() {
        // note, only 1 of TEXT is allowed, zero or more of IMAGE
        return Stream.of(
                List.of(ContentType.TEXT),
                List.of(ContentType.IMAGE, ContentType.TEXT),
                List.of(ContentType.TEXT, ContentType.IMAGE),
                List.of(ContentType.IMAGE, ContentType.IMAGE, ContentType.TEXT),
                List.of(ContentType.IMAGE, ContentType.TEXT, ContentType.IMAGE)
        );
    }
}
