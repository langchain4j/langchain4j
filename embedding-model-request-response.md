# Design: `EmbeddingRequest` / `EmbeddingResponse` for `EmbeddingModel`

**Status:** proposal (design only — no code yet)
**Scope:** its own PR, independent of the non-blocking/reactive RAG work (see `non-blocking-ai-services.md`).
**Author's note:** this document is implementation-ready for a follow-up session. Code sketches are illustrative, not final.

---

## 1. Goal

Bring `EmbeddingModel` in line with the request/response shape the rest of the library already uses, so that:

- **Consistency with `ChatModel`** — `ChatResponse chat(ChatRequest)` / `CompletableFuture<ChatResponse> chatAsync(ChatRequest)`.
- **Consistency with the store side** — `EmbeddingStore` already exposes `EmbeddingSearchResult search(EmbeddingSearchRequest)`. Today the retrieval pipeline is *half* request/response (store) and *half* loose args (model). This closes that asymmetry.
- **Per-call parameters** — the concrete driver. Embedding parameters (`model`, `dimensions`, and provider-specifics like OpenAI/NVIDIA `input_type`, `encodingFormat`, `user`) are currently **constructor-only**. The canonical RAG pattern of embedding a **query** with `input_type=query` and **documents** with `input_type=passage` requires *two model instances* today. A per-call `EmbeddingRequestParameters` fixes this.
- **Structured response + retiring `Response<T>`** — replace the legacy generic `Response<List<Embedding>>` with an `EmbeddingResponse` carrying `EmbeddingResponseMetadata` (model name, token usage, extensible), mirroring `ChatResponse` + `ChatResponseMetadata`.

Non-goal: migrating all ~48 `EmbeddingModel` implementations in one PR. The design is explicitly **incremental and non-breaking** (see §6).

---

## 2. Current state

```java
public interface EmbeddingModel {
    default Response<Embedding> embed(String text) { return embed(TextSegment.from(text)); }
    default Response<Embedding> embed(TextSegment textSegment) { /* → embedAll(singletonList(...)) */ }
    Response<List<Embedding>> embedAll(List<TextSegment> textSegments);   // the one abstract method
    default int dimension() { ... }
    default String modelName() { return "unknown"; }
    default EmbeddingModel addListener(EmbeddingModelListener listener) { ... }   // wrapper-based
    // + embedAllAsync(List<TextSegment>) was added by the async-RAG branch — see §7
}
```

Problems: no request object (no per-call params), `Response<T>` is legacy, and `embedAll` takes `List<TextSegment>` even though **only the text is ever used** (`TextSegment.text()`); the `Metadata` on `TextSegment` is ignored.

There are **~48 implementations** across provider modules (OpenAI, Google, Ollama, HuggingFace, Oracle, VertexAI, the in-process ONNX `langchain4j-embeddings-*` models, …) plus the separate `langchain4j-community` repo. They all implement `embedAll(List<TextSegment>)`.

---

## 3. Proposed API

### 3.1 `EmbeddingRequest` — core type is `String`, not `TextSegment`

`EmbeddingModel` is general-purpose text→vector and must not depend on the RAG-domain `TextSegment`. The **core input type is `String`**; the builder *accepts* `TextSegment`(s) for convenience and extracts `.text()`.

```java
public class EmbeddingRequest {

    private final List<String> inputs;                 // core type: String
    private final EmbeddingRequestParameters parameters;

    // accessors
    public List<String> inputs();
    public EmbeddingRequestParameters parameters();
    // convenience mirrors (like ChatRequest.modelName() etc.)
    public String modelName();     // parameters.modelName()
    public Integer dimensions();   // parameters.dimensions()

    public static Builder builder();

    public static class Builder {
        Builder input(String text);
        Builder inputs(List<String> texts);
        Builder inputs(String... texts);
        // convenience: accept TextSegments, store their .text() (metadata dropped — unused)
        Builder textSegment(TextSegment segment);
        Builder textSegments(List<TextSegment> segments);
        Builder parameters(EmbeddingRequestParameters parameters);
        // flat convenience setters that build/override parameters:
        Builder modelName(String modelName);
        Builder dimensions(Integer dimensions);
        EmbeddingRequest build();
    }
}
```

### 3.2 `EmbeddingRequestParameters` — extensible, mirrors `ChatRequestParameters`

