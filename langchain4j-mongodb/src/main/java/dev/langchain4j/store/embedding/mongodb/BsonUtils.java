package dev.langchain4j.store.embedding.mongodb;

import org.bson.BsonDocument;
import org.bson.BsonDocumentWriter;
import org.bson.conversions.Bson;
import org.bson.json.JsonWriterSettings;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

class BsonUtils {
    private static final JsonWriterSettings settings = JsonWriterSettings.builder().indent(true).build();

    public static String asJson(List<Bson> bsonArray) {
        return bsonArray.stream()
                .map(BsonUtils::asJson)
                .collect(Collectors.joining(", "));

    }

    public static String asJson(Bson bson) {
        return bson.toBsonDocument().toJson(settings);

    }


    public static Bson getMapping(int dimensions, Set<String> metadataFields) {
        BsonDocumentWriter writer = new BsonDocumentWriter(new BsonDocument());
        // @formatter:off
        writer.writeStartDocument();
            writer.writeStartDocument("mappings");
            writer.writeBoolean("dynamic", false);
                writer.writeStartDocument("fields");
                writeEmbedding(dimensions, writer);

                if( metadataFields != null && !metadataFields.isEmpty()){
                    writeMetadata(metadataFields, writer);
                }
                writer.writeEndDocument();
            writer.writeEndDocument();
        writer.writeEndDocument();
        // @formatter:on

        return writer.getDocument();

    }

    private static void writeMetadata(Set<String> metadataFields, BsonDocumentWriter writer) {
        // @formatter:off
        writer.writeStartDocument("metadata");
            writer.writeBoolean("dynamic", false);
            writer.writeString("type", "document");
                writer.writeStartDocument("fields");
                metadataFields.forEach(field -> writeMetadataField(writer, field));
                writer.writeEndDocument();
        writer.writeEndDocument();
        // @formatter:on
    }

    private static void writeMetadataField(BsonDocumentWriter writer, String fieldName) {
        writer.writeStartDocument(fieldName);
        writer.writeString("type", "token");
        writer.writeEndDocument();
    }

    private static void writeEmbedding(int dimensions, BsonDocumentWriter writer) {
        writer.writeStartDocument("embedding");
        writer.writeInt32("dimensions", dimensions);
        writer.writeString("similarity", "cosine");
        writer.writeString("type", "knnVector");
        writer.writeEndDocument();
    }
}
