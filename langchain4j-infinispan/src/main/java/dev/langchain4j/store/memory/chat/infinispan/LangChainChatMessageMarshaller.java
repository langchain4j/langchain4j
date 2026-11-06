package dev.langchain4j.store.memory.chat.infinispan;

import dev.langchain4j.data.message.ChatMessageType;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.infinispan.protostream.MessageMarshaller;

/**
 * ProtoStream marshaller for {@link LangChainChatMessage}
 */
public class LangChainChatMessageMarshaller implements MessageMarshaller<LangChainChatMessage> {

    private final String typeName;

    public LangChainChatMessageMarshaller(String typeName) {
        this.typeName = typeName;
    }

    @Override
    public LangChainChatMessage readFrom(ProtoStreamReader reader) throws IOException {
        ChatMessageType type = reader.readEnum("type", ChatMessageType.class);
        String text = reader.readString("text");
        String name = reader.readString("name");
        String thinking = reader.readString("thinking");
        List<LangChainToolExecutionRequest> toolExecutionRequests =
                reader.readCollection("toolExecutionRequests", new ArrayList<>(), LangChainToolExecutionRequest.class);
        String toolExecutionId = reader.readString("toolExecutionId");
        String toolName = reader.readString("toolName");
        Boolean isError = reader.readBoolean("isError");
        return new LangChainChatMessage(
                type, text, name, thinking, toolExecutionRequests, toolExecutionId, toolName, isError);
    }

    @Override
    public void writeTo(ProtoStreamWriter writer, LangChainChatMessage message) throws IOException {
        writer.writeEnum("type", message.type());
        writer.writeString("text", message.text());
        writer.writeString("name", message.name());
        writer.writeString("thinking", message.thinking());
        writer.writeCollection(
                "toolExecutionRequests", message.toolExecutionRequests(), LangChainToolExecutionRequest.class);
        writer.writeString("toolExecutionId", message.toolExecutionId());
        writer.writeString("toolName", message.toolName());
        writer.writeBoolean("isError", message.isError());
    }

    @Override
    public Class<? extends LangChainChatMessage> getJavaClass() {
        return LangChainChatMessage.class;
    }

    @Override
    public String getTypeName() {
        return typeName;
    }
}
