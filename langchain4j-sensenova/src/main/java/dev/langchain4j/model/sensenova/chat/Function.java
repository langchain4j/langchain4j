package dev.langchain4j.model.sensenova.chat;

import dev.langchain4j.agent.tool.JsonSchemaProperty;
import lombok.Data;

import java.util.HashMap;
import java.util.Map;

@Data
public final class Function {

	private final String name;
	private final String description;
	private final Parameters parameters;

	private Function(Builder builder) {
		this.name = builder.name;
		this.description = builder.description;
		this.parameters = builder.parameters;
	}

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {
		private String name;
		private String description;
		private Parameters parameters;

		private Builder() {
		}

		public Builder name(String name) {
			this.name = name;
			return this;
		}

		public Builder description(String description) {
			this.description = description;
			return this;
		}

		public Builder parameters(Parameters parameters) {
			this.parameters = parameters;
			return this;
		}

		public Builder addParameter(String name, JsonSchemaProperty... jsonSchemaProperties) {
			this.addOptionalParameter(name, jsonSchemaProperties);
			this.parameters.required().add(name);
			return this;
		}

		public Builder addOptionalParameter(String name, JsonSchemaProperty... jsonSchemaProperties) {
			if (this.parameters == null) {
				this.parameters = Parameters.builder().build();
			}

			Map<String, Object> jsonSchemaPropertiesMap = new HashMap<>();

			for (JsonSchemaProperty jsonSchemaProperty : jsonSchemaProperties) {
				jsonSchemaPropertiesMap.put(jsonSchemaProperty.key(), jsonSchemaProperty.value());
			}

			this.parameters.properties().put(name, jsonSchemaPropertiesMap);
			return this;
		}

		public Function build() {
			return new Function(this);
		}
	}
}
