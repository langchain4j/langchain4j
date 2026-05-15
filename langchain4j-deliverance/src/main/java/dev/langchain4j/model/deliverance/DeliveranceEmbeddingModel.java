package dev.langchain4j.model.deliverance;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.deliverance.spi.DeliveranceEmbeddingModelBuilderFactory;
import dev.langchain4j.model.embedding.DimensionAwareEmbeddingModel;
import dev.langchain4j.model.output.Response;
import io.teknek.deliverance.embedding.PoolingType;
import io.teknek.deliverance.model.AbstractModel;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static dev.langchain4j.spi.ServiceHelper.loadFactories;

public class DeliveranceEmbeddingModel extends DimensionAwareEmbeddingModel {

    private final AbstractModel model;
    private final PoolingType poolingType;
    private final String modelName;

    public DeliveranceEmbeddingModel(AbstractModel model, String modelName, PoolingType poolingType) {
        this.model = model;
        this.dimension = model.getConfig().embeddingLength;
        this.poolingType = poolingType == null ? PoolingType.AVG : poolingType;
        this.modelName = modelName;
    }

    public static DeliveranceEmbeddingModelBuilder builder() {
        for (DeliveranceEmbeddingModelBuilderFactory factory : loadFactories(DeliveranceEmbeddingModelBuilderFactory.class)) {
            return factory.get();
        }
        return new DeliveranceEmbeddingModelBuilder();
    }

    @Override
    public Response<List<Embedding>> embedAll(List<TextSegment> textSegments) {
        List<Embedding> embeddings = new ArrayList<>();
        textSegments.forEach(textSegment -> embeddings.add(Embedding.from(model.embed(textSegment.text(), poolingType))));
        return Response.from(embeddings);
    }

    @Override
    public String modelName() {
        return modelName;
    }

    public static class DeliveranceEmbeddingModelBuilder {

        private DeliveranceEmbeddingModels.Builder modelBuilder;
        private PoolingType poolingType;

        public DeliveranceEmbeddingModelBuilder() {
        }

        public DeliveranceEmbeddingModelBuilder modelBuilder(DeliveranceEmbeddingModels.Builder modelBuilder) {
            this.modelBuilder = modelBuilder;
            return this;
        }

        public DeliveranceEmbeddingModelBuilder poolingType(PoolingType poolingType) {
            this.poolingType = poolingType;
            return this;
        }

        public DeliveranceEmbeddingModel build() {
            DeliveranceEmbeddingModels.Builder builder = Objects.requireNonNull(modelBuilder, "modelBuilder must be set");
            return new DeliveranceEmbeddingModel(
                    DeliveranceModelSupport.loadEmbeddingModel(builder),
                    DeliveranceModelSupport.modelName(builder),
                    poolingType
            );
        }

        @Override
        public String toString() {
            return "DeliveranceEmbeddingModel.DeliveranceEmbeddingModelBuilder(modelBuilder=" + modelBuilder
                    + ", poolingType=" + poolingType + ")";
        }
    }
}
