package dev.langchain4j.store.memory.chat.infinispan;

import dev.langchain4j.data.message.ChatMessageType;
import org.infinispan.protostream.EnumMarshaller;

/**
 * ProtoStream enum marshaller for {@link ChatMessageType}
 */
public class ChatMessageTypeMarshaller implements EnumMarshaller<ChatMessageType> {

    private final String typeName;

    public ChatMessageTypeMarshaller(String typeName) {
        this.typeName = typeName;
    }

    @Override
    public ChatMessageType decode(int enumValue) {
        return ChatMessageType.values()[enumValue];
    }

    @Override
    public int encode(ChatMessageType chatMessageType) {
        return chatMessageType.ordinal();
    }

    @Override
    public Class<? extends ChatMessageType> getJavaClass() {
        return ChatMessageType.class;
    }

    @Override
    public String getTypeName() {
        return typeName;
    }
}
