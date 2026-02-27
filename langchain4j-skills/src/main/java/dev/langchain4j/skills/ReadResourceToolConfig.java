package dev.langchain4j.skills;

import java.util.List;
import java.util.function.Function;

public class ReadResourceToolConfig {

    final String name;
    final String description;
    final String skillNameParameterName;
    final String skillNameParameterDescription;
    final String relativePathParameterName;
    final String relativePathParameterDescription;
    final Function<List<? extends Skill>, String> relativePathParameterDescriptionProvider;

    private ReadResourceToolConfig(Builder builder) {
        this.name = builder.name;
        this.description = builder.description;
        this.skillNameParameterName = builder.skillNameParameterName;
        this.skillNameParameterDescription = builder.skillNameParameterDescription;
        this.relativePathParameterName = builder.relativePathParameterName;
        this.relativePathParameterDescription = builder.relativePathParameterDescription;
        this.relativePathParameterDescriptionProvider = builder.relativePathParameterDescriptionProvider;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private String name;
        private String description;
        private String skillNameParameterName;
        private String skillNameParameterDescription;
        private String relativePathParameterName;
        private String relativePathParameterDescription;
        private Function<List<? extends Skill>, String> relativePathParameterDescriptionProvider;

        /**
         * Sets the name of the {@code read_skill_resource} tool.
         * <p>
         * Default value is {@value Skills#DEFAULT_READ_RESOURCE_TOOL_NAME}.
         */
        public Builder name(String name) {
            this.name = name;
            return this;
        }

        /**
         * Sets the description of the {@code read_skill_resource} tool.
         * <p>
         * Default value is {@value Skills#DEFAULT_READ_RESOURCE_TOOL_DESCRIPTION}.
         */
        public Builder description(String description) {
            this.description = description;
            return this;
        }

        /**
         * Sets the name of the {@code skill_name} parameter of the {@code read_skill_resource} tool.
         * <p>
         * Default value is {@value Skills#DEFAULT_READ_RESOURCE_TOOL_SKILL_NAME_PARAMETER_NAME}.
         */
        public Builder skillNameParameterName(String skillNameParameterName) {
            this.skillNameParameterName = skillNameParameterName;
            return this;
        }

        /**
         * Sets the description of the {@code skill_name} parameter of the {@code read_skill_resource} tool.
         * <p>
         * Default value is {@value Skills#DEFAULT_READ_RESOURCE_TOOL_SKILL_NAME_PARAMETER_DESCRIPTION}.
         */
        public Builder skillNameParameterDescription(String skillNameParameterDescription) {
            this.skillNameParameterDescription = skillNameParameterDescription;
            return this;
        }

        /**
         * Sets the name of the {@code relative_path} parameter of the {@code read_skill_resource} tool.
         * <p>
         * Default value is {@value Skills#DEFAULT_READ_RESOURCE_TOOL_RELATIVE_PATH_PARAMETER_NAME}.
         */
        public Builder relativePathParameterName(String relativePathParameterName) {
            this.relativePathParameterName = relativePathParameterName;
            return this;
        }

        /**
         * Sets the description of the {@code relative_path} parameter of the {@code read_skill_resource} tool.
         * <p>
         * By default, the description is generated dynamically and includes an example path
         * taken from the first available skill resource.
         * <p>
         * Takes precedence over {@link #relativePathParameterDescriptionProvider(Function)}.
         */
        public Builder relativePathParameterDescription(String relativePathParameterDescription) {
            this.relativePathParameterDescription = relativePathParameterDescription;
            return this;
        }

        /**
         * Sets a function that produces the description of the {@code relative_path} parameter
         * of the {@code read_skill_resource} tool.
         * <p>
         * The function receives the list of configured skills and returns the full parameter description.
         * This allows customizing the description template while still incorporating dynamic information
         * such as an example path derived from the available skill resources.
         * Ignored if {@link #relativePathParameterDescription(String)} is set.
         * <p>
         * Default: {@code skills -> "Relative path to the resource. For example: " + <first resource path>}
         */
        public Builder relativePathParameterDescriptionProvider(Function<List<? extends Skill>, String> relativePathParameterDescriptionProvider) {
            this.relativePathParameterDescriptionProvider = relativePathParameterDescriptionProvider;
            return this;
        }

        public ReadResourceToolConfig build() {
            return new ReadResourceToolConfig(this);
        }
    }
}
