package dev.langchain4j.store.embedding.infinispan;

import org.infinispan.protostream.schema.Field;
import org.infinispan.protostream.schema.Schema;
import org.infinispan.protostream.schema.Type;

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
        Field.Builder builder = new Schema.Builder(storeConfiguration.fileName())
                .packageName(storeConfiguration.packageItem())
                // Medata Item
                .addMessage(storeConfiguration.metadataItemName())
                .addComment("@Indexed")
                .addField(Type.Scalar.STRING, "name", 1)
                .addComment("@Basic(projectable=true)")
                .addField(Type.Scalar.STRING, "value", 2)
                .addComment("@Basic(projectable=true)")
                .addField(Type.Scalar.INT64, "value_int", 3)
                .addComment("@Basic(projectable=true)")
                .addField(Type.Scalar.DOUBLE, "value_float", 4)
                .addComment("@Basic(projectable=true)")
                // Langchain item
                .addMessage(storeConfiguration.langchainItemName())
                .addComment("@Indexed")
                .addField(Type.Scalar.STRING, "id", 1)
                .addComment("@Basic(projectable=true)")
                .addField(Type.Scalar.STRING, "text", 2)
                .addComment("@Basic(projectable=true)")
                .addRepeatedField(Type.Scalar.FLOAT, "embedding", 3)
                .addComment(String.format(
                        "@Vector(dimension=%d, similarity=%s)",
                        storeConfiguration.dimension(), storeConfiguration.similarity()));

        // Metadata embedded field
        builder.addRepeatedField(Type.create(storeConfiguration.metadataItemName()), "metadata", 4)
                .addComment("@Embedded");
        return builder.build();
    }
}
