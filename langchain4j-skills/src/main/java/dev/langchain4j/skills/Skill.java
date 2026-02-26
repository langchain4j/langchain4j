package dev.langchain4j.skills;

import java.util.List;

/**
 * TODO
 */
public interface Skill {

    /**
     * TODO
     */
    String name();

    /**
     * TODO
     */
    String description();

    /**
     * TODO
     */
    String content();

    /**
     * TODO
     */
    List<SkillResource> resources(); // TODO type

    static DefaultSkill.Builder builder() {
        return new DefaultSkill.Builder();
    }
}
