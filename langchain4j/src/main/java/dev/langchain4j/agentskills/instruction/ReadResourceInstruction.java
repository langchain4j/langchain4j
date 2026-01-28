package dev.langchain4j.agentskills.instruction;

import dev.langchain4j.Experimental;

import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;

/**
 * Instruction to read a resource file from a skill.
 * <p>
 * Format: {@code <read_resource skill="skill-name">path/to/resource</read_resource>}
 *
 * @author Shrink (shunke.wjl@alibaba-inc.com)
 * @since 1.12.0
 */
@Experimental
public final class ReadResourceInstruction extends AgentSkillsInstruction {

    private final String skillName;
    private final String resourcePath;

    public ReadResourceInstruction(String skillName, String resourcePath) {
        this.skillName = ensureNotBlank(skillName, "skillName");
        this.resourcePath = ensureNotBlank(resourcePath, "resourcePath");
    }

    /**
     * Returns the name of the skill containing the resource.
     *
     * @return the skill name
     */
    public String skillName() {
        return skillName;
    }

    /**
     * Returns the path to the resource relative to the skill directory.
     *
     * @return the resource path
     */
    public String resourcePath() {
        return resourcePath;
    }

    @Override
    public String type() {
        return "read_resource";
    }

    @Override
    public String toString() {
        return "ReadResourceInstruction { skillName = " + skillName + ", resourcePath = " + resourcePath + " }";
    }
}
