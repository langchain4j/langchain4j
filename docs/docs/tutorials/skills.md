---
sidebar_position: 32
---

# Skills

Skills is a mechanism for equipping an LLM with reusable, self-contained behavioral instructions.
A skill bundles a name, a short description, and a body of instructions (its _content_),
together with optional reference files (_resources_).
The LLM loads a skill on demand, keeping the initial context small and only pulling in
the detailed instructions when they are actually needed.

:::note
Skills are designed around the [Agent Skills specification](https://agentskills.io).
The primary focus of the LangChain4j implementation is on **progressive loading of content
into the LLM context**, not on script execution.
:::

## How It Works

A `Skills` instance registers a fixed set of tools with LangChain4j's `ToolProvider` mechanism.
Exactly which tools are registered depends on your configuration:

| Tool | When registered |
|---|---|
| `activate_skill` | Always. The LLM calls this to load a skill's full instructions into the context. |
| `read_skill_resource` | When at least one skill has resources **and** `allowRunningShellCommands` is `false` (the default). The LLM calls this to read individual reference files. |
| `run_shell_command` | When `allowRunningShellCommands(true)` is set. Replaces `read_skill_resource`. |

A typical interaction looks like this:

1. The system message lists the available skills (names and descriptions) so the LLM can choose.
2. The user asks a question that requires a specific skill.
3. The LLM calls `activate_skill("my-skill")` to receive its instructions.
4. The LLM follows those instructions to complete the task, optionally reading resource files along the way.

## Creating Skills

### From the File System

The most convenient way to manage skills is with `FileSystemSkillLoader`.
Each skill lives in its own subdirectory containing a `SKILL.md` file.
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
2. Run `python scripts/accept_changes.py` to accept all pending changes before editing.
...
```

Loading all skills from a directory:

```java
List<Skill> skills = FileSystemSkillLoader.loadSkills(Path.of("skills/"));
Skills skills = Skills.from(skills);
```

Loading a single skill:

```java
Skill skill = FileSystemSkillLoader.loadSkill(Path.of("skills/docx"));
```

Any file in the skill directory (other than `SKILL.md` itself and files under a `scripts/`
subdirectory) is automatically loaded as a `SkillResource` that the LLM can read via the
`read_skill_resource` tool.

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

Skills skills = Skills.from(skill);
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

## Use Cases

Skills can be integrated with an AI Service in two distinct ways, depending on how much
control and trust you need.

### Use Case 1: Skills with Tools (Recommended)

In this mode the LLM activates a skill to receive step-by-step instructions, then carries
them out by calling the [Tools (function calling)](/tutorials/tools) you have explicitly
registered. Because only your pre-defined `@Tool` methods can be invoked, **there is no
risk of arbitrary code execution**.

This is the recommended approach for production. Skills describe the *policy* — the exact
order of calls, required arguments, error-handling steps, and worked examples — while the
actual execution stays in type-safe, tested Java code:

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

Pass the `ToolProvider` from `Skills` to your AI Service builder alongside your regular tools.
Use `formatNamesAndDescriptions()` to inject the skill catalogue into the system message so
the LLM knows which skills it can activate:

```java
Skills skills = Skills.from(FileSystemSkillLoader.loadSkills(Path.of("skills/")));

MyAiService service = AiServices.builder(MyAiService.class)
        .chatModel(chatModel)
        .tools(new OrderTools()) // your @Tool methods
        .toolProvider(skills.toolProvider()) // or .toolProviders(mcpToolProvider, skills.toolProvider())
        .systemMessage("You have access to the following skills:\n" + skills.formatNamesAndDescriptions()
                + "\nWhen the user's request relates to one of these skills, activate it first using the `activate_skill` tool before proceeding.")
        // or, if you already have a system message configured:
        // .systemMessageTransformer(systemMessage -> systemMessage + "\n\nYou have access to the following skills:\n" + skills.formatNamesAndDescriptions()
        //         + "\nWhen the user's request relates to one of these skills, activate it first using the `activate_skill` tool before proceeding.")
        .build();
```

`formatNamesAndDescriptions()` returns an XML-formatted block listing each skill's name and description, for example:

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

### Use Case 2: Skills with Scripts (Experimental)

:::warning
**Script execution is inherently unsafe.**
Commands run directly in the host process environment **without any sandboxing, containerisation,
or privilege restriction**. A misbehaving or prompt-injected LLM can execute arbitrary commands
on the machine running your application.
Only enable this feature in controlled environments where you fully trust the input and accept
the associated risks.
:::

Script execution is **disabled by default**. Enable it with `allowRunningShellCommands(true)`:

```java
Skills skills = Skills.builder()
        .skills(FileSystemSkillLoader.loadSkills(Path.of("skills/")))
        .allowRunningShellCommands(true)
        .build();

MyAiService service = AiServices.builder(MyAiService.class)
        .chatModel(chatModel)
        .toolProvider(skills.toolProvider())
        .systemMessage("You have access to the following skills:\n" + skills.formatNamesAndDescriptions()
                + "\nWhen the user's request relates to one of these skills, activate it first using the `activate_skill` tool before proceeding.")
        // or, if you already have a system message configured:
        // .systemMessageTransformer(systemMessage -> systemMessage + "\n\nYou have access to the following skills:\n" + skills.formatNamesAndDescriptions()
        //         + "\nWhen the user's request relates to one of these skills, activate it first using the `activate_skill` tool before proceeding.")
        .build();
```

When enabled, the `run_shell_command` tool is registered instead of `read_skill_resource`.
The LLM can run any shell command, optionally scoped to a specific skill's root directory
as the working directory.

This mode is best suited for **experimentation, prototyping**, or when you want to use
third-party skills published by the community (e.g. from the
[agentskills.io](https://agentskills.io) ecosystem) without first porting them to Java.
It lets you wire up a working workflow quickly, then migrate individual actions to proper
`@Tool` methods as the solution matures.

## Customisation

The name, description, and parameter metadata of each tool can be overridden through the
corresponding config class on the builder:

```java
Skills skills = Skills.builder()
        .skills(mySkills)
        .activateSkillToolConfig(ActivateSkillToolConfig.builder()
                .description("Load the instructions for a skill by name")
                .build())
        .readResourceToolConfig(ReadResourceToolConfig.builder()
                .description("Read a reference file belonging to the active skill")
                .build())
        .build();
```

When shell commands are enabled, use `RunShellCommandToolConfig` to tune timeouts,
output limits, and parameter names:

```java
Skills skills = Skills.builder()
        .skills(mySkills)
        .allowRunningShellCommands(true)
        .runShellCommandToolConfig(RunShellCommandToolConfig.builder()
                .maxStdOutChars(5_000)
                .maxStdErrChars(2_000)
                .build())
        .build();
```
