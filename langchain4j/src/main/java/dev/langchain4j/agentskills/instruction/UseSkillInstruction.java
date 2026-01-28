package dev.langchain4j.agentskills.instruction;

import dev.langchain4j.Experimental;

import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;

/**
 * Instruction to load a skill's full content.
 * <p>
 * Format: {@code <use_skill>skill-name</use_skill>}
 *
 * @author Shrink (shunke.wjl@alibaba-inc.com)
 * @since 1.12.0
 */
@Experimental
public final class UseSkillInstruction extends AgentSkillsInstruction {

    private final String skillName;

    public UseSkillInstruction(String skillName) {
        this.skillName = ensureNotBlank(skillName, "skillName");
    }

    /**
     * Returns the name of the skill to load.
     *
     * @return the skill name
     */
    public String skillName() {
        return skillName;
    }

    @Override
    public String type() {
        return "use_skill";
    }

    @Override
    public String toString() {
        return "UseSkillInstruction { skillName = " + skillName + " }";
    }
}
