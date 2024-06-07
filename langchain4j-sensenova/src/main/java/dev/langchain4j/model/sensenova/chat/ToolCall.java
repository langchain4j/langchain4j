package dev.langchain4j.model.sensenova.chat;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

@Getter
@ToString
@EqualsAndHashCode
public final class ToolCall {
	private final String id;
	private final Integer index;
	private final ToolType type;
	private final FunctionCall function;

	private ToolCall(Builder builder) {
		this.id = builder.id;
		this.index = builder.index;
		this.type = builder.type;
		this.function = builder.function;
	}

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {
		private String id;
		private Integer index;
		private ToolType type;
		private FunctionCall function;

		private Builder() {
		}

		public Builder id(String id) {
			this.id = id;
			return this;
		}

		public Builder index(Integer index) {
			this.index = index;
			return this;
		}

		public Builder type(ToolType type) {
			this.type = type;
			return this;
		}

		public Builder function(FunctionCall function) {
			this.function = function;
			return this;
		}

		public ToolCall build() {
			return new ToolCall(this);
		}
	}
}
