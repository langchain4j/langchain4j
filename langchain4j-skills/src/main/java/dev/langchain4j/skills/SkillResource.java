package dev.langchain4j.skills;

/**
 * An additional resource associated with a {@link Skill}, such as a reference file or asset,
 * that the LLM can read via the {@code read_skill_resource} tool.
 */
public interface SkillResource {

    /**
     * Returns the relative path of this resource within the skill's directory.
     * Used to identify the resource when reading it via the {@code read_skill_resource} tool.
     */
    String relativePath();

    /**
     * Returns the content of this resource.
     */
    String content();

    static DefaultSkillResource.Builder builder() {
        return new DefaultSkillResource.Builder();
    }
}
