package dev.langchain4j.data.message;

import static java.util.Collections.emptyList;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.List;

public class GsonChatMessageJsonCodec implements ChatMessageJsonCodec {

    static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(ChatMessage.class, new GsonChatMessageAdapter())
            .registerTypeAdapter(SystemMessage.class, new GsonChatMessageAdapter())
            .registerTypeAdapter(UserMessage.class, new GsonChatMessageAdapter())
            .registerTypeAdapter(AiMessage.class, new GsonChatMessageAdapter())
            .registerTypeAdapter(ToolExecutionResultMessage.class, new GsonChatMessageAdapter())
            .create();
    private static final Type MESSAGE_LIST_TYPE = (new TypeToken<List<ChatMessage>>() {}).getType();

    @Override
    public ChatMessage messageFromJson(String json) {
        return GSON.fromJson(json, ChatMessage.class);
    }

    @Override
    public List<ChatMessage> messagesFromJson(String json) {
        List<ChatMessage> messages = GSON.fromJson(json, MESSAGE_LIST_TYPE);
        return messages == null ? emptyList() : messages;
    }

    @Override
    public String messageToJson(ChatMessage message) {
        return GSON.toJson(message);
    }

    @Override
    public String messagesToJson(List<ChatMessage> messages) {
        return GSON.toJson(messages);
    }
}
