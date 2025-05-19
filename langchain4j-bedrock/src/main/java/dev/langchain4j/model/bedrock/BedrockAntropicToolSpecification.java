package dev.langchain4j.model.bedrock;

/**
 * @deprecated please use {@link BedrockChatModel}
 */
@Deprecated(forRemoval = true, since = "1.0.0-beta2")
class BedrockAntropicToolSpecification {

    private String name;
    private String description;
    private Object input_schema;

    public BedrockAntropicToolSpecification() {}

    public BedrockAntropicToolSpecification(final String name, final String description, final Object input_schema) {
        this.name = name;
        this.description = description;
        this.input_schema = input_schema;
    }

    public String getName() {
        return name;
    }

    public void setName(final String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(final String description) {
        this.description = description;
    }

    public Object getInput_schema() {
        return input_schema;
    }

    public void setInput_schema(final Object input_schema) {
        this.input_schema = input_schema;
    }

    public static BedrockAntropicToolSpecificationBuilder builder() {
        return new BedrockAntropicToolSpecificationBuilder();
    }

    public static class BedrockAntropicToolSpecificationBuilder {
        private String name;
        private String description;
        private Object input_schema;

        public BedrockAntropicToolSpecificationBuilder name(String name) {
            this.name = name;
            return this;
        }

        public BedrockAntropicToolSpecificationBuilder description(String description) {
            this.description = description;
            return this;
        }

        public BedrockAntropicToolSpecificationBuilder input_schema(Object input_schema) {
            this.input_schema = input_schema;
            return this;
        }

        public BedrockAntropicToolSpecification build() {
            return new BedrockAntropicToolSpecification(this.name, this.description, this.input_schema);
        }

        public String toString() {
            return "BedrockAntropicToolSpecification.BedrockAntropicToolSpecificationBuilder(name=" + this.name
                    + ", description=" + this.description + ", input_schema=" + this.input_schema + ")";
        }
    }
}
