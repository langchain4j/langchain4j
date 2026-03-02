package dev.langchain4j.skills;

import java.util.List;

/**
 * Represents a skill that an LLM can activate and use.
 * A skill bundles instructions (its {@link #content()}),
 * optional {@link #resources()}, and metadata ({@link #name()} and {@link #description()})
 * into a self-contained unit that can be provided to the LLM via the {@code activate_skill} tool.
 * </p>
 * See more details <a href="http://agentskills.io">here</a>.
 */
public interface Skill {

    /**
     * Returns the unique name of this skill.
     * Used to identify it when activating and listing available skills.
     */
    String name();

    /**
     * Returns a short description of what this skill does.
     * Shown to the LLM so it can decide which skill to activate.
     */
    String description();

    /**
     * Returns the full instructions of this skill (e.g. the contents of a {@code SKILL.md} file).
     * Returned to the LLM when the skill is activated via the {@code activate_skill} tool.
     */
    String content();

    /**
     * Returns the list of additional resources associated with this skill
     * (e.g. references, assets, templates, etc.).
     * The LLM can read them by calling the {@code read_skill_resource} tool once the skill is activated.
     */
    List<SkillResource> resources();

    static DefaultSkill.Builder builder() {
        return new DefaultSkill.Builder();
    }
}
