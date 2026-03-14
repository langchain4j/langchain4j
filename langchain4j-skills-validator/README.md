# langchain4j-skills-validator

A Java implementation of the Agent Skills reference library. This utility provides tools for validating, parsing, and working with Agent Skills metadata defined in `SKILL.md` files.


## Features

- **Validate Skills**: Check that skill directories have valid `SKILL.md` files with proper frontmatter, correct naming conventions, and required fields
- **Parse Properties**: Extract skill metadata from YAML frontmatter in `SKILL.md` files
- **Generate Prompts**: Create `<available_skills>` XML blocks for inclusion in agent system prompts (Anthropic's recommended format)
- **CLI Tool**: Command-line interface for all major operations

## Installation & Setup

### Build from Source

```bash
cd langchain4j-skills-validator
mvn clean package
```

This creates an executable JAR at `target/langchain4j-skills-validator-{version}.jar`.

### Running the CLI

```bash
java -jar skills-validator.jar --help
```

## Usage

### Command Line Interface

#### Validate a Skill

```bash
java -jar skills-validator.jar validate /path/to/skill-directory
```

Validates that a skill directory has:
- A `SKILL.md` file (uppercase or lowercase)
- Valid YAML frontmatter with required fields
- Proper naming conventions (lowercase, hyphens allowed, no leading/trailing hyphens)
- Directory name matching the skill name

#### Read Skill Properties

```bash
java -jar skills-validator.jar read-properties /path/to/skill-directory
```

Outputs skill properties as JSON:

```json
{
  "name": "my-skill",
  "description": "What this skill does",
  "license": "Apache 2.0",
  "compatibility": "Java 17+",
  "allowed-tools": "file:read, file:write",
  "metadata": {
    "category": "file-operations"
  }
}
```

#### Generate Agent Prompt XML

```bash
java -jar skills-validator.jar to-prompt /path/to/skill-a /path/to/skill-b
```

Generates `<available_skills>` XML for agent prompts:

```xml
<available_skills>
<skill>
<name>
my-skill
</name>
<description>
What this skill does
</description>
<location>
/path/to/skill/SKILL.md
</location>
</skill>
</available_skills>
```

### Java API

Use the library directly in your Java applications:

```java
public class Example {
    public static void main(String[] args) throws Exception {
        Path skillDir = Path.of("path/to/skill");

        // Validate a skill
        SkillValidator validator = new SkillValidator();
        List<String> errors = validator.validate(skillDir);
        if (!errors.isEmpty()) {
            errors.forEach(System.err::println);
        }

        // Read skill properties
        FrontmatterParser parser = new FrontmatterParser();
        SkillProperties props = parser.readProperties(skillDir);
        System.out.println("Skill: " + props.getName());
        System.out.println("Description: " + props.getDescription());

        // Generate prompt XML
        PromptGenerator generator = new PromptGenerator();
        String prompt = generator.toPrompt(List.of(skillDir));
        System.out.println(prompt);
    }
}
```