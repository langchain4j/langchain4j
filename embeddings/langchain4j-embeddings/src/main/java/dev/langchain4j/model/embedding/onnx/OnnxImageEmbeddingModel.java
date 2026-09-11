package dev.langchain4j.model.embedding.onnx;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import ai.onnxruntime.OrtSession.Result;
import dev.langchain4j.Experimental;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.image.Image;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.ContentType;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.exception.UnsupportedFeatureException;
import dev.langchain4j.model.embedding.DimensionAwareEmbeddingModel;
import dev.langchain4j.model.embedding.onnx.internal.ImagePreprocessor;
import dev.langchain4j.model.embedding.onnx.internal.OnnxModelLoader;
import dev.langchain4j.model.embedding.onnx.internal.VectorUtils;
import dev.langchain4j.model.embedding.request.EmbeddingInput;
import dev.langchain4j.model.embedding.request.EmbeddingRequest;
import dev.langchain4j.model.embedding.response.EmbeddingResponse;
import dev.langchain4j.model.embedding.response.EmbeddingResponseMetadata;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * An {@link dev.langchain4j.model.embedding.EmbeddingModel} that embeds <b>images</b> within your Java
 * application's process using <a href="https://onnxruntime.ai/">ONNX runtime</a>.
 * <br>
 * It runs the vision tower of a model such as CLIP or ViT and turns each image into an
 * {@link Embedding}, so that images can be stored in an
 * {@link dev.langchain4j.store.embedding.EmbeddingStore} and searched just like text.
 * <br>
 * Any vision model in the ONNX format can be used. Information on how to convert a model into ONNX format can be
 * found <a href="https://huggingface.co/docs/optimum/exporters/onnx/usage_guides/export_a_model">here</a>; many
 * already-converted models are available <a href="https://huggingface.co/Xenova">here</a>.
 * <br>
 * Because images are not text, this model accepts only {@link ContentType#IMAGE} content. Calling the text-based
 * methods it inherits (such as {@code embed(String)}) throws an {@link UnsupportedFeatureException}. Images are
 * passed through an {@link EmbeddingRequest}:
 * <pre>{@code
 * try (OnnxImageEmbeddingModel model = OnnxImageEmbeddingModel.builder()
 *         .pathToModel("/path/to/vision_model.onnx")
 *         .preprocessorConfig(ImagePreprocessorConfig.CLIP)
 *         .build()) {
 *
 *     EmbeddingResponse response = model.embed(EmbeddingRequest.builder()
 *             .input(ImageContent.from(Paths.get("/path/to/cat.png").toUri()))
 *             .build());
 *
 *     Embedding embedding = response.embeddings().get(0);
 * }
 * }</pre>
 * To search those images with a text query rather than with another image, embed the query with the text half of
 * the same model through {@link OnnxEmbeddingModel}: both halves of a model such as CLIP project into one shared
 * vector space, so the embedding of the text "a photo of a cat" lands close to the embedding of an actual photo
 * of a cat.
 * <br>
 * The preprocessing applied to each image (resizing, cropping, normalization) must match what the model was
 * trained with, and is configured through {@link ImagePreprocessorConfig}.
 * <br>
 * This model holds a native ONNX session and should be {@link #close() closed} when no longer needed.
 */
@Experimental
public class OnnxImageEmbeddingModel extends DimensionAwareEmbeddingModel implements AutoCloseable {

    private static final String DEFAULT_INPUT_NAME = "pixel_values";

    private final OnnxModelLoader modelLoader;
    private final ImagePreprocessorConfig preprocessorConfig;
    private final ImagePreprocessor preprocessor;
    private final PoolingMode poolingMode;
    private final String inputName;

    public OnnxImageEmbeddingModel(Builder builder) {
        this.modelLoader = new OnnxModelLoader(ensureNotNull(builder.pathToModel, "pathToModel"));
        this.preprocessorConfig = getOrDefault(builder.preprocessorConfig, ImagePreprocessorConfig.DEFAULT);
        this.preprocessor = new ImagePreprocessor(preprocessorConfig);
        this.poolingMode = getOrDefault(builder.poolingMode, PoolingMode.CLS);
        this.inputName = resolveInputName(modelLoader.session());
    }

    @Override
    public Set<ContentType> supportedContentTypes() {
        return Set.of(ContentType.IMAGE);
    }

    @Override
    public EmbeddingResponse doEmbed(EmbeddingRequest request) {
        List<Embedding> embeddings = new ArrayList<>();
        for (EmbeddingInput input : request.inputs()) {
            embeddings.add(embed(extractSingleImage(input)));
        }
        return EmbeddingResponse.builder()
                .embeddings(embeddings)
                .metadata(EmbeddingResponseMetadata.builder()
                        .modelName(modelName())
                        .build())
                .build();
    }

    /**
     * Returns the dimension of the embeddings this model produces.
     * <p>
     * The dimension of an arbitrary ONNX vision model is only known once it has run, so the first call embeds a
     * single blank image to find out. The result is cached, so this happens at most once.
     */
    @Override
    public int dimension() {
        if (dimension == null) {
            int side = Math.max(preprocessorConfig.imageSize(), preprocessorConfig.cropSize());
            dimension = embed(new BufferedImage(side, side, BufferedImage.TYPE_INT_RGB))
                    .dimension();
        }
        return dimension;
    }

    /**
     * Releases the native ONNX session held by this model.
     */
    @Override
    public void close() {
        modelLoader.close();
    }

    private Embedding embed(Image image) {
        return embed(preprocessor.process(image));
    }

    private Embedding embed(BufferedImage image) {
        return embed(preprocessor.process(image));
    }

    private Embedding embed(float[][][][] pixelValues) {
        try (Result result = run(pixelValues)) {
            return Embedding.from(VectorUtils.normalize(extractEmbedding(result)));
        } catch (OrtException e) {
            throw new IllegalStateException("ONNX inference failed", e);
        }
    }

    private Result run(float[][][][] pixelValues) throws OrtException {
        try (OnnxTensor inputTensor = OnnxTensor.createTensor(modelLoader.environment(), pixelValues)) {
            return modelLoader.session().run(Map.of(inputName, inputTensor));
        }
    }

    private float[] extractEmbedding(Result result) throws OrtException {
        Object output = result.get(0).getValue();
        if (output instanceof float[][] pooled) {
            return pooled[0]; // [1, dimension]: the model already pooled, nothing left to do
        } else if (output instanceof float[][][] sequence) {
            return pool(sequence[0]); // [1, sequenceLength, dimension]
        } else {
            throw new IllegalStateException("Expected the ONNX model to output [1, dimension] or "
                    + "[1, sequenceLength, dimension], but got: "
                    + output.getClass().getSimpleName());
        }
    }

    private float[] pool(float[][] tokenEmbeddings) {
        return switch (poolingMode) {
            case CLS -> tokenEmbeddings[0];
            case MEAN -> meanPool(tokenEmbeddings);
        };
    }

    private static float[] meanPool(float[][] tokenEmbeddings) {
        float[] mean = new float[tokenEmbeddings[0].length];
        for (float[] tokenEmbedding : tokenEmbeddings) {
            for (int i = 0; i < mean.length; i++) {
                mean[i] += tokenEmbedding[i];
            }
        }
        for (int i = 0; i < mean.length; i++) {
            mean[i] /= tokenEmbeddings.length;
        }
        return mean;
    }

    private static Image extractSingleImage(EmbeddingInput input) {
        Image image = null;
        for (Content content : input.contents()) {
            if (content instanceof ImageContent imageContent) {
                if (image != null) {
                    throw new UnsupportedFeatureException(OnnxImageEmbeddingModel.class.getName()
                            + " embeds one image into one vector, so an input cannot contain more than one image");
                }
                image = imageContent.image();
            }
        }
        return ensureNotNull(image, "image");
    }

    /**
     * Finds the name of the tensor the image should be fed into. Vision models conventionally call it
     * {@value #DEFAULT_INPUT_NAME}, but the name is taken from the model itself whenever that is unambiguous.
     */
    private static String resolveInputName(OrtSession session) {
        Set<String> inputNames = session.getInputNames();
        if (inputNames.size() == 1) {
            return inputNames.iterator().next();
        }
        if (inputNames.contains(DEFAULT_INPUT_NAME)) {
            return DEFAULT_INPUT_NAME;
        }
        throw new IllegalArgumentException("Expected the ONNX model to have a single input, or an input named '"
                + DEFAULT_INPUT_NAME + "', but found: " + inputNames
                + ". Please make sure this is a vision model and not, for example, a full CLIP model "
                + "(which expects both text and image inputs; export its vision tower instead).");
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private Path pathToModel;
        private ImagePreprocessorConfig preprocessorConfig;
        private PoolingMode poolingMode;

        /**
         * @param pathToModel the path to the ONNX vision model file (e.g., "/path/to/vision_model.onnx").
         */
        public Builder pathToModel(Path pathToModel) {
            this.pathToModel = pathToModel;
            return this;
        }

        /**
         * @param pathToModel the path to the ONNX vision model file (e.g., "/path/to/vision_model.onnx").
         */
        public Builder pathToModel(String pathToModel) {
            return pathToModel(pathToModel == null ? null : Paths.get(pathToModel));
        }

        /**
         * @param preprocessorConfig how each image is resized, cropped and normalized before it reaches the model.
         *                           This must match how the model was trained; the values can be found in the
         *                           {@code preprocessor_config.json} of the model on HuggingFace.
         *                           Defaults to {@link ImagePreprocessorConfig#DEFAULT}.
         */
        public Builder preprocessorConfig(ImagePreprocessorConfig preprocessorConfig) {
            this.preprocessorConfig = preprocessorConfig;
            return this;
        }

        /**
         * @param poolingMode how to reduce a sequence of token embeddings into a single vector, for models that
         *                    output one embedding per image patch. Ignored by models that pool internally and
         *                    already output a single vector per image. Defaults to {@link PoolingMode#CLS}, which
         *                    is what vision transformers such as CLIP and ViT use.
         */
        public Builder poolingMode(PoolingMode poolingMode) {
            this.poolingMode = poolingMode;
            return this;
        }

        public OnnxImageEmbeddingModel build() {
            return new OnnxImageEmbeddingModel(this);
        }
    }
}
