package dev.langchain4j.model.googleai;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
class GeminiFunctionDeclaration {
    private String name;
    private String description;
    private GeminiSchema parameters;

    @JsonCreator
    GeminiFunctionDeclaration(@JsonProperty("name") String name, @JsonProperty("description") String description, @JsonProperty("parameters") GeminiSchema parameters) {
        this.name = name;
        this.description = description;
        this.parameters = parameters;
    }

    public static GeminiFunctionDeclarationBuilder builder() {
        return new GeminiFunctionDeclarationBuilder();
    }

    public String getName() {
        return this.name;
    }

    public String getDescription() {
        return this.description;
    }

    public GeminiSchema getParameters() {
        return this.parameters;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setParameters(GeminiSchema parameters) {
        this.parameters = parameters;
    }

    public boolean equals(final Object o) {
        if (o == this) return true;
        if (!(o instanceof GeminiFunctionDeclaration)) return false;
        final GeminiFunctionDeclaration other = (GeminiFunctionDeclaration) o;
        if (!other.canEqual((Object) this)) return false;
        final Object this$name = this.getName();
        final Object other$name = other.getName();
        if (this$name == null ? other$name != null : !this$name.equals(other$name)) return false;
        final Object this$description = this.getDescription();
        final Object other$description = other.getDescription();
        if (this$description == null ? other$description != null : !this$description.equals(other$description))
            return false;
        final Object this$parameters = this.getParameters();
        final Object other$parameters = other.getParameters();
        if (this$parameters == null ? other$parameters != null : !this$parameters.equals(other$parameters))
            return false;
        return true;
    }

    protected boolean canEqual(final Object other) {
        return other instanceof GeminiFunctionDeclaration;
    }

    public int hashCode() {
        final int PRIME = 59;
        int result = 1;
        final Object $name = this.getName();
        result = result * PRIME + ($name == null ? 43 : $name.hashCode());
        final Object $description = this.getDescription();
        result = result * PRIME + ($description == null ? 43 : $description.hashCode());
        final Object $parameters = this.getParameters();
        result = result * PRIME + ($parameters == null ? 43 : $parameters.hashCode());
        return result;
    }

    public String toString() {
        return "GeminiFunctionDeclaration(name=" + this.getName() + ", description=" + this.getDescription() + ", parameters=" + this.getParameters() + ")";
    }

    public static class GeminiFunctionDeclarationBuilder {
        private String name;
        private String description;
        private GeminiSchema parameters;

        GeminiFunctionDeclarationBuilder() {
        }

        public GeminiFunctionDeclarationBuilder name(String name) {
            this.name = name;
            return this;
        }

        public GeminiFunctionDeclarationBuilder description(String description) {
            this.description = description;
            return this;
        }

        public GeminiFunctionDeclarationBuilder parameters(GeminiSchema parameters) {
            this.parameters = parameters;
            return this;
        }

        public GeminiFunctionDeclaration build() {
            return new GeminiFunctionDeclaration(this.name, this.description, this.parameters);
        }

        public String toString() {
            return "GeminiFunctionDeclaration.GeminiFunctionDeclarationBuilder(name=" + this.name + ", description=" + this.description + ", parameters=" + this.parameters + ")";
        }
    }
}
