package dev.langchain4j.store.memory.chat;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import dev.langchain4j.Internal;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageType;
import dev.langchain4j.data.message.CustomMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;

import static com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.ANY;
import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;
import static com.fasterxml.jackson.annotation.JsonTypeInfo.As.EXISTING_PROPERTY;
import static com.fasterxml.jackson.annotation.JsonTypeInfo.Id.NAME;
import static com.fasterxml.jackson.annotation.PropertyAccessor.FIELD;

@Internal
class JacksonInMemoryChatMemoryStoreJsonCodec implements InMemoryChatMemoryStoreJsonCodec {

    private static final ObjectMapper OBJECT_MAPPER = JsonMapper.builder()
            .visibility(FIELD, ANY)
            .addMixIn(ChatMessage.class, ChatMessageMixin.class)
            .build();

    @Override
    public InMemoryChatMemoryStore fromJson(final String json) {
        try {
            return OBJECT_MAPPER.readValue(json, InMemoryChatMemoryStore.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String toJson(final InMemoryChatMemoryStore store) {
        try {
            return OBJECT_MAPPER.writeValueAsString(store);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    // FIXME duplicated with JacksonChatMessageJsonCodec.ChatMessageMixin
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
}
