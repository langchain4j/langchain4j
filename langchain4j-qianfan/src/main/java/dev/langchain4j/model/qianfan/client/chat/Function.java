package dev.langchain4j.model.qianfan.client.chat;



import java.util.HashMap;
import java.util.Map;

public class Function {

    private final String name;
    private final String description;
    private final Parameters parameters;

    private final Responses responses;
    private final Examples examples;

    private Function(Builder builder) {
        this.name = builder.name;
        this.description = builder.description;
        this.parameters = builder.parameters;
        this.examples = builder.examples;
        this.responses = builder.responses;
    }


    public String name() {
        return this.name;
    }

    public String description() {
        return this.description;
    }

    public Parameters parameters() {
        return this.parameters;
    }

    public Responses responses() {
        return responses;
    }

    public Examples examples() {
        return examples;
    }



    @Override
    public String toString() {
        return "Function{" +
                "name='" + name + '\'' +
                ", description='" + description + '\'' +
                ", parameters=" + parameters +
                ", responses=" + responses +
                ", examples=" + examples +
                '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private String name;
        private String description;
        private Parameters parameters;
        private Responses responses;
        private Examples examples;

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

            Map<String, Object> jsonSchemaPropertiesMap = new HashMap();
            JsonSchemaProperty[] var4 = jsonSchemaProperties;
            int var5 = jsonSchemaProperties.length;

            for (int var6 = 0; var6 < var5; ++var6) {
                JsonSchemaProperty jsonSchemaProperty = var4[var6];
                jsonSchemaPropertiesMap.put(jsonSchemaProperty.key(), jsonSchemaProperty.value());
            }

            this.parameters.properties().put(name, jsonSchemaPropertiesMap);
            return this;
        }

        public Builder responses(Responses responses) {
            this.responses = responses;
            return this;
        }

        public Builder examples(Examples examples) {
            this.examples = examples;
            return this;
        }

        public Function build() {
            return new Function(this);
        }
    }
}

