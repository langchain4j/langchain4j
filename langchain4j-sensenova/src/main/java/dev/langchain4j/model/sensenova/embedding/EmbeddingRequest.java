package dev.langchain4j.model.sensenova.embedding;

import lombok.Getter;
import lombok.ToString;

import java.util.Arrays;
import java.util.Objects;

@Getter
@ToString
public final class EmbeddingRequest {

	/**
	 * Embedding model ID
	 */
	private final String model;

	/**
	 * A maximum of 32 items are supported, and the length of each item cannot exceed 512 tokens.
	 */
	private final String[] input;

	private EmbeddingRequest(Builder builder) {
		this.model = builder.model;
		this.input = builder.input;
	}

	public static Builder builder() {
		return new Builder();
	}

	@Override
	public boolean equals(Object another) {
		if (this == another) return true;
		return another instanceof EmbeddingRequest
				&& equalTo((EmbeddingRequest) another);
	}

	private boolean equalTo(EmbeddingRequest another) {
		return Objects.equals(model, another.model)
				&& Arrays.equals(input, another.input);
	}

	@Override
	public int hashCode() {
		int h = 5381;
		h += (h << 5) + Objects.hashCode(model);
		h += (h << 5) + Objects.hashCode(input);
		return h;
	}

	public static final class Builder {

		private String model = EmbeddingModel.NOVA_EMBEDDING_STABLE.toString();
		private String[] input;

		private Builder() {
		}

		public Builder model(EmbeddingModel model) {
			return model(model.toString());
		}

		public Builder model(String model) {
			this.model = model;
			return this;
		}

		public Builder input(String[] input) {
			this.input = input;
			return this;
		}

		public EmbeddingRequest build() {
			return new EmbeddingRequest(this);
		}
	}
}