```java
public interface EmbeddingRequestParameters {
    String modelName();
    Integer dimensions();
    // overrideWith(): combine defaults (from the model instance) with per-call params — same semantics
    // as ChatRequestParameters.overrideWith(...)
    EmbeddingRequestParameters overrideWith(EmbeddingRequestParameters that);

    static DefaultEmbeddingRequestParameters.Builder builder();
    EmbeddingRequestParameters EMPTY = DefaultEmbeddingRequestParameters.EMPTY;
}
```

- `DefaultEmbeddingRequestParameters` — the common impl (modelName, dimensions).
- **Provider-specific params** follow the `ChatRequestParameters` precedent: a provider *extends* the interface, e.g. `OpenAiEmbeddingRequestParameters` adds `user`, `encodingFormat`, and a `customParameters` map (this is where NVIDIA's `input_type` lives today). **Open question (§9):** base interface + provider subclasses (ChatModel style) vs. a generic `customParameters(Map<String,Object>)` on the base. Recommendation: mirror `ChatRequestParameters` (subclasses) for type-safety, and *also* expose a `customParameters` map on the base for the OpenAI-style passthrough that already exists.

### 3.3 `EmbeddingResponse` + `EmbeddingResponseMetadata`

```java
public class EmbeddingResponse {
    private final List<Embedding> embeddings;
    private final EmbeddingResponseMetadata metadata;   // like ChatResponse.metadata()
    public List<Embedding> embeddings();
    public EmbeddingResponseMetadata metadata();
    public static Builder builder();
}

public class EmbeddingResponseMetadata {   // mirrors ChatResponseMetadata (minus chat-only fields)
    private final String modelName;
    private final TokenUsage tokenUsage;
    // extensible; provider subclasses may add fields
}
```

### 3.4 Updated `EmbeddingModel`

```java
public interface EmbeddingModel {

    // ---------- new request/response surface ----------

    /**
     * The primary method a provider overrides for genuine per-call parameters. The default BRIDGES to the
     * legacy embedAll(List<TextSegment>) so every existing implementation supports the new API unchanged
     * (per-call parameters from the request are ignored by the bridge — see §6).
     */
    default EmbeddingResponse doEmbed(EmbeddingRequest request) {
        Response<List<Embedding>> legacy =
                embedAll(request.inputs().stream().map(TextSegment::from).toList());
        return EmbeddingResponse.builder()
                .embeddings(legacy.content())
                .metadata(EmbeddingResponseMetadata.builder()
                        .modelName(modelName())
                        .tokenUsage(legacy.tokenUsage())
                        .build())
                .build();
    }

    /** Public entry point: merges the model's default parameters with the request's, fires listeners, calls doEmbed. */
    default EmbeddingResponse embed(EmbeddingRequest request) {
        // EmbeddingRequest finalRequest = request.overrideParametersWith(defaultRequestParameters());
        // onRequest(...); try { EmbeddingResponse r = doEmbed(finalRequest); onResponse(...); return r; } catch ...
        return doEmbed(request);
    }

    /** Non-blocking counterpart. Default offloads doEmbed to the shared virtual-thread executor; providers with a
     *  genuine async HTTP path (e.g. OpenAI) override doEmbedAsync. */
    default CompletableFuture<EmbeddingResponse> doEmbedAsync(EmbeddingRequest request) {
        return CompletableFuture.supplyAsync(
                () -> doEmbed(request), DefaultExecutorProvider.getDefaultExecutorService());
    }

    default CompletableFuture<EmbeddingResponse> embedAsync(EmbeddingRequest request) {
        return doEmbedAsync(request);
    }

    // ---------- legacy + convenience (retained; deprecation is a later phase) ----------

    Response<List<Embedding>> embedAll(List<TextSegment> textSegments);   // stays ABSTRACT in Phase A (§6)

    default Response<Embedding> embed(String text) { ... }          // unchanged
    default Response<Embedding> embed(TextSegment textSegment) { ... }
    default int dimension() { ... }
    default String modelName() { return "unknown"; }
    default EmbeddingModel addListener(EmbeddingModelListener listener) { ... }
}
```

---

## 4. Why `String`, not `TextSegment` (the requested refinement)

- `EmbeddingModel` is **not** a RAG type — it converts arbitrary text. Depending on `TextSegment` (a `dev.langchain4j.data.segment` / RAG-adjacent type carrying `Metadata`) pulls a domain concept into a general-purpose model API.
- The `Metadata` on `TextSegment` is **never read** by any `EmbeddingModel` implementation — they all call `.text()`. So nothing is lost by using `String`.
- Ergonomics are preserved: the builder accepts `TextSegment`/`List<TextSegment>` (document splitters produce these), extracting `.text()`. Callers that already hold `TextSegment`s stay one call away.

---

## 5. Async surface & relationship to the RAG-async work

The async-RAG branch added `EmbeddingModel.embedAllAsync(List<TextSegment>) : CompletableFuture<Response<List<Embedding>>>` and a native `OpenAiEmbeddingModel.embedAllAsync`. **This design supersedes that**: the async surface becomes `embedAsync(EmbeddingRequest) : CompletableFuture<EmbeddingResponse>`.

Concretely, when this lands:
- **Remove** `embedAllAsync(List<TextSegment>)` (it is unreleased POC code) — or keep it briefly as a thin `@Deprecated` bridge to `embedAsync`.
- `EmbeddingStoreContentRetriever.retrieveAsync` changes its one call site from
  `embeddingModel.embedAllAsync(List.of(TextSegment.from(query.text())))`
  to `embeddingModel.embedAsync(EmbeddingRequest.builder().input(query.text()).build())` and reads `response.embeddings().get(0)`.
- The native `OpenAiEmbeddingModel.embedAllAsync` (which already uses `client.embedding(req).executeRawAsync()`) is re-homed into `doEmbedAsync(EmbeddingRequest)`, now also honoring `dimensions`/`user`/`encodingFormat`/custom params from the request.

Recommended ordering: **land this request/response change first**, then rebase the RAG-async retriever onto `embedAsync(EmbeddingRequest)`, so we don't ship `embedAllAsync(List)` only to remove it.

---

## 6. Migration strategy — non-breaking & incremental (the seamless-for-all-providers part)

The key requirement: the ~48 existing implementations (and the community repo) must keep compiling and behaving **with zero changes**, while OpenAI (and later others) opt into native per-call parameters.

### 6.1 Phase A (this PR) — additive bridge, no circularity

- **Keep `embedAll(List<TextSegment>)` abstract.** All 48 impls satisfy it exactly as today → untouched.
- **`doEmbed(EmbeddingRequest)` defaults *down* to `embedAll`** (§3.4). Therefore **every existing implementation instantly supports the whole new API** — `embed(EmbeddingRequest)`, `embedAsync(EmbeddingRequest)` — for free, with **no code change**. This is the "seamless for as many implementations as possible" property: one default method retrofits the entire ecosystem.
- Because `embedAll` stays a real (abstract) leaf and `doEmbed` only calls *down* to it, there is **no default→default cycle** (contrast with the `ChatModel` end-state where `doChat`'s default throws — we can't do that here without breaking all 48).
- **Per-call parameters are honored only by providers that override `doEmbed`.** For un-migrated providers the bridge drops request parameters — but this is **not a regression**: those providers never supported per-call parameters (their params were constructor-fixed). Document clearly: *"`EmbeddingRequestParameters` are applied by implementations that override `doEmbed`; others use their fixed configuration."*

### 6.2 Provider migration (OpenAI first)

A migrated provider overrides `doEmbed` (and `doEmbedAsync`) natively. It still must satisfy the abstract `embedAll` — implement it as a one-line delegate so there's a single source of truth:

```java
// OpenAiEmbeddingModel
@Override public EmbeddingResponse doEmbed(EmbeddingRequest request) { /* native: applies dimensions/user/input_type */ }
@Override public CompletableFuture<EmbeddingResponse> doEmbedAsync(EmbeddingRequest request) { /* executeRawAsync */ }
@Override public Response<List<Embedding>> embedAll(List<TextSegment> segments) {   // delegate to the new path
    EmbeddingResponse r = doEmbed(EmbeddingRequest.builder().textSegments(segments).build());
    return Response.from(r.embeddings(), r.metadata().tokenUsage());
}
```

Each subsequent provider is a self-contained follow-up PR; nothing forces a big-bang migration.

### 6.3 Phase B (future, optional) — retire `embedAll`

Once the important providers implement `doEmbed`, deprecate `embedAll(List<TextSegment>)` and give it a **default** that bridges *up* to `embed(EmbeddingRequest)`. At that point `doEmbed` and `embedAll` both have defaults, so break the potential cycle the way `ChatModel` does: `doEmbed`'s default throws `"implement doEmbed or embedAll"`, and any provider must override at least one. This is a separate, later, semver-major-ish cleanup — **out of scope for the first PR**.

### 6.4 revapi / binary compatibility

- Phase A adds only `default` methods to `EmbeddingModel` and new classes → revapi reports `defaultMethodAddedToInterface`, classified **non-breaking** (confirmed on this codebase for the RAG-async default additions; no suppressions were needed).
- Keeping `embedAll` abstract means **no** `methodAbstractedInInterface` break.
- New public types (`EmbeddingRequest`, `EmbeddingResponse`, params/metadata) are pure additions.

---

## 7. Note on "supporting more implementations seamlessly"

The single lever that makes the migration seamless for the whole provider ecosystem is **§6.1's `doEmbed` default bridging down to `embedAll`**: it retrofits `embed(EmbeddingRequest)`/`embedAsync(EmbeddingRequest)` onto all ~48 implementations (and community ones) without touching them. Providers then adopt native per-call parameters purely opt-in, one PR at a time. (The `EmbeddingStore` side already follows the request/response pattern via `EmbeddingSearchRequest`/`EmbeddingSearchResult`, so no analogous change is needed there.)

---

## 8. Illustrative end-to-end (post-migration, OpenAI)

```java
// asymmetric query vs passage encoding — impossible per-call today, natural after this change
EmbeddingResponse q = model.embed(EmbeddingRequest.builder()
        .input(userQuery)
        .parameters(OpenAiEmbeddingRequestParameters.builder().customParameter("input_type", "query").build())
        .build());

EmbeddingResponse docs = model.embed(EmbeddingRequest.builder()
        .textSegments(chunks)
        .dimensions(256)
        .parameters(OpenAiEmbeddingRequestParameters.builder().customParameter("input_type", "passage").build())
        .build());
```

---

## 9. Open questions for the implementer

1. **Provider params**: base-interface + provider subclasses (à la `ChatRequestParameters`/`OpenAiChatRequestParameters`) vs. a generic `customParameters(Map)` on the base — or both? (Recommendation: both — typed subclasses + a passthrough map, since the OpenAI model already carries `customParameters`.)
2. **Listeners**: fold `EmbeddingModelListener` into `embed(EmbeddingRequest)` as `onRequest`/`onResponse`/`onError` (like `ChatModel`), or keep the existing `ListeningEmbeddingModel` wrapper? The wrapper stays source-compatible; aligning with `ChatModel` is cleaner long-term.
3. **`dimension()`**: today it calls `embed("test")`. Leave as-is, or add `dimensions` to `EmbeddingResponseMetadata` so callers can read it from a real response?
4. **`embedAllAsync` disposition**: hard-remove (unreleased) vs. keep as a deprecated bridge for one release. (Recommendation: remove — it never shipped.)
5. **Naming collision**: `dev.langchain4j.model.openai.internal.embedding.EmbeddingRequest`/`EmbeddingResponse` already exist as OpenAI *wire* types. The new core types live in `dev.langchain4j.model.embedding` — different package, but worth a deliberate name check to avoid confusion in the OpenAI module (consider importing with care or aliasing in docs).
6. **`Response<T>` retirement**: this change is a step toward removing `Response<List<Embedding>>`. Decide whether to deprecate the `Response`-returning convenience methods now or later.

---

## 10. Summary

Introduce `EmbeddingRequest` (String-core, TextSegment-friendly builder) / `EmbeddingResponse` and a `doEmbed`/`embed`/`embedAsync` surface on `EmbeddingModel`, mirroring `ChatModel`. Make it **non-breaking and incremental** by keeping `embedAll` abstract and letting `doEmbed` default down to it — retrofitting the new (and async) API onto all existing providers for free, with native per-call parameters adopted opt-in, OpenAI first. This closes the model/store asymmetry, unlocks per-call parameters (notably query-vs-passage encoding), and retires the legacy `Response<T>` on the embedding path.
