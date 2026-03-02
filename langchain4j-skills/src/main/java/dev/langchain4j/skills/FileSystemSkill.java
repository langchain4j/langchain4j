package dev.langchain4j.skills;

import java.nio.file.Path;

/**
 * A {@link Skill} backed by the file system. TODO
 * Provides a {@link #basePath()} that is used as the working directory
 * when the LLM runs shell commands via the {@code run_shell_command} tool.
 */
public interface FileSystemSkill extends Skill {

    /**
     * Returns the base directory path of this skill.
     * Used as the working directory when the LLM runs shell commands for this skill.
     */
    Path basePath();

    static DefaultFileSystemSkill.Builder builder() {
        return new DefaultFileSystemSkill.Builder();
    }
}
