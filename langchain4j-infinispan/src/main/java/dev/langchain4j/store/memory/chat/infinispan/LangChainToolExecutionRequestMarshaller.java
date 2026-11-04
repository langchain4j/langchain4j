package dev.langchain4j.store.memory.chat.infinispan;

import java.io.IOException;
import org.infinispan.protostream.MessageMarshaller;

/**
 * ProtoStream marshaller for {@link LangChainToolExecutionRequest}
 */
public class LangChainToolExecutionRequestMarshaller implements MessageMarshaller<LangChainToolExecutionRequest> {

    private final String typeName;

    public LangChainToolExecutionRequestMarshaller(String typeName) {
        this.typeName = typeName;
    }

    @Override
    public LangChainToolExecutionRequest readFrom(ProtoStreamReader reader) throws IOException {
        String id = reader.readString("id");
        String name = reader.readString("name");
        String arguments = reader.readString("arguments");
        return new LangChainToolExecutionRequest(id, name, arguments);
    }

    @Override
    public void writeTo(ProtoStreamWriter writer, LangChainToolExecutionRequest request) throws IOException {
        writer.writeString("id", request.id());
        writer.writeString("name", request.name());
        writer.writeString("arguments", request.arguments());
    }

    @Override
    public Class<? extends LangChainToolExecutionRequest> getJavaClass() {
        return LangChainToolExecutionRequest.class;
    }

    @Override
    public String getTypeName() {
        return typeName;
    }
}
