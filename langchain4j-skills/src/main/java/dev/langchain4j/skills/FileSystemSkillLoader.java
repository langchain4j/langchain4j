package dev.langchain4j.skills;

import org.commonmark.ext.front.matter.YamlFrontMatterExtension;
import org.commonmark.ext.front.matter.YamlFrontMatterVisitor;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

import static dev.langchain4j.internal.Exceptions.unchecked;
import static dev.langchain4j.internal.Utils.isNullOrBlank;
import static java.util.stream.Collectors.joining;
import static java.util.stream.StreamSupport.stream;

/**
 * Loads skills from the file system.
 * <p>
 * Each skill must reside in its own directory containing a {@code SKILL.md} file.
 * The file must have a YAML front matter block that declares the skill's {@code name}
 * and {@code description}. The body of the file (below the front matter) becomes the
 * skill's {@link Skill#content() content}.
 * <p>
 * Any additional files in the skill directory are loaded as {@link SkillResource}s,
 * with the exception of {@code SKILL.md} itself and any files under a {@code scripts/}
 * subdirectory. Empty files are silently skipped.
 * <p>
 * All content (skill instructions and resources) is read into memory eagerly at load time.
 * Once loaded, no further file system access occurs at inference time: the LLM retrieves
 * resources via the {@code read_skill_resource} tool, which serves the pre-loaded in-memory
 * content.
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
 */
public class FileSystemSkillLoader {

    private static final Parser PARSER = Parser.builder()
            .extensions(List.of(YamlFrontMatterExtension.create()))
            .build();

    /**
     * Loads all skills found in immediate subdirectories of the given directory.
     * A subdirectory is treated as a skill only if it contains a {@code SKILL.md} file;
     * subdirectories without one are silently skipped.
     *
     * @param directory the directory whose immediate subdirectories are scanned for skills
     * @return the list of loaded skills, in filesystem iteration order
     * @throws RuntimeException if the directory cannot be listed or a skill fails to load
     */
    public static List<Skill> loadSkills(Path directory) {
        try (Stream<Path> entries = Files.list(directory)) {
            return entries
                    .filter(Files::isDirectory)
                    .filter(dir -> Files.exists(dir.resolve("SKILL.md")))
                    .map(FileSystemSkillLoader::loadSkill)
                    .toList();
        } catch (Exception e) {
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
     * @throws IllegalArgumentException if {@code SKILL.md} is not found in the directory
     * @throws RuntimeException if the file cannot be read or resources cannot be loaded
     */
    public static Skill loadSkill(Path skillDirectory) {
        Path skillFile = skillDirectory.resolve("SKILL.md");

        if (!Files.exists(skillFile)) {
            throw new IllegalArgumentException("SKILL.md not found in " + skillDirectory);
        }

        String markdown = unchecked(() -> Files.readString(skillFile));

        Map<String, List<String>> frontMatter = parseFrontMatter(markdown);
        String content = extractContent(markdown);

        String name = getSingle(frontMatter, "name");
        String description = getSingle(frontMatter, "description");

        List<DefaultSkillResource> resources = loadResources(skillDirectory);

        return DefaultFileSystemSkill.builder()
                .name(name)
                .description(description)
                .content(content)
                .resources(resources)
                .basePath(skillDirectory)
                .build();
    }

    private static Map<String, List<String>> parseFrontMatter(String markdown) {
        Node document = PARSER.parse(markdown);
        YamlFrontMatterVisitor visitor = new YamlFrontMatterVisitor();
        document.accept(visitor);
        return visitor.getData();
    }

    private static String extractContent(String markdown) {
        if (markdown.startsWith("---")) {
            int secondDelimiter = markdown.indexOf("\n---", 3);
            if (secondDelimiter != -1) {
                return markdown.substring(secondDelimiter + 4).trim();
            }
        }
        return markdown;
    }

    private static String getSingle(Map<String, List<String>> map, String key) {
        return map.getOrDefault(key, List.of())
                .stream()
                .findFirst()
                .orElse(null);
    }

    private static List<DefaultSkillResource> loadResources(Path skillDirectory) {
        try (Stream<Path> files = Files.walk(skillDirectory)) {
            return files
                    .filter(Files::isRegularFile)
                    .filter(path -> !path.getFileName().toString().equals("SKILL.md"))
                    .filter(path -> !skillDirectory.relativize(path).startsWith("scripts"))
                    .map(path -> {
                        try {
                            String content = unchecked(() -> Files.readString(path));
                            if (isNullOrBlank(content)) {
                                return null;
                            }
                            String relativePath = stream(skillDirectory.relativize(path).spliterator(), false)
                                    .map(Path::toString)
                                    .collect(joining("/"));
                            return SkillResource.builder()
                                    .relativePath(relativePath)
                                    .content(content)
                                    .build();
                        } catch (Exception e) {
                            throw new RuntimeException("Failed to load skill resource from " + path, e);
                        }
                    })
                    .filter(Objects::nonNull)
                    .toList();
        } catch (Exception e) {
            throw new RuntimeException("Failed to load skill resources from " + skillDirectory, e);
        }
    }
}
