/**
 * Provides batch processing model interfaces and types for asynchronous, large-scale
 * operations across different modalities (chat, embeddings, images).
 *
 * <p>Batch processing offers significant cost savings (typically 50% reduction) compared
 * to real-time requests, with a 24-hour turnaround SLO, making it ideal for non-urgent,
 * large-scale tasks.</p>
 *
 * @see dev.langchain4j.model.batch.BatchModel
 * @see dev.langchain4j.model.batch.BatchResponse
 */
@NullMarked
package dev.langchain4j.model.batch;

import org.jspecify.annotations.NullMarked;
