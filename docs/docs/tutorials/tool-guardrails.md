---
sidebar_position: 12.5
toc_max_heading_level: 4
---

import Tabs from '@theme/Tabs';
import TabItem from '@theme/TabItem';

# Tool Guardrails

:::note
Tool guardrails are an experimental feature. Their API and behavior might change in future versions.
:::

Tool guardrails validate or rewrite what goes into a [tool](/tutorials/tools) and what comes out of it. They are a
sibling of the [guardrails](/tutorials/guardrails) that validate the user message and the LLM response of an
[AI Service](/tutorials/ai-services), and they reuse the same machinery (guardrail results, executors, events and
listeners).

- A [**tool input guardrail**](#tool-input-guardrails) runs _before_ a tool is executed. It sees the arguments the
  LLM chose and can accept them, rewrite them or reject the call.
- A [**tool output guardrail**](#tool-output-guardrails) runs _after_ a tool was executed. It sees the result of the
  tool, including an error result, and can accept it, rewrite it or reject it, _before_ the LLM sees it.

## Why tool guardrails

The main motivation is [**indirect prompt injection**](https://genai.owasp.org/llmrisk/llm01-prompt-injection/).
The text that a tool returns (a fetched web page, a search snippet, the answer of an MCP server, even an exception
message that echoes remote content) goes straight to the LLM, and the LLM may treat instructions hidden in it as if
they came from the user. A tool output guardrail is the place to detect, block or redact such content.

Tool input guardrails are the other half: the LLM chooses the arguments of a tool call, so they are untrusted too.
An input guardrail can enforce limits (an allowed host, a maximum amount), normalize values, or block a call
altogether.

Tool guardrails work the same way for every kind of tool:

- `@Tool` methods
- tools specified [programmatically](/tutorials/tools#specifying-tools-programmatically) (`tools(Map)`,
  `tools(List<AiServiceTool>)`)
- tools returned by a [`ToolProvider`](/tutorials/tools#specifying-tools-dynamically)
- [MCP](/tutorials/mcp#guarding-mcp-tools) tools

and in every mode of an AI Service: blocking, `CompletableFuture`, `TokenStream` and reactive
(see [Non-blocking and Reactive](/tutorials/non-blocking)).

Tool guardrails run for the tools that LangChain4j executes. They do not run for:

- the built-in tool search tool of a [`ToolSearchStrategy`](/tutorials/tools#tool-search). They run for the tools
  that it finds;
- tools that the LLM provider runs on its own side (for example the web search and web fetch server tools of
  Anthropic, the built-in web search of OpenAI, or Google Search grounding in Gemini). Their results reach the LLM
  at the provider, before LangChain4j sees them.

## Tool Input Guardrails

A tool input guardrail implements
[`ToolInputGuardrail`](https://github.com/langchain4j/langchain4j/blob/main/langchain4j-core/src/main/java/dev/langchain4j/guardrail/ToolInputGuardrail.java)
and its `validate` method, which receives a
[`ToolInputGuardrailRequest`](https://github.com/langchain4j/langchain4j/blob/main/langchain4j-core/src/main/java/dev/langchain4j/guardrail/ToolInputGuardrailRequest.java):

```java
public class AllowedHostsInputGuardrail implements ToolInputGuardrail {

    @Override
    public ToolInputGuardrailResult validate(ToolInputGuardrailRequest request) {
        if (!request.toolName().equals("fetch_page")) {
            return success(); // this guardrail only guards one tool
        }

        String url = String.valueOf(request.argumentsAsMap().get("url"));
        return url.startsWith("https://docs.example.com/")
                ? success()
                : failure("Only pages of https://docs.example.com can be fetched. Do not retry with another host.");
    }
}
```

`fetch_page` is the name of the tool, and `url` the name of its parameter, as declared in the
[`WebTools` class](#annotations) below. The name of a `@Tool` method is the method name, unless `@Tool(name = ...)`
sets another one.

The request gives access to:

| Accessor                                  | Description                                                                                                                                                                      |
|:------------------------------------------|:---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `toolName()`                              | The name of the tool, as the LLM sees it.                                                                                                                                        |
| `arguments()` / `argumentsAsMap()`        | The arguments the LLM chose, as the raw JSON string or as a read-only map. They are **untrusted**.                                                                                |
| `toolExecutionRequest()`                  | The whole tool execution request.                                                                                                                                                |
| `toolSpecification()`                     | The specification of the tool, if known.                                                                                                                                         |
| `toolSource()`                            | Where the tool comes from. See [Telling the tools apart](#telling-the-tools-apart-toolsource).                                                                                    |
| `invocationContext()` / `memoryId()`      | The context of the AI Service invocation (invocation id, chat memory id, method arguments, ...).                                                                                  |
| `requestParams()`                         | The [`GuardrailRequestParams`](https://github.com/langchain4j/langchain4j/blob/main/langchain4j-core/src/main/java/dev/langchain4j/guardrail/GuardrailRequestParams.java) of the AI Service invocation. |

### Tool Input Guardrail Outcomes

There are helper methods on the `ToolInputGuardrail` interface for the outcomes:

| Outcome                         | Helper method                                  | Description                                                                                                                                                                                                                                                                                   |
|:--------------------------------|:-----------------------------------------------|:----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **_success_**                   | `success()`                                    | - The arguments are valid.<br/> - The next guardrail in the chain is executed, then the tool.                                                                                                                                                                                                 |
| **_success with rewrite_**      | `successWith(String)`                          | - The argument is a JSON object, the new arguments of the tool call. `null` means no rewrite (a plain success). Anything else (malformed JSON, an array, a number) is treated as a fatal failure of the guardrail: the invocation is aborted with a `ToolInputGuardrailException` before the tool runs.<br/> - The following guardrails and the tool receive the rewritten arguments, and so do the output guardrails, the `afterToolExecution` callback and the [events](#observability). `beforeToolExecution` runs before the guardrails and sees the original arguments.<br/> - The rewritten arguments also replace the tool call in the chat memory (the `AiMessage` with the tool call is updated), so the LLM sees them in its later requests. Put values that only the tool needs in [`InvocationParameters`](/tutorials/tools#invocationparameters), not in the arguments. |
| **_failure_**                   | `failure(String)` or `failure(String, Throwable)` | - The call is rejected and **the tool is not executed**.<br/> - The remaining guardrails of the chain still run, to accumulate all the failures.<br/> - The message is sent to the LLM as an error tool result. See [Failures](#failures-are-error-results-for-the-llm).                       |
| **_fatal_**                     | `fatal(String)` or `fatal(String, Throwable)`  | - The call is rejected, **the tool is not executed**, the remaining guardrails do not run, and the AI Service invocation is aborted with a `ToolInputGuardrailException`. See [Fatal failures](#fatal-failures).                                                                                |

### Arguments that cannot be parsed

The LLM sometimes produces arguments that are not valid JSON. `argumentsAsMap()` then throws a
`ToolArgumentsException`. A guardrail does not need to catch it: the tool is not executed, and the call is handled
like any other arguments error, by the
[`ToolArgumentsErrorHandler`](/tutorials/tools#handling-tool-arguments-errors) of the AI Service. The handler decides
whether the invocation goes on. When you do not set one, it depends on the mode of the AI Service:

- blocking and `TokenStream`: the default handler fails the invocation;
- `CompletableFuture` and `Flow.Publisher`: the error is sent to the LLM, which can correct the arguments.

To send the error to the LLM in every mode, set
`.toolArgumentsErrorHandler(ToolArgumentsErrorHandler.sendExceptionMessageToLlm())` on the AI Service builder. The
handler, the chat memory and the
[events](#observability) see the tool call as the LLM sent it, even when an earlier guardrail rewrote its arguments.

Any other exception thrown by a guardrail aborts the invocation, like a [fatal failure](#fatal-failures).

## Tool Output Guardrails

A tool output guardrail implements
[`ToolOutputGuardrail`](https://github.com/langchain4j/langchain4j/blob/main/langchain4j-core/src/main/java/dev/langchain4j/guardrail/ToolOutputGuardrail.java),
which receives a
[`ToolOutputGuardrailRequest`](https://github.com/langchain4j/langchain4j/blob/main/langchain4j-core/src/main/java/dev/langchain4j/guardrail/ToolOutputGuardrailRequest.java):

```java
public class InjectionOutputGuardrail implements ToolOutputGuardrail {

    private static final Pattern INJECTION = Pattern.compile(
            "ignore (all )?(previous|prior) instructions", Pattern.CASE_INSENSITIVE);

    @Override
    public ToolOutputGuardrailResult validate(ToolOutputGuardrailRequest request) {
        return INJECTION.matcher(request.text()).find()
                // the message does not quote the content: that would pass the injection to the LLM anyway
                ? failure("The tool result contains instructions and was blocked. Do not retry.")
                : success();
    }
}
```

The output request has the same accessors as the input request (`toolName()`, `arguments()`, `toolSource()`, ...),
where `arguments()` are the arguments the tool was executed with, and adds:

| Accessor           | Description                                                                                                                                                                                      |
|:-------------------|:-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `resultContents()` | **All** the contents of the tool result (text, images, ...), see [Multi-content results](#multi-content-results).                                                                                  |
| `text()`           | The texts of all the text contents, joined with a newline. It never throws, unlike `ToolExecutionResult.resultText()`, and is an empty string if there is no text content.                       |
| `isError()`        | Whether the result is an error result: the tool threw, an MCP server answered with `isError`, or an earlier guardrail rewrote it into an error. See [Error results](#output-guardrails-also-see-error-results). |

### Tool Output Guardrail Outcomes

| Outcome                                       | Helper method                                          | Description                                                                                                                                                                                                                                                                                                                                                         |
|:----------------------------------------------|:-------------------------------------------------------|:--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **_success_**                                 | `success()`                                            | - The result is valid.<br/> - The next guardrail in the chain is executed, then the LLM sees the result.                                                                                                                                                                                                                                                            |
| **_success with rewritten text_**             | `successWith(String)`                                  | - Replaces the text contents of the result with the given text. Non-text contents and the `isError` flag **are kept**.<br/> - `null` means no rewrite (a plain success). To remove the text, prefer a short placeholder (such as "The content was removed.") to an empty text, which some LLM providers reject.<br/> - Use it to redact or sanitize: a sanitized success stays a success, and a sanitized error result stays an error (see [Compensation](#compensation)).                                                                                  |
| **_success with rewritten contents_**         | `successWith(List<Content>, boolean isError)`          | - Replaces **all** the contents of the result **and** its `isError` flag.<br/> - The only way to turn a result into an error result, or an error result into a success, without a failure. The list must not be empty.                                                                                                                                              |
| **_failure_**                                 | `failure(String)` or `failure(String, Throwable)`      | - The result is replaced by an error result with the message. The LLM never sees the original result.<br/> - The remaining guardrails of the chain still run, to accumulate all the failures.<br/> - See [Failures](#failures-are-error-results-for-the-llm).                                                                                                          |
| **_fatal_**                                   | `fatal(String)` or `fatal(String, Throwable)`          | - The remaining guardrails do not run, and the AI Service invocation is aborted with a `ToolOutputGuardrailException`.<br/> - See [Fatal failures](#fatal-failures).                                                                                                                                                                                               |

### Output guardrails also see error results

A tool output guardrail runs on **every** result of a tool that is sent to the LLM, including error results
(`isError() == true`). An exception of the tool becomes such an error result when the
[`ToolExecutionErrorHandler`](/tutorials/tools#error-handling) sends it to the LLM, which the default handler does in
the blocking and `TokenStream` modes. In the `CompletableFuture` and `Flow.Publisher` modes, the default handler fails
the invocation instead, so the output guardrails do not run for that exception. The text of an error result is often
attacker-controllable too: an MCP server can put remote content in the error it
returns, and the message of an exception can echo what the tool fetched. A guardrail that is not interested in
errors opts out:

```java
@Override
public ToolOutputGuardrailResult validate(ToolOutputGuardrailRequest request) {
    if (request.isError()) {
        return success(); // error results are not checked
    }
    // ... check request.text()
    return success();
}
```

`successWith(String)` keeps the `isError` flag of the result it rewrites, so sanitizing an error result can never
turn it into a success. Only `successWith(List<Content>, boolean)` can change the flag.

A tool that [returns immediately](/tutorials/tools#returning-immediately-the-result-of-a-tool-execution-request) is
guarded too. A call that a guardrail rejects does not return immediately: the LLM gets the failure message and is
called again. (The `@OutputGuardrails` of the AI Service do not run on an immediate return; tool guardrails do.)

What the AI Service returns depends on its return type:

- `Result<T>`: the guarded result is in `Result#toolExecutions()`, as `ToolExecution#result()` (or `resultContents()`).
- Any other type, such as `String`: the AI Service returns the Java object that the `@Tool` method returned,
  `ToolExecution#resultObject()`. An output guardrail that rewrites the result drops that object, since it no longer
  matches what the LLM sees, so the tool has no object to return, like a programmatic or MCP tool, and the AI Service
  returns `null`. Use `Result<T>` to get the guarded result of a tool whose output a guardrail can rewrite.

With any return type, the `afterToolExecution` callback, `TokenStream#onToolExecuted` and the `ToolExecutedEvent`
also get the guarded result.

### Multi-content results

A tool result can contain several contents, such as text and images (see
[Multimodal Tool Results](/tutorials/tools#multimodal-tool-results)). A tool output guardrail sees all of them in
`resultContents()`, and the `text()` helper never throws for non-text contents. To rewrite them, either replace
only the text with `successWith(String)`, or build the new list of contents:

```java
@Override
public ToolOutputGuardrailResult validate(ToolOutputGuardrailRequest request) {
    List<Content> textOnly = request.resultContents().stream()
            .filter(TextContent.class::isInstance)
            .toList();

    if (textOnly.size() == request.resultContents().size()) {
        return success(); // nothing to remove
    }

    // a tool result always has contents, so say what happened when nothing is left
    List<Content> rewritten = textOnly.isEmpty()
            ? List.of(TextContent.from("The tool returned only images, which were removed."))
            : textOnly;

    return successWith(rewritten, request.isError()); // keep the isError flag unless you mean to change it
}
```

The list given to `successWith(List<Content>, boolean)` must not be empty: an empty list makes the guardrail throw,
which aborts the invocation like a [fatal failure](#fatal-failures).

## Failures are error results for the LLM

A **non-fatal** failure (`failure(...)`) does not abort anything. It becomes an **error tool result**
(`isError == true`) that is sent to the LLM like any other tool result, so the LLM can correct itself: pick other
arguments, choose another tool, or give up. Many LLM providers have no error flag for tool results, so the message
may be all that the LLM gets: write it so that it reads as a rejection and says what to do next.

- The text of the error result is the failure message of the guardrail, **verbatim**. LangChain4j adds no prefix and
  no wrapper text, so the message is entirely under your control. If several guardrails of the chain fail, their
  messages are joined with a newline, in the order of the chain.
- A failing input guardrail means the tool is **not executed**.
- A failing output guardrail means the tool **was executed**, and its real result is replaced.
- Like AI service guardrails, **a chain keeps running after a non-fatal failure**, and failures are aggregated.
  A guardrail that costs money (for instance one that calls an LLM) still runs after an earlier guardrail failed.
  Within one level (the builder, a class annotation, a method annotation or an `AiServiceTool`), put the cheap and
  most selective guardrails first. The guardrails given to the builder always run first (see
  [Resolution order](#resolution-order-every-guardrail-runs)).

### Fatal failures

A **fatal** failure (`fatal(...)`) stops the chain and aborts the AI Service invocation:

- A `ToolInputGuardrailException` or a `ToolOutputGuardrailException` (both extend `GuardrailException`) is thrown to
  the caller. With a `CompletableFuture` or a reactive AI Service, the future fails or the publisher signals
  `onError` with it; with a `TokenStream`, it goes to `onError`. It is never thrown from a callback thread.
- It **bypasses** the [`ToolExecutionErrorHandler`](/tutorials/tools#error-handling): the handler is for errors of
  the tool itself, and a guardrail rejection is a policy decision that the LLM must not be able to negotiate with.
- The LLM does not get a chance to retry.

`ToolInputGuardrailException#arguments()` gives the arguments that the failing guardrail validated.

:::warning
A fatal failure ends the invocation and leaves the tool call without a result in the
[chat memory](/tutorials/chat-memory). With a persistent chat memory, that conversation keeps a tool call that never
got an answer. For content that the LLM should simply skip, prefer `failure(..)` with a message such as
"The result was blocked. Do not retry.", and keep `fatal(..)` for the cases where the whole invocation must stop.
:::

### No retry and no reprompt

There is no counterpart of `retry` or `reprompt` of [output guardrails](/tutorials/guardrails#output-guardrails),
and no `maxRetries`. The failure message already is the feedback to the LLM: it decides what to do next. A model
that keeps calling a tool will be stopped by `maxToolCallingRoundTrips` (default: 100).

## Declaring tool guardrails

There are four ways to declare tool guardrails.

:::note
One guardrail instance serves every tool it applies to and every invocation of the AI Service, and it can run
concurrently, for example when tools run in parallel with
[`executeToolsConcurrently()`](/tutorials/tools#executing-tools-concurrently) or when several invocations run at
the same time. Make your guardrails thread-safe, and keep the state of one call in local variables, not in fields.
:::

### `AiServices` builder

Guardrails set on the builder apply to **every** tool of the AI Service, including tools that have no method (MCP
and `ToolProvider` tools, programmatic tools). The built-in [tool search](/tutorials/tools#tool-search) tool is the
only exception: it is not guarded. There are instance and class variants, like for
[input guardrails](/tutorials/guardrails#declaring-input-guardrails):

```java
Assistant assistant = AiServices.builder(Assistant.class)
        .chatModel(chatModel)
        .tools(new WebTools())
        .toolInputGuardrails(new AllowedHostsInputGuardrail())
        .toolOutputGuardrailClasses(InjectionOutputGuardrail.class)
        .build();
```

The methods are `toolInputGuardrails(...)`, `toolInputGuardrailClasses(...)`, `toolOutputGuardrails(...)`,
`toolOutputGuardrailClasses(...)`, and `toolInputGuardrailsConfig(...)` / `toolOutputGuardrailsConfig(...)`.
Classes are instantiated with their default constructor, or by the
[`ClassInstanceFactory`](/tutorials/guardrails#extension-points) of your framework.

### Annotations

[`@ToolInputGuardrails`](https://github.com/langchain4j/langchain4j/blob/main/langchain4j/src/main/java/dev/langchain4j/service/guardrail/ToolInputGuardrails.java)
and
[`@ToolOutputGuardrails`](https://github.com/langchain4j/langchain4j/blob/main/langchain4j/src/main/java/dev/langchain4j/service/guardrail/ToolOutputGuardrails.java)
can be placed on a tool **method** or on the **class** that declares the tools:

```java
@ToolOutputGuardrails(InjectionOutputGuardrail.class) // every tool of the class
class WebTools {

    @Tool(name = "fetch_page", value = "Fetches a web page")
    @ToolInputGuardrails(AllowedHostsInputGuardrail.class) // this tool only
    String fetchPage(@P(name = "url", value = "The URL of the page") String url) {
        return "<html>...</html>"; // fetch the page here
    }

    @Tool("Searches the web")
    String search(@P(name = "query", value = "What to search for") String query) {
        return "1. ..."; // search the web here
    }
}
```

`@P(name = "url")` gives the parameter a fixed name. Without it, the name comes from the compiled class, and it is
`arg0` unless the code is compiled with the `-parameters` option of `javac`. A guardrail that reads
`argumentsAsMap().get("url")` needs that name.

:::note
These annotations are unrelated to `@InputGuardrails` and `@OutputGuardrails`. `@InputGuardrails` applies to the user
message of an AI Service method, `@ToolInputGuardrails` applies to the arguments of a tool call. When a class is both
an AI Service and a tool (see
[AI services as tools](/tutorials/tools#ai-services-as-tools-for-other-ai-services)), each annotation keeps its own
meaning.

`@ToolInputGuardrails` and `@ToolOutputGuardrails` on an AI Service interface, or on its methods, do not guard the
tools of that AI Service. Set those guardrails on the `AiServices` builder (see above), or on the tool classes.
:::

### `AiServiceTool`

A tool that has no method can carry its guardrails (and its [source](#telling-the-tools-apart-toolsource)) on its
[`AiServiceTool`](/tutorials/tools#specifying-tools-programmatically):

```java
AiServiceTool tool = AiServiceTool.builder()
        .toolSpecification(toolSpecification)
        .toolExecutor(toolExecutor)
        .toolInputGuardrails(new AllowedHostsInputGuardrail())
        .toolOutputGuardrails(new InjectionOutputGuardrail())
        .build();
```

This also works for the tools that a `ToolProvider` returns.

### `AgentBuilder`

The builder of the agents of the [`langchain4j-agentic`](/tutorials/agents) module has the setters
`toolInputGuardrails(...)`, `toolInputGuardrailClasses(...)`, `toolOutputGuardrails(...)` and
`toolOutputGuardrailClasses(...)`, which take varargs (there are no `List` variants), plus the two
`...GuardrailsConfig(...)` setters. They are passed to the `AiServices` of the agent:

```java
CreativeWriter writer = AgenticServices.agentBuilder(CreativeWriter.class)
        .chatModel(chatModel)
        .tools(new WebTools())
        .toolOutputGuardrails(new InjectionOutputGuardrail())
        .build();
```

## Resolution order: every guardrail runs

:::warning
This **differs from AI service guardrails**. For `@InputGuardrails` and `@OutputGuardrails`, the guardrails set on
the `AiServices` builder _replace_ the annotations, and a method annotation replaces the class annotation. **Tool
guardrails never replace each other: every tool guardrail that applies to a tool runs.**
:::

For each tool, the guardrails of all the levels form **one chain**, resolved separately for input and output, in
this order:

1. the guardrails set on the `AiServices` (or `AgentBuilder`) builder
2. the guardrails of the `@ToolInputGuardrails` / `@ToolOutputGuardrails` annotation on the class
3. the guardrails of the annotation on the method
4. the guardrails of the tool's `AiServiceTool`

Within the builder level, the guardrail instances (`toolInputGuardrails(...)`, `toolOutputGuardrails(...)`) run
before the guardrails created from classes (`toolInputGuardrailClasses(...)`, `toolOutputGuardrailClasses(...)`),
whatever order the setters were called in.

Why not replace? A global injection check on the builder and a tool-specific check (a size limit on one tool) must
both run, and MCP and `ToolProvider` tools can _only_ be guarded from the builder or their `AiServiceTool`: with
replace semantics, every per-tool guardrail would silently stop running as soon as you add a global one.

### Skipping some tools

Tool guardrails have no per-tool opt-out. A guardrail that was declared globally, or on a class, and must not apply to
some tools, skips them itself, using `toolName()` or [`toolSource()`](#telling-the-tools-apart-toolsource).

For a security check, check every tool by default, and skip only the tools that you list as trusted. A guardrail
that checks only the tools it knows about would silently skip any new tool, or any MCP server that you add later:

```java
public class ExternalContentOutputGuardrail implements ToolOutputGuardrail {

    // the MCP servers you trust, by the key of their McpClient
    private static final Set<String> TRUSTED_SERVERS = Set.of("internal-docs");

    // local tools that never return external content
    private static final Set<String> SAFE_LOCAL_TOOLS = Set.of("clock");

    @Override
    public ToolOutputGuardrailResult validate(ToolOutputGuardrailRequest request) {
        if (isTrusted(request)) {
            return success();
        }
        // ... inspect request.text()
        return success();
    }

    private static boolean isTrusted(ToolOutputGuardrailRequest request) {
        if (request.toolSource() instanceof ToolSource.Mcp mcp) {
            // skip only if the keys are known and all of them are trusted
            return !mcp.serverKeys().isEmpty() && TRUSTED_SERVERS.containsAll(mcp.serverKeys());
        }
        return request.toolSource() instanceof ToolSource.Local
                && SAFE_LOCAL_TOOLS.contains(request.toolName());
    }
}
```

`serverKeys()` holds only the keys that are known: a client without a key adds none. So a trust decision must
check that there is at least one key and that **all** of them are trusted. `involves("internal-docs")` alone is not
enough: it is also `true` for a tool whose source spans a trusted and an untrusted server. A `DefaultMcpClient`
built without `key(...)` gets a random key, which is never in your list, so its tools are checked (see
[Guarding MCP tools](/tutorials/mcp#guarding-mcp-tools)).

### Duplicates

The same guardrail is not executed twice for one tool:

- The same guardrail **class**, declared by annotations or by the `...GuardrailClasses(...)` setters at one or
  several levels, runs **once**, at the first position where it appears (the builder comes first). Guardrails
  created from a class are all configured the same way, so running one of them is enough.
- **Instances** are only duplicates of themselves: the same object passed twice runs once. Two instances of the
  same class run both, because they may be configured differently (for instance two regular-expression
  guardrails with different patterns), and silently dropping a check is worse than running one twice.
- An instance on the builder and an annotation that names the same class both run: LangChain4j cannot tell
  whether the instance is configured like the one it would create.

## Telling the tools apart: `ToolSource`

A global guardrail sees all the tools, so it needs a way to tell them apart. Both requests have a
[`toolSource()`](https://github.com/langchain4j/langchain4j/blob/main/langchain4j-core/src/main/java/dev/langchain4j/agent/tool/ToolSource.java),
which is one of:

| `ToolSource`                | Meaning                                                                                                                                                   |
|:----------------------------|:----------------------------------------------------------------------------------------------------------------------------------------------------------|
| `ToolSource.Local`          | A local `@Tool` method: `declaringClassName()`, `methodName()` and `parameterTypeNames()`.                                                                |
| `ToolSource.Mcp`            | One or more MCP servers: `serverKeys()` and `involves(String serverKey)`. See [Guarding MCP tools](/tutorials/mcp#guarding-mcp-tools).                    |
| `ToolSource.Programmatic`   | A tool registered programmatically, without an explicit source. This is also the default for the tools of a custom `ToolProvider`.                        |

The source is **trusted** information. It is set by LangChain4j or by your application when the tool is registered,
and it is **never** derived from remote data such as the tool specification's metadata or the hints an MCP server
sends. A guardrail can therefore base security decisions on it.

All programmatic tools have the same source, `ToolSource.programmatic()`, which is the default, so a guardrail tells
them apart by `toolName()`. A `ToolSource` can only be created with `ToolSource.local(...)`, `ToolSource.mcp(...)` or
`ToolSource.programmatic()`. When a tool of a custom `ToolProvider` or of `tools(List<AiServiceTool>)` runs a local
method or calls an MCP server, you can give it the matching source with `toolSource(...)` on its
`AiServiceTool.builder()`.

## Compensation

When [compensation](/tutorials/tools#compensating-tool-actions) is enabled (`compensateOnToolErrors(true)`), "an
error result that the LLM sees means a failed step" applies to guardrails too. The rule depends on the **final
result the LLM ends up with**, not on which guardrail method was called, so guardrails need no special case:

| What happens                                                                                                              | Failed step? | What is rolled back                                                                                                                                           | `CompensationReason`           |
|:--------------------------------------------------------------------------------------------------------------------------|:-------------|:--------------------------------------------------------------------------------------------------------------------------------------------------------------|:-------------------------------|
| An **input** guardrail rejects the call (`failure(..)`)                                                                   | Yes          | The other compensable tools. The rejected tool never ran, so there is nothing to roll back for it.                                                            | `TOOL_INPUT_GUARDRAIL_FAILED`  |
| An **output** guardrail turns a successful result into an error result (`failure(..)`, or a rewrite with `isError == true`) | Yes          | **All** compensable tools, **including the rejected one**, which stays compensable with its original result (the one before the guardrail).                    | `TOOL_OUTPUT_GUARDRAIL_FAILED` |
| An output guardrail **rewrites** the result and keeps `isError == false` (`successWith(String)`)                          | No           | Nothing. This is the way to sanitize a result without rolling anything back.                                                                                  | -                              |
| The tool **returned an error result** and an output guardrail keeps it an error (`successWith(String)` keeps the flag)    | Yes          | Like any tool failure: the other compensable tools; the tool itself did not succeed so it is not compensable.                                                 | `TOOL_EXECUTION_FAILED`        |
| An output guardrail turns an error result into a success (`successWith(contents, false)`)                                 | No           | Nothing, and the tool is not compensable because it did not succeed.                                                                                          | -                              |
| A guardrail returns **fatal**                                                                                             | Like a thrown tool error | Like a tool error that ends the call, with the rules of the input and output rows above: the compensable tools are rolled back, then the invocation fails. See the note below. | `TOOL_INPUT_GUARDRAIL_FAILED` or `TOOL_OUTPUT_GUARDRAIL_FAILED` |

- The LLM sees the message of the rejecting guardrail for the rejected tool **verbatim**, not the "rolled back"
  text. The other tools that were rolled back get the usual rolled-back text.
- **How far a failure reaches:** every compensable tool of the **whole invocation** is rolled back, not only the
  ones of the current tool-calling round. With
  [cross-agent compensation](/tutorials/agents#cross-agent-compensation) it also includes the compensable tools
  that **other agents ran earlier**.
- If the only goal of a guardrail on a read-only tool is to sanitize the result, use `successWith(..)`: a
  rejection with `failure(..)` rolls back the writes that happened earlier in the invocation.
- When a chain has several guardrails, only the **final** result counts.
- If a round has several failures, the first failing tool in request order decides the reason.
- A compensating action always gets the result that the tool returned, before its output guardrails, also when a
  guardrail sanitized that result with `successWith(..)` and the tool is rolled back later because of another tool.

:::note
A fatal guardrail failure is treated like a tool error that ends the call, in every AI Service mode (a plain return
value, `TokenStream`, `CompletableFuture` and `Flow.Publisher`): the compensable tools are rolled back first, and then
the invocation fails with the guardrail's exception. If a compensating action fails, its exception is added as a
suppressed exception to the guardrail's exception (see
[Compensating Tool Actions](/tutorials/tools#compensating-tool-actions)).
:::

## Security notes

- **Never quote the checked content in a failure message.** A message such as
  `Blocked suspicious instruction: "<snippet>"` sends the injection to the LLM anyway, because the message is sent
  to the LLM verbatim.
- **A guardrail that uses an LLM to judge content can be injected itself.** Pass the tool result to the judge as
  data (clearly delimited, never as instructions), or combine the judge with a check that is not an LLM.
- **A rejected output usually comes back identical on retry** (the same URL gives the same page). The LLM may call
  the tool again, up to `maxToolCallingRoundTrips` times (default: 100), and each run of an LLM-based guardrail is a
  paid call. Write the failure message so that it tells the LLM not to retry, or use `fatal(...)` when the whole
  invocation must stop (see the warning in [Fatal failures](#fatal-failures)). Lower `maxToolCallingRoundTrips` to
  bound the cost.
- **`text()` only covers the text contents.** Images and other non-text contents of a tool result pass through a
  guardrail that only checks `text()`, and `successWith(String)` keeps them. For untrusted sources, drop or check
  them with `successWith(List<Content>, boolean)` (see [Multi-content results](#multi-content-results)).
- **Some texts that reach the LLM are not guarded.** Tool guardrails do not see:
  - the tool names, descriptions and parameter schemas that MCP servers send;
  - the output of the built-in [tool search](/tutorials/tools#tool-search) tool;
  - the results of tools that the LLM provider runs on its own side (see [Why tool guardrails](#why-tool-guardrails));
  - the result for a tool name that the LLM made up (see
    [Handling Tool Name Errors](/tutorials/tools#handling-tool-name-errors));
  - the text that a `ToolArgumentsErrorHandler` produces when an input guardrail cannot parse the arguments (see
    [Arguments that cannot be parsed](#arguments-that-cannot-be-parsed)).
- **A tool guardrail is not a sandbox.** It checks what you tell it to check. Combine it with the least privilege
  principle for the tools themselves.
- Tool arguments are untrusted. Use [`InvocationParameters`](/tutorials/tools#invocationparameters) or the
  [chat memory id](/tutorials/tools#toolmemoryid), not an argument the LLM supplies, to decide _whose_ data a tool
  reads.

## Observability

Each tool guardrail that returns a result fires an event, which you can listen to with a listener, like for
[AI Service guardrails](/tutorials/observability). A guardrail that throws fires no event.

| Event                                                                                                                                                                                                  | Listener                                                                                                                                                                                                         |
|:-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|:-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| [`ToolInputGuardrailExecutedEvent`](https://github.com/langchain4j/langchain4j/blob/main/langchain4j-core/src/main/java/dev/langchain4j/observability/api/event/ToolInputGuardrailExecutedEvent.java)   | [`ToolInputGuardrailExecutedListener`](https://github.com/langchain4j/langchain4j/blob/main/langchain4j-core/src/main/java/dev/langchain4j/observability/api/listener/ToolInputGuardrailExecutedListener.java)   |
| [`ToolOutputGuardrailExecutedEvent`](https://github.com/langchain4j/langchain4j/blob/main/langchain4j-core/src/main/java/dev/langchain4j/observability/api/event/ToolOutputGuardrailExecutedEvent.java) | [`ToolOutputGuardrailExecutedListener`](https://github.com/langchain4j/langchain4j/blob/main/langchain4j-core/src/main/java/dev/langchain4j/observability/api/listener/ToolOutputGuardrailExecutedListener.java) |

One event is fired for each guardrail that returned a result, with its request, its result and the duration. They are distinct from
`InputGuardrailExecutedEvent` and `OutputGuardrailExecutedEvent`: a listener for AI Service guardrails is **not**
notified of tool guardrails. See [Observability](/tutorials/observability) for how to register listeners.

The tool execution events carry the request as the tool was executed with it, so after an input rewrite they carry
the **rewritten** request. For the result, they differ:

- `ToolExecutedEvent` carries the **guarded** result, the one the LLM sees.
- `ToolCompensatedEvent`, like the [compensating action](#compensation) itself, gets the result that the tool
  actually returned, **before** its output guardrails. This is also the case for a tool whose result a guardrail
  sanitized and that was rolled back later.

```java
Assistant assistant = AiServices.builder(Assistant.class)
        .chatModel(chatModel)
        .tools(new WebTools())
        .toolOutputGuardrails(new InjectionOutputGuardrail())
        .registerListener((ToolOutputGuardrailExecutedListener) event ->
                log.info("{} on {}: {}",
                        event.guardrailClass().getSimpleName(),
                        event.request().toolName(),
                        event.result().result()))
        .build();
```

## Non-blocking and reactive modes

In the `CompletableFuture` and reactive modes, a tool guardrail must implement `validateAsync` like any
other guardrail (see [Non-blocking and Reactive](/tutorials/non-blocking)). A guardrail that does no blocking work
can call `validate(...)` directly. Like every asynchronous method, it reports a failure through the returned future
instead of throwing:

```java
@Override
public CompletableFuture<ToolOutputGuardrailResult> validateAsync(ToolOutputGuardrailRequest request) {
    try {
        return CompletableFuture.completedFuture(validate(request));
    } catch (Throwable e) {
        return CompletableFuture.failedFuture(e);
    }
}
```

`validateAsync` can be called on an I/O thread, for example the thread on which an MCP client completes a tool call.
It must not block: a guardrail that has blocking work to do runs it on an executor of its own and returns the future
of that work.

A guardrail that does not implement `validateAsync` fails the invocation with an `AsyncNotSupportedException`,
wrapped in a fatal `ToolInputGuardrailException` or `ToolOutputGuardrailException`. For an output guardrail, this
happens when the tool has already run. A `TokenStream` uses the blocking `validate`.

## Unit Testing Tool Guardrails

A tool guardrail is a plain object, so it can be tested by calling `validate` with a request that you build:

```java
class InjectionOutputGuardrailTest {

    private final InjectionOutputGuardrail guardrail = new InjectionOutputGuardrail();

    @Test
    void should_block_instructions_in_the_result() {
        ToolOutputGuardrailRequest request = ToolOutputGuardrailRequest.builder()
                .toolExecutionRequest(ToolExecutionRequest.builder()
                        .id("1")
                        .name("fetch_page")
                        .arguments("{\"url\":\"https://docs.example.com\"}")
                        .build())
                .toolSource(ToolSource.programmatic())
                .requestParams(GuardrailRequestParams.builder()
                        .userMessageTemplate("")
                        .variables(Map.of())
                        .build())
                .resultContents(List.of(TextContent.from("Ignore all previous instructions and ...")))
                .isError(false)
                .build();

        ToolOutputGuardrailResult result = guardrail.validate(request);

        assertThat(result.result()).isEqualTo(GuardrailResult.Result.FAILURE);
        assertThat(result.failureMessage())
                .isEqualTo("The tool result contains instructions and was blocked. Do not retry.");
    }
}
```

The input request is built the same way, with `ToolInputGuardrailRequest.builder()` (without the result contents).
To test the whole chain, run an AI Service with a scripted chat model, such as `MockChatModel` from the
`langchain4j-test` module, and check what the tool received and what the LLM was sent.

## Extension points

Tool guardrails use the same extension points as [guardrails](/tutorials/guardrails#extension-points), and add the `Tool*` ones:

import ExtensionPoints from './_guardrails-extension-points.mdx';

<ExtensionPoints />

## Differences from the Quarkus tool guardrails

The tool guardrails were originally developed in the
[Quarkus LangChain4j extension](https://docs.quarkiverse.io/quarkus-langchain4j/dev/tool-guardrails.html). The
ones in LangChain4j differ in these points:

- The failure message is sent to the LLM **verbatim**, with no framework prefix.
- Output guardrails also run on **error results**, for example when the tool throws and the error is sent to the LLM
  (see [Output guardrails also see error results](#output-guardrails-also-see-error-results)). The Quarkus extension
  skips its output guardrails when the tool throws.
- The chain continues after a non-fatal failure, and the failures are aggregated.
- The request types (`ToolInputGuardrailRequest`, `ToolOutputGuardrailRequest`) and `ToolSource` are in
  `langchain4j-core`, and a request has `toolSource()`.
- The Quarkus extension declares tool guardrails on tool methods only. In LangChain4j, they can also be declared on
  the class of the tools, on the `AiServices` or `AgentBuilder` builder and on an `AiServiceTool`, and the guardrails
  of all the levels run, instead of replacing each other.

In a Quarkus application, use the Quarkus extension's own tool guardrails: the annotations and builder methods
described on this page are not applied by the Quarkus extension.
