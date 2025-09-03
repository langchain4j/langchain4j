package dev.langchain4j.store.embedding.infinispan;

import java.io.IOException;
import org.infinispan.protostream.MessageMarshaller;

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
        String valueStr = reader.readString("value");
        Long valueInt = reader.readLong("value_int");
        Double valueFloat = reader.readDouble("value_float");
        Object value = valueStr;

        if (value == null) {
            value = valueInt;
        }
        if (value == null) {
            value = valueFloat;
        }

        return new LangChainMetadata(name, value);
    }

    @Override
    public void writeTo(ProtoStreamWriter writer, LangChainMetadata item) throws IOException {
        writer.writeString("name", item.name());
        String value = null;
        Long value_int = null;
        Double value_float = null;
        if (item.value() instanceof String) {
            value = (String) item.value();
        } else if (item.value() instanceof Integer) {
            value_int = ((Integer) item.value()).longValue();
        } else if (item.value() instanceof Long) {
            value_int = (Long) item.value();
        } else if (item.value() instanceof Float) {
            value_float = ((Float) item.value()).doubleValue();
        } else if (item.value() instanceof Double) {
            value_float = (Double) item.value();
        } else {
            value = item.value().toString();
        }

        writer.writeString("value", value);
        writer.writeLong("value_int", value_int);
        writer.writeDouble("value_float", value_float);
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
