package dev.langchain4j.skills;

import dev.langchain4j.Experimental;
import dev.langchain4j.exception.ToolArgumentsException;
import dev.langchain4j.exception.ToolExecutionException;

import static dev.langchain4j.internal.Utils.getOrDefault;

@Experimental
public class ActivateSkillToolConfig {

    static final String DEFAULT_NAME = "activate_skill";
    static final String DEFAULT_DESCRIPTION = "Returns the full instructions for a skill. Call this before following any skill-specific steps.";
    static final String DEFAULT_PARAMETER_NAME = "skill_name";
    static final String DEFAULT_PARAMETER_DESCRIPTION = "The name of the skill to activate";

    final String name;
    final String description;
    final String parameterName;
    final String parameterDescription;
    final boolean throwToolArgumentsExceptions;

    private ActivateSkillToolConfig(Builder builder) {
        this.name = getOrDefault(builder.name, DEFAULT_NAME);
        this.description = getOrDefault(builder.description, DEFAULT_DESCRIPTION);
        this.parameterName = getOrDefault(builder.parameterName, DEFAULT_PARAMETER_NAME);
        this.parameterDescription = getOrDefault(builder.parameterDescription, DEFAULT_PARAMETER_DESCRIPTION);
        this.throwToolArgumentsExceptions = getOrDefault(builder.throwToolArgumentsExceptions, false);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private String name;
        private String description;
        private String parameterName;
        private String parameterDescription;
        private Boolean throwToolArgumentsExceptions;

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

        /**
         * Controls which exception type is thrown when tool arguments
         * are missing, invalid, or cannot be parsed.
         * <p>
         * Although all errors produced by this tool are argument-related,
         * this strategy throws {@link ToolExecutionException} by default
         * instead of {@link ToolArgumentsException}.
         * <p>
         * The reason is historical: by default, AI Services fail fast when
         * a {@link ToolArgumentsException} is thrown, whereas
         * {@link ToolExecutionException} allows the error message to be
         * returned to the LLM. For these tools, returning the error message
         * to the LLM is usually the desired behavior.
         * <p>
         * If this flag is set to {@code true}, {@link ToolArgumentsException}
         * will be thrown instead.
         *
         * @param throwToolArgumentsExceptions whether to throw {@link ToolArgumentsException}
         * @return this builder
         */
        public Builder throwToolArgumentsExceptions(Boolean throwToolArgumentsExceptions) {
            this.throwToolArgumentsExceptions = throwToolArgumentsExceptions;
            return this;
        }

        public ActivateSkillToolConfig build() {
            return new ActivateSkillToolConfig(this);
        }
    }
}
