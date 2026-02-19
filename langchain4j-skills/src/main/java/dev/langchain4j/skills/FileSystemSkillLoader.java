package dev.langchain4j.skills;

import org.commonmark.ext.front.matter.YamlFrontMatterExtension;
import org.commonmark.ext.front.matter.YamlFrontMatterVisitor;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static dev.langchain4j.internal.Exceptions.toRuntimeException;
import static java.util.stream.Collectors.joining;
import static java.util.stream.StreamSupport.stream;

public class FileSystemSkillLoader {

    public static Skill loadSkill(Path skillDirectory) {
        Path skillFile = skillDirectory.resolve("SKILL.md");

        if (!Files.exists(skillFile)) {
            throw new IllegalArgumentException("SKILL.md not found in: " + skillDirectory);
        }

        String markdown = toRuntimeException(() -> Files.readString(skillFile));

        Map<String, List<String>> frontMatter = parseFrontMatter(markdown);
        String body = extractRawBody(markdown);

        String name = getSingle(frontMatter, "name");
        String description = getSingle(frontMatter, "description");

        List<DefaultSkillFile> files = loadFiles(skillDirectory);

        return DefaultSkill.builder()
                .name(name)
                .description(description)
                .body(body)
                .files(files)
                .build();
    }

    private static Map<String, List<String>> parseFrontMatter(String markdown) {
        Parser parser = Parser.builder()
                .extensions(List.of(YamlFrontMatterExtension.create()))
                .build();
        Node document = parser.parse(markdown);
        YamlFrontMatterVisitor visitor = new YamlFrontMatterVisitor();
        document.accept(visitor);
        return visitor.getData();
    }

    private static String extractRawBody(String markdown) {
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

    private static List<DefaultSkillFile> loadFiles(Path skillDirectory) {
        try (Stream<Path> files = Files.walk(skillDirectory)) {
            return files
                    .filter(Files::isRegularFile)
                    .filter(path -> !path.getFileName().toString().equals("SKILL.md"))
                    .map(path -> {
                        String formattedPath = stream(skillDirectory.relativize(path).spliterator(), false)
                                .map(Path::toString)
                                .collect(joining("/"));
                        String body = toRuntimeException(() -> Files.readString(path));
                        return DefaultSkillFile.builder()
                                .path(formattedPath)
                                .body(body)
                                .build();
                    })
                    .toList();
        } catch (IOException e) {
            throw new RuntimeException("Failed to read skill directory: " + skillDirectory, e);
        }
    }
}
