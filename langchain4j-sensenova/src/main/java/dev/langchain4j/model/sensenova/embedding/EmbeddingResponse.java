package dev.langchain4j.model.sensenova.embedding;


import dev.langchain4j.model.sensenova.shared.Usage;
import lombok.*;

import java.util.List;


@Data
@Builder
public final class EmbeddingResponse {

	private List<Embedding> embeddings;
	private Usage usage;

	/**
	 * Convenience method to get the embedding from the first data.
	 */
	public List<Float> getEmbedding() {
		return embeddings.get(0).getEmbedding();
	}


}
