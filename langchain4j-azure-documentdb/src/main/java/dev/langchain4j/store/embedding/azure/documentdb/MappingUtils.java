package dev.langchain4j.store.embedding.azure.documentdb;

import static dev.langchain4j.internal.Utils.toStringValueMap;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;

class MappingUtils {

    private MappingUtils() throws InstantiationException {
        throw new InstantiationException("can't instantiate this class");
    }

    static AzureDocumentDbDocument toMongoDbDocument(String id, Embedding embedding, TextSegment textSegment) {
        if (textSegment == null) {
            return new AzureDocumentDbDocument(id, embedding.vectorAsList(), null, null);
        }
        return new AzureDocumentDbDocument(
                id,
                embedding.vectorAsList(),
                textSegment.text(),
                toStringValueMap(textSegment.metadata().toMap()));
    }

    static EmbeddingMatch<TextSegment> toEmbeddingMatch(AzureDocumentDbMatchedDocument matchedDocument) {
        TextSegment textSegment = null;
        if (matchedDocument.getText() != null) {
            textSegment = TextSegment.from(matchedDocument.getText(), Metadata.from(matchedDocument.getMetadata()));
        }
        return new EmbeddingMatch<>(
                matchedDocument.getScore(),
                matchedDocument.getId(),
                Embedding.from(matchedDocument.getEmbedding()),
                textSegment);
    }
}
