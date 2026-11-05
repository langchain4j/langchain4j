package dev.langchain4j.model.azure;

import static org.assertj.core.api.Assertions.assertThat;

import com.azure.ai.openai.models.ChatRequestMessage;
import com.azure.ai.openai.models.ChatRequestUserMessage;
import com.azure.json.JsonProviders;
import com.azure.json.JsonWriter;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.ImageContent.DetailLevel;
import dev.langchain4j.data.message.UserMessage;
import java.io.StringWriter;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Integration test for <a href="https://github.com/langchain4j/langchain4j/issues/5508">issue #5508</a>:
 * verifies that the {@link ImageContent#detailLevel()} a user sets actually appears in the wire-format JSON
 * the Azure SDK would send over the network.
 *
 * <p>This does not require live Azure credentials: it goes through the SDK's own JSON serialization
 * path ({@link ChatRequestUserMessage#toJson(JsonWriter)}) which is the same code that writes the
 * outbound request body. If the helper silently drops the detail, the wire-format JSON will not
 * contain a {@code "detail"} field — which is exactly what we're guarding against.</p>
 */
class AzureOpenAiImageDetailLevelJsonIT {

    @ParameterizedTest(name = "detail={0} should serialize to \"detail\":\"{1}\"")
    @MethodSource("supportedDetailLevels")
    void shouldSerializeDetailLevelToWireFormat(DetailLevel level, String expectedJsonValue) throws Exception {
        // Given - a user message with an image whose detail level is explicitly set
        ImageContent imageContent = ImageContent.from("https://example.com/cat.jpg", level);
        UserMessage userMessage = UserMessage.from("What is in this image?", imageContent);

        // When - run the helper that converts to the Azure SDK request message, then serialize
        // it via the SDK's own toJson(...) — the same code path the SDK uses to build the body.
        ChatRequestMessage azureRequest = InternalAzureOpenAiHelper.toOpenAiMessage(userMessage);
        String wireJson = toJson((ChatRequestUserMessage) azureRequest);

        // Then - the wire-format JSON contains the detail field with the lowercase value Azure expects
        assertThat(wireJson)
                .as("Wire-format JSON should contain the user-specified detail level")
                .contains("\"detail\":\"" + expectedJsonValue + "\"");
    }

    @Test
    void shouldNotEmitDetailForUnsupportedMediumAndUltraHigh() throws Exception {
        // MEDIUM and ULTRA_HIGH are part of the langchain4j API but have no Azure SDK equivalent.
        // Per issue #5508 the helper must skip setDetail so Azure applies its server default
        // rather than sending a bogus value.
        for (DetailLevel unsupported : new DetailLevel[] {DetailLevel.MEDIUM, DetailLevel.ULTRA_HIGH}) {
            ImageContent imageContent = ImageContent.from("https://example.com/cat.jpg", unsupported);
            UserMessage userMessage = UserMessage.from("describe", imageContent);

            ChatRequestMessage azureRequest = InternalAzureOpenAiHelper.toOpenAiMessage(userMessage);
            String wireJson = toJson((ChatRequestUserMessage) azureRequest);

            assertThat(wireJson)
                    .as("detail field must be absent for unsupported DetailLevel " + unsupported)
                    .doesNotContain("\"detail\"");
        }
    }

    @Test
    void wireFormatShouldPreserveDetailAlongsideUrl() throws Exception {
        // End-to-end sanity check: the same JSON must contain both the image URL and the detail.
        // Catches a class of bugs where setDetail wipes or replaces the URL set in the constructor.
        ImageContent imageContent = ImageContent.from("https://example.com/cat.jpg", DetailLevel.HIGH);
        UserMessage userMessage = UserMessage.from("describe", imageContent);

        ChatRequestMessage azureRequest = InternalAzureOpenAiHelper.toOpenAiMessage(userMessage);
        String wireJson = toJson((ChatRequestUserMessage) azureRequest);

        assertThat(wireJson)
                .contains("https://example.com/cat.jpg")
                .contains("\"detail\":\"high\"");
    }

    private static Stream<Arguments> supportedDetailLevels() {
        return Stream.of(
                Arguments.of(DetailLevel.LOW, "low"),
                Arguments.of(DetailLevel.HIGH, "high"),
                Arguments.of(DetailLevel.AUTO, "auto"));
    }

    private static String toJson(ChatRequestUserMessage message) throws Exception {
        StringWriter stringWriter = new StringWriter();
        try (JsonWriter jsonWriter = JsonProviders.createWriter(stringWriter)) {
            message.toJson(jsonWriter);
        }
        return stringWriter.toString();
    }
}