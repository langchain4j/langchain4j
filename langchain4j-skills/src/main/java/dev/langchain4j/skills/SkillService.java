package dev.langchain4j.skills;

import dev.langchain4j.service.tool.ToolProvider;

import java.util.Collection;
import java.util.List;
import java.util.function.Function;

import static dev.langchain4j.skills.SkillUtils.createSystemMessage;
import static dev.langchain4j.skills.SkillUtils.createToolProvider;
import static java.util.Arrays.asList;

public class SkillService { // TODO name: SkillRepository?

    private final ToolProvider toolProvider;
    private final String systemMessage;

    public SkillService(Builder builder) {
        this.toolProvider = createToolProvider(builder);
        this.systemMessage = createSystemMessage(builder.skills);
    }

    public ToolProvider toolProvider() {
        return toolProvider;
    }

    public String systemMessage() {
        return systemMessage;
    }

    public static SkillService from(Collection<? extends Skill> skills) {
        return builder().skills(skills).build();
    }

    public static SkillService from(Skill... skills) {
        return builder().skills(skills).build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        Collection<? extends Skill> skills;
        Boolean allowRunningShellCommands;

        // activate_skill tool
        String activateSkillToolName;
        String activateSkillToolDescription;
        String activateSkillToolParameterName;
        String activateSkillToolParameterDescription;

        // read_skill_resource tool
        String readResourceToolName;
        String readResourceToolDescription;
        String readResourceToolSkillNameParameterName;
        String readResourceToolSkillNameParameterDescription;
        String readResourceToolRelativePathParameterName;
        String readResourceToolRelativePathParameterDescription;
        Function<List<? extends Skill>, String> readResourceToolRelativePathParameterDescriptionProvider;

        // run_shell_command tool
        String runShellCommandToolName;
        String runShellCommandToolDescription;
        String runShellCommandToolCommandParameterName;
        String runShellCommandToolCommandParameterDescription;
        String runShellCommandToolSkillNameParameterName;
        String runShellCommandToolSkillNameParameterDescription;
        String runShellCommandToolTimeoutSecondsParameterName;
        String runShellCommandToolTimeoutSecondsParameterDescription;

        public Builder skills(Collection<? extends Skill> skills) {
            this.skills = skills;
            return this;
        }

        public Builder skills(Skill... skills) {
            return skills(asList(skills));
        }

        public Builder allowRunningShellCommands(Boolean allowRunningShellCommands) { // TODO name
            this.allowRunningShellCommands = allowRunningShellCommands;
            return this;
        }

        /**
         * Sets the name of the {@code activate_skill} tool.
         * <p>
         * Default value is {@value SkillUtils#DEFAULT_ACTIVATE_SKILL_TOOL_NAME}.
         */
        public Builder activateSkillToolName(String activateSkillToolName) {
            this.activateSkillToolName = activateSkillToolName;
            return this;
        }

        /**
         * Sets the description of the {@code activate_skill} tool.
         * <p>
         * Default value is {@value SkillUtils#DEFAULT_ACTIVATE_SKILL_TOOL_DESCRIPTION}.
         */
        public Builder activateSkillToolDescription(String activateSkillToolDescription) {
            this.activateSkillToolDescription = activateSkillToolDescription;
            return this;
        }

        /**
         * Sets the name of the parameter that specifies which skill to activate.
         * <p>
         * Default value is {@value SkillUtils#DEFAULT_ACTIVATE_SKILL_TOOL_PARAMETER_NAME}.
         */
        public Builder activateSkillToolParameterName(String activateSkillToolParameterName) {
            this.activateSkillToolParameterName = activateSkillToolParameterName;
            return this;
        }

        /**
         * Sets the description of the parameter that specifies which skill to activate.
         * <p>
         * Default value is {@value SkillUtils#DEFAULT_ACTIVATE_SKILL_TOOL_PARAMETER_DESCRIPTION}.
         */
        public Builder activateSkillToolParameterDescription(String activateSkillToolParameterDescription) {
            this.activateSkillToolParameterDescription = activateSkillToolParameterDescription;
            return this;
        }

        /**
         * Sets the name of the {@code read_skill_resource} tool.
         * <p>
         * Default value is {@value SkillUtils#DEFAULT_READ_RESOURCE_TOOL_NAME}.
         */
        public Builder readResourceToolName(String readResourceToolName) {
            this.readResourceToolName = readResourceToolName;
            return this;
        }

        /**
         * Sets the description of the {@code read_skill_resource} tool.
         * <p>
         * Default value is {@value SkillUtils#DEFAULT_READ_RESOURCE_TOOL_DESCRIPTION}.
         */
        public Builder readResourceToolDescription(String readResourceToolDescription) {
            this.readResourceToolDescription = readResourceToolDescription;
            return this;
        }

        /**
         * Sets the name of the {@code skill_name} parameter of the {@code read_skill_resource} tool.
         * <p>
         * Default value is {@value SkillUtils#DEFAULT_READ_RESOURCE_TOOL_SKILL_NAME_PARAMETER_NAME}.
         */
        public Builder readResourceToolSkillNameParameterName(String readResourceToolSkillNameParameterName) {
            this.readResourceToolSkillNameParameterName = readResourceToolSkillNameParameterName;
            return this;
        }

        /**
         * Sets the description of the {@code skill_name} parameter of the {@code read_skill_resource} tool.
         * <p>
         * Default value is {@value SkillUtils#DEFAULT_READ_RESOURCE_TOOL_SKILL_NAME_PARAMETER_DESCRIPTION}.
         */
        public Builder readResourceToolSkillNameParameterDescription(String readResourceToolSkillNameParameterDescription) {
            this.readResourceToolSkillNameParameterDescription = readResourceToolSkillNameParameterDescription;
            return this;
        }

        /**
         * Sets the name of the {@code relative_path} parameter of the {@code read_skill_resource} tool.
         * <p>
         * Default value is {@value SkillUtils#DEFAULT_READ_RESOURCE_TOOL_RELATIVE_PATH_PARAMETER_NAME}.
         */
        public Builder readResourceToolRelativePathParameterName(String readResourceToolRelativePathParameterName) {
            this.readResourceToolRelativePathParameterName = readResourceToolRelativePathParameterName;
            return this;
        }

        /**
         * Sets the description of the {@code relative_path} parameter of the {@code read_skill_resource} tool.
         * <p>
         * By default, the description is generated dynamically and includes an example path
         * taken from the first available skill resource.
         * <p>
         * Takes precedence over {@link #readResourceToolRelativePathParameterDescriptionProvider(Function)}.
         */
        public Builder readResourceToolRelativePathParameterDescription(String readResourceToolRelativePathParameterDescription) {
            this.readResourceToolRelativePathParameterDescription = readResourceToolRelativePathParameterDescription;
            return this;
        }

        /**
         * Sets a function that produces the description of the {@code relative_path} parameter
         * of the {@code read_skill_resource} tool.
         * <p>
         * The function receives the list of configured skills and returns the full parameter description.
         * This allows customizing the description template while still incorporating dynamic information
         * such as an example path derived from the available skill resources.
         * Ignored if {@link #readResourceToolRelativePathParameterDescription(String)} is set.
         * <p>
         * Default: {@code skills -> "Relative path to the resource. For example: " + <first resource path>}
         */
        public Builder readResourceToolRelativePathParameterDescriptionProvider(Function<List<? extends Skill>, String> readResourceToolRelativePathParameterDescriptionProvider) {
            this.readResourceToolRelativePathParameterDescriptionProvider = readResourceToolRelativePathParameterDescriptionProvider;
            return this;
        }

        /**
         * Sets the name of the {@code run_shell_command} tool.
         * <p>
         * Default value is {@value SkillUtils#DEFAULT_RUN_SHELL_COMMAND_TOOL_NAME}.
         */
        public Builder runShellCommandToolName(String runShellCommandToolName) {
            this.runShellCommandToolName = runShellCommandToolName;
            return this;
        }

        /**
         * Sets the description of the {@code run_shell_command} tool.
         * <p>
         * By default, the description is generated dynamically and includes the current OS name.
         */
        public Builder runShellCommandToolDescription(String runShellCommandToolDescription) {
            this.runShellCommandToolDescription = runShellCommandToolDescription;
            return this;
        }

        /**
         * Sets the name of the {@code command} parameter of the {@code run_shell_command} tool.
         * <p>
         * Default value is {@value SkillUtils#DEFAULT_RUN_SHELL_COMMAND_TOOL_COMMAND_PARAMETER_NAME}.
         */
        public Builder runShellCommandToolCommandParameterName(String runShellCommandToolCommandParameterName) {
            this.runShellCommandToolCommandParameterName = runShellCommandToolCommandParameterName;
            return this;
        }

        /**
         * Sets the description of the {@code command} parameter of the {@code run_shell_command} tool.
         * <p>
         * Default value is {@value SkillUtils#DEFAULT_RUN_SHELL_COMMAND_TOOL_COMMAND_PARAMETER_DESCRIPTION}.
         */
        public Builder runShellCommandToolCommandParameterDescription(String runShellCommandToolCommandParameterDescription) {
            this.runShellCommandToolCommandParameterDescription = runShellCommandToolCommandParameterDescription;
            return this;
        }

        /**
         * Sets the name of the {@code skill_name} parameter of the {@code run_shell_command} tool.
         * <p>
         * Default value is {@value SkillUtils#DEFAULT_RUN_SHELL_COMMAND_TOOL_SKILL_NAME_PARAMETER_NAME}.
         */
        public Builder runShellCommandToolSkillNameParameterName(String runShellCommandToolSkillNameParameterName) {
            this.runShellCommandToolSkillNameParameterName = runShellCommandToolSkillNameParameterName;
            return this;
        }

        /**
         * Sets the description of the {@code skill_name} parameter of the {@code run_shell_command} tool.
         * <p>
         * Default value is {@value SkillUtils#DEFAULT_RUN_SHELL_COMMAND_TOOL_SKILL_NAME_PARAMETER_DESCRIPTION}.
         */
        public Builder runShellCommandToolSkillNameParameterDescription(String runShellCommandToolSkillNameParameterDescription) {
            this.runShellCommandToolSkillNameParameterDescription = runShellCommandToolSkillNameParameterDescription;
            return this;
        }

        /**
         * Sets the name of the {@code timeout_seconds} parameter of the {@code run_shell_command} tool.
         * <p>
         * Default value is {@value SkillUtils#DEFAULT_RUN_SHELL_COMMAND_TOOL_TIMEOUT_SECONDS_PARAMETER_NAME}.
         */
        public Builder runShellCommandToolTimeoutSecondsParameterName(String runShellCommandToolTimeoutSecondsParameterName) {
            this.runShellCommandToolTimeoutSecondsParameterName = runShellCommandToolTimeoutSecondsParameterName;
            return this;
        }

        /**
         * Sets the description of the {@code timeout_seconds} parameter of the {@code run_shell_command} tool.
         * <p>
         * Default value is {@value SkillUtils#DEFAULT_RUN_SHELL_COMMAND_TOOL_TIMEOUT_SECONDS_PARAMETER_DESCRIPTION}.
         */
        public Builder runShellCommandToolTimeoutSecondsParameterDescription(String runShellCommandToolTimeoutSecondsParameterDescription) {
            this.runShellCommandToolTimeoutSecondsParameterDescription = runShellCommandToolTimeoutSecondsParameterDescription;
            return this;
        }

        public SkillService build() {
            return new SkillService(this);
        }
    }
}
