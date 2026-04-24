package dev.langchain4j.skills;

import dev.langchain4j.Experimental;

import java.nio.file.Path;

/**
 * A {@link Skill} backed by the file system.
 * Extends {@link Skill} with a {@link #basePath()} pointing to the directory
 * that contains the skill's {@code SKILL.md} and any associated resource files.
 */
@Experimental
public interface FileSystemSkill extends Skill {

    /**
     * Returns the base directory of this skill on the file system.
     * This directory is expected to contain a {@code SKILL.md} file
     * and optionally additional resource files.
     */
    Path basePath();

    static DefaultFileSystemSkill.Builder builder() {
        return new DefaultFileSystemSkill.Builder();
    }
}
