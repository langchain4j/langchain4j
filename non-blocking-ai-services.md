# Non-blocking / reactive AI Services (`publisher-poc`, milestone 2.0)

## 1. Goal

Today an AI Service method blocks the calling thread for the entire interaction (model HTTP call, tool
execution, memory I/O, guardrails). This branch adds **truly non-blocking** ways to call an AI Service, so a
single thread can drive many concurrent interactions — required for reactive stacks (Quarkus/Mutiny, Vert.x,
WebFlux) and for efficient high-concurrency services.

The work is **additive**: existing synchronous and `TokenStream` APIs are unchanged. Non-blocking is threaded
through **every** layer of the stack — a blocking gap at any level re-blocks the whole call.

## 2. What's delivered

Four AI Service "modes", from one interface definition — you pick the mode by the method's **return type**:

| Mode | Return type | Nature |
|---|---|---|
| Synchronous (existing) | `String`, POJO, `Result<T>`, … | blocking |
| Streaming callback (existing) | `TokenStream` | handler-based |
| **Async single-response (new)** | `CompletableFuture<T>`, `CompletionStage<T>` | non-blocking |
| **Reactive streaming (new)** | `Flow.Publisher<AiServiceStreamingEvent>`, `Flow.Publisher<String>` | non-blocking, cold |

Third-party reactive types (Mutiny `Uni`/`Multi`, Reactor `Mono`/`Flux`) are supported through **SPI adapters**
(`CompletableFutureAdapter`, `PublisherAdapter`), discovered via `ServiceLoader` — so the core stays
dependency-free.

Delivered building blocks (each non-blocking end to end):
- **Reactive event vocabulary** `AiServiceStreamingEvent` — a dedicated, open (extensible) event set for
  streaming AI Services, each event carrying an `InvocationContext`.
- **Reactive tool loop** — a cold publisher built on **`mutiny-zero`** (see §3.8), consuming the model's reactive
  stream round-by-round.
- **Non-blocking tool calling** across all modes (sync + `CompletableFuture` tools, sequential + concurrent).
- **Non-blocking chat memory** — async `ChatMemory` / `ChatMemoryStore` SPI.
- **Non-blocking guardrails** — async `Guardrail` SPI; input + output guardrails, buffer-then-validate, with
  tool-aware reprompts on all modes.
- **Non-blocking RAG** — async SPI across the whole retrieval graph (`RetrievalAugmentor`, `QueryTransformer`,
  `QueryRouter`, `ContentRetriever`, `ContentAggregator`) and its leaves (`EmbeddingModel`, `EmbeddingStore`,
  `ScoringModel`, `WebSearchEngine`), with a fail-loud-by-default / opt-in-offload policy (see §3.9).

## 3. Design

### 3.0 Updated core interfaces (layer by layer)

Each layer gained an async/reactive counterpart **alongside** its existing blocking method. The new methods are
`default` and **throw "not implemented"** (see the contract in §3.7), so existing custom implementations still
compile and only providers/implementors that opt in become non-blocking.

| Layer | Interface | Existing (blocking) | Added |
|---|---|---|---|
| HTTP | `HttpClient` (`langchain4j-http-client`) | `execute(HttpRequest)` | `executeAsync(HttpRequest) : CompletableFuture<SuccessfulHttpResponse>`, `stream(HttpRequest[, ServerSentEventParser]) : Flow.Publisher<StreamingHttpEvent>` (JDK / OkHttp / Apache clients) |
| Model — single response | `ChatModel` | `chat`/`doChat(ChatRequest)` | `chatAsync`/`doChatAsync(ChatRequest) : CompletableFuture<ChatResponse>` |
| Model — streaming | `StreamingChatModel` | `chat`/`doChat(ChatRequest, handler)` | `chat`/`doChat(ChatRequest) : Flow.Publisher<StreamingEvent>` (cold) |
| Memory | `ChatMemory`, `ChatMemoryStore` | `add`/`messages`/`set`, `getMessages`/`updateMessages`/`deleteMessages` | `addAsync(List)`/`messagesAsync`/`setAsync(List)`, `getMessagesAsync`/`updateMessagesAsync`/`deleteMessagesAsync` (`CompletableFuture`) |
| Guardrails | `Guardrail`, `ChatExecutor`, `GuardrailExecutor`, `GuardrailService` | `validate`, `execute`, `executeGuardrails` | `validateAsync`, `executeAsync`, `executeGuardrailsAsync` (`CompletableFuture`) |
| Tools | `ToolExecutor`, `ToolService` | `execute…`, `executeInferenceAndToolsLoop` | `executeAsync(...) : CompletableFuture<…>`, `executeInferenceAndToolsLoopAsync(...)` |
| AI Service | `DefaultAiServices` | sync / `TokenStream` dispatch | dispatch by return type into the `CompletableFuture` pipeline or the reactive publisher |

