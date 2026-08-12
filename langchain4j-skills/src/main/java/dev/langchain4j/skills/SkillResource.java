package dev.langchain4j.skills;

import dev.langchain4j.Experimental;

/**
 * An additional resource associated with a {@link Skill},
 * such as a reference file, asset, or template that the LLM can read on demand.
 */
@Experimental
public interface SkillResource {

    /**
     * Returns the relative path of this resource within the skill's directory.
     * Used to identify the resource when the LLM requests it.
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
