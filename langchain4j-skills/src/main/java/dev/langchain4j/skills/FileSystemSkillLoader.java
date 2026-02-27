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

import static dev.langchain4j.internal.Exceptions.toRuntimeException;
import static dev.langchain4j.internal.Utils.isNullOrBlank;
import static java.util.stream.Collectors.joining;
import static java.util.stream.StreamSupport.stream;

public class FileSystemSkillLoader {

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

    public static Skill loadSkill(Path skillDirectory) {
        Path skillFile = skillDirectory.resolve("SKILL.md");

        if (!Files.exists(skillFile)) {
            throw new IllegalArgumentException("SKILL.md not found in " + skillDirectory);
        }

        String markdown = toRuntimeException(() -> Files.readString(skillFile));

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
        Parser parser = Parser.builder() // TODO reuse for multiple skills
                .extensions(List.of(YamlFrontMatterExtension.create()))
                .build();
        Node document = parser.parse(markdown);
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
                            String content = toRuntimeException(() -> Files.readString(path));
                            if (isNullOrBlank(content)) {
                                return null; // TODO test
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
