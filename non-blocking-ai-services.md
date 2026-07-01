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
| Memory | `ChatMemory`, `ChatMemoryStore` | `add`/`messages`/`set`, `getMessages`/`updateMessages`/`deleteMessages` | `addAsync(List)`/`messagesAsync`/`setAsync(List)`, `getMessagesAsync`/`updateMessagesAsync`/`deleteMessagesAsync` (`CompletionStage`) |
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

Today only the **OpenAI** provider implements the async + reactive model methods; other providers fall back to
the throwing defaults until implemented (see §8).

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
- The scarce **model-delivery thread** is never blocked — enforced by BlockHound (§6).

### 3.2 Concurrency & error defaults for the new APIs
- **Tools run concurrently by default** for the async/reactive modes (legacy modes stay sequential); opt out with
  `executeToolsConcurrently(false)`.
- **Reversed, safer tool-error defaults** for the new APIs: a tool **execution** error **fails** the invocation; a
  tool **argument-parse** error is **sent back to the LLM**. (Old APIs keep their existing behavior.)
- On the publisher path, tools start **as soon as** their call completes streaming (`onCompleteToolCall`),
  overlapping with the rest of the stream.

### 3.3 Cancellation contract
Cancelling the `CompletableFuture` (`cancel(true)`) or the `Flow.Subscription` releases the caller, stops further
rounds, and best-effort aborts the in-flight model HTTP call. A tool (or guardrail validation) that has **already
started is not interrupted** — it runs to completion and its result is discarded (Java can't safely interrupt
arbitrary code; documented as deliberate best-effort).

### 3.4 Non-blocking chat memory
- `CompletionStage`-returning methods: `ChatMemoryStore.{getMessagesAsync, updateMessagesAsync,
  deleteMessagesAsync}` and `ChatMemory.{addAsync(List<ChatMessage>), messagesAsync, setAsync(List<ChatMessage>)}`
  (`setAsync` is the async bulk-replace used e.g. by async tool compensation, delegating to the store's
  `updateMessagesAsync`).
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
  loop** asynchronously, re-calling the model via `ChatExecutor.executeAsync`.
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

> **(a) `CompletableFuture` vs `CompletionStage` is the cancellability contract.**
> - SPIs whose operation sits in the **cancellable execution path** return **`CompletableFuture`**: `ChatModel.chatAsync`,
>   `ToolExecutor.executeAsync`, `Guardrail.validateAsync`, `ChatExecutor.executeAsync`, the guardrail/tool-loop async methods.
>   The framework may cancel them when the invocation is cancelled (best-effort, §3.3).
> - SPIs whose operation **must run to completion for consistency** return **`CompletionStage`**: `ChatMemory` /
>   `ChatMemoryStore` async methods — cancelling a memory write mid-flight could corrupt conversation state, so it is never
>   cancelled and the type advertises that.

> **(b) The new async SPIs throw by default.**
> `ChatMemory`/`ChatMemoryStore`, `ToolExecutor`, and `Guardrail` all default their async methods to
> `UnsupportedOperationException`. Rationale: callers must **opt in** to the non-blocking paths rather than have a
> (potentially blocking) synchronous implementation silently run on the model-delivery thread. A forgotten override fails
> **loudly** on the async/reactive paths instead of quietly blocking. Existing synchronous AI Services are unaffected (they
> never call the async methods).

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

**Chat memory** — async SPI on `ChatMemory` + `ChatMemoryStore` (`CompletionStage`).
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

**Custom async chat-memory store (e.g. reactive Redis) — note `CompletionStage`**
```java
public class RedisChatMemoryStore implements ChatMemoryStore {
    @Override public CompletionStage<List<ChatMessage>> getMessagesAsync(Object id) {
        return redis.get(key(id)).toCompletableFuture().thenApply(this::fromJson);  // no thread blocked
    }
    @Override public CompletionStage<Void> updateMessagesAsync(Object id, List<ChatMessage> m) { … }
    @Override public CompletionStage<Void> deleteMessagesAsync(Object id) { … }
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
| **RAG / content retrieval** non-blocking | Deferred — retrieval still runs on the caller thread (opt-in feature). Highest-value next step; needs an async `RetrievalAugmentor` SPI. |
| **Reactive support for non-OpenAI model providers** | Only OpenAI implements the reactive `doChat` publisher / async `doChatAsync`; others fall back to the throwing defaults and need a per-provider implementation (or a handler→publisher bridge). |
| Per-provider `setAsync` / async stores | `ChatMemory.setAsync` and the async store methods are implemented by the bundled in-memory stores; persistent-store integrations (Redis, JDBC, …) need their async methods implemented to be non-blocking on the async/reactive paths (they throw by default). |
| **Tool cancellation** (interrupting already-started tools) | Parked by design — contract is run-to-completion, result discarded. |
| Moderation (`@Moderate`) on the new APIs | Intentionally **forbidden** (fails fast) — not meaningful for the async/reactive flow. |
| Minor naming `TODO`s (e.g. `messagesAsync`) | Open. |

