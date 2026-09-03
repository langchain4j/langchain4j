package dev.langchain4j.jackson3;

import static com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.ANY;
import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_EMPTY;
import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;
import static com.fasterxml.jackson.annotation.JsonTypeInfo.As.EXISTING_PROPERTY;
import static com.fasterxml.jackson.annotation.JsonTypeInfo.Id.NAME;
import static com.fasterxml.jackson.annotation.PropertyAccessor.FIELD;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import dev.langchain4j.exception.JsonWriteException;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.audio.Audio;
import dev.langchain4j.data.image.Image;
import dev.langchain4j.data.message.*;
import dev.langchain4j.data.pdf.PdfFile;
import dev.langchain4j.data.video.Video;
import java.util.List;
import java.util.Map;
import tools.jackson.core.type.TypeReference;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.json.JsonMapper;

/**
 * Jackson 3 twin of {@code JacksonChatMessageJsonCodec}.
 *
 * <p>Every annotation here except {@code @JsonDeserialize} comes from the shared
 * {@code jackson-annotations} artifact and is identical to the Jackson 2 version.
 * Only {@code @JsonDeserialize} lives in the databind package and therefore had to move.
 */
public class Jackson3ChatMessageJsonCodec implements ChatMessageJsonCodec {

    public static JsonMapper.Builder chatMessageJsonMapperBuilder() {
        return Jackson3Defaults.pinJackson2Defaults(JsonMapper.builder())
                .changeDefaultVisibility(vc -> vc.withVisibility(FIELD, ANY))
                .addMixIn(ChatMessage.class, ChatMessageMixin.class)
                .addMixIn(SystemMessage.class, SystemMessageMixin.class)
                .addMixIn(UserMessage.class, UserMessageMixin.class)
                .addMixIn(AiMessage.class, AiMessageMixin.class)
                .addMixIn(ToolExecutionRequest.class, ToolExecutionRequestMixin.class)
                .addMixIn(ToolExecutionResultMessage.class, ToolExecutionResultMessageMixin.class)
                .addMixIn(CustomMessage.class, CustomMessageMixin.class)
                .addMixIn(Content.class, ContentMixin.class)
                .addMixIn(TextContent.class, TextContentMixin.class)
                .addMixIn(ImageContent.class, ImageContentMixin.class)
                .addMixIn(Image.class, ImageMixin.class)
                .addMixIn(AudioContent.class, AudioContentMixin.class)
                .addMixIn(Audio.class, AudioMixin.class)
                .addMixIn(VideoContent.class, VideoContentMixin.class)
                .addMixIn(Video.class, VideoMixin.class)
                .addMixIn(PdfFileContent.class, PdfFileContentMixin.class)
                .addMixIn(PdfFile.class, PdfFileMixin.class);
    }

    private static final ObjectMapper OBJECT_MAPPER = chatMessageJsonMapperBuilder().build();

    private static final TypeReference<List<ChatMessage>> MESSAGE_LIST_TYPE = new TypeReference<>() {};

    @Override
    public ChatMessage messageFromJson(String json) {
        return OBJECT_MAPPER.readValue(json, ChatMessage.class);
    }

    @Override
    public List<ChatMessage> messagesFromJson(String json) {
        return OBJECT_MAPPER.readValue(json, MESSAGE_LIST_TYPE);
    }

    @Override
    public String messageToJson(ChatMessage message) {
        try {
            return OBJECT_MAPPER.writeValueAsString(message);
        } catch (JacksonException e) {
            throw new JsonWriteException(e);
        }
    }

    @Override
    public String messagesToJson(List<ChatMessage> messages) {
        try {
            return OBJECT_MAPPER.writeValueAsString(messages);
        } catch (JacksonException e) {
            throw new JsonWriteException(e);
        }
    }

