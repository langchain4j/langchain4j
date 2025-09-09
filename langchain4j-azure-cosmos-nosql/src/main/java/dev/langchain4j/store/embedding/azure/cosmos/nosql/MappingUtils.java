package dev.langchain4j.store.embedding.azure.cosmos.nosql;

import static dev.langchain4j.internal.Utils.toStringValueMap;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;

class MappingUtils {

    private MappingUtils() throws InstantiationException {
        throw new InstantiationException("can't instantiate this class");
    }

    static AzureCosmosDbNoSqlDocument toNoSqlDbDocument(String id, Embedding embedding, TextSegment textSegment) {
        if (textSegment == null) {
            return new AzureCosmosDbNoSqlDocument(id, embedding.vectorAsList(), null, null);
        }
        if (embedding == null) {
            return new AzureCosmosDbNoSqlDocument(
                    id,
                    null,
                    textSegment.text(),
                    toStringValueMap(textSegment.metadata().toMap()));
        }
        return new AzureCosmosDbNoSqlDocument(
                id,
                embedding.vectorAsList(),
                textSegment.text(),
                toStringValueMap(textSegment.metadata().toMap()));
    }

    static EmbeddingMatch<TextSegment> toEmbeddingMatch(AzureCosmosDbNoSqlMatchedDocument matchedDocument) {
        TextSegment textSegment = null;
        if (matchedDocument.getText() != null) {
            textSegment = TextSegment.from(matchedDocument.getText(), Metadata.from(matchedDocument.getMetadata()));
        }
        if (matchedDocument.getScore() == null) {
            return new EmbeddingMatch<>(0.0, matchedDocument.getId(), null, textSegment);
        }
        return new EmbeddingMatch<>(
                matchedDocument.getScore(),
                matchedDocument.getId(),
                Embedding.from(matchedDocument.getEmbedding()),
                textSegment);
    }
}
