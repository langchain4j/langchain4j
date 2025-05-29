package dev.langchain4j.data.message;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.json.JsonMapper;
import dev.langchain4j.Internal;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.audio.Audio;
import dev.langchain4j.data.image.Image;
import dev.langchain4j.data.pdf.PdfFile;
import dev.langchain4j.data.video.Video;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;

import static com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.ANY;
import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;
import static com.fasterxml.jackson.annotation.JsonTypeInfo.As.EXISTING_PROPERTY;
import static com.fasterxml.jackson.annotation.JsonTypeInfo.Id.NAME;
import static com.fasterxml.jackson.annotation.PropertyAccessor.FIELD;
import static java.util.Collections.emptyList;

@Internal
class JacksonChatMessageJsonCodec implements ChatMessageJsonCodec {

    private static final ObjectMapper OBJECT_MAPPER = JsonMapper.builder()
            .visibility(FIELD, ANY)
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
            .addMixIn(PdfFile.class, PdfFileMixin.class)
            .build();

    private static final Type MESSAGE_LIST_TYPE = new TypeReference<List<ChatMessage>>() {
    }.getType();

    @Override
    public ChatMessage messageFromJson(String json) {
        try {
            return OBJECT_MAPPER.readValue(json, ChatMessage.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public List<ChatMessage> messagesFromJson(String json) {
        if (json == null) {
            return List.of();
        }
        try {
            List<ChatMessage> messages = OBJECT_MAPPER.readValue(json, OBJECT_MAPPER.constructType(MESSAGE_LIST_TYPE));
            return messages == null ? emptyList() : messages;
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String messageToJson(ChatMessage message) {
        try {
            return OBJECT_MAPPER.writeValueAsString(message);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String messagesToJson(List<ChatMessage> messages) {
        try {
            return OBJECT_MAPPER.writeValueAsString(messages);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
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
    private static abstract class ChatMessageMixin {

        @JsonProperty
        public abstract ChatMessageType type();
    }

    @JsonInclude(NON_NULL)
    private static abstract class SystemMessageMixin {

        @JsonCreator
        public SystemMessageMixin(@JsonProperty("text") String text) {
        }
    }

    @JsonInclude(NON_NULL)
    @JsonDeserialize(builder = UserMessage.Builder.class)
    private static abstract class UserMessageMixin {
    }

    @JsonInclude(NON_NULL)
    @JsonDeserialize(builder = AiMessage.Builder.class)
    private static abstract class AiMessageMixin {
    }

    @JsonInclude(NON_NULL)
    @JsonDeserialize(builder = ToolExecutionRequest.Builder.class)
    private static abstract class ToolExecutionRequestMixin {
    }

    @JsonInclude(NON_NULL)
    private static class ToolExecutionResultMessageMixin {

        @JsonCreator
        public ToolExecutionResultMessageMixin(@JsonProperty("id") String id,
                                               @JsonProperty("toolName") String toolName,
                                               @JsonProperty("text") String text) {
        }
    }

    @JsonInclude(NON_NULL)
    private static class CustomMessageMixin {

        @JsonCreator
        public CustomMessageMixin(@JsonProperty("attributes") Map<String, Object> attributes) {
        }
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
    private static abstract class ContentMixin {

        @JsonProperty
        public abstract ContentType type();
    }

    @JsonInclude(NON_NULL)
    private static abstract class TextContentMixin {

        @JsonCreator
        public TextContentMixin(@JsonProperty("text") String text) {
        }
    }

    @JsonInclude(NON_NULL)
    private static abstract class ImageContentMixin {

        @JsonCreator
        public ImageContentMixin(@JsonProperty("image") Image image,
                                 @JsonProperty("detailLevel") ImageContent.DetailLevel detailLevel) {
        }
    }

    @JsonInclude(NON_NULL)
    @JsonDeserialize(builder = Image.Builder.class)
    private static abstract class ImageMixin {

    }

    @JsonInclude(NON_NULL)
    private static abstract class AudioContentMixin {

        @JsonCreator
        public AudioContentMixin(@JsonProperty("audio") Audio audio) {
        }
    }

    @JsonInclude(NON_NULL)
    @JsonDeserialize(builder = Audio.Builder.class)
    private static abstract class AudioMixin {

    }

    @JsonInclude(NON_NULL)
    private static abstract class VideoContentMixin {

        @JsonCreator
        public VideoContentMixin(@JsonProperty("video") Video video) {
        }
    }

    @JsonInclude(NON_NULL)
    @JsonDeserialize(builder = Video.Builder.class)
    private static abstract class VideoMixin {

    }

    @JsonInclude(NON_NULL)
    private static abstract class PdfFileContentMixin {

        @JsonCreator
        public PdfFileContentMixin(@JsonProperty("pdfFile") PdfFile pdfFile) {
        }
    }

    @JsonInclude(NON_NULL)
    @JsonDeserialize(builder = PdfFile.Builder.class)
    private static abstract class PdfFileMixin {

    }
}
