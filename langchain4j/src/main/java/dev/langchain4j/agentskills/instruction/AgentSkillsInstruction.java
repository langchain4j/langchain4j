package dev.langchain4j.agentskills.instruction;

import dev.langchain4j.Experimental;

/**
 * Base class for Agent Skills instructions parsed from LLM responses.
 * <p>
 * Instructions are XML-like tags that the LLM uses to request skill operations:
 * <ul>
 *   <li>{@code <use_skill>name</use_skill>} - Load a skill's full content</li>
 *   <li>{@code <execute_script skill="name">command</execute_script>} - Execute a script</li>
 *   <li>{@code <read_resource skill="name">path</read_resource>} - Read a resource file</li>
 * </ul>
 *
 * @author Shrink (shunke.wjl@alibaba-inc.com)
 * @since 1.12.0
 */
@Experimental
public abstract sealed class AgentSkillsInstruction
        permits UseSkillInstruction, ExecuteScriptInstruction, ReadResourceInstruction {

    /**
     * Returns the type of this instruction.
     *
     * @return the instruction type
     */
    public abstract String type();
}
