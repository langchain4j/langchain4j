package dev.langchain4j.model.googleai;

/**
 * Represents the media resolution levels for controlling how the Gemini API
 * processes media inputs like images, videos, and PDF documents.
 *
 * <p>
 * The resolution determines the maximum number of tokens allocated for media
 * inputs, allowing balance between response quality and latency/cost.
 * </p>
 *
 * @see <a href="https://ai.google.dev/gemini-api/docs/media-resolution">Media Resolution Documentation</a>
 */
public enum GeminiMediaResolutionLevel {
    /**
     * Default setting. Token count varies between model versions.
     */
    MEDIA_RESOLUTION_UNSPECIFIED,

    /**
     * Lower token count, resulting in faster processing and lower cost, but with less detail.
     */
    MEDIA_RESOLUTION_LOW,

    /**
     * A balance between detail, cost, and latency.
     */
    MEDIA_RESOLUTION_MEDIUM,

    /**
     * Higher token count, providing more detail for the model to work with, at the expense of
     * increased latency and cost.
     */
    MEDIA_RESOLUTION_HIGH,

    /**
     * (Per part only) Highest token count, required for specific use cases such as computer use.
     */
    MEDIA_RESOLUTION_ULTRA_HIGH
}
