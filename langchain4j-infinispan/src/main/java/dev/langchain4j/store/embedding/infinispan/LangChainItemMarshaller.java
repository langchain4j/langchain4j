package dev.langchain4j.store.embedding.infinispan;

import org.infinispan.protostream.MessageMarshaller;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

/**
 * Marshaller to read and write embeddings to Infinispan
 */
public class LangChainItemMarshaller implements MessageMarshaller<LangChainInfinispanItem> {

    private final String typeName;

    /**
     * Constructor for the LangChainItemMarshaller Marshaller
     * @param typeName, the full type of the protobuf entity
     */
    public LangChainItemMarshaller(String typeName) {
        this.typeName = typeName;
    }

    @Override
    public LangChainInfinispanItem readFrom(ProtoStreamReader reader) throws IOException {
        String id = reader.readString("id");
        float[] embedding = reader.readFloats("embedding");
        String text = reader.readString("text");
        Set<LangChainMetadata> metadata = reader.readCollection("metadata", new HashSet<>(), LangChainMetadata.class);
        return new LangChainInfinispanItem(id, embedding, text, metadata);
    }

    @Override
    public void writeTo(ProtoStreamWriter writer, LangChainInfinispanItem item)
            throws IOException {
        writer.writeString("id", item.id());
        writer.writeFloats("embedding", item.embedding());
        writer.writeString("text", item.text());
        writer.writeCollection("metadata", item.metadata(), LangChainMetadata.class);
    }

    @Override
    public Class<? extends LangChainInfinispanItem> getJavaClass() {
        return LangChainInfinispanItem.class;
    }

    @Override
    public String getTypeName() {
        return typeName;
    }
}
