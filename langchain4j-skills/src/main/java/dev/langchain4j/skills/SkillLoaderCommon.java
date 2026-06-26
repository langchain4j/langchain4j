package dev.langchain4j.skills;

import java.util.List;
import java.util.Map;
import org.commonmark.ext.front.matter.YamlFrontMatterExtension;
import org.commonmark.ext.front.matter.YamlFrontMatterVisitor;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;

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
}
