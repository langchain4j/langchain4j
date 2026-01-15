# LangChain4j Agent Skills

> **Status**: Experimental (1.12.0)

Extend your AI agents with dynamically loadable skills defined in `SKILL.md` files. Enable agents to execute scripts, read resources, and use external tools to accomplish complex tasks.

---

## Quick Start

### 1. Add Dependency

```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-agent-skills</artifactId>
    <version>1.12.0-SNAPSHOT</version>
</dependency>
```

### 2. Create a Skill

Create `skills/calculator/SKILL.md`:

```markdown
---
name: calculator
description: Perform mathematical calculations
allowed-tools:
  - python
---

# Calculator Skill

Use `python scripts/calc.py <expression>` to evaluate math expressions.
```

Create `skills/calculator/scripts/calc.py`:

```python
#!/usr/bin/env python3
import sys
print(eval(sys.argv[1]))
```

Make it executable:
```bash
chmod +x skills/calculator/scripts/calc.py
```

### 3. Configure Agent

```java
import dev.langchain4j.agentskills.AgentSkillsConfig;
import dev.langchain4j.agentskills.DefaultAgentSkillsProvider;
import dev.langchain4j.service.AiServices;
import java.nio.file.Path;

AgentSkillsConfig config = AgentSkillsConfig.builder()
    .skillsProvider(DefaultAgentSkillsProvider.builder()
        .skillDirectories(Path.of("skills"))
        .build())
    .maxIterations(10)
    .build();

interface Assistant {
    String chat(String message);
}

Assistant assistant = AiServices.builder(Assistant.class)
    .chatModel(yourChatModel)
    .agentSkillsConfig(config)  // ← Enable Agent Skills
    .build();
```

### 4. Use It!

```java
String result = assistant.chat("Calculate 15 * 23");
// LLM automatically uses calculator skill: "The result is 345"
```

---

## How It Works

### 1. System Prompt Enhancement
When you send a message, Agent Skills automatically adds available skills to the system prompt:

```xml
<available_skills>
  <skill>
    <name>calculator</name>
    <description>Perform mathematical calculations</description>
  </skill>
</available_skills>

Instructions:
- Load skill: <use_skill>skill-name</use_skill>
- Execute script: <execute_script skill="skill-name">command</execute_script>
- Read resource: <read_resource skill="skill-name">path</read_resource>
```

### 2. LLM Response Processing
The LLM can respond with instructions like:

```xml
<execute_script skill="calculator">python scripts/calc.py 15*23</execute_script>
```

Agent Skills automatically:
1. Validates the command against `allowed-tools`
2. Executes the script in the skill directory
3. Captures output and sends it back to the LLM
4. LLM formulates the final response to the user

### 3. Automatic Iteration
The process continues until the LLM responds with a user-facing message (up to `maxIterations`).

---

## Skill Definition

### SKILL.md Format

```markdown
---
name: skill-name                    # Required: lowercase-with-hyphens
description: Brief description      # Required: ≤ 500 chars
license: MIT                        # Optional
compatibility: Works with GPT-4     # Optional: ≤ 200 chars
allowed-tools:                      # Optional: command whitelist
  - python
  - bash
  - scripts/*                       # Wildcard patterns supported
metadata:                           # Optional: custom fields
  author: Your Name
  version: 1.0.0
---

# Skill Documentation

Markdown documentation here...
Instructions for using the skill...
```

### Directory Structure

```
skills/
└── skill-name/
    ├── SKILL.md           # Required: metadata + docs
    ├── scripts/           # Optional: executable scripts
    │   ├── script1.sh
    │   └── script2.py
    └── assets/            # Optional: config/resources
        ├── config.json
        └── template.txt
```

---

## Instructions Reference

### 1. Load Skill Content

**Instruction**: `<use_skill>skill-name</use_skill>`

**Purpose**: Load the full `SKILL.md` content (excluding frontmatter)

**Response**:
```xml
<skill_content name="skill-name">
...skill instructions...
</skill_content>
```

**Use Case**: LLM needs detailed instructions before using the skill

---

### 2. Execute Script

**Instruction**: `<execute_script skill="skill-name">command args</execute_script>`

**Purpose**: Run a script or command within the skill directory

**Security**:
- Command must be in `allowed-tools` list
- Executes in skill directory as working directory
- 60-second timeout (configurable)

**Response**:
```xml
<script_result exit_code="0">
stdout output here
STDERR:
stderr output here (if any)
</script_result>
```

**Use Case**: Execute processing scripts, run calculations, fetch data

