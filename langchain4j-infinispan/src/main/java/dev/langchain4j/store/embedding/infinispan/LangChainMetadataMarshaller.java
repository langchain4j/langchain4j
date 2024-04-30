package dev.langchain4j.store.embedding.infinispan;

import org.infinispan.protostream.MessageMarshaller;

import java.io.IOException;

/**
 * Marshaller to read and write metadata to Infinispan
 */
public class LangChainMetadataMarshaller implements MessageMarshaller<LangChainMetadata> {

    private final String typeName;

    /**
     * Constructor for the LangChainMetadata Marshaller
     * @param typeName, the full type of the protobuf entity
     */
    public LangChainMetadataMarshaller(String typeName) {
        this.typeName = typeName;
    }

    @Override
    public LangChainMetadata readFrom(ProtoStreamReader reader) throws IOException {
        String name = reader.readString("name");
        String value = reader.readString("value");
        return new LangChainMetadata(name, value);
    }

    @Override
    public void writeTo(ProtoStreamWriter writer, LangChainMetadata item)
            throws IOException {
        writer.writeString("name", item.name());
        writer.writeString("value", item.value());
    }

    @Override
    public Class<? extends LangChainMetadata> getJavaClass() {
        return LangChainMetadata.class;
    }

    @Override
    public String getTypeName() {
        return typeName;
    }
}
