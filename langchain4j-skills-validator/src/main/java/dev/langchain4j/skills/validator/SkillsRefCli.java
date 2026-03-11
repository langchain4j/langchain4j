package dev.langchain4j.skills.validator;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.langchain4j.skills.validator.error.SkillError;
import dev.langchain4j.skills.validator.model.SkillProperties;
import dev.langchain4j.skills.validator.parser.FrontmatterParser;
import dev.langchain4j.skills.validator.prompt.PromptGenerator;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Main CLI entry point for skills-ref.
 */
public class SkillsRefCli {
    private static final String VERSION = "1.0-SNAPSHOT";
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private static final SkillValidator validator = new SkillValidator();
    private static final FrontmatterParser parser = new FrontmatterParser();
    private static final PromptGenerator generator = new PromptGenerator();

    public static void main(String[] args) {
        if (args.length == 0) {
            printHelp();
            System.exit(0);
        }

        String command = args[0];

        try {
            switch (command) {
                case "validate":
                    validateCommand(args);
                    break;
                case "read-properties":
                    readPropertiesCommand(args);
                    break;
                case "to-prompt":
                    toPromptCommand(args);
                    break;
                case "--version":
                case "-v":
                    System.out.println(VERSION);
                    break;
                case "--help":
                case "-h":
                    printHelp();
                    break;
                default:
                    System.err.println("Unknown command: " + command);
                    printHelp();
                    System.exit(1);
            }
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            System.exit(1);
        }
    }

    private static void validateCommand(String[] args) throws SkillError {
        if (args.length < 2) {
            System.err.println("Usage: skills-ref validate <skill-path>");
            System.exit(1);
        }

        Path skillPath = Paths.get(args[1]).toAbsolutePath();

        // If a SKILL.md file is provided, use its parent directory
        if (Files.isRegularFile(skillPath) && skillPath.getFileName().toString().equalsIgnoreCase("skill.md")) {
            skillPath = skillPath.getParent();
        }

        List<String> errors = validator.validate(skillPath);

        if (!errors.isEmpty()) {
            System.err.println("Validation failed for " + skillPath + ":");
            for (String error : errors) {
                System.err.println("  - " + error);
            }
            System.exit(1);
        } else {
            System.out.println("Valid skill: " + skillPath);
        }
    }

    private static void readPropertiesCommand(String[] args) throws SkillError {
        if (args.length < 2) {
            System.err.println("Usage: skills-ref read-properties <skill-path>");
            System.exit(1);
        }

        Path skillPath = Paths.get(args[1]).toAbsolutePath();

        // If a SKILL.md file is provided, use its parent directory
        if (Files.isRegularFile(skillPath) && skillPath.getFileName().toString().equalsIgnoreCase("skill.md")) {
            skillPath = skillPath.getParent();
        }

        SkillProperties props = parser.readProperties(skillPath);
        String json = gson.toJson(props.toMap());
        System.out.println(json);
    }

    private static void toPromptCommand(String[] args) throws SkillError {
        if (args.length < 2) {
            System.err.println("Usage: skills-ref to-prompt <skill-path> [skill-path...]");
            System.exit(1);
        }

        List<Path> skillPaths = new ArrayList<>();
        for (int i = 1; i < args.length; i++) {
            Path skillPath = Paths.get(args[i]).toAbsolutePath();

            // If a SKILL.md file is provided, use its parent directory
            if (Files.isRegularFile(skillPath)
                    && skillPath.getFileName().toString().equalsIgnoreCase("skill.md")) {
                skillPath = skillPath.getParent();
            }

            skillPaths.add(skillPath);
        }

        String output = generator.toPrompt(skillPaths);
        System.out.println(output);
    }

    private static void printHelp() {
        System.out.println("Usage: skills-ref [command] [options]");
        System.out.println();
        System.out.println("Commands:");
        System.out.println(
                "  validate <skill-path>          Validate a skill directory. Checks that the skill has a valid");
        System.out.println(
                "                                 SKILL.md with proper frontmatter, correct naming conventions,");
        System.out.println("                                 and required fields.");
        System.out.println();
        System.out.println("  read-properties <skill-path>  Read and print skill properties as JSON. Parses the YAML");
        System.out.println("                                 frontmatter from SKILL.md and outputs the properties");
        System.out.println("                                 as JSON.");
        System.out.println();
        System.out.println("  to-prompt <skill-path>...     Generate <available_skills> XML for agent prompts.");
        System.out.println("                                 Accepts one or more skill directories.");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  --version, -v                 Show version");
        System.out.println("  --help, -h                    Show this help message");
    }
}
