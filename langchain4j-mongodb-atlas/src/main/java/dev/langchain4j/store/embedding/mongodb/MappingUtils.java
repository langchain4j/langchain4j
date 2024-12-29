package dev.langchain4j.store.embedding.mongodb;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import org.bson.Document;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

class MappingUtils {

    private MappingUtils() throws InstantiationException {
        throw new InstantiationException("Can't instantiate this class");
    }

    static MongoDbDocument toMongoDbDocument(String id, Embedding embedding, TextSegment textSegment) {
        boolean hasTextSegment = textSegment != null;
        return MongoDbDocument.builder()
                .id(id)
                .embedding(embedding.vectorAsList())
                .text(hasTextSegment ? textSegment.text() : null)
                .metadata(hasTextSegment ? textSegment.metadata().toMap() : null)
                .build();
    }

    static EmbeddingMatch<TextSegment> toEmbeddingMatch(MongoDbMatchedDocument matchedDocument) {
        TextSegment textSegment = null;
        if (matchedDocument.getText() != null) {
            textSegment = matchedDocument.getMetadata() == null
                    ? TextSegment.from(matchedDocument.getText())
                    : TextSegment.from(matchedDocument.getText(), Metadata.from(matchedDocument.getMetadata()));
        }
        return new EmbeddingMatch<>(matchedDocument.getScore(), matchedDocument.getId(), Embedding.from(matchedDocument.getEmbedding()), textSegment);
    }

    static Document fromIndexMapping(IndexMapping indexMapping) {
        List<Document> list = new ArrayList<>();
        list.add(new Document()
                .append("type", "vector")
                .append("path", "embedding")
                .append("numDimensions", indexMapping.getDimension())
                .append("similarity", "cosine")
        );
        Set<String> metadataFields = indexMapping.getMetadataFieldNames();
        if (metadataFields != null && !metadataFields.isEmpty()) {
            metadataFields.forEach(field -> {
                list.add(new Document()
                        .append("type", "filter")
                        .append("path", "metadata." + field));

            });
        }
        return new Document("fields", list);
    }
}
