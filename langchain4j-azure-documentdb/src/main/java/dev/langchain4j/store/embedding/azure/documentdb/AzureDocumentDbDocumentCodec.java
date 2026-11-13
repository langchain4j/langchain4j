package dev.langchain4j.store.embedding.azure.documentdb;

import static java.util.stream.Collectors.toList;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bson.BsonReader;
import org.bson.BsonWriter;
import org.bson.Document;
import org.bson.codecs.Codec;
import org.bson.codecs.DecoderContext;
import org.bson.codecs.DocumentCodec;
import org.bson.codecs.EncoderContext;

final class AzureDocumentDbDocumentCodec implements Codec<AzureDocumentDbDocument> {

    private final DocumentCodec documentCodec = new DocumentCodec();

    @Override
    public void encode(BsonWriter writer, AzureDocumentDbDocument value, EncoderContext encoderContext) {
        Document document = new Document();
        if (value.getId() != null) {
            document.append("_id", value.getId());
        }
        if (value.getEmbedding() != null) {
            document.append("embedding", value.getEmbedding());
        }
        if (value.getMetadata() != null) {
            document.append("metadata", value.getMetadata());
        }
        if (value.getText() != null) {
            document.append("text", value.getText());
        }
        documentCodec.encode(writer, document, encoderContext);
    }

    @Override
    public AzureDocumentDbDocument decode(BsonReader reader, DecoderContext decoderContext) {
        Document document = documentCodec.decode(reader, decoderContext);
        List<Number> vector = document.getList("embedding", Number.class);
        List<Float> embedding =
                vector == null ? null : vector.stream().map(Number::floatValue).collect(toList());
        Document metadataDocument = document.get("metadata", Document.class);
        Map<String, String> metadata = null;
        if (metadataDocument != null) {
            metadata = new HashMap<>();
            for (String key : metadataDocument.keySet()) {
                metadata.put(key, metadataDocument.getString(key));
            }
        }
        return new AzureDocumentDbDocument(document.getString("_id"), embedding, document.getString("text"), metadata);
    }

    @Override
    public Class<AzureDocumentDbDocument> getEncoderClass() {
        return AzureDocumentDbDocument.class;
    }
}
