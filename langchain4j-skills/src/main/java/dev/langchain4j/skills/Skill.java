package dev.langchain4j.skills;

import dev.langchain4j.Experimental;

import java.util.List;

/**
 * Represents a skill that can be used by an LLM.
 * <p>
 * A skill has a mandatory {@link #name()} and {@link #description()} that the LLM always sees.
 * The LLM can read the full {@link #content()} and any {@link #resources()} on demand.
 * <p>
 * See more details <a href="https://agentskills.io">here</a>.
 */
@Experimental
public interface Skill {

    /**
     * Returns the unique name of this skill.
     * The LLM uses this name to identify the skill when selecting from the available skills.
     */
    String name();

    /**
     * Returns a short description of what this skill does.
     * Shown to the LLM so it can decide which skill is relevant for the current request.
     */
    String description();

    /**
     * Returns the full instructions of this skill (e.g. the contents of a {@code SKILL.md} file).
     */
    String content();

    /**
     * Returns the list of additional resources associated with this skill (e.g. references, assets, templates, etc.).
     */
    List<SkillResource> resources();

    static DefaultSkill.Builder builder() {
        return new DefaultSkill.Builder();
    }
}
