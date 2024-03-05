package dev.langchain4j.store.embedding.infinispan;

import org.infinispan.protostream.MessageMarshaller;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Marshaller to read and write embeddings to Infinispan
 */
class LangChainItemMarshaller implements MessageMarshaller<LangChainInfinispanItem> {

    private final String typeName;

    public LangChainItemMarshaller(Integer dimension) {
        this.typeName = "LangChainItem" + dimension.toString();
    }

    @Override
    public LangChainInfinispanItem readFrom(ProtoStreamReader reader) throws IOException {
        String id = reader.readString("id");
        float[] embedding = reader.readFloats("embedding");
        String text = reader.readString("text");
        List<String> metadataKeys = reader.readCollection("metadataKeys", new ArrayList<>(), String.class);
        List<String> metadataValues = reader.readCollection("metadataValues", new ArrayList<>(), String.class);
        return new LangChainInfinispanItem(id, embedding, text, metadataKeys, metadataValues);
    }

    @Override
    public void writeTo(ProtoStreamWriter writer, LangChainInfinispanItem item)
            throws IOException {
        writer.writeString("id", item.getId());
        writer.writeFloats("embedding", item.getEmbedding());
        writer.writeString("text", item.getText());
        writer.writeCollection("metadataKeys", item.getMetadataKeys(), String.class);
        writer.writeCollection("metadataValues", item.getMetadataValues(), String.class);
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
