---
sidebar_position: 32
---

# Skills

:::note
The Skills API is experimental. APIs and behavior may still change in future releases.
:::

Skills is a mechanism for equipping an LLM with reusable, self-contained behavioral instructions.
A skill bundles a name, a short description, and a body of instructions (its _content_),
together with optional resources (e.g., references, assets, templates, etc.).
The LLM loads a skill on demand, keeping the initial context small and only pulling in
the detailed instructions when they are actually needed.

:::note
Skills are designed according to the [Agent Skills specification](https://agentskills.io).
:::

## Creating Skills

### From the File System

Typically, each skill lives in its own directory containing a `SKILL.md` file.
The file must start with a YAML front matter block that declares the skill's `name` and `description`.
Everything below the front matter becomes the skill's content — the instructions given to the LLM
when it activates the skill.

```
skills/
├── docx/
│   ├── SKILL.md
│   └── references/
│       └── tracked-changes.md   ← loaded as a resource
└── data-analysis/
    └── SKILL.md
```

Example `SKILL.md`:

```markdown
---
name: docx
description: Edit and review Word documents using tracked changes
---

When the user asks you to edit a Word document:

1. Always use tracked changes so edits can be reviewed.
   ...
```

Any file in the skill directory (other than `SKILL.md` itself and files under a `scripts/`
subdirectory) is automatically loaded as a `SkillResource` that the LLM can read on demand.

Use `FileSystemSkillLoader` from the `langchain4j-skills` module to load skills from the file system:

```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-skills</artifactId>
    <version>1.12.1-beta21</version>
</dependency>
```

```java
// Load all skills found in immediate subdirectories:
List<FileSystemSkill> skills = FileSystemSkillLoader.loadSkills(Path.of("skills/"));

// Or load a single skill by its directory:
FileSystemSkill skill = FileSystemSkillLoader.loadSkill(Path.of("skills/docx"));
```

### Programmatically

Skills do not have to be file-system based.
You can create them from any source — a database, a remote API, generated at runtime — using the builder API:

```java
Skill skill = Skill.builder()
        .name("incident-response")
        .description("Step-by-step runbook for diagnosing and resolving production incidents")
        .content("""
                When a production alert fires:
                1. Call `fetchRecentLogs(serviceName)` to retrieve the last 5 minutes of logs.
                2. Call `checkServiceHealth(serviceName)` to get current health metrics.
                3. Based on the findings, call `createIncidentTicket(summary, severity)`.
                4. If severity is CRITICAL, also call `pageOnCall(incidentId)`.
                """)
        .build();
```

You can also attach resources programmatically:

```java
SkillResource reference = SkillResource.builder()
        .relativePath("references/tone-guide.md")
        .content("Use warm, concise language. Avoid jargon.")
        .build();

Skill skill = Skill.builder()
        .name("customer-support")
        .description("Handles customer support inquiries")
        .content("Follow the tone guide in references/tone-guide.md ...")
        .resources(List.of(reference))
        .build();
```

### Skill-Scoped Tools

You can attach tools directly to a skill. These tools are **only exposed to the LLM
after the skill has been activated** via the `activate_skill` tool.
This keeps the LLM's tool list small and focused, and ensures skill-specific tools only
appear when they are relevant:

```java
ToolSpecification validateOrder = ToolSpecification.builder()
        .name("validateOrder")
        .description("Validates a customer order")
        .parameters(JsonObjectSchema.builder()
                .addStringProperty("orderId", "The order ID")
                .required("orderId")
                .build())
        .build();

ToolExecutor validateOrderExecutor = (request, memoryId) -> {
    // validation logic
    return "valid";
};

Skill skill = Skill.builder()
        .name("process-order")
        .description("Processes a customer order end-to-end")
        .content("""
                To process an order:
                1. Call `validateOrder(orderId)` to check the order is valid.
                2. Call `chargePayment(orderId)`.
                """)
        .tools(Map.of(validateOrder, validateOrderExecutor))
        .build();

Skills skills = Skills.from(skill);

MyAiService service = AiServices.builder(MyAiService.class)
        .chatModel(chatModel)
        .chatMemory(MessageWindowChatMemory.withMaxMessages(100))
        .toolProviders(skills.toolProviders())
        .systemMessage("You have access to the following skills:\n" + skills.formatAvailableSkills()
                + "\nWhen the user's request relates to one of these skills, activate it first.")
        .build();
```

#### How Skill-Scoped Tools Work

1. Before skill activation, the LLM only sees the `activate_skill` (and `read_skill_resource`) tools.
   Skill-scoped tools are not included in the tool list.
2. When the LLM calls `activate_skill("process-order")`, the activation is recorded in the conversation messages.
3. Before the next LLM call (within the same AI Service invocation), the AI Service re-evaluates dynamic tool providers
   against the current messages. The skill-scoped tools (e.g. `validateOrder`) become
   visible and the LLM can call them immediately, in the same AI Service invocation.
   The skill-scoped tools stay visible to the LLM in the next AI Service invocations, they become invisible when
   the skill is deactivated. TODO

## Modes

Skills can be integrated with an AI Service in two distinct modes, depending on how much
control and trust you need.

### Tool Mode (Recommended)

**Class:** `Skills` (from the `langchain4j-skills` module)

This corresponds to the **Tool-based agents** integration approach described in the
[Agent Skills specification](https://agentskills.io/integrate-skills).

In this mode, the LLM activates a skill to receive step-by-step instructions, then carries
them out by calling the [tools](/tutorials/tools) you have explicitly registered.
**The LLM has no access to the file system at inference time** — all skill content and
resources are loaded into memory upfront (e.g. via `FileSystemSkillLoader`), and the `activate_skill`
and `read_skill_resource` tools returns that preloaded content rather than reading from disk.
Because only your pre-defined tools can be invoked, **there is no risk of arbitrary code execution**.

#### Registered Tools

| Tool                  | When registered                                                                               |
|-----------------------|-----------------------------------------------------------------------------------------------|
| `activate_skill`      | Always. The LLM calls this to load a skill's full instructions into the context.              |
| `read_skill_resource` | When at least one skill has resources. The LLM calls this to read individual reference files. |
| Skill-scoped tools    | After the skill is activated.                                                                 |

#### How It Works

1. The system message lists the available skills (names and descriptions) so the LLM can choose.
2. The user asks a question that requires a specific skill.
3. The LLM calls `activate_skill("my-skill")` to receive its instructions.
4. The LLM follows those instructions to complete the task, optionally reading resource files along the way.

#### Example Skill

Skills describe the _policy_ — the exact order of calls, required arguments, error-handling steps,
and worked examples — while the actual execution stays in type-safe, tested Java code:

```markdown
---
name: process-order
description: Processes a customer order end-to-end
---

To process an order:

1. Call `validateOrder(orderId)` to check the order is valid.
2. Call `reserveInventory(orderId)` to reserve the required stock.
3. Only if reservation succeeds, call `chargePayment(orderId)`.
4. Finally, call `sendConfirmationEmail(orderId)`.

If any step fails, call `rollbackOrder(orderId)` before reporting the error.
```

#### Wiring It Up

Pass the `ToolProvider` from `Skills` to your AI Service builder alongside your regular tools.
Use `formatAvailableSkills()` to inject the skill catalogue into the system message so
the LLM knows which skills it can activate:

```java
Skills skills = Skills.from(FileSystemSkillLoader.loadSkills(Path.of("skills/")));

MyAiService service = AiServices.builder(MyAiService.class)
        .chatModel(chatModel)
        .tools(new OrderTools()) // your tools
        .toolProviders(skills.toolProviders())
        .systemMessage("You have access to the following skills:\n" + skills.formatAvailableSkills()
                + "\nWhen the user's request relates to one of these skills, activate it first using the `activate_skill` tool before proceeding.")
        .build();
```

`formatAvailableSkills()` returns an XML-formatted block listing each skill's name and description:

```xml

<available_skills>
    <skill>
        <name>process-order</name>
        <description>Processes a customer order end-to-end</description>
    </skill>
    <skill>
        <name>data-analysis</name>
        <description>Analyse tabular data and produce charts</description>
    </skill>
</available_skills>
```

#### Customisation

The name, description, and parameter metadata of each tool can be overridden through the
corresponding config class on the builder:

```java
Skills skills = Skills.builder()
        .skills(mySkills)
        .activateSkillToolConfig(ActivateSkillToolConfig.builder()
                .name(...)                    // tool name (default: "activate_skill")
                .description(...)             // tool description
                .parameterName(...)           // parameter name (default: "skill_name")
                .parameterDescription(...)    // parameter description
                .throwToolArgumentsExceptions(...) // throw ToolArgumentsException instead of ToolExecutionException (default: false)
                .build())
        .readResourceToolConfig(ReadResourceToolConfig.builder()
                .name(...)                              // tool name (default: "read_skill_resource")
                .description(...)                       // tool description
                .skillNameParameterName(...)             // skill_name parameter name (default: "skill_name")
                .skillNameParameterDescription(...)      // skill_name parameter description
                .relativePathParameterName(...)          // relative_path parameter name (default: "relative_path")
                .relativePathParameterDescription(...)   // static description (takes precedence over provider)
                .relativePathParameterDescriptionProvider(...) // dynamic description based on available resources
                .throwToolArgumentsExceptions(...)       // throw ToolArgumentsException instead of ToolExecutionException (default: false)
                .build())
        .build();
```

### Shell Mode (Experimental)

**Class:** `ShellSkills` (from the `langchain4j-experimental-skills-shell` module)

This corresponds to the **Filesystem-based agents** integration approach described in the
[Agent Skills specification](https://agentskills.io/integrate-skills).

:::warning
**Shell execution is inherently unsafe.**
Commands run directly in the host process environment **without any sandboxing, containerization,
or privilege restriction**. A misbehaving or prompt-injected LLM can execute arbitrary commands
on the machine running your application.
Only use this in controlled environments where you fully trust the input and accept
the associated risks.
:::

In this mode, the LLM is given a single `run_shell_command` tool and reads skill instructions
directly from the file system using shell commands. There is no `activate_skill` or
`read_skill_resource` tool — the LLM navigates skill files like a human developer would.

#### Registered Tools

| Tool                | When registered                                                                                   |
|---------------------|---------------------------------------------------------------------------------------------------|
| `run_shell_command` | Always. The LLM runs shell commands to read `SKILL.md` files, resource files and execute scripts. |

#### How It Works

1. The system message lists available skills with their absolute filesystem paths.
2. The user asks a question that requires a specific skill.
3. The LLM runs `cat /path/to/skills/docx/SKILL.md` to read the instructions.
4. The LLM follows those instructions by running further shell commands.

#### Dependency

Shell execution lives in a separate experimental artifact — add it to your build:

```xml

<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-experimental-skills-shell</artifactId>
    <version>1.12.1-beta21</version>
</dependency>
```

#### Wiring It Up

All skills must be filesystem-based (loaded via `FileSystemSkillLoader`).
Use `ShellSkills` instead of `Skills`:

```java
ShellSkills skills = ShellSkills.from(FileSystemSkillLoader.loadSkills(Path.of("skills/")));

MyAiService service = AiServices.builder(MyAiService.class)
        .chatModel(chatModel)
        .toolProviders(skills.toolProviders())
        .systemMessage("You have access to the following skills:\n" + skills.formatAvailableSkills()
                + "\nWhen the user's request relates to one of these skills, read its SKILL.md before proceeding.")
        .build();
```

`formatAvailableSkills()` includes a `<location>` field so the LLM knows
exactly where to find each `SKILL.md`:

```xml

<available_skills>
    <skill>
        <name>docx</name>
        <description>Edit and review Word documents using tracked changes</description>
        <location>/path/to/skills/docx/SKILL.md</location>
    </skill>
    <skill>
        <name>data-analysis</name>
        <description>Analyse tabular data and produce charts</description>
        <location>/path/to/skills/data-analysis/SKILL.md</location>
    </skill>
</available_skills>
```

#### When to Use Shell Mode

This mode is best suited for **experimentation and prototyping**, or when you want to use
third-party skills published by the community (e.g. from the
[agentskills.io](https://agentskills.io) ecosystem) without first porting them to Java.
It lets you wire up a working workflow quickly, then migrate individual actions
to tools as the solution matures.

#### Customisation

Use `RunShellCommandToolConfig` to tune the working directory, output limits,
and parameter names:

```java
ShellSkills skills = ShellSkills.builder()
        .skills(mySkills)
        .runShellCommandToolConfig(RunShellCommandToolConfig.builder()
                .name(...)                              // tool name (default: "run_shell_command")
                .description(...)                       // tool description (default: includes OS name)
                .commandParameterName(...)              // command parameter name (default: "command")
                .commandParameterDescription(...)       // command parameter description
                .timeoutSecondsParameterName(...)       // timeout parameter name (default: "timeout_seconds")
                .timeoutSecondsParameterDescription(...) // timeout parameter description
                .workingDirectory(...)                  // working directory for commands (default: JVM's user.dir)
                .maxStdOutChars(...)                    // max stdout chars in result (default: 10_000)
                .maxStdErrChars(...)                    // max stderr chars in result (default: 10_000)
                .executorService(...)                   // ExecutorService for reading stdout/stderr streams
                .throwToolArgumentsExceptions(...)      // throw ToolArgumentsException instead of ToolExecutionException (default: false)
                .build())
        .build();
```
