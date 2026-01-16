package dev.langchain4j.agentskills.instruction;

import dev.langchain4j.Experimental;

import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;

/**
 * Instruction to execute a script from a skill.
 * <p>
 * Format: {@code <execute_script skill="skill-name">command args</execute_script>}
 *
 * @author Shrink (shunke.wjl@alibaba-inc.com)
 * @since 1.12.0
 */
@Experimental
public final class ExecuteScriptInstruction extends AgentSkillsInstruction {

    private final String skillName;
    private final String command;

    public ExecuteScriptInstruction(String skillName, String command) {
        this.skillName = ensureNotBlank(skillName, "skillName");
        this.command = ensureNotBlank(command, "command");
    }

    /**
     * Returns the name of the skill containing the script.
     *
     * @return the skill name
     */
    public String skillName() {
        return skillName;
    }

    /**
     * Returns the command to execute.
     *
     * @return the command
     */
    public String command() {
        return command;
    }

    @Override
    public String type() {
        return "execute_script";
    }

    @Override
    public String toString() {
        return "ExecuteScriptInstruction { skillName = " + skillName + ", command = " + command + " }";
    }
}
