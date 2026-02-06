package dev.langchain4j.model.batch;

import dev.langchain4j.Experimental;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;

/**
 * A batch model for processing multiple embedding requests asynchronously.
 */
@Experimental
public interface BatchEmbeddingModel extends BatchModel<TextSegment, Embedding> {}
