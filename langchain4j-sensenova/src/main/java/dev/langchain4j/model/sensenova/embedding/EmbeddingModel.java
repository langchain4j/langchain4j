package dev.langchain4j.model.sensenova.embedding;

public enum EmbeddingModel {

    NOVA_EMBEDDING_STABLE("nova-embedding-stable"),
	;

	private final String value;

	EmbeddingModel(String value) {
		this.value = value;
	}

	@Override
	public String toString() {
		return this.value;
	}
}