**Three event vocabularies, one per layer:**
- **HTTP layer** — `StreamingHttpEvent`: a parsed transport event (an SSE `ServerSentEvent`, or stream
  completion). The model client turns these into model events.
- **Model layer** — low-level `StreamingEvent`: `PartialResponse`, `PartialThinking`, `PartialToolCall`,
  `CompleteToolCall`, `RawStreamingEvent`, and the terminal `ChatResponse`.
- **AI Service layer** — high-level `AiServiceStreamingEvent` (§4). `DefaultAiServices` maps each model event to
  its AI-Service counterpart and adds service-level events (tool execution, intermediate/final response,
  retrieved RAG content).

On the model/provider side, **OpenAI** implements the async + reactive chat methods and async embeddings,
**Cohere** implements async scoring/re-rank (`scoreAllAsync`) and **Tavily** implements async web search
(`searchAsync`); other providers fall back to the throwing defaults until implemented (see §8).

### 3.1 Threading model — "non-blocking" and "non-pinning"

- **Model call:** genuinely async I/O (OpenAI's reactive `doChat`/`doChatAsync`); no thread waits for the response.
- **Async tools** (returning a future/reactive type): composed, never waited on.
- **Sync (blocking) tools:** offloaded to a **virtual-thread** executor (`DefaultExecutorProvider`) — they block a
  *virtual* thread, which unmounts from its carrier (*non-pinning*), so they scale without starving the event
  loop. This is the Loom-aligned answer to "what about blocking user code".
- **Chat memory:** async SPI (§3.4); bundled in-memory stores complete synchronously, persistent stores supply
  real async I/O.
- **Guardrails:** async SPI (§3.5) — a guardrail that does blocking I/O implements `validateAsync` to stay off the
  delivery thread.
- **Thread-local context does not follow the async hops.** Because a single invocation now crosses several
  threads (async model I/O → the virtual-thread tool executor → arbitrary future-completion threads), ambient
  `ThreadLocal` state — MDC logging context, tracing / OpenTelemetry spans, security / authentication context —
  is **not** automatically propagated the way it is in the fully-synchronous mode (which runs on one caller
  thread). `InvocationContext` is unaffected: it is passed **explicitly** as a parameter, never through a
  thread-local. Callers that rely on ambient context must propagate it themselves — e.g. wrap the executor given
  to `executeToolsConcurrently(Executor)` (and any memory / guardrail executor) in a context-capturing decorator
  (Micrometer Context Propagation, an MDC-copying `Executor`, etc.). This is inherent to any async/reactive
  model, and it now applies **by default** since tools run concurrently for the new modes (§3.2).
- The scarce **model-delivery thread** is never blocked — enforced by BlockHound (§6).

### 3.2 Concurrency & error defaults for the new APIs
- **Tools run concurrently by default** for the async/reactive modes (legacy modes stay sequential). To run them
  serially instead, pass a single-threaded executor to `executeToolsConcurrently(Executor)` — tools are then
  submitted in request order and executed one at a time, still off the model-delivery thread.
- **Reversed, safer tool-error defaults** for the new APIs: a tool **execution** error **fails** the invocation; a
  tool **argument-parse** error is **sent back to the LLM**. (Old APIs keep their existing behavior.)
- On the publisher path, tools start **as soon as** their call completes streaming (`onCompleteToolCall`),
  overlapping with the rest of the stream.

### 3.3 Cancellation contract
Cancelling the `CompletableFuture` (`cancel(true)`) or the `Flow.Subscription` releases the caller, stops further
rounds, and best-effort aborts the in-flight I/O — the model HTTP call, the RAG graph (the augmentor forwards
cancellation to each stage via a `CancellationChain`), and guardrail validation (incl. the output-guardrail
reprompt model call). "Best-effort" means the cancellation is propagated to the in-flight future, so a
cancellation-aware async client aborts its call; a component that ignores future cancellation (or a multi-step
stage that does not re-forward it to its own sub-calls) simply completes and its result is discarded. A **`@Tool` method that
has already started is not interrupted** — it runs to completion and its result is discarded (Java can't safely
interrupt arbitrary user code). **Chat-memory writes are also never cancelled** — deliberately, so a half-written
history can't corrupt conversation state (see §3.7).

### 3.4 Non-blocking chat memory
- `CompletableFuture`-returning methods: `ChatMemoryStore.{getMessagesAsync, updateMessagesAsync,
  deleteMessagesAsync}` and `ChatMemory.{addAsync(List<ChatMessage>), messagesAsync, setAsync(List<ChatMessage>)}`
  (`setAsync` is the async bulk-replace used e.g. by async tool compensation, delegating to the store's
  `updateMessagesAsync`). The type is `CompletableFuture` — uniform with the rest of the async SPI surface — but the
  framework never *cancels* a memory operation (§3.7).
- **Defaults throw** `UnsupportedOperationException` (no silent offload): a blocking store used with an async
  service fails loudly instead of secretly tying up a worker. Bundled stores implement them natively.
- `addAsync` takes a **list** (single method to implement; batches a round's writes into one atomic store
  read-modify-write).
- Memory I/O was pulled out of the shared tool-loop bookkeeping so each mode does it on the right thread; the
  async/reactive paths assemble the initial messages **off the caller thread** too (CF composes into the returned
  future; the reactive cold publisher assembles on `subscribe`).

### 3.5 Non-blocking guardrails
- `Guardrail.validateAsync(P) : CompletableFuture<R>` — the async primitive (throwing default). CPU-only guardrails
  return `CompletableFuture.completedFuture(validate(request))`; I/O guardrails offload in `validateAsync` (e.g. an
  async moderation client, or `supplyAsync(..., executor)`).
- `GuardrailService.executeGuardrailsAsync` / `GuardrailExecutor.executeAsync` compose the guardrails **without a
  vthread offload** — true async all the way down. `OutputGuardrailExecutor` reimplements the **reprompt/retry
  loop** asynchronously, re-calling the model via `ChatExecutor.executeAsync`. Cancellation is propagated to the
  in-flight `validateAsync` and reprompt model call (best-effort, §3.3).
- **Output guardrails use buffer-then-validate** on the reactive path: partial responses are buffered (not
  streamed) until the assembled final response passes; then the original partials are flushed and the
  (possibly rewritten) final response emitted.
- **Tool-aware reprompts** on all three modes: if a reprompt's response requests tools, they are resolved through
  the (sync or async) tool loop so a guardrail only ever sees a final textual response
  (`ToolAwareRepromptExecutor.wrap` / `wrapAsync`).
- Input guardrails run **off the caller thread** on the CF/reactive paths (deferred, like memory).

### 3.6 Tool compensation across modes
`@CompensateFor` rollback (merged from `main`, originally sync-only) now runs on the CF and reactive paths too,
**truly async (no vthread offload)**:
- A **`@CompensateFor` method may return `void` (synchronous) or `CompletableFuture<Void>` (asynchronous)** — same
  rule as `@Tool` methods and guardrails: a compensating action that performs blocking I/O (e.g. a remote
  "cancelBooking") should return a future, so it never blocks the model-delivery thread.
- The chat-memory rewrite uses the new **`ChatMemory.setAsync(List)`** (§3.4).
- The async compensation (`ToolService.compensateIfNeededAsync`) composes the actions (in reverse order) and the
  memory rewrite without blocking; the synchronous mode (`compensateIfNeeded`) shares the same action runner and
  simply awaits it. A failing action is logged and does not abort the rest, as before.

### 3.7 Design contracts

> **(a) Every async SPI returns `CompletableFuture` (uniform surface); cancellability is a usage decision, not a type.**
> All async SPI methods return `CompletableFuture<T>` (or `Flow.Publisher<T>` for streaming) — `ChatModel.chatAsync`,
> `EmbeddingModel.embedAsync`, `EmbeddingStore.searchAsync`, `ScoringModel.scoreAllAsync`, `WebSearchEngine.searchAsync`,
> the RAG stage methods, `ToolExecutor.executeAsync`, `Guardrail.validateAsync`, `ChatExecutor.executeAsync`, **and** the
> `ChatMemory` / `ChatMemoryStore` async methods. A uniform return type keeps the SPI predictable (a provider implementing
> both an `EmbeddingStore` and a `ChatMemoryStore` sees the same shape) and lets the framework wire cancellation the same
> way everywhere.
> - Whether a given operation is actually **cancelled** is a framework decision, not encoded in the type: the invocation's
>   cancellation is propagated to the in-flight model / RAG / tool / guardrail futures (best-effort, §3.3).
> - **Memory writes are deliberately *not* cancelled** even though the type would allow it — cancelling a memory write
>   mid-flight could corrupt conversation state — so the framework simply lets them run to completion.

> **(b) The new async SPIs throw `UnsupportedOperationException` by default — uniformly.**
> Every async SPI default throws `UnsupportedOperationException`: the model layer (`ChatModel.doChatAsync`,
> `StreamingChatModel.doChat(ChatRequest)`, `EmbeddingModel.doEmbedAsync`, `ScoringModel.scoreAllAsync`),
> `EmbeddingStore.searchAsync`, `WebSearchEngine.searchAsync`, the RAG stage SPIs (`RetrievalAugmentor.augmentAsync`,
> `QueryTransformer.transformAsync`, `QueryRouter.routeAsync`, `ContentRetriever.retrieveAsync`,
> `ContentAggregator.aggregateAsync`), `ChatMemory`/`ChatMemoryStore`, `ToolExecutor.executeAsync`,
> `Guardrail.validateAsync`, `ChatExecutor.executeAsync`, and `HttpClient.executeAsync`/`stream`. Rationale: an
> implementation that is not genuinely asynchronous does not pretend to be — a forgotten (or impossible) async
> implementation fails **loudly** on the async/reactive paths instead of quietly running a blocking call on a parked
> thread. `UnsupportedOperationException` is the internal "not implemented async" signal the orchestrators catch to
> decide offload-vs-fail-loud; the distinct `UnsupportedFeatureException` is reserved for the terminal, user-facing
> error they *emit* when failing loudly (it carries the actionable `offloadBlocking(true)` hint). Most default messages
> follow `"<method>() is not implemented by " + getClass().getName()`; a couple of user-implementable SPIs
> (`ToolExecutor`, `Guardrail`) use a deliberately more actionable "…override X to…" message. Existing synchronous AI
> Services are unaffected (they never call the async methods).

> **(c) Async methods report errors through the future, not by throwing.**
> A method returning a `CompletableFuture` or `Flow.Publisher` reports *operation* errors by completing the future
> exceptionally (or via `onError`), never by throwing synchronously — a synchronous throw would bypass the caller's
> `whenComplete`/`exceptionally` and, on the reactive path, break the Reactive Streams contract by escaping
> `subscribe()`. The throwing SPI *defaults* from (b) are an internal "not implemented" seam; the public async entry
> points (`embedAsync`, `retrieveAsync`, the AI Service's future/publisher) convert such a throw into a failed future /
> `onError`, so a caller always has a single error channel.

### 3.8 Implementation note: `mutiny-zero`
The reactive publishers (the AI Service `AiServiceStreamingEventPublisher`, and the model/HTTP streaming
publishers) are built on **`mutiny-zero`** (`ZeroPublisher` + `Tube`), a tiny Reactive-Streams-compliant emitter
library — chosen so the core does not depend on a full reactive framework.

- Back-pressure strategy is **bounded `BUFFER`**: the model's stream is consumed with unbounded demand and relayed
  through a **bounded** buffer (default **16384** entries). A consumer slower than the model that overflows the
  buffer **fails fast** with `IllegalStateException` rather than dropping events (which would corrupt the assembled
  response) or buffering unbounded (OOM risk).
- The buffer size is **configurable per AI Service** via `AiServices.streamingBufferSize(int)` (and per HTTP/model
  client builder); set it to `Integer.MAX_VALUE` for an effectively unbounded buffer.
- **Why unbounded demand toward the model (rather than socket-level back-pressure)?** The byte/event stream
  *can* be throttled — the JDK HTTP client's demand maps to TCP receive-window and HTTP/2 `WINDOW_UPDATE` flow
  control — but we deliberately don't. Throttling the socket cannot reach token **generation**: providers
  decouple generation from delivery (batched GPU inference into a server-side buffer), the tokens are produced
  and billed regardless of read speed, and intermediary proxies often buffer the full response anyway. The only
  thing client back-pressure would protect is **our heap**, and stalling a half-read response risks an
  idle-timeout connection reset. So we read eagerly and bound memory at the `Tube` buffer instead — a local,
  fail-fast guard that doesn't hold the network call hostage to a slow consumer.

### 3.9 Non-blocking RAG and the blocking-component policy

RAG has an async surface mirroring the synchronous one, at every stage: `RetrievalAugmentor.augmentAsync`,
`QueryTransformer.transformAsync`, `QueryRouter.routeAsync`, `ContentRetriever.retrieveAsync`,
`ContentAggregator.aggregateAsync`, and — beneath the leaf stages — `EmbeddingModel.embedAsync` (over the
overridable `doEmbedAsync`) and `EmbeddingStore.searchAsync` (beneath the embedding retriever),
`ScoringModel.scoreAllAsync` (beneath the re-ranking aggregator), and `WebSearchEngine.searchAsync` (beneath the
web-search retriever). `DefaultRetrievalAugmentor.augmentAsync` composes the
transform → route → retrieve → aggregate stages, each on its component's native async method. The default,
no-I/O stages (`DefaultQueryTransformer`, `DefaultQueryRouter`, `DefaultContentAggregator`, and the CPU-only
`DefaultContentInjector`) complete synchronously, so a pipeline of defaults over an async retriever is fully
non-blocking. `EmbeddingStoreContentRetriever.retrieveAsync` runs the query embedding and the vector-store search
natively non-blocking when the model and store provide async I/O (for example the OpenAI embedding model and the
in-memory store).

**A not-truly-async component fails loudly by default; offloading is opt-in.** The non-blocking API is entirely new
and opt-in, so there is no existing async code that must keep working — which means the framework never needs to
*silently* turn a blocking component into an "async" one by parking a thread. When a component on the async path is
blocking (it does not implement its `*Async` method), the call fails with a clear, actionable error that names the
component, instead of quietly offloading it:

- `EmbeddingStoreContentRetriever.retrieveAsync` fails when its `EmbeddingModel` or `EmbeddingStore` is blocking —
  unless the retriever is built with `offloadBlocking(true)`.
- `DefaultRetrievalAugmentor.augmentAsync` fails when a pipeline stage (transform, route, retrieve or aggregate) is
  blocking — unless built with `DefaultRetrievalAugmentor.builder().offloadBlocking(true)`.
- An asynchronous AI Service fails when its configured `RetrievalAugmentor` does not implement `augmentAsync` —
  unless built with `AiServices.builder().offloadBlocking(true)`.

With `offloadBlocking(true)`, only the blocking component is offloaded to a shared virtual-thread executor (parking
a virtual thread on I/O is non-pinning) — a deliberate, per-component opt-in to "blocking on a (virtual) thread."
This is per-component, not all-or-nothing: `EmbeddingStoreContentRetriever` embeds and searches independently, so an
async embedding model paired with a blocking vector store (the common production case) keeps the model on its native
async path and offloads only the store's search. The opt-in lives on the component that owns the blocking
dependency: the retriever owns its model and store, the AI Service owns the augmentor.

**Why fail rather than offload by default?** Two reasons. First, *clarity*: the caller can tell a genuinely
non-blocking pipeline from an offloaded-blocking one, and an error that says exactly which component to fix is more
useful than hidden behavior. Second, *correctness*: offloading a CPU-bound in-process model (such as an ONNX
embedding model) to a virtual thread pins its carrier thread, so a blanket offload would be actively wrong for part
of the ecosystem. Requiring the component to be async, or the user to opt in, makes offloading an informed choice.

**Scope and exceptions.** Blocking `@Tool` methods are *not* subject to this policy: they are arbitrary user code
that cannot be required to be asynchronous, so they are always offloaded to the virtual-thread executor (a tool that
wants genuine async returns a future). The bare-retriever convenience `AiServices.contentRetriever(...)` builds a
`DefaultRetrievalAugmentor` with the default `offloadBlocking(false)`, so it is fail-loud by default too — a blocking
plain retriever (e.g. a web-search retriever without `searchAsync`) surfaces a clear, actionable error on the async
modes rather than being silently offloaded. To offload it, build a `DefaultRetrievalAugmentor` explicitly with
`offloadBlocking(true)` and pass it via `retrievalAugmentor(...)`, or use a retriever that provides genuine async I/O.

Which stage implementations are async today:

- The default no-I/O stages (`DefaultQueryTransformer`, `DefaultQueryRouter`, `DefaultContentAggregator`) are native.
- The LLM-backed query stages — `CompressingQueryTransformer`, `ExpandingQueryTransformer` and
  `LanguageModelQueryRouter` — run natively over `ChatModel.chatAsync` (so they are genuinely non-blocking when the
  chat model is, e.g. OpenAI).
- `EmbeddingStoreContentRetriever` is native over `EmbeddingModel.embedAsync` + `EmbeddingStore.searchAsync`
  (genuinely non-blocking with, e.g., OpenAI embeddings + a store that overrides `searchAsync`).
- `ReRankingContentAggregator` is native over `ScoringModel.scoreAllAsync`, and `WebSearchContentRetriever` is
  native over `WebSearchEngine.searchAsync`. They are genuinely non-blocking when their model/engine overrides the
  async method — `CohereScoringModel` and `TavilyWebSearchEngine` do (via the OkHttp async dispatcher, no thread
  parked, cancellation propagated to the in-flight call). A `ScoringModel`/`WebSearchEngine` that has not overridden
  its `*Async` method (e.g. `GoogleCustomWebSearchEngine`, `SearchApiWebSearchEngine`) is still usable from the
  non-blocking path: the consumer surfaces `UnsupportedOperationException`, which is offloaded when opted in, or
  fails loudly otherwise.

The `*Async` methods on `ScoringModel` and `WebSearchEngine` are `default` methods that throw
`UnsupportedOperationException`, so adding them is not a breaking change and providers opt in incrementally.

## 4. Supported types

**AI Service method return types**
- `CompletableFuture<T>`, `CompletionStage<T>` — `T` = `String`, a POJO, `Result<T>`, etc.
- Mutiny `Uni<T>`, Reactor `Mono<T>` — via `CompletableFutureAdapter` SPI.
- `Flow.Publisher<AiServiceStreamingEvent>` (rich) or `Flow.Publisher<String>` (text-only) — native.
- Mutiny `Multi<…>`, Reactor `Flux<…>` — via `PublisherAdapter` SPI.

**`@Tool` return types** — synchronous value, `CompletableFuture<T>`, `CompletionStage<T>` (and subtypes), Mutiny
`Uni` / Reactor `Mono` (via adapter).

**Streaming events** (`AiServiceStreamingEvent`): `PartialResponseEvent`, `PartialThinkingEvent`,
`PartialToolCallEvent`, `CompleteToolCallEvent`, `RawEvent`, `RetrievedContentsEvent`, `IntermediateResponseEvent`,
`BeforeToolExecutionEvent`, `AfterToolExecutionEvent`, `FinalResponseEvent`.

**Chat memory** — async SPI on `ChatMemory` + `ChatMemoryStore` (`CompletableFuture`).
**Guardrails** — async SPI on `Guardrail` (`validateAsync`, `CompletableFuture`).

## 5. Headline new APIs

| Concern | API |
|---|---|
| Reactive model stream | `StreamingChatModel.chat(ChatRequest) : Flow.Publisher<StreamingEvent>` |
| Async unary model | `ChatModel.chatAsync(ChatRequest) : CompletableFuture<ChatResponse>` |
| Reactive HTTP | `HttpClient.stream(...)`, `HttpClient.executeAsync(...)` |
| Non-blocking memory | `ChatMemory.addAsync/messagesAsync/setAsync`, `ChatMemoryStore.*Async` |
| Async tool compensation | `@CompensateFor` may return `CompletableFuture<Void>`; `ToolService.compensateIfNeededAsync` |
| Non-blocking guardrails | `Guardrail.validateAsync`, `ChatExecutor.executeAsync` |
| Non-blocking tools | `ToolExecutor.executeAsync`, `ToolService.executeInferenceAndToolsLoopAsync` |
| AI Service stream events | `AiServiceStreamingEvent`, `Flow.Publisher<AiServiceStreamingEvent>` / `Flow.Publisher<String>` |
| Mutiny/Reactor bridges | `CompletableFutureAdapter`, `PublisherAdapter` (SPI) |
| Streaming buffer config | `AiServices.streamingBufferSize(int)` |
| Streaming→future bridge | `StreamingChatModelHelper.chatAsync(model, request)` (`@Internal`) |

## 6. How it's tested

- **BlockHound (runtime):** `AiServicesNonBlockingTest` runs each mode against a stub model that delivers on a
  policed `ai-service-delivery` thread; any hidden blocking call (`Future.get`, sleep, I/O) on that thread fails
  the test. Covers tool loops (sync/async/concurrent/mixed), input + output guardrails (incl. blocking ones made
  async via `validateAsync`, and the reprompt loop), tool compensation (CF + reactive, with a blocking
  `@CompensateFor` rollback returning a `CompletableFuture`), `Result`/POJO parsing, event listeners, a
  deliberately-blocking `ChatMemoryStore`, cancellation, and latch tests proving the **caller** thread isn't
  blocked on memory or input guardrails. A self-test guarantees BlockHound is actually policing.
- **ArchUnit (static):** `AsyncAiServicesArchTest` fails the build if any method in the async pipeline calls a
  blocking API — a guard against regressions on paths a test might not hit.
- **Unit tests per mode** with mock models — `AiServicesAsyncTest` (CF), `AiServiceStreamingPublisherTest` and
  `AiServiceStreamingPublisherGuardrailTest` (publisher), `AsyncChatMemoryTest`; plus a coverage matrix
  (sequential/concurrent × multi-round × error modes × cancellation) mirrored from the legacy suites.
- **Integration tests (real OpenAI):** `AiServicesIT` (CF, CF + chat memory across turns),
  `AiServicesStreamingPublisherIT` (no-tools / tools / tool-provider).

## 7. Usage examples

**Async single response**
```java
interface Assistant {
    CompletableFuture<String> chat(String message);
}

assistant.chat("What is the capital of Germany?")
         .thenAccept(System.out::println);   // never call .get() on an event loop
// Quarkus/Mutiny: Uni.createFrom().completionStage(() -> assistant.chat(msg))
// Spring WebFlux: Mono.fromFuture(() -> assistant.chat(msg))
```

**Structured / Result return**
```java
CompletionStage<String>           chat(String message);
CompletableFuture<Result<Person>> extractPerson(String text);   // sources, token usage, tool executions
```

**Reactive streaming — rich events**
```java
interface Assistant {
    Flow.Publisher<AiServiceStreamingEvent> chat(String message);
}

assistant.chat("Tell me about Berlin").subscribe(new Flow.Subscriber<>() {
    public void onSubscribe(Flow.Subscription s) { s.request(Long.MAX_VALUE); }
    public void onNext(AiServiceStreamingEvent e) {
        switch (e) {
            case PartialResponseEvent p     -> System.out.print(p.partialResponse().text());
            case BeforeToolExecutionEvent b -> log("calling tool…");
            case FinalResponseEvent f       -> done(f.chatResponse());
            default -> {}                   // open event set — keep a default branch
        }
    }
    public void onError(Throwable t) { … }
    public void onComplete() { … }
});
```

**Reactive streaming — text only / third-party types**
```java
Flow.Publisher<String> chat(String message);                 // emits the text of each partial response
io.smallrye.mutiny.Multi<String>     chat(String message);   // via PublisherAdapter SPI
reactor.core.publisher.Flux<String>  chat(String message);
```

**Tools + memory + guardrails (configuration is the same; the return type chooses the mode)**
```java
Assistant assistant = AiServices.builder(Assistant.class)
        .chatModel(model)                                   // or .streamingChatModel(model)
        .chatMemory(MessageWindowChatMemory.withMaxMessages(20))
        .tools(new WeatherTools())                          // sync or CompletableFuture-returning @Tool
        .streamingBufferSize(16384)                         // bounded back-pressure buffer (default)
        .build();                                           // concurrent tools by default for async modes
```

**Cancellation**
```java
CompletableFuture<String> f = assistant.chat("…");
f.cancel(true);   // releases caller, stops rounds, aborts the in-flight model call
```

**Custom async chat-memory store (e.g. reactive Redis) — note `CompletableFuture`**
```java
public class RedisChatMemoryStore implements ChatMemoryStore {
    @Override public CompletableFuture<List<ChatMessage>> getMessagesAsync(Object id) {
        return redis.get(key(id)).toCompletableFuture().thenApply(this::fromJson);  // no thread blocked
    }
    @Override public CompletableFuture<Void> updateMessagesAsync(Object id, List<ChatMessage> m) { … }
    @Override public CompletableFuture<Void> deleteMessagesAsync(Object id) { … }
    // synchronous methods still required (used by the sync / TokenStream modes)
}
```

**I/O guardrail (e.g. remote moderation) — note `CompletableFuture`**
```java
public class ModerationGuardrail implements InputGuardrail {
    @Override public InputGuardrailResult validate(UserMessage msg) { /* sync fallback */ }
    @Override public CompletableFuture<InputGuardrailResult> validateAsync(InputGuardrailRequest req) {
        return moderationClient.checkAsync(req.userMessage().singleText())   // async client, no thread blocked
                .thenApply(ok -> ok ? success() : fatal("blocked"));
    }
}
```

**Tool compensation with an async (I/O) rollback — note `CompletableFuture` `@CompensateFor`**
```java
class BookingTools {
    @Tool String bookFlight(String flight) { return bookingApi.book(flight); }     // sync or async @Tool

    @CompensateFor("bookFlight")
    CompletableFuture<Void> cancelFlight(String flight) {                          // rolled back if a later tool fails
        return bookingApi.cancelAsync(flight);                                     // async client, no thread blocked
    }
}
// AiServices.builder(...).tools(new BookingTools()).compensateOnToolErrors(true).build();
```

## 8. Follow-ups / what's left

| Item | Status |
|---|---|
| **RAG / content retrieval** non-blocking | **Delivered** (§3.9) — async SPI across the whole retrieval graph, fail-loud-by-default with opt-in offload. Native async today: RAG defaults, the LLM-backed query stages (over `chatAsync`), `EmbeddingStoreContentRetriever` (over async embeddings + store), `ReRankingContentAggregator` (Cohere) and `WebSearchContentRetriever` (Tavily). Other providers throw the default and are offloaded-or-fail-loud. |
| **Reactive support for non-OpenAI model providers** | Only OpenAI implements the reactive `doChat` publisher / async `doChatAsync` / async embeddings; other chat/embedding providers fall back to the throwing defaults and need a per-provider implementation (or a handler→publisher bridge). |
| Per-provider `setAsync` / async stores | `ChatMemory.setAsync` and the async store methods are implemented by the bundled in-memory stores; persistent-store integrations (Redis, JDBC, …) need their async methods implemented to be non-blocking on the async/reactive paths (they throw by default). |
| **Tool cancellation** (interrupting already-started tools) | Parked by design — contract is run-to-completion, result discarded. |
| Moderation (`@Moderate`) on the new APIs | Intentionally **forbidden** (fails fast) — not meaningful for the async/reactive flow. |
| Minor naming / cleanup `TODO`s | A few leftover `// TODO`s remain in javadoc/code (e.g. `ToolExecutor`, `DefaultToolExecutor`, `StreamingChatModel`); harmless, to be swept before release. |
| `@since` tags | Not yet reconciled to the final release version — the async members carry a mix (`1.13.0`/`1.17.0`/`1.18.0`) to be normalized in one pass at release. |

