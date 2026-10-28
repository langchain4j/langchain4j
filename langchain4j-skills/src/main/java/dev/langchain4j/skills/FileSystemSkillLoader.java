package dev.langchain4j.skills;

import static dev.langchain4j.internal.Exceptions.unchecked;

import dev.langchain4j.Experimental;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Loads skills from the file system.
 * <p>
 * Each skill must reside in its own directory containing a {@code SKILL.md} file.
 * The file must have a YAML front matter block that declares the skill's {@code name}
 * and {@code description}. The body of the file (below the front matter) becomes the
 * skill's {@link Skill#content() content}.
 * <p>
 * Example {@code SKILL.md} structure:
 * <pre>{@code
 * ---
 * name: my-skill
 * description: Does something useful
 * ---
 *
 * Detailed instructions for the LLM go here.
 * }</pre>
 * <p>
 * Any additional files in the skill directory are loaded as {@link SkillResource}s,
 * except {@code SKILL.md} itself and any files under a {@code scripts/} subdirectory.
 * Empty files are silently skipped.
 * <p>
 * Symbolic links inside a skill directory are followed only when they point to a file inside
 * the same skill directory. Symbolic links to directories inside a skill are not followed.
 * If {@code SKILL.md} or a resource is a symbolic link to a file outside of the skill directory,
 * loading fails with an {@link IllegalArgumentException}.
 * The skill directory itself may be a symbolic link; in that case, the files of the directory
 * it points to are loaded.
 */
@Experimental
public class FileSystemSkillLoader {

    private static final Logger log = LoggerFactory.getLogger(FileSystemSkillLoader.class);

    /**
     * Loads all skills found in immediate subdirectories of the given directory.
     * A subdirectory is treated as a skill only if it contains a {@code SKILL.md} file;
     * subdirectories without one are silently skipped.
     *
     * @param directory the directory whose immediate subdirectories are scanned for skills
     * @return the list of loaded skills, in filesystem iteration order
     * @throws IllegalArgumentException if a skill's {@code SKILL.md} or resource is a symbolic link to a file
     *                                  outside of its skill directory
     * @throws RuntimeException         if the directory cannot be listed or a skill fails to load
     */
    public static List<FileSystemSkill> loadSkills(Path directory) {
        try (Stream<Path> entries = Files.list(directory)) {
            return entries.filter(Files::isDirectory)
                    .filter(dir -> Files.exists(dir.resolve("SKILL.md")))
                    .map(FileSystemSkillLoader::loadSkill)
                    .toList();
        } catch (IOException | UncheckedIOException e) {
            throw new RuntimeException("Failed to load skills from " + directory, e);
        }
    }

    /**
     * Loads a single skill from the given directory.
     * The directory must contain a {@code SKILL.md} file with a YAML front matter block
     * declaring the skill's {@code name} and {@code description}.
     *
     * @param skillDirectory the directory to load the skill from
     * @return the loaded skill
     * @throws IllegalArgumentException if {@code SKILL.md} is not found in the directory,
     *                                  or if {@code SKILL.md} or a resource is a symbolic link to a file
     *                                  outside of the directory
     * @throws RuntimeException         if the file cannot be read or resources cannot be loaded
     */
    public static FileSystemSkill loadSkill(Path skillDirectory) {
        Path skillFile = skillDirectory.resolve("SKILL.md");

        if (!Files.exists(skillFile)) {
            throw new IllegalArgumentException("SKILL.md not found in " + skillDirectory);
        }

        Path realSkillDirectory = unchecked(() -> skillDirectory.toRealPath());
        String markdown = unchecked(() -> SkillLoaderCommon.readSkillMarkdown(realSkillDirectory));

        Map<String, List<String>> frontMatter = SkillLoaderCommon.parseFrontMatter(markdown);
        String content = SkillLoaderCommon.extractContent(markdown);

        String name = SkillLoaderCommon.getSingle(frontMatter, "name");
        String description = SkillLoaderCommon.getSingle(frontMatter, "description");

        List<DefaultSkillResource> resources = SkillLoaderCommon.loadResources(realSkillDirectory, log);

        return DefaultFileSystemSkill.builder()
                .name(name)
                .description(description)
                .content(content)
                .resources(resources)
                .basePath(skillDirectory)
                .build();
    }
}