---

### 3. Read Resource

**Instruction**: `<read_resource skill="skill-name">relative/path.txt</read_resource>`

**Purpose**: Read a file from the skill directory

**Security**:
- Path must be relative (no absolute paths)
- Path traversal blocked (`../` outside skill directory)
- Symlink escape prevention

**Response**:
```xml
<resource_content path="relative/path.txt">
file content here
</resource_content>
```

**Use Case**: Load configuration files, templates, lookup data

---

## Security

### Command Whitelisting

**Problem**: Prevent arbitrary command execution

**Solution**: `allowed-tools` in SKILL.md

```yaml
allowed-tools:
  - python              # Allow: python script.py
  - bash                # Allow: bash script.sh
  - scripts/*           # Allow: scripts/anything
  - "*"                 # Allow: everything (use with caution!)
```

**Validation**: Exact match or prefix match with wildcards

---

### Path Traversal Prevention

**Problem**: Accessing files outside skill directory

**Solution**: Multi-layer validation
1. Normalize path before resolution
2. Check `startsWith(skillPath)`
3. Resolve real path (follow symlinks)
4. Check `startsWith(skillPath)` again

**Blocked**:
```
../../../etc/passwd          ❌
/tmp/file.txt                ❌
symlink-to-outside-dir       ❌
```

**Allowed**:
```
assets/config.json           ✅
scripts/helper.py            ✅
./relative/path.txt          ✅
```

---

### Process Isolation

**Working Directory**: All scripts execute in skill directory
- No access to parent directories by default
- Relative paths resolve within skill directory

**Timeout**: Scripts terminated after 60 seconds (configurable)

---

## Configuration

### AgentSkillsConfig Builder

```java
AgentSkillsConfig config = AgentSkillsConfig.builder()
    .skillsProvider(provider)           // Required
    .scriptExecutor(customExecutor)     // Optional: default is DefaultScriptExecutor
    .maxIterations(10)                  // Optional: default is 10
    .build();
```

### DefaultAgentSkillsProvider Builder

```java
AgentSkillsProvider provider = DefaultAgentSkillsProvider.builder()
    .skillDirectories(
        Path.of("skills"),              // Load from multiple directories
        Path.of("/opt/shared-skills")
    )
    .build();

// Reload skills after modifications
provider.reload();
```

### Custom Script Executor

```java
ScriptExecutor dockerExecutor = (workingDir, command) -> {
    // Run scripts in Docker containers
    ProcessBuilder pb = new ProcessBuilder(
        "docker", "run", "--rm",
        "-v", workingDir + ":/workspace",
        "-w", "/workspace",
        "python:3.11-alpine",
        "sh", "-c", command
    );
    Process process = pb.start();
    // ... capture output ...
    return new ScriptExecutionResult(
        process.waitFor(),
        stdout,
        stderr
    );
};

AgentSkillsConfig config = AgentSkillsConfig.builder()
    .skillsProvider(provider)
    .scriptExecutor(dockerExecutor)      // Use custom executor
    .build();
```

---

## Examples

### Example 1: Web Scraping Skill

**Skill**: `skills/web-scraper/SKILL.md`
```markdown
---
name: web-scraper
description: Fetch and parse web page content
allowed-tools:
  - curl
  - python
---

# Web Scraper

Fetch a URL:
```bash
curl -s https://example.com
```

Parse HTML:
```bash
python scripts/parse.py <url>
```
```

**Usage**:
```java
assistant.chat("What's on the homepage of example.com?");
// LLM uses web-scraper skill to fetch and analyze the page
```

---

### Example 2: Data Analysis Skill

**Skill**: `skills/data-analyzer/SKILL.md`
```markdown
---
name: data-analyzer
description: Analyze CSV data and generate insights
allowed-tools:
  - python
---

# Data Analyzer

Analyze a CSV file:
```bash
python scripts/analyze.py <csv-file>
```

Configuration in `assets/config.json`:
- `threshold`: Minimum value for filtering
- `chart_type`: Type of visualization
```

**Resources**: `skills/data-analyzer/assets/config.json`
```json
{
  "threshold": 0.5,
  "chart_type": "bar"
}
```

**Usage**:
```java
assistant.chat("Analyze sales_data.csv and find top products");
// LLM:
// 1. <read_resource skill="data-analyzer">assets/config.json</read_resource>
// 2. <execute_script skill="data-analyzer">python scripts/analyze.py sales_data.csv</execute_script>
// 3. Summarizes results for user
```

---

### Example 3: Multi-Skill Workflow

