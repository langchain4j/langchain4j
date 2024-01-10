package dev.langchain4j.data.message;

import com.google.gson.*;

import java.lang.reflect.Type;

class GsonChatMessageAdapter implements JsonDeserializer<ChatMessage>, JsonSerializer<ChatMessage> {

    private static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(Content.class, new GsonContentAdapter())
            .registerTypeAdapter(TextContent.class, new GsonContentAdapter())
            .registerTypeAdapter(ImageContent.class, new GsonContentAdapter())
            .create();

    private static final String CHAT_MESSAGE_TYPE = "type"; // do not change, will break backward compatibility!

    @Override
    public JsonElement serialize(ChatMessage chatMessage, Type ignored, JsonSerializationContext context) {
        JsonObject messageJsonObject = GSON.toJsonTree(chatMessage).getAsJsonObject();
        messageJsonObject.addProperty(CHAT_MESSAGE_TYPE, chatMessage.type().toString());
        return messageJsonObject;
    }

    @Override
    public ChatMessage deserialize(JsonElement messageJsonElement, Type ignored, JsonDeserializationContext context) throws JsonParseException {
        String chatMessageTypeString = messageJsonElement.getAsJsonObject().get(CHAT_MESSAGE_TYPE).getAsString();
        ChatMessageType chatMessageType = ChatMessageType.valueOf(chatMessageTypeString);
        ChatMessage chatMessage = GSON.fromJson(messageJsonElement, chatMessageType.messageClass());
        if (chatMessage instanceof UserMessage && ((UserMessage) chatMessage).contents() == null) {
            // keeping backward compatibility with old schema TODO remove after a few releases
            chatMessage = UserMessage.from(messageJsonElement.getAsJsonObject().get("text").getAsString());
        }
        return chatMessage;
    }
}