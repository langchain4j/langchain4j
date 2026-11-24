package dev.langchain4j.skills;

import static dev.langchain4j.internal.Utils.isNullOrBlank;
import static java.util.stream.Collectors.joining;
import static java.util.stream.StreamSupport.stream;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.MalformedInputException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;
import org.commonmark.ext.front.matter.YamlFrontMatterExtension;
import org.commonmark.ext.front.matter.YamlFrontMatterVisitor;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.slf4j.Logger;

/**
 * Shared utilities for skill loaders ({@link FileSystemSkillLoader}, {@link ClassPathSkillLoader}).
 */
class SkillLoaderCommon {

    static final Parser PARSER = Parser.builder()
            .extensions(List.of(YamlFrontMatterExtension.create()))
            .build();

    private SkillLoaderCommon() {}

    static Map<String, List<String>> parseFrontMatter(String markdown) {
        Node document = PARSER.parse(markdown);
        YamlFrontMatterVisitor visitor = new YamlFrontMatterVisitor();
        document.accept(visitor);
        return visitor.getData();
    }

    static String extractContent(String markdown) {
        if (markdown.startsWith("---")) {
            int secondDelimiter = markdown.indexOf("\n---", 3);
            if (secondDelimiter != -1) {
                return markdown.substring(secondDelimiter + 4).trim();
            }
        }
        return markdown;
    }

    static String getSingle(Map<String, List<String>> map, String key) {
        return map.getOrDefault(key, List.of()).stream().findFirst().orElse(null);
    }

    static String readSkillMarkdown(Path realSkillDirectory) throws IOException {
        return readFileInsideSkillDirectory(realSkillDirectory.resolve("SKILL.md"), realSkillDirectory);
    }

    static List<DefaultSkillResource> loadResources(Path realSkillDirectory, Logger log) {
        try (Stream<Path> files = Files.walk(realSkillDirectory)) {
            return files.filter(Files::isRegularFile)
                    .filter(path -> !path.getFileName().toString().equals("SKILL.md"))
                    .filter(path -> !realSkillDirectory.relativize(path).startsWith("scripts"))
                    .map(path -> loadResource(path, realSkillDirectory, log))
                    .filter(Objects::nonNull)
                    .toList();
        } catch (IOException | UncheckedIOException e) {
            throw new RuntimeException("Failed to load skill resources from " + realSkillDirectory, e);
        }
    }

    private static DefaultSkillResource loadResource(Path path, Path realSkillDirectory, Logger log) {
        try {
            String content = readFileInsideSkillDirectory(path, realSkillDirectory);
            if (isNullOrBlank(content)) {
                return null;
            }
            String relativePath = stream(realSkillDirectory.relativize(path).spliterator(), false)
                    .map(Path::toString)
                    .collect(joining("/"));
            return SkillResource.builder()
                    .relativePath(relativePath)
                    .content(content)
                    .build();
        } catch (MalformedInputException e) {
            log.warn("Skipping binary file that cannot be read as UTF-8 text: {}", path);
            return null;
        } catch (IOException e) {
            throw new RuntimeException("Failed to load skill resource from " + path, e);
        }
    }

    private static String readFileInsideSkillDirectory(Path file, Path realSkillDirectory) throws IOException {
        Path realFile = file.toRealPath();
        if (!realFile.startsWith(realSkillDirectory)) {
            throw new IllegalArgumentException(("'%s' is a symbolic link to a file outside of the skill directory '%s'. "
                            + "Copy the file into the skill directory instead.")
                    .formatted(file, realSkillDirectory));
        }
        return Files.readString(realFile);
    }
}