**Skills**: `calculator`, `report-generator`

**User Request**:
```java
assistant.chat("Calculate monthly revenue (1500 * 30) and generate a report");
```

**LLM Workflow**:
1. `<execute_script skill="calculator">python scripts/calc.py 1500*30</execute_script>`
   → Result: 45000
2. `<execute_script skill="report-generator">python scripts/generate.py 45000</execute_script>`
   → Report generated
3. Responds: "Monthly revenue is $45,000. Report has been generated."

---

## Testing

### Unit Tests

Run all unit tests:
```bash
mvn test -pl langchain4j-agent-skills
```

Test specific component:
```bash
mvn test -Dtest=FileSystemSkillLoaderTest -pl langchain4j-agent-skills
```

### End-to-End Tests

Requires real LLM (e.g., Qwen):
```bash
export QWEN_API_KEY="your-api-key"
mvn test -Dtest=AgentSkillsEndToEndTest -pl langchain4j-agent-skills
```

**Test Coverage**:
- 66 total tests (9 + 11 + 13 + 26 + 7)
- 100% pass rate
- Full integration with real LLM

---

## Troubleshooting

### Skills Not Loading

**Check**:
1. `SKILL.md` exists in skill directory
2. Skill name in frontmatter matches directory name
3. YAML frontmatter is valid (use YAML linter)
4. Check logs for parsing errors

**Debug**:
```java
provider.provideSkills(request).skills().forEach(skill ->
    System.out.println("Loaded: " + skill.name())
);
```

---

### Script Execution Fails

**Check**:
1. Script has execute permissions: `chmod +x scripts/script.sh`
2. Shebang is correct: `#!/bin/bash` or `#!/usr/bin/env python3`
3. Command is in `allowed-tools` list
4. Script is in skill directory

**Debug**:
```java
ScriptExecutor debugExecutor = (workingDir, command) -> {
    System.out.println("Executing: " + command);
    System.out.println("Working dir: " + workingDir);
    // ... actual execution ...
};
```

---

### LLM Not Using Skills

**Try**:
1. More explicit prompts: "Use the calculator skill to compute..."
2. Check system prompt enhancement (enable debug logging)
3. Increase `maxIterations` if process stops early
4. Try different LLM models (GPT-4, Claude, etc.)
5. Verify model supports tool/function calling patterns

**Debug**:
```java
// Enable logging
import org.slf4j.LoggerFactory;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;

Logger root = (Logger) LoggerFactory.getLogger("dev.langchain4j.service.agentskills");
root.setLevel(Level.DEBUG);
```

---

## Architecture

For detailed architecture, design decisions, and implementation details, see:
- **[agent-skills-architecture.md](agent-skills-architecture.md)** - Full design document

**Key Components**:
- `AgentSkillsService` - Orchestrates skill operations
- `DefaultAgentSkillsProvider` - Loads skills from file system
- `FileSystemSkillLoader` - Parses SKILL.md files
- `DefaultScriptExecutor` - Executes shell commands
- `AgentSkillsConfig` - Configuration container

---

## Limitations

### Current Limitations

1. **File System Only**: Skills must be on local file system (no HTTP/database loaders yet)
2. **Single Instruction**: Only first instruction in LLM response is processed per iteration
3. **No Streaming**: Script output captured after completion (no real-time streaming)
4. **Unix-Centric**: Best support for Unix-like systems (macOS, Linux)
5. **No Dependency Management**: Skills cannot depend on other skills

### Roadmap

- [ ] Database-backed skill provider
- [ ] HTTP API skill loader
- [ ] Skill dependency resolution
- [ ] Docker-based sandboxing
- [ ] Streaming script output
- [ ] Skill marketplace/registry
- [ ] Windows-native script support
- [ ] Skill composition DSL

---

## Contributing

Contributions welcome! See [agent-skills-architecture.md](agent-skills-architecture.md) for:
- Code style guidelines
- Testing requirements
- Architecture decisions

---

## License

Apache 2.0 (same as LangChain4j)

---

## Support

**Issues**: [GitHub Issues](https://github.com/langchain4j/langchain4j/issues)
**Discussions**: [GitHub Discussions](https://github.com/langchain4j/langchain4j/discussions)
**Author**: Shrink (shunke.wjl@alibaba-inc.com)

---

## See Also

- [LangChain4j Documentation](https://docs.langchain4j.dev)
- [AI Services Guide](https://docs.langchain4j.dev/tutorials/ai-services)
- [Tool System](https://docs.langchain4j.dev/tutorials/tools)
