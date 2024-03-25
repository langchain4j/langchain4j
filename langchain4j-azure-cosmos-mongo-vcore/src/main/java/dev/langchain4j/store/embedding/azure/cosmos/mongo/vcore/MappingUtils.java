package dev.langchain4j.store.embedding.azure.cosmos.mongo.vcore;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;

class MappingUtils {

    private MappingUtils() throws InstantiationException {
        throw new InstantiationException("can't instantiate this class");
    }

    static AzureCosmosDbMongoVCoreDocument toMongoDbDocument(String id, Embedding embedding, TextSegment textSegment) {
        if (textSegment == null) {
            return new AzureCosmosDbMongoVCoreDocument(id, embedding.vectorAsList(), null, null);
        }
        return new AzureCosmosDbMongoVCoreDocument(id, embedding.vectorAsList(), textSegment.text(), textSegment.metadata().asMap());
    }

    static EmbeddingMatch<TextSegment> toEmbeddingMatch(AzureCosmosDbMongoVCoreMatchedDocument matchedDocument) {
        TextSegment textSegment = null;
        if (matchedDocument.getText() != null) {
            textSegment = TextSegment.from(matchedDocument.getText(), Metadata.from(matchedDocument.getMetadata()));
        }
        return new EmbeddingMatch<>(matchedDocument.getScore(), matchedDocument.getId(), Embedding.from(matchedDocument.getEmbedding()), textSegment);
    }
}
