package dev.langchain4j.skills;

public class ActivateSkillToolConfig {

    final String name;
    final String description;
    final String parameterName;
    final String parameterDescription;

    private ActivateSkillToolConfig(Builder builder) {
        this.name = builder.name;
        this.description = builder.description;
        this.parameterName = builder.parameterName;
        this.parameterDescription = builder.parameterDescription;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private String name;
        private String description;
        private String parameterName;
        private String parameterDescription;

        /**
         * Sets the name of the {@code activate_skill} tool.
         * <p>
         * Default value is {@value Skills#DEFAULT_ACTIVATE_SKILL_TOOL_NAME}.
         */
        public Builder name(String name) {
            this.name = name;
            return this;
        }

        /**
         * Sets the description of the {@code activate_skill} tool.
         * <p>
         * Default value is {@value Skills#DEFAULT_ACTIVATE_SKILL_TOOL_DESCRIPTION}.
         */
        public Builder description(String description) {
            this.description = description;
            return this;
        }

        /**
         * Sets the name of the parameter that specifies which skill to activate.
         * <p>
         * Default value is {@value Skills#DEFAULT_ACTIVATE_SKILL_TOOL_PARAMETER_NAME}.
         */
        public Builder parameterName(String parameterName) {
            this.parameterName = parameterName;
            return this;
        }

        /**
         * Sets the description of the parameter that specifies which skill to activate.
         * <p>
         * Default value is {@value Skills#DEFAULT_ACTIVATE_SKILL_TOOL_PARAMETER_DESCRIPTION}.
         */
        public Builder parameterDescription(String parameterDescription) {
            this.parameterDescription = parameterDescription;
            return this;
        }

        public ActivateSkillToolConfig build() {
            return new ActivateSkillToolConfig(this);
        }
    }
}
