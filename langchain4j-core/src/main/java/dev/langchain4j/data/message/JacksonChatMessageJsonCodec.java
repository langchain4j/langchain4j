package dev.langchain4j.data.message;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.exc.ValueInstantiationException;
import com.fasterxml.jackson.databind.module.SimpleModule;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.audio.Audio;
import dev.langchain4j.data.image.Image;
import dev.langchain4j.data.pdf.PdfFile;
import dev.langchain4j.data.text.TextFile;
import dev.langchain4j.data.video.Video;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.ANY;
import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;
import static com.fasterxml.jackson.annotation.JsonTypeInfo.As.EXISTING_PROPERTY;
import static com.fasterxml.jackson.annotation.JsonTypeInfo.Id.NAME;
import static com.fasterxml.jackson.annotation.PropertyAccessor.FIELD;
import static java.util.Collections.emptyList;

class JacksonChatMessageJsonCodec implements ChatMessageJsonCodec { // TODO move into main, ensure no split package

    private static final ObjectMapper MESSAGE_MAPPER = new ObjectMapper()
            .setVisibility(FIELD, ANY);

    static {

        MESSAGE_MAPPER.addMixIn(ChatMessage.class, ChatMessageMixin.class);
        MESSAGE_MAPPER.addMixIn(SystemMessage.class, SystemMessageMixin.class);
        SimpleModule userMessageModule = new SimpleModule();
        userMessageModule.addDeserializer(UserMessage.class, new UserMessageDeserializer());
        MESSAGE_MAPPER.registerModule(userMessageModule);
        MESSAGE_MAPPER.addMixIn(AiMessage.class, AiMessageMixin.class);
        MESSAGE_MAPPER.addMixIn(ToolExecutionRequest.class, ToolExecutionRequestMixin.class);
        MESSAGE_MAPPER.addMixIn(ToolExecutionResultMessage.class, ToolExecutionResultMessageMixin.class);
        MESSAGE_MAPPER.addMixIn(CustomMessage.class, CustomMessageMixin.class);

        MESSAGE_MAPPER.addMixIn(Content.class, ContentMixin.class);
        MESSAGE_MAPPER.addMixIn(TextContent.class, TextContentMixin.class);
        MESSAGE_MAPPER.addMixIn(ImageContent.class, ImageContentMixin.class);
        MESSAGE_MAPPER.addMixIn(Image.class, ImageMixin.class);
        MESSAGE_MAPPER.addMixIn(AudioContent.class, AudioContentMixin.class);
        MESSAGE_MAPPER.addMixIn(Audio.class, AudioMixin.class);
        MESSAGE_MAPPER.addMixIn(VideoContent.class, VideoContentMixin.class);
        MESSAGE_MAPPER.addMixIn(Video.class, VideoMixin.class);
        MESSAGE_MAPPER.addMixIn(TextFileContent.class, TextFileContentMixin.class);
        MESSAGE_MAPPER.addMixIn(TextFile.class, TextFileMixin.class);
        MESSAGE_MAPPER.addMixIn(PdfFileContent.class, PdfFileContentMixin.class);
        MESSAGE_MAPPER.addMixIn(PdfFile.class, PdfFileMixin.class);
    }

    private static final Type MESSAGE_LIST_TYPE = new TypeReference<List<ChatMessage>>() {
    }.getType();

    @Override
    public ChatMessage messageFromJson(String json) {
        try {
            return MESSAGE_MAPPER.readValue(json, ChatMessage.class);
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
            List<ChatMessage> messages = MESSAGE_MAPPER.readValue(json, MESSAGE_MAPPER.constructType(MESSAGE_LIST_TYPE));
            return messages == null ? emptyList() : messages;
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String messageToJson(ChatMessage message) {
        try {
            return MESSAGE_MAPPER.writeValueAsString(message);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String messagesToJson(List<ChatMessage> messages) {
        try {
            return MESSAGE_MAPPER.writeValueAsString(messages);
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

    private static class UserMessageDeserializer extends StdDeserializer<UserMessage> { // TODO

        public UserMessageDeserializer() {
            super(UserMessage.class);
        }

        @Override
        public UserMessage deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {

            String text = null;
            String name = null;
            List<Content> contents = null;
            while (p.nextToken() != JsonToken.END_OBJECT) {
                String key = p.getCurrentName();
                switch (key) {
                    case "text":
                        text = p.getText();
                        break;
                    case "name":
                        name = p.getText();
                        break;
                    case "contents":
                        if (p.currentToken() == JsonToken.FIELD_NAME) {
                            p.nextToken();
                        }
                        if (p.currentToken() != JsonToken.START_ARRAY) {
                            throw ValueInstantiationException.from(p,
                                    "Cannot construct instance of `dev.langchain4j.data.message.UserMessage`, problem: expected `"
                                            + p.currentToken() + "` to be start of array",
                                    ctxt.constructType(UserMessage.class));
                        }
                        contents = new ArrayList<>();
                        while (p.nextToken() != JsonToken.END_ARRAY) {
                            contents.add(ctxt.readValue(p, Content.class));
                        }
                        break;
                    default:
                        // ignore unknown properties
                }
            }

            if (text != null) {
                if (name == null) {
                    return new UserMessage(text);
                } else {
                    return new UserMessage(name, text);
                }
            } else if (contents != null) {
                if (name == null) {
                    return new UserMessage(contents);
                } else {
                    return new UserMessage(name, contents);
                }
            } else {
                throw ValueInstantiationException.from(p,
                        "Cannot construct instance of `dev.langchain4j.data.message.UserMessage`, problem: No `text` or `contents` field present",
                        ctxt.constructType(
                                UserMessage.class));
            }
        }
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
            @JsonSubTypes.Type(value = TextFileContent.class, name = "TEXT_FILE"),
            @JsonSubTypes.Type(value = PdfFileContent.class, name = "PDF"), // TODO notify Georgios about updates
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
    private static abstract class TextFileContentMixin {

        @JsonCreator
        public TextFileContentMixin(@JsonProperty("textFile") TextFile textFile) {
        }
    }

    @JsonInclude(NON_NULL)
    @JsonDeserialize(builder = TextFile.Builder.class)
    private static abstract class TextFileMixin {

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
