---
sidebar_position: 36
---

# Using Jackson 3

LangChain4j reads and writes JSON in a lot of places: the requests and responses it exchanges with
LLM providers, the structured output an AI Service parses, chat memory you persist, and more. By
default it does that with [Jackson 2](https://github.com/FasterXML/jackson).

If your application is on Jackson 3, you can have LangChain4j use it instead.

## Turning it on

Add one dependency:

```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-json-jackson3</artifactId>
    <version>1.20.0-beta30</version>
</dependency>
```

That is the whole of it. LangChain4j finds the module through the `ServiceLoader` and routes its
JSON through Jackson 3. There is nothing to configure and no API to call, and if you remove the
dependency everything goes back to Jackson 2.

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

The first one matters most: without it, a final collection field is left empty instead of being
populated, and nothing tells you.

**Data you have already stored stays readable.** Chat memory and `InMemoryEmbeddingStore` files
written by Jackson 2 are read correctly by Jackson 3, and what Jackson 3 writes is byte-for-byte
what Jackson 2 would have written. That is covered by tests, so it stays true.

## What it does not do

It does not remove Jackson 2 from your application.

LangChain4j's *own* reading and writing no longer requires Jackson 2, but whether the library can
leave your classpath depends on which modules you use:

| Module | Needs Jackson 2? |
|---|---|
| `langchain4j-core`, `langchain4j` | Yes — they ship the Jackson 2 codecs that are used when this module is absent |
| Provider modules — OpenAI, Anthropic, Mistral, Ollama, Gemini, Bedrock, and the rest | No |
| Embedding stores, web search, code execution | No |
| `langchain4j-mcp` | Yes — Jackson's `JsonNode` appears in its public API |
| `langchain4j-agentic` | Yes — `AgenticScope` persistence has no Jackson 3 implementation yet |
| `langchain4j-vespa` | Yes — its HTTP client uses Retrofit's own Jackson converter |
| Anything using a vendor SDK — AWS, Azure, Google | Yes — the SDK depends on Jackson 2 itself |

So an application built on the core plus a provider or two can run without Jackson 2 on the
classpath. One that uses MCP, agents, or a cloud SDK cannot, and the opt-in does not change that.
It still gives you a single Jackson 3 code path for everything LangChain4j itself serializes.

## If you write a provider or a DTO

Two things to know if you contribute a wire type.

**Naming belongs on the codec, not on the type.** `@JsonNaming` lives in Jackson 2's `databind`
package, so Jackson 3 does not see it at all — the field names silently come out camelCase. Set the
naming on the codec instead:

```java
WireJson.codec(WireJsonSpec.builder()
        .propertyNaming(WireJsonSpec.PropertyNaming.SNAKE_CASE)
        .build());
```

If a single field needs a different name, `@JsonProperty("...")` works under both, because it comes
from `jackson-annotations`, the artifact the two versions share.

**A builder-based DTO needs `@JsonCreator`.** `@JsonDeserialize(builder = ...)` is also a `databind`
annotation, so under Jackson 3 the DTO is instead built through the `@JsonCreator` on the
constructor that takes the builder. Both have to be present. One consequence is easy to miss: that
route hands the builder straight to the constructor without calling `build()`, so a default applied
inside `build()` never happens. Default the builder's field where it is declared instead.

Every migrated module can be run against Jackson 3 to check this:

```bash
mvn test -Pjackson3 -pl langchain4j-your-module
```
