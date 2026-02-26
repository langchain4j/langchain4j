package dev.langchain4j.skills;

import java.nio.file.Path;

/**
 * TODO
 */
public interface FileSystemSkill extends Skill {

    /**
     * TODO
     */
    Path basePath();

    static DefaultFileSystemSkill.Builder builder() {
        return new DefaultFileSystemSkill.Builder();
    }
}
