package dev.langchain4j.store.embedding.infinispan;

import org.infinispan.protostream.schema.Schema;
import org.infinispan.protostream.schema.Type;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

/**
 * LangchainSchemaCreator for Infinispan
 */
public final class LangchainSchemaCreator {
   /**
    * Build the Infinispan Schema to marshall embeddings
    *
    * @param storeConfiguration, the configuration of the store
    * @return produced Schema
    */
   public static Schema buildSchema(InfinispanStoreConfiguration storeConfiguration) {
     return new Schema.Builder(storeConfiguration.fileName())
            .packageName(storeConfiguration.packageItem())
            .addMessage(storeConfiguration.metadataItemName())
            .addComment("@Indexed")
            .addField(Type.Scalar.STRING, "name", 1)
            .addComment("@Text")
            .addField(Type.Scalar.STRING, "value", 2)
            .addComment("@Text")
            .addMessage(storeConfiguration.langchainItemName())
            .addComment("@Indexed")
            .addField(Type.Scalar.STRING, "id", 1)
            .addComment("@Text")
            .addField(Type.Scalar.STRING, "text", 2)
            .addComment("@Keyword")
            .addRepeatedField(Type.Scalar.FLOAT, "embedding", 3)
            .addComment("@Vector(dimension=" + storeConfiguration.dimension() + ", similarity=" + storeConfiguration.similarity() + ")")
            .addRepeatedField(Type.create(storeConfiguration.metadataItemName()), "metadata", 4)
            .build();
   }
}
