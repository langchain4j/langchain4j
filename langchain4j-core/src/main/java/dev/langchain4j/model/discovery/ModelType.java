package dev.langchain4j.model.discovery;

/**
 * Represents the type/category of a model.
 */
public enum ModelType {
    /**
     * Chat/conversational models (e.g., GPT-4, Claude, etc.)
     */
    CHAT,

    /**
     * Text embedding models for vector representations
     */
    EMBEDDING,

    /**
     * Image generation models (e.g., DALL-E, Stable Diffusion)
     */
    IMAGE_GENERATION,

    /**
     * Image understanding/vision models
     */
    IMAGE_UNDERSTANDING,

    /**
     * Audio transcription models (speech-to-text)
     */
    AUDIO_TRANSCRIPTION,

    /**
     * Audio generation models (text-to-speech)
     */
    AUDIO_GENERATION,

    /**
     * Video understanding models
     */
    VIDEO_UNDERSTANDING,

    /**
     * Content moderation models
     */
    MODERATION,

    /**
     * Code completion/generation models
     */
    CODE_COMPLETION,

    /**
     * Document reranking models
     */
    RERANKING,

    /**
     * Other or unclassified model types
     */
    OTHER
}
