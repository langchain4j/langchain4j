---
sidebar_position: 37
---

# Using Jackson 3

LangChain4j reads and writes JSON in a lot of places: the requests and responses it exchanges with
LLM providers, the structured output an AI Service parses, chat memory you persist, and more. By
default it does that with [Jackson 2](https://github.com/FasterXML/jackson). How LangChain4j uses
JSON, and how to plug in your own mapper, is covered in [JSON](/tutorials/json).

If your application is on Jackson 3, you can have LangChain4j use it instead.

## Turning it on

Add one dependency:

```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-jackson3</artifactId>
    <version>1.20.0-beta30</version>
</dependency>
```

That is the whole of it, for every module including `langchain4j-agentic`. LangChain4j finds the
module through the `ServiceLoader` and routes its JSON through Jackson 3. There is nothing to
configure and no API to call, and if you remove the dependency everything goes back to Jackson 2.

The support is split across two artifacts, and `langchain4j-jackson3` above pulls in both, so that
is the one to add unless you have a reason not to:

| Artifact | Covers | Depends on |
|---|---|---|
| `langchain4j-core-jackson3` | everything in `langchain4j-core`: provider requests and responses, structured output, chat-memory serialization, agent state | `langchain4j-core` |
| `langchain4j-jackson3` | the above, plus `InMemoryEmbeddingStore` persistence, which lives in the `langchain4j` module | `langchain4j-core-jackson3` and `langchain4j` |

Add `langchain4j-core-jackson3` on its own only if your application uses `langchain4j-core` and a
provider without the `langchain4j` module - for instance a framework integration that builds models
directly. Adding it alongside `langchain4j-jackson3` is unnecessary but harmless.

## What stays the same

Switching a JSON library is a good way to change behaviour by accident, so the module works hard not
to. Jackson 3 changed several defaults, and every one of them is set back to what Jackson 2 did:

| Setting | Jackson 3 default | What this module does |
|---|---|---|
| `ALLOW_FINAL_FIELDS_AS_MUTATORS` | disabled | enabled |
| `USE_GETTERS_AS_SETTERS` | disabled | enabled |
| `SORT_PROPERTIES_ALPHABETICALLY` | enabled | disabled |
| `FAIL_ON_TRAILING_TOKENS` | enabled | disabled |
| `FAIL_ON_NULL_FOR_PRIMITIVES` | enabled | disabled |
| `""` coerced to an enum | rejected | read as `null`, as Jackson 2 does |

The first one matters most: without it, a final collection field is left empty instead of being
populated, and nothing tells you.

**Failures get a LangChain4j type.** This is the one place where the opt-in does change something.
By default, a JSON failure surfaces as a `RuntimeException` wrapping Jackson 2's own exception -
which means code that reacts to it has to know Jackson 2. With this module, reading or writing JSON
that fails throws `JsonReadException` or `JsonWriteException` instead, both `LangChain4jException`,
with the library's exception kept as the cause.

That is a deliberate step rather than an inconsistency: the typed exceptions are where LangChain4j
is going in the next major version, and the Jackson 2 codecs stay as they are until then so that
existing code keeps working. If you catch a JSON failure by its Jackson type, that is the one thing
to revisit when you add this module:

```java
- } catch (JsonParseException e) {
+ } catch (JsonReadException e) {
```

Catching `RuntimeException` works either way.

**Data you have already stored stays readable.** Chat memory and `InMemoryEmbeddingStore` files
written by Jackson 2 are read correctly by Jackson 3, and what Jackson 3 writes is byte-for-byte
what Jackson 2 would have written. That is covered by tests, so it stays true.

## Removing Jackson 2

Adding the module does not by itself remove Jackson 2 - it still arrives as a normal transitive
dependency, and the two can coexist indefinitely. If you want it gone, you can usually have that,
because LangChain4j no longer loads any Jackson 2 class once this module is present.

Whether it can actually leave depends on which modules you use:

| Module | Can Jackson 2 be excluded? |
|---|---|
| `langchain4j-core`, `langchain4j` | Yes — they ship Jackson 2 codecs, but those are the fallback and are never loaded while this module is on the classpath |
| Provider modules — OpenAI, Anthropic, Mistral, Ollama, Gemini, and the rest | Yes |
| Embedding stores, web search, code execution | Yes |
| `langchain4j-agentic` | Yes |
| `langchain4j-mcp` | No — Jackson's `JsonNode` appears in its public API, so the module loads Jackson 2 on every path |
| `langchain4j-vespa` | No — its HTTP client uses Retrofit's own Jackson 2 converter |
| Anything using a vendor SDK — AWS, Azure, Google | No — the SDK depends on Jackson 2 itself |

Maven exclusions apply to the dependency they are written on, so they go on **every** LangChain4j
dependency you declare, not only the first:

```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j</artifactId>
    <version>1.20.0</version>
    <exclusions>
        <exclusion>
            <groupId>com.fasterxml.jackson.core</groupId>
            <artifactId>jackson-databind</artifactId>
        </exclusion>
        <exclusion>
            <groupId>com.fasterxml.jackson.core</groupId>
            <artifactId>jackson-core</artifactId>
        </exclusion>
    </exclusions>
</dependency>
<!-- and the same on langchain4j-open-ai, and on any other LangChain4j module you use -->
```

Do **not** exclude `com.fasterxml.jackson.core:jackson-annotations`. It keeps the 2.x coordinates
but is shared: Jackson 3 depends on it and reads those annotations.

This configuration is not just believed to work, it is built and run on every pull request:
`integration-tests/integration-tests-jackson3` is an application assembled exactly this way - core,
the main module, a provider, agents and this module, with Jackson 2 excluded from the whole graph -
and its
tests cover AI Services structured output, tool calling, chat-memory persistence, embedding-store
persistence and agent-state persistence. If any of that started needing Jackson 2, those tests would
fail to find the class.

If you use MCP, Vespa, or a cloud SDK, Jackson 2 stays. The opt-in still gives you a single Jackson 3
code path for everything LangChain4j itself serializes.

## If you write a provider or a DTO

Two things to know if you contribute a wire type.

**Naming belongs on the codec, not on the type.** `@JsonNaming` lives in Jackson 2's `databind`
package, so Jackson 3 does not see it at all — the field names silently come out camelCase. Set the
naming on the codec instead:

```java
ProviderJson.codec(ProviderJsonSpec.builder()
        .propertyNaming(ProviderJsonSpec.PropertyNaming.SNAKE_CASE)
        .build());
```

If a single field needs a different name, `@JsonProperty("...")` works under both, because it comes
from `jackson-annotations`, the artifact the two versions share.

**A builder-based DTO needs `@JsonCreator`.** `@JsonDeserialize(builder = ...)` is also a `databind`
annotation, so under Jackson 3 the DTO is instead built through the `@JsonCreator` on the
constructor that takes the builder. Both have to be present.

**Whether a builder method runs depends on the annotations on it.** This is the part to
internalise, because it is silent and the rule is not the one you would guess. Jackson 2 fills a
builder by calling its setters and then `build()`. Jackson 3 calls a setter only when it carries a
property marker, writes the field directly otherwise, and never calls `build()` at all:

| On a builder setter | Jackson 2 | Jackson 3 |
|---|---|---|
| `@JsonSetter("odd-name")`, `@JsonProperty`, bare `@JsonSetter` | called | called |
| `@JsonAlias` on its own | called | **not called** - the alias is ignored |
| no annotation | called | **not called** - the field is written directly |
| `build()` | called | **never called** |

So the two codecs can diverge property by property within one type, which is why this is worth a
rule rather than vigilance:

**Put what the type guarantees where both routes go through it.** A default belongs on the builder's
field, not in `build()`. A defensive copy or any normalising belongs in the constructor, not in a
setter. An alias needs `@JsonAlias` on the builder's *field* as well as its setter.

Each of those went wrong here: a type defaulted in `build()` dropped every mistral-ai tool call;
`@JsonAlias` on a setter dropped reasoning content from vLLM, OpenRouter and Groq; and
`unmodifiableList(...)` in an unannotated setter meant the same response came back mutable under one
codec and not the other. None of them threw.

A module that supports the opt-in declares a `jackson3` Maven profile, which puts
`langchain4j-jackson3` on that module's test classpath so its existing tests run against
Jackson 3. CI runs all of them on every pull request, and you can run one module the same way:

```bash
mvn test -Pjackson3 -pl langchain4j-your-module
```

Check that the module's `pom.xml` actually declares that profile before trusting the result:
Maven ignores a profile the selected module does not declare, so the command above reports
success having run everything on Jackson 2. Adding the profile is part of migrating a module.

The profile puts `langchain4j-core-jackson3` on the test classpath, not `langchain4j-jackson3`,
because the latter depends on the `langchain4j` module and would make the graph cyclic for the
modules that module is itself built on. `InMemoryEmbeddingStore` persistence, which is the part
that needs the full artifact, is covered by `langchain4j-jackson3`'s own tests and by
`integration-tests/integration-tests-jackson3`.

`langchain4j-open-ai` also carries `OpenAiBuilderCreatorParityTest`, which compares every
builder-based DTO built through its builder against the same DTO parsed from `{}`. That is the
difference the missing `build()` call above produces, so the test catches it for the whole of the
OpenAI wire model at once rather than one field at a time.

## If you plug in your own JSON

You do not need this section to use Jackson 3 — adding the dependency is enough. It is for
frameworks that supply their own configured JSON mapper to LangChain4j rather than letting it pick
one, which is what `langchain4j-jackson3` itself does.

LangChain4j does not have a single JSON entry point. It asks a `ServiceLoader` for a codec at each
of the places below, so that each can be answered separately:

| Service interface | Decides how LangChain4j reads and writes |
|---|---|
| `dev.langchain4j.spi.json.JsonCodecFactory` | general-purpose JSON — an AI Service's structured output, a model's tool arguments |
| `dev.langchain4j.spi.json.ProviderJsonCodecFactory` | the requests and responses exchanged with LLM providers |
| `dev.langchain4j.spi.json.StateJsonCodecFactory` | state whose types are not known ahead of time, and which therefore carries type names — agent state |
| `dev.langchain4j.spi.data.message.ChatMessageJsonCodecFactory` | chat memory you persist |
| `dev.langchain4j.spi.agent.tool.ToolSpecificationJsonCodecFactory` | `ToolSpecification.toJson()` and `ToolSpecification.fromJson(String)` |
| `dev.langchain4j.spi.prompt.structured.StructuredPromptFactory` | `@StructuredPrompt` templates |
| `dev.langchain4j.spi.store.embedding.inmemory.InMemoryEmbeddingStoreJsonCodecFactory` | `InMemoryEmbeddingStore.serializeToJson()` |

The last one lives in `langchain4j`; the rest live in `langchain4j-core`. All are `@Internal`, which
here means they are meant for integrations rather than applications, and can change between minor
versions.

**A framework's own implementation wins.** If something already supplies one of these - Quarkus
supplies four - adding this module does not take it away. The Jackson 3 factories declare a lower
priority than anything else, so they apply only to the services nothing else has claimed, and
LangChain4j logs a warning naming the implementation it chose. That means on such a framework the
opt-in is partial by design: provider traffic and agent state move to Jackson 3 while the services
the framework owns stay on its own codec. If you want the whole application on Jackson 3, remove
the framework's registrations rather than relying on classpath order.

**Implement all of them, or know which you are leaving out.** Each is resolved independently, and
one with no implementation registered falls back to Jackson 2. Answering some but not others is not
an error and produces no warning — it produces an application where, say, chat memory is written by
your mapper and agent state by a different library. If you deliberately leave one out, the fallback
is what you get.

Two of these are worth a second look if you already integrate with an older version:
`ProviderJsonCodecFactory` and `StateJsonCodecFactory` are new, so an existing integration that
does not know about them keeps working while quietly using Jackson 2 for provider traffic and agent
state.
