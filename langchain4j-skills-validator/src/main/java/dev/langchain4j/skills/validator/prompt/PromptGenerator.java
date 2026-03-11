package dev.langchain4j.skills.validator.prompt;

import dev.langchain4j.skills.validator.error.SkillError;
import dev.langchain4j.skills.validator.model.SkillProperties;
import dev.langchain4j.skills.validator.parser.FrontmatterParser;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Generate &lt;available_skills&gt; XML prompt block for agent system prompts.
 */
public class PromptGenerator {
    private final FrontmatterParser parser = new FrontmatterParser();

    /**
     * Generate the &lt;available_skills&gt; XML block for inclusion in agent prompts.
     *
     * <p>This XML format is what Anthropic uses and recommends for Claude models. Skill Clients
     * may format skill information differently to suit their models or preferences.
     *
     * @param skillDirs List of paths to skill directories
     * @return XML string with &lt;available_skills&gt; block containing each skill's name,
     *     description, and location.
     * @throws SkillError If any skill cannot be processed
     *     <p>Example output:
     *     <pre>
     * &lt;available_skills&gt;
     * &lt;skill&gt;
     * &lt;name&gt;pdf-reader&lt;/name&gt;
     * &lt;description&gt;Read and extract text from PDF files&lt;/description&gt;
     * &lt;location&gt;/path/to/pdf-reader/SKILL.md&lt;/location&gt;
     * &lt;/skill&gt;
     * &lt;/available_skills&gt;
     *     </pre>
     */
    public String toPrompt(List<Path> skillDirs) throws SkillError {
        Objects.requireNonNull(skillDirs, "skillDirs cannot be null");

        if (skillDirs.isEmpty()) {
            return "<available_skills>\n</available_skills>";
        }

        List<String> lines = new ArrayList<>();
        lines.add("<available_skills>");

        for (Path skillDir : skillDirs) {
            Path resolvedDir = skillDir.toAbsolutePath();
            SkillProperties props = parser.readProperties(resolvedDir);

            lines.add("<skill>");
            lines.add("<name>");
            lines.add(escapeXml(props.getName()));
            lines.add("</name>");
            lines.add("<description>");
            lines.add(escapeXml(props.getDescription()));
            lines.add("</description>");

            Path skillMd = parser.findSkillMd(resolvedDir);
            lines.add("<location>");
            lines.add(skillMd.toString());
            lines.add("</location>");

            lines.add("</skill>");
        }

        lines.add("</available_skills>");

        return String.join("\n", lines);
    }

    /**
     * Escape special XML characters.
     *
     * @param text Text to escape
     * @return XML-escaped text
     */
    private String escapeXml(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
