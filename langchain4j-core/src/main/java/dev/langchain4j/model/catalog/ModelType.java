package dev.langchain4j.model.catalog;

import dev.langchain4j.Experimental;
import dev.langchain4j.model.audio.AudioTranscriptionModel;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.image.ImageModel;
import dev.langchain4j.model.moderation.ModerationModel;
import dev.langchain4j.model.scoring.ScoringModel;

/**
 * Represents the type/category of a model.
 *
 * @since 1.10.0
 */
@Experimental
public enum ModelType {

    /**
     * Chat/conversational models (e.g., GPT-5, Claude, etc.).
     * Can be used with {@link ChatModel} or {@link StreamingChatModel}.
     */
    CHAT,

    /**
     * Text embedding models for vector representations.
     * Can be used with {@link EmbeddingModel}.
     */
    EMBEDDING,

    /**
     * Image generation models (e.g., DALL-E, Stable Diffusion).
     * Can be used with {@link ImageModel}.
     */
    IMAGE_GENERATION,

    /**
     * Audio transcription models (speech-to-text).
     * Can be used with {@link AudioTranscriptionModel}.
     */
    AUDIO_TRANSCRIPTION,

    /**
     * Audio generation models (text-to-speech).
     */
    AUDIO_GENERATION,

    /**
     * Content moderation models.
     * Can be used with {@link ModerationModel}.
     */
    MODERATION,

    /**
     * Document scoring or re-ranking models.
     * Can be used with {@link ScoringModel}.
     */
    SCORING,

    /**
     * Other or unclassified model types.
     */
    OTHER
}