    @JsonInclude(NON_NULL)
    @JsonTypeInfo(use = NAME, include = EXISTING_PROPERTY, property = "type")
    @JsonSubTypes({
        @JsonSubTypes.Type(value = SystemMessage.class, name = "SYSTEM"),
        @JsonSubTypes.Type(value = UserMessage.class, name = "USER"),
        @JsonSubTypes.Type(value = AiMessage.class, name = "AI"),
        @JsonSubTypes.Type(value = ToolExecutionResultMessage.class, name = "TOOL_EXECUTION_RESULT"),
        @JsonSubTypes.Type(value = CustomMessage.class, name = "CUSTOM"),
    })
    private abstract static class ChatMessageMixin {
        @JsonProperty
        public abstract ChatMessageType type();
    }

    @JsonInclude(NON_NULL)
    private abstract static class SystemMessageMixin {
        @JsonCreator
        public SystemMessageMixin(@JsonProperty("text") String text) {}
    }

    @JsonInclude(NON_EMPTY)
    @JsonDeserialize(builder = UserMessage.Builder.class)
    private abstract static class UserMessageMixin {}

    @JsonInclude(NON_NULL)
    @JsonDeserialize(builder = AiMessage.Builder.class)
    private abstract static class AiMessageMixin {}

    @JsonInclude(NON_NULL)
    @JsonDeserialize(builder = ToolExecutionRequest.Builder.class)
    private abstract static class ToolExecutionRequestMixin {}

    @JsonInclude(NON_NULL)
    @JsonDeserialize(builder = ToolExecutionResultMessage.Builder.class)
    private abstract static class ToolExecutionResultMessageMixin {
        @JsonProperty("isError")
        abstract Boolean isError();
    }

    @JsonInclude(NON_NULL)
    private static class CustomMessageMixin {
        @JsonCreator
        public CustomMessageMixin(@JsonProperty("attributes") Map<String, Object> attributes) {}
    }

    @JsonInclude(NON_NULL)
    @JsonTypeInfo(use = NAME, include = EXISTING_PROPERTY, property = "type")
    @JsonSubTypes({
        @JsonSubTypes.Type(value = TextContent.class, name = "TEXT"),
        @JsonSubTypes.Type(value = ImageContent.class, name = "IMAGE"),
        @JsonSubTypes.Type(value = AudioContent.class, name = "AUDIO"),
        @JsonSubTypes.Type(value = VideoContent.class, name = "VIDEO"),
        @JsonSubTypes.Type(value = PdfFileContent.class, name = "PDF"),
    })
    private abstract static class ContentMixin {
        @JsonProperty
        public abstract ContentType type();
    }

    @JsonInclude(NON_NULL)
    private abstract static class TextContentMixin {
        @JsonCreator
        public TextContentMixin(@JsonProperty("text") String text) {}
    }

    @JsonInclude(NON_NULL)
    private abstract static class ImageContentMixin {
        @JsonCreator
        public ImageContentMixin(
                @JsonProperty("image") Image image,
                @JsonProperty("detailLevel") ImageContent.DetailLevel detailLevel) {}
    }

    @JsonInclude(NON_NULL)
    @JsonDeserialize(builder = Image.Builder.class)
    private abstract static class ImageMixin {}

    @JsonInclude(NON_NULL)
    private abstract static class AudioContentMixin {
        @JsonCreator
        public AudioContentMixin(@JsonProperty("audio") Audio audio) {}
    }

    @JsonInclude(NON_NULL)
    @JsonDeserialize(builder = Audio.Builder.class)
    private abstract static class AudioMixin {}

    @JsonInclude(NON_NULL)
    private abstract static class VideoContentMixin {
        @JsonCreator
        public VideoContentMixin(@JsonProperty("video") Video video) {}
    }

    @JsonInclude(NON_NULL)
    @JsonDeserialize(builder = Video.Builder.class)
    private abstract static class VideoMixin {}

    @JsonInclude(NON_NULL)
    private abstract static class PdfFileContentMixin {
        @JsonCreator
        public PdfFileContentMixin(@JsonProperty("pdfFile") PdfFile pdfFile) {}
    }

    @JsonInclude(NON_NULL)
    @JsonDeserialize(builder = PdfFile.Builder.class)
    private abstract static class PdfFileMixin {}
}
