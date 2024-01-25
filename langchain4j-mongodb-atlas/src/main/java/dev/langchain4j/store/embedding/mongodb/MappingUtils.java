package dev.langchain4j.store.embedding.mongodb;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import org.bson.Document;

import java.util.Set;

class MappingUtils {

    private MappingUtils() throws InstantiationException {
        throw new InstantiationException("can't instantiate this class");
    }

    static MongoDbDocument toMongoDbDocument(String id, Embedding embedding, TextSegment textSegment) {
        if (textSegment == null) {
            return new MongoDbDocument(id, embedding.vectorAsList(), null, null);
        }
        return new MongoDbDocument(id, embedding.vectorAsList(), textSegment.text(), textSegment.metadata().asMap());
    }

    static EmbeddingMatch<TextSegment> toEmbeddingMatch(MongoDbMatchedDocument matchedDocument) {
        TextSegment textSegment = null;
        if (matchedDocument.getText() != null) {
            textSegment = matchedDocument.getMetadata() == null ? TextSegment.from(matchedDocument.getText()) :
                    TextSegment.from(matchedDocument.getText(), Metadata.from(matchedDocument.getMetadata()));
        }
        return new EmbeddingMatch<>(matchedDocument.getScore(), matchedDocument.getId(), Embedding.from(matchedDocument.getEmbedding()), textSegment);
    }

    static Document fromIndexMapping(IndexMapping indexMapping) {
        Document mapping = new Document();
        mapping.append("dynamic", false);

        Document fields = new Document();
        writeEmbedding(indexMapping.getDimension(), fields);

        Set<String> metadataFields = indexMapping.getMetadataFieldNames();
        if (metadataFields != null && !metadataFields.isEmpty()) {
            writeMetadata(metadataFields, fields);
        }

        mapping.append("fields", fields);

        return new Document("mappings", mapping);
    }

    private static void writeMetadata(Set<String> metadataFields, Document fields) {
        Document metadata = new Document();
        metadata.append("dynamic", false);
        metadata.append("type", "document");

        Document metadataFieldDoc = new Document();
        metadataFields.forEach(field -> writeMetadataField(metadataFieldDoc, field));

        metadata.append("fields", metadataFieldDoc);

        fields.append("metadata", metadata);
    }

    private static void writeMetadataField(Document metadataFieldDoc, String fieldName) {
        Document field = new Document();
        field.append("type", "token");
        metadataFieldDoc.append(fieldName, field);
    }

    private static void writeEmbedding(int dimensions, Document fields) {
        Document embedding = new Document();
        embedding.append("dimensions", dimensions);
        embedding.append("similarity", "cosine");
        embedding.append("type", "knnVector");

        fields.append("embedding", embedding);
    }
}
