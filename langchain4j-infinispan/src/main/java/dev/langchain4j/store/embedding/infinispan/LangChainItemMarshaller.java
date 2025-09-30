package dev.langchain4j.store.embedding.infinispan;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.infinispan.protostream.MessageMarshaller;

/**
 * Marshaller to read and write embeddings to Infinispan
 */
public class LangChainItemMarshaller implements MessageMarshaller<LangChainInfinispanItem> {

    private final String typeName;

    /**
     * Constructor for the LangChainItemMarshaller Marshaller
     *
     * @param typeName,      the full type of the protobuf entity
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

        Map<String, Object> metadataMap = new HashMap<>();
        if (metadata != null) {
            for (LangChainMetadata meta : metadata) {
                metadataMap.put(meta.name(), meta.value());
            }
        }
        return new LangChainInfinispanItem(id, embedding, text, metadata, metadataMap);
    }

    @Override
    public void writeTo(ProtoStreamWriter writer, LangChainInfinispanItem item) throws IOException {
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
