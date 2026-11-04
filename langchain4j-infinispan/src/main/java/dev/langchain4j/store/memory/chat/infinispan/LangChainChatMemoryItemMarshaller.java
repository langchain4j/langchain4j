package dev.langchain4j.store.memory.chat.infinispan;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.infinispan.protostream.MessageMarshaller;

/**
 * ProtoStream marshaller for {@link LangChainChatMemoryItem}
 */
public class LangChainChatMemoryItemMarshaller implements MessageMarshaller<LangChainChatMemoryItem> {

    private final String typeName;

    public LangChainChatMemoryItemMarshaller(String typeName) {
        this.typeName = typeName;
    }

    @Override
    public LangChainChatMemoryItem readFrom(ProtoStreamReader reader) throws IOException {
        String memoryId = reader.readString("memoryId");
        List<LangChainChatMessage> messages =
                reader.readCollection("messages", new ArrayList<>(), LangChainChatMessage.class);
        return new LangChainChatMemoryItem(memoryId, messages);
    }

    @Override
    public void writeTo(ProtoStreamWriter writer, LangChainChatMemoryItem item) throws IOException {
        writer.writeString("memoryId", item.memoryId());
        writer.writeCollection("messages", item.messages(), LangChainChatMessage.class);
    }

    @Override
    public Class<? extends LangChainChatMemoryItem> getJavaClass() {
        return LangChainChatMemoryItem.class;
    }

    @Override
    public String getTypeName() {
        return typeName;
    }
}
