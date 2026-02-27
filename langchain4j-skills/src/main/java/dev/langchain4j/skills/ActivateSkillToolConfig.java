package dev.langchain4j.skills;

import static dev.langchain4j.internal.Utils.getOrDefault;

public class ActivateSkillToolConfig {

    // TODO others
    static final String DEFAULT_NAME = "activate_skill";
    static final String DEFAULT_DESCRIPTION = "Activates a skill by its name";
    static final String DEFAULT_PARAMETER_NAME = "skill_name";
    static final String DEFAULT_PARAMETER_DESCRIPTION = "The name of the skill to activate";

    final String name;
    final String description;
    final String parameterName;
    final String parameterDescription;

    private ActivateSkillToolConfig(Builder builder) {
        this.name = getOrDefault(builder.name, DEFAULT_NAME);
        this.description = getOrDefault(builder.description, DEFAULT_DESCRIPTION);
        this.parameterName = getOrDefault(builder.parameterName, DEFAULT_PARAMETER_NAME);
        this.parameterDescription = getOrDefault(builder.parameterDescription, DEFAULT_PARAMETER_DESCRIPTION);
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
         * Default value is {@value ActivateSkillToolConfig#DEFAULT_NAME}.
         */
        public Builder name(String name) {
            this.name = name;
            return this;
        }

        /**
         * Sets the description of the {@code activate_skill} tool.
         * <p>
         * Default value is {@value ActivateSkillToolConfig#DEFAULT_DESCRIPTION}.
         */
        public Builder description(String description) {
            this.description = description;
            return this;
        }

        /**
         * Sets the name of the parameter that specifies which skill to activate.
         * <p>
         * Default value is {@value ActivateSkillToolConfig#DEFAULT_PARAMETER_NAME}.
         */
        public Builder parameterName(String parameterName) {
            this.parameterName = parameterName;
            return this;
        }

        /**
         * Sets the description of the parameter that specifies which skill to activate.
         * <p>
         * Default value is {@value ActivateSkillToolConfig#DEFAULT_PARAMETER_DESCRIPTION}.
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
