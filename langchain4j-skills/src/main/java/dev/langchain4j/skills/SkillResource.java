package dev.langchain4j.skills;

/**
 * TODO
 */
public interface SkillResource {

    /**
     * TODO
     */
    String relativePath();

    /**
     * TODO
     */
    String content();

    static DefaultSkillResource.Builder builder() {
        return new DefaultSkillResource.Builder();
    }
}
