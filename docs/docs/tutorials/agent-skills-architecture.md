# Agent Skills Feature Design Document

## Overview

Agent Skills is a capability extension system for LangChain4j that allows AI agents to dynamically load and execute external skills defined in `SKILL.md` files. This feature enables agents to access pre-defined tools, scripts, and resources to accomplish complex tasks.

**Version**: 1.12.0
**Author**: Shrink (shunke.wjl@alibaba-inc.com)
**Status**: Experimental

---

## Table of Contents

1. [Architecture Overview](#architecture-overview)
2. [Core Components](#core-components)
3. [Design Principles](#design-principles)
4. [Skill Definition Format](#skill-definition-format)
5. [Workflow & Execution Model](#workflow--execution-model)
6. [Security Considerations](#security-considerations)
7. [Integration with AI Services](#integration-with-ai-services)
8. [Usage Examples](#usage-examples)
9. [Testing Strategy](#testing-strategy)
10. [References & Inspiration](#references--inspiration)

---

## Architecture Overview

### High-Level Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                        User Application                      │
└─────────────────┬───────────────────────────────────────────┘
                  │
                  ▼
┌─────────────────────────────────────────────────────────────┐
│                      AiServices Builder                      │
│  ┌──────────────────────────────────────────────────────┐   │
│  │          AgentSkillsConfig (Configuration)           │   │
│  │  • skillsProvider: AgentSkillsProvider               │   │
│  │  • scriptExecutor: ScriptExecutor                    │   │
│  │  • maxIterations: int                                │   │
│  └──────────────────────────────────────────────────────┘   │
└─────────────────┬───────────────────────────────────────────┘
                  │
                  ▼
┌─────────────────────────────────────────────────────────────┐
│                   AgentSkillsService                         │
│  ┌──────────────────────────────────────────────────────┐   │
│  │  Core Responsibilities:                              │   │
│  │  • Generate system prompt with available skills      │   │
│  │  • Parse LLM responses for skill instructions        │   │
│  │  • Execute skill operations (use/execute/read)       │   │
│  │  • Validate security constraints                     │   │
│  └──────────────────────────────────────────────────────┘   │
└─────────────┬───────────────────────┬───────────────────────┘
              │                       │
              ▼                       ▼
┌─────────────────────────┐ ┌─────────────────────────────────┐
│  AgentSkillsProvider    │ │    ScriptExecutor               │
│  ┌───────────────────┐  │ │  ┌───────────────────────────┐  │
│  │ • provideSkills() │  │ │  │ • execute(path, command)  │  │
│  │ • skillByName()   │  │ │  │ • Security: working dir   │  │
│  │ • reload()        │  │ │  │ • Timeout management      │  │
│  └───────────────────┘  │ │  └───────────────────────────┘  │
└─────────────┬───────────┘ └─────────────────────────────────┘
              │
              ▼
┌─────────────────────────────────────────────────────────────┐
│            DefaultAgentSkillsProvider                        │
│  ┌──────────────────────────────────────────────────────┐   │
│  │  • Loads skills from directories                     │   │
│  │  • Caches parsed skills                              │   │
│  │  • Thread-safe lazy initialization                   │   │
│  │  • Supports hot reload                               │   │
│  └──────────────────────────────────────────────────────┘   │
└─────────────┬───────────────────────────────────────────────┘
              │
              ▼
┌─────────────────────────────────────────────────────────────┐
│                   FileSystemSkillLoader                      │
│  ┌──────────────────────────────────────────────────────┐   │
│  │  • Parses SKILL.md files                             │   │
│  │  • Validates YAML frontmatter                        │   │
│  │  • Extracts skill metadata                           │   │
│  │  • Validates naming and constraints                  │   │
│  └──────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────┐
│                      File System                             │
│  skills/                                                     │
│  ├── skill-name/                                             │
│  │   ├── SKILL.md          (Required: metadata + docs)      │
│  │   ├── scripts/          (Optional: executable scripts)   │
│  │   │   └── *.sh, *.py                                     │
│  │   └── assets/           (Optional: config/resources)     │
│  │       └── config.json, templates, etc.                   │
└─────────────────────────────────────────────────────────────┘
```

---

## Core Components

### 1. **Skill** (Domain Model)
- **Location**: `langchain4j-core/src/main/java/dev/langchain4j/agentskills/Skill.java`
- **Purpose**: Immutable data class representing a skill
- **Key Properties**:
  - `name`: Unique skill identifier (lowercase with hyphens)
  - `description`: Brief skill description
  - `instructions`: Detailed usage instructions (markdown)
  - `path`: File system path to skill directory
  - `allowedTools`: Whitelist of executable commands
  - `compatibility`: Target LLM compatibility info
  - `license`: License information

### 2. **FileSystemSkillLoader**
- **Location**: `langchain4j-agent-skills/src/main/java/dev/langchain4j/agentskills/loader/FileSystemSkillLoader.java`
- **Purpose**: Parses `SKILL.md` files and constructs `Skill` objects
- **Key Features**:
  - YAML frontmatter parsing with validation
  - Skill name validation (regex: `^[a-z0-9-]+$`)
  - Length constraints (description ≤ 500 chars, compatibility ≤ 200 chars)
  - Error handling for malformed files
- **Reference**: Inspired by Jekyll/Hugo frontmatter parsing

### 3. **AgentSkillsProvider** (Interface)
- **Location**: `langchain4j-core/src/main/java/dev/langchain4j/agentskills/AgentSkillsProvider.java`
- **Purpose**: Abstract skill discovery and loading
- **Methods**:
  - `provideSkills(request)`: Returns filtered skills based on context
  - `skillByName(name)`: Retrieves a specific skill
  - `reload()`: Refreshes skill cache

### 4. **DefaultAgentSkillsProvider**
- **Location**: `langchain4j-agent-skills/src/main/java/dev/langchain4j/agentskills/DefaultAgentSkillsProvider.java`
- **Purpose**: Default file system-based skill provider
- **Key Features**:
  - Lazy loading with double-checked locking
  - Thread-safe skill caching
  - Multi-directory support
  - Duplicate skill name detection
  - Hot reload capability
- **Design Pattern**: Singleton-like lazy initialization per instance

### 5. **ScriptExecutor** (Interface)
- **Location**: `langchain4j-core/src/main/java/dev/langchain4j/agentskills/execution/ScriptExecutor.java`
- **Purpose**: Abstract script execution
- **Method**: `execute(workingDirectory, command)` → `ScriptExecutionResult`

### 6. **DefaultScriptExecutor**
- **Location**: `langchain4j-core/src/main/java/dev/langchain4j/agentskills/execution/DefaultScriptExecutor.java`
- **Purpose**: Shell command executor with security controls
- **Key Features**:
  - Process spawning with `ProcessBuilder`
  - Configurable timeout (default: 60 seconds)
  - Output/error stream capture
  - Working directory isolation
  - Exit code handling
- **Security**: Runs commands within skill's working directory only

### 7. **AgentSkillsService**
- **Location**: `langchain4j-core/src/main/java/dev/langchain4j/service/agentskills/AgentSkillsService.java`
- **Purpose**: Central orchestration service
- **Key Responsibilities**:
  - **System Prompt Generation**: Injects available skills into LLM context
  - **Instruction Parsing**: Extracts XML-like instructions from LLM responses
  - **Instruction Execution**: Handles `use_skill`, `execute_script`, `read_resource`
  - **Security Enforcement**: Path traversal prevention, command whitelisting
- **Instruction Types**:
  - `<use_skill>skill-name</use_skill>` → Load full skill instructions
  - `<execute_script skill="name">command</execute_script>` → Run script
  - `<read_resource skill="name">path</read_resource>` → Read file

### 8. **AgentSkillsConfig**
- **Location**: `langchain4j-core/src/main/java/dev/langchain4j/agentskills/AgentSkillsConfig.java`
- **Purpose**: Configuration container
- **Builder Pattern**: Fluent API for configuration
- **Properties**:
  - `skillsProvider`: Skill source
  - `scriptExecutor`: Optional custom executor
  - `maxIterations`: Maximum skill execution loops (default: 10)

---

## Design Principles

### 1. **Security by Default**
- **Command Whitelisting**: `allowed-tools` in SKILL.md controls executable commands
- **Path Traversal Prevention**: Validates all resource paths stay within skill directory
- **Symlink Protection**: Resolves real paths to prevent symlink escapes
- **Working Directory Isolation**: Scripts execute in skill directory only

### 2. **Extensibility**
- **Provider Pattern**: Easy to implement custom skill sources (database, API, etc.)
- **Executor Pattern**: Custom script executors for sandboxing or containers
- **Instruction System**: XML-like tags for clear LLM communication

### 3. **Observability**
- **SLF4J Logging**: DEBUG/INFO/WARN levels for traceability
- **Structured Errors**: XML-wrapped error messages for LLM understanding
- **Exit Code Preservation**: Script results include exit codes

### 4. **Performance**
- **Lazy Loading**: Skills loaded on first access
- **Caching**: Parsed skills cached in memory
- **Reload Support**: Manual cache invalidation for hot updates

### 5. **LLM-Friendly Design**
- **XML Instructions**: Structured format for reliable parsing
- **Clear Error Messages**: English-language error responses
- **System Prompt Enhancement**: Skills automatically advertised to LLM

---

## Skill Definition Format

### SKILL.md Structure

```markdown
---
name: pdf-processing
description: Extract and analyze text from PDF documents
license: MIT
compatibility: Works with GPT-4, Claude, and other models
allowed-tools:
  - python
  - bash
  - scripts/*
metadata:
  author: John Doe
  version: 1.0.0
  tags:
    - pdf
    - text-extraction
---

# PDF Processing Skill

## Overview
This skill enables text extraction from PDF files...

## Usage
To extract text from a PDF:
```bash
scripts/extract.sh <pdf-file>
```

## Configuration
See `assets/config.json` for extraction options.
```

### Frontmatter Fields

| Field | Required | Type | Constraints | Description |
|-------|----------|------|-------------|-------------|
| `name` | ✅ | string | `^[a-z0-9-]+$` | Unique skill identifier |
| `description` | ✅ | string | ≤ 500 chars | Brief description |
| `license` | ❌ | string | - | License type |
| `compatibility` | ❌ | string | ≤ 200 chars | LLM compatibility info |
| `allowed-tools` | ❌ | list | - | Command whitelist (`*` for all) |
| `metadata` | ❌ | object | - | Custom key-value pairs |

---

## Workflow & Execution Model

### 1. Initialization Phase
```java
// User configures skills
AgentSkillsConfig config = AgentSkillsConfig.builder()
    .skillsProvider(DefaultAgentSkillsProvider.builder()
        .skillDirectories(Path.of("skills"))
        .build())
    .scriptExecutor(new DefaultScriptExecutor())
    .maxIterations(10)
    .build();

// Integrate with AI service
Assistant assistant = AiServices.builder(Assistant.class)
    .chatModel(model)
    .agentSkillsConfig(config)
    .build();
```

### 2. System Prompt Enhancement
When a user sends a message:
1. `AgentSkillsService.generateSystemPromptAddition()` is called
2. Available skills are formatted as XML:
```xml
<available_skills>
  <skill>
    <name>pdf-processing</name>
    <description>Extract text from PDFs</description>
  </skill>
</available_skills>

When you need to use a skill to complete a task, please use the following instructions:
- Load skill content: <use_skill>skill-name</use_skill>
- Execute skill script: <execute_script skill="skill-name">script-command</execute_script>
- Read skill resource: <read_resource skill="skill-name">resource-path</read_resource>
The system will automatically execute these instructions and return the results to you.
```
3. This is appended to the system prompt sent to the LLM

### 3. LLM Response Processing
When LLM responds with instructions:
1. **Parsing**: `AgentSkillsService.parseInstruction()` extracts first instruction
2. **Validation**: Checks skill exists, validates parameters
3. **Execution**: Calls appropriate handler:
   - `handleUseSkill()`: Returns full `SKILL.md` content
   - `handleExecuteScript()`: Validates command, executes via `ScriptExecutor`
   - `handleReadResource()`: Validates path, reads file
4. **Result Wrapping**: Output wrapped in XML tags:
```xml
<skill_content name="pdf-processing">
...skill instructions...
</skill_content>

<script_result exit_code="0">
Script output here
</script_result>

<resource_content path="assets/config.json">
{"key": "value"}
</resource_content>
```

### 4. Iteration Loop
- Results are sent back to LLM as user message
- LLM can issue more instructions or respond to user
- Continues until `maxIterations` reached or LLM responds normally

---

## Security Considerations

### 1. Command Injection Prevention
**Threat**: Malicious commands via `execute_script`
**Mitigation**:
- Command whitelist in `allowed-tools`
- Wildcard support (`scripts/*`) for safe prefixes
- No shell expansion (commands split into args)

**Example**:
```yaml
allowed-tools:
  - python
  - scripts/*.sh  # Only scripts in scripts/ directory
```

### 2. Path Traversal Prevention
**Threat**: Reading files outside skill directory via `read_resource`
**Mitigation**:
- Path normalization before resolution
- `startsWith()` check on normalized path
- Real path resolution (symlink protection)
- Double validation: normalized + real path

**Code**:
```java
Path fullPath = skillPath.resolve(resourcePath).normalize();
if (!fullPath.startsWith(skillPath.normalize())) {
    return "<resource_error>Illegal path</resource_error>";
}

Path realPath = fullPath.toRealPath();
if (!realPath.startsWith(skillPath.toRealPath())) {
    return "<resource_error>Illegal path</resource_error>";
}
```

### 3. Process Timeout
**Threat**: Runaway scripts consuming resources
**Mitigation**:
- Default 60-second timeout
- Process forcibly terminated after timeout
- Configurable timeout in `DefaultScriptExecutor`

### 4. Working Directory Isolation
**Threat**: Scripts modifying files outside skill directory
**Mitigation**:
- Scripts always execute with skill directory as working directory
- No access to parent directories without explicit path manipulation

---

## Integration with AI Services

### AiServices Integration Point

The `AgentSkillsService` is integrated into the `AiServices` framework at the message processing level:

1. **Pre-processing**: System prompt enhancement before LLM call
2. **Post-processing**: Response inspection for skill instructions
3. **Iteration**: Automatic re-invocation with instruction results

### Configuration API

```java
// Method 1: Via builder
Assistant assistant = AiServices.builder(Assistant.class)
    .chatModel(model)
    .agentSkillsConfig(config)  // ← Agent Skills integration
    .build();

// Method 2: Programmatic configuration
AgentSkillsService service = new AgentSkillsService();
service.agentSkillsProvider(provider);
service.scriptExecutor(executor);
service.maxIterations(5);
```

---

## Usage Examples

### Example 1: Basic Skill Usage

**Skill Definition** (`skills/calculator/SKILL.md`):
```markdown
---
name: calculator
description: Perform mathematical calculations
allowed-tools:
  - python
---

# Calculator Skill

Use `python scripts/calc.py <expression>` to evaluate expressions.
```

**Script** (`skills/calculator/scripts/calc.py`):
```python
import sys
print(eval(sys.argv[1]))
```

**User Code**:
```java
AgentSkillsConfig config = AgentSkillsConfig.builder()
    .skillsProvider(DefaultAgentSkillsProvider.builder()
        .skillDirectories(Path.of("skills"))
        .build())
    .build();

Assistant assistant = AiServices.builder(Assistant.class)
    .chatModel(OpenAiChatModel.builder()
        .apiKey(System.getenv("OPENAI_API_KEY"))
        .modelName("gpt-4")
        .build())
    .agentSkillsConfig(config)
    .build();

String result = assistant.chat("Calculate 15 * 23");
// LLM will use calculator skill to compute 345
```

### Example 2: Multi-Skill Workflow

**Skills**:
- `web-scraper`: Fetches web content
- `summarizer`: Summarizes text

**User Request**:
```java
assistant.chat("Summarize the latest news from example.com");
```

**LLM Workflow**:
1. LLM issues: `<use_skill>web-scraper</use_skill>`
2. System returns scraper instructions
3. LLM issues: `<execute_script skill="web-scraper">scripts/fetch.sh example.com</execute_script>`
4. System returns HTML content
5. LLM issues: `<use_skill>summarizer</use_skill>`
6. LLM uses summarizer instructions to condense content
7. LLM responds to user with summary

### Example 3: Custom Executor

```java
// Custom executor for Docker sandboxing
ScriptExecutor dockerExecutor = (workingDir, command) -> {
    ProcessBuilder pb = new ProcessBuilder(
        "docker", "run", "--rm",
        "-v", workingDir + ":/workspace",
        "-w", "/workspace",
        "alpine:latest",
        "sh", "-c", command
    );
    // ... process execution ...
};

AgentSkillsConfig config = AgentSkillsConfig.builder()
    .skillsProvider(provider)
    .scriptExecutor(dockerExecutor)  // Custom executor
    .build();
```

---

## Testing Strategy

### 1. Unit Tests

#### **FileSystemSkillLoaderTest** (9 tests)
- ✅ Parse complete frontmatter with all fields
- ✅ Parse minimal frontmatter (required fields only)
- ✅ Validate skill name regex (`^[a-z0-9-]+$`)
- ✅ Enforce description length (≤ 500 chars)
- ✅ Enforce compatibility length (≤ 200 chars)
- ✅ Parse `allowed-tools` list
- ✅ Handle missing/malformed/empty files
- ✅ Load multiple skills from directory

**Reference**: MCP module test style (given-when-then, AssertJ fluent assertions)

#### **DefaultAgentSkillsProviderTest** (11 tests)
- ✅ Load from single/multiple directories
- ✅ Lookup by name (case-sensitive)
- ✅ Cache loaded skills
- ✅ Thread-safe concurrent access
- ✅ Reload/invalidate cache
- ✅ Handle duplicate skill names (last wins)
- ✅ Handle empty/non-existent directories
- ✅ Builder validation (null checks)

**Reference**: MCP `DefaultMcpClientTest` caching patterns

#### **DefaultScriptExecutorTest** (13 tests)
- ✅ Execute simple commands (echo, true, false)
- ✅ Capture stdout and stderr separately
- ✅ Respect working directory
- ✅ Handle null/blank commands/directories
- ✅ Execute Python scripts
- ✅ Handle large output
- ✅ Timeout long-running commands
- ✅ Capture non-zero exit codes
- ✅ Handle non-existent commands
- ✅ Execute relative path scripts
- ✅ OS-specific commands (Windows: `dir`, Unix: `ls`)

**Reference**: Tool module test conventions

#### **AgentSkillsServiceTest** (26 tests)
- ✅ Parse `use_skill` instruction
- ✅ Parse `execute_script` instruction
- ✅ Parse `read_resource` instruction
- ✅ Handle multiple instructions (first only)
- ✅ Return error for missing skills
- ✅ Validate command against `allowed-tools`
- ✅ Prevent path traversal (`../../../etc/passwd`)
- ✅ Use default executor if none provided
- ✅ Generate system prompt with skills
- ✅ Handle empty skill list
- ✅ Validate `maxIterations` (≥ 1)
- ✅ Handle wildcard `allowed-tools: "*"`
- ✅ Successfully read resources
- ✅ Successfully execute scripts
- ✅ Handle script failures (non-zero exit)

**Coverage**: Instruction parsing, security validation, error handling

### 2. End-to-End Tests

#### **AgentSkillsEndToEndTest** (7 tests)
Uses **real LLM** (Qwen via DashScope) to validate full integration:

- ✅ **Skills Discovery**: LLM lists available skills
- ✅ **Skill Content Loading**: LLM uses `<use_skill>` to load instructions
- ✅ **Script Execution**: LLM uses `<execute_script>` to run `extract.sh`
- ✅ **Resource Reading**: LLM uses `<read_resource>` to read `config.json`
- ✅ **Multiple Operations**: LLM chains multiple instructions in one turn
- ✅ **Error Handling**: LLM gracefully handles non-existent skills
- ✅ **System Prompt Enhancement**: Verifies skills appear in system context

**Configuration**:
- Model: `qwen-plus`
- Endpoint: `https://dashscope.aliyuncs.com/compatible-mode/v1`
- Requires: `QWEN_API_KEY` environment variable
- Test annotation: `@EnabledIfEnvironmentVariable(named = "QWEN_API_KEY", matches = ".+")`

**Test Resources**:
- `test-skills/pdf-processing/SKILL.md`
- `test-skills/pdf-processing/scripts/extract.sh`
- `test-skills/pdf-processing/assets/config.json`

### 3. Test Execution Summary

| Test Suite | Tests | Pass Rate | Coverage |
|------------|-------|-----------|----------|
| FileSystemSkillLoaderTest | 9 | 100% | Parsing, validation |
| DefaultAgentSkillsProviderTest | 11 | 100% | Loading, caching, concurrency |
| DefaultScriptExecutorTest | 13 | 100% | Process execution, timeouts |
| AgentSkillsServiceTest | 26 | 100% | Orchestration, security |
| AgentSkillsEndToEndTest | 7 | 100% | Real LLM integration |
| **Total** | **66** | **100%** | **Full stack** |

**Build Command**:
```bash
mvn test -pl langchain4j,langchain4j-agent-skills
```

---

## References & Inspiration

### 1. **MCP (Model Context Protocol) - Anthropic**
- **Source**: LangChain4j MCP module (`langchain4j-mcp`)
- **Inspired**:
  - Provider pattern for extensible skill sources
  - Caching strategy in `DefaultMcpClientTest`
  - Lazy initialization with thread-safe double-checked locking
  - Builder pattern conventions
- **Differences**:
  - MCP focuses on server-client RPC; Agent Skills is file system-based
  - MCP tools are function-based; Agent Skills are script/resource-based

### 2. **Tool System - LangChain4j**
- **Source**: `langchain4j/src/main/java/dev/langchain4j/service/tool/`
- **Inspired**:
  - Test naming conventions (`should_*` prefix)
  - AssertJ assertion style
  - Parameterized test patterns
  - Tool execution model (input → execution → output)
- **Differences**:
  - Tools are Java methods; Skills are external scripts
  - Tools use JSON Schema; Skills use YAML frontmatter

### 3. **Jekyll/Hugo Frontmatter**
- **Source**: Static site generators
- **Inspired**:
  - YAML frontmatter for metadata (`---` delimiters)
  - Markdown content for documentation
- **Usage**: `SKILL.md` format

### 4. **Unix Philosophy**
- **Principles**:
  - Small, composable units (skills)
  - Text streams for I/O (stdout/stderr)
  - Exit codes for success/failure
  - Working directory as execution context

### 5. **OpenAI Function Calling**
- **Concept**: LLM-driven tool invocation
- **Inspired**:
  - XML-like instruction format (instead of JSON function calls)
  - System prompt skill advertisement
  - Automatic iteration loop
- **Differences**:
  - OpenAI uses JSON schema; we use XML tags
  - OpenAI validates signatures; we validate with regex

### 6. **Langroid Agent Skills (Python)**
- **Source**: Langroid framework
- **Inspired**:
  - "Skills as capabilities" mental model
  - Dynamic skill loading
- **Differences**:
  - Langroid is Python-native; ours is shell/polyglot
  - Langroid skills are Python classes; ours are file system artifacts

---

## Module Structure

### langchain4j-agent-skills (Integration Module)
```
langchain4j-agent-skills/
├── pom.xml
├── src/main/java/dev/langchain4j/agentskills/
│   ├── DefaultAgentSkillsProvider.java
│   └── loader/
│       ├── SkillLoader.java (interface)
│       └── FileSystemSkillLoader.java
└── src/test/java/dev/langchain4j/agentskills/
    ├── AgentSkillsEndToEndTest.java (E2E tests)
    ├── DefaultAgentSkillsProviderTest.java
    └── loader/
        └── FileSystemSkillLoaderTest.java
```

### langchain4j-core (Core APIs)
```
langchain4j/src/main/java/dev/langchain4j/
├── agentskills/
│   ├── AgentSkillsConfig.java
│   ├── AgentSkillsProvider.java (interface)
│   ├── AgentSkillsProviderRequest.java
│   ├── AgentSkillsProviderResult.java
│   ├── Skill.java (domain model)
│   ├── execution/
│   │   ├── ScriptExecutor.java (interface)
│   │   ├── DefaultScriptExecutor.java
│   │   └── ScriptExecutionResult.java
│   └── instruction/
│       ├── AgentSkillsInstruction.java (sealed interface)
│       ├── UseSkillInstruction.java
│       ├── ExecuteScriptInstruction.java
│       └── ReadResourceInstruction.java
└── service/agentskills/
    └── AgentSkillsService.java (orchestrator)
```

---

## API Reference

### Public Interfaces

#### AgentSkillsConfig.Builder
```java
AgentSkillsConfig.builder()
    .skillsProvider(AgentSkillsProvider)
    .scriptExecutor(ScriptExecutor)      // Optional
    .maxIterations(int)                   // Default: 10
    .build()
```

#### DefaultAgentSkillsProvider.Builder
```java
DefaultAgentSkillsProvider.builder()
    .skillDirectories(Path...)            // Varargs
    .build()
```

#### Skill (Record)
```java
record Skill(
    String name,
    String description,
    String instructions,
    Path path,
    List<String> allowedTools,
    String compatibility,
    String license,
    Map<String, Object> metadata
)
```

#### ScriptExecutionResult (Record)
```java
record ScriptExecutionResult(
    int exitCode,
    String output,
    String error
)
```

---

## Future Enhancements

### 1. Skill Marketplace
- Central repository for community skills
- Version management and dependency resolution
- Skill signatures and verification

### 2. Advanced Security
- Sandboxed execution (Docker, gVisor)
- Resource limits (CPU, memory, disk)
- Network policies (allow/deny lists)

### 3. Skill Composition
- Skill dependencies and pipelines
- Skill chaining DSL
- Parallel skill execution

### 4. Enhanced Observability
- Execution tracing and metrics
- Performance profiling
- Cost tracking (LLM token usage per skill)

### 5. Alternative Loaders
- Database-backed skills
- HTTP API skill sources
- Git repository skills

### 6. Skill Testing Framework
- Built-in test runner for skills
- Mock LLM for skill validation
- CI/CD integration

---

## Troubleshooting

### Common Issues

#### 1. Skills Not Loading
**Symptom**: `AgentSkillsProvider.provideSkills()` returns empty list
**Solutions**:
- Verify `SKILL.md` exists in skill directory
- Check skill name matches directory name
- Validate YAML frontmatter syntax
- Check logs for parsing errors

#### 2. Script Execution Fails
**Symptom**: `<script_error>` in LLM response
**Solutions**:
- Verify script has execute permissions (`chmod +x`)
- Check `allowed-tools` whitelist includes command
- Verify working directory is correct
- Check script shebang (`#!/bin/bash`)

#### 3. Path Traversal Errors
**Symptom**: `<resource_error>Illegal path</resource_error>`
**Solutions**:
- Use relative paths only (`assets/file.txt`, not `/tmp/file.txt`)
- Avoid `..` in paths
- Check symlinks point within skill directory

#### 4. LLM Not Using Skills
**Symptom**: LLM responds without using available skills
**Solutions**:
- Verify system prompt enhancement is working (log inspection)
- Use more explicit user prompts ("Use the calculator skill to...")
- Check `maxIterations` is not set too low
- Try different LLM models (some better at tool use)

---

## Contributing

### Adding New Skills
1. Create skill directory: `skills/your-skill-name/`
2. Write `SKILL.md` with required frontmatter
3. Add scripts to `scripts/` directory
4. Add resources to `assets/` directory
5. Test with unit tests and E2E tests

### Code Style
- Follow existing naming conventions
- Use builder patterns for configuration
- Add SLF4J logging at appropriate levels
- Write Javadoc for public APIs
- Use `should_*` test naming convention
- Prefer AssertJ assertions over JUnit assertions

### Test Requirements
- Unit tests for new components
- E2E test for new instruction types
- Security tests for new execution paths
- Documentation for new features

---

## License

This feature is part of LangChain4j and follows the same Apache 2.0 license.

---

## Contact

**Author**: Shrink (shunke.wjl@alibaba-inc.com)
**Module**: `langchain4j-agent-skills`
**Version**: 1.12.0 (Experimental)
**Documentation**: This file + Javadoc in source files
