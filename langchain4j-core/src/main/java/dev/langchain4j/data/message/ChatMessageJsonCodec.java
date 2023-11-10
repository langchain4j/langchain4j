package dev.langchain4j.data.message;

import java.util.List;

public interface ChatMessageJsonCodec {

    ChatMessage messageFromJson(String json);

    List<ChatMessage> messagesFromJson(String json);

    String messageToJson(ChatMessage message);

    String messagesToJson(List<ChatMessage> messages);
}
