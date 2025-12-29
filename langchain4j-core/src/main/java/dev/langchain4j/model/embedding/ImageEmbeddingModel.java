package dev.langchain4j.model.embedding;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.internal.ValidationUtils;
import dev.langchain4j.model.output.Response;

import java.net.URI;
import java.util.List;

import static java.util.Collections.singletonList;

/**
 * Represents a model that can convert images into embeddings (vector representations).
 * <p>
 * Models that support both text and image embeddings can implement both
 * {@link EmbeddingModel} and {@link ImageEmbeddingModel}.
 * <p>
 * Example:
 * <pre>{@code
 * public class MyMultimodalModel implements EmbeddingModel, ImageEmbeddingModel {
 *     // Supports both text and image embedding
 * }
 * }</pre>
 *
 * @see EmbeddingModel for text embeddings
 * @see ImageContent for image content representation
 */
public interface ImageEmbeddingModel {

    /**
     * Embeds a single image.
     *
     * @param image the image content to embed
     * @return the embedding response containing the vector representation
     */
    default Response<Embedding> embed(ImageContent image) {
        Response<List<Embedding>> response = embedAllImages(singletonList(image));
        ValidationUtils.ensureEq(response.content().size(), 1,
                "Expected a single embedding, but got %d", response.content().size());
        return Response.from(response.content().get(0), response.tokenUsage(), response.finishReason());
    }

    /**
     * Embeds an image from a base64-encoded string.
     *
     * @param base64Data the base64-encoded image data
     * @param mimeType   the MIME type of the image (e.g., "image/png", "image/jpeg")
     * @return the embedding response
     */
    default Response<Embedding> embed(String base64Data, String mimeType) {
        return embed(ImageContent.from(base64Data, mimeType));
    }

    /**
     * Embeds an image from a URI.
     * <p>
     * Supports HTTP(S) URLs and cloud storage URIs (e.g., gs:// for GCS).
     *
     * @param imageUri the URI of the image
     * @return the embedding response
     */
    default Response<Embedding> embed(URI imageUri) {
        return embed(ImageContent.from(imageUri));
    }

    /**
     * Embeds multiple images in a batch.
     * <p>
     * This is the core method that implementations must provide.
     * The method is named {@code embedAllImages} (rather than {@code embedAll}) to avoid
     * a method signature clash with {@link EmbeddingModel#embedAll(List)} when a class
     * implements both interfaces, due to Java's type erasure.
     *
     * @param images the list of images to embed
     * @return a response containing embeddings in the same order as the input
     */
    Response<List<Embedding>> embedAllImages(List<ImageContent> images);

    /**
     * Returns the dimension of the {@link Embedding} produced by this embedding model.
     *
     * @return the embedding dimension
     */
    default int dimension() {
        throw new UnsupportedOperationException(
                "dimension() not implemented. Override this method or use a test image to determine dimension.");
    }

    /**
     * Returns the name of the underlying embedding model.
     * <p>
     * Implementations are encouraged to override this method and provide the actual model name.
     * The default implementation returns {@code "unknown"}, which indicates
     * that the model name is unknown.
     *
     * @return the model name or a fallback value if not provided
     */
    default String modelName() {
        return "unknown";
    }
}
