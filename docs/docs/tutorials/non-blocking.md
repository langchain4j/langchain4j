---
sidebar_position: 36
---

# Non-blocking and Reactive

:::note
Asynchronous and reactive support is experimental. The asynchronous and reactive APIs on this page are
annotated `@Experimental`;
APIs and behavior may still change in future releases. The synchronous and `TokenStream` APIs are unaffected.
:::

By default, calling an AI Service blocks the calling thread for the whole interaction: the LLM call, tool
executions, chat memory access and guardrails all happen before the method returns. That is simple and works well
for most applications.

If you are building on a reactive stack (Quarkus/Mutiny, Vert.x, Spring WebFlux) or you need one thread to drive
many concurrent interactions, LangChain4j can run the same interaction without blocking. You choose by declaring a
different **return type** on your AI Service method — nothing else about the interface changes.

## The four modes

| Return type | Nature |
|---|---|
| `String`, a POJO, `Result<T>`, … | synchronous, blocks the calling thread |
| `TokenStream` | streaming via callbacks |
| `CompletableFuture<T>`, `CompletionStage<T>` | one response, non-blocking |
| `Flow.Publisher<AiServiceStreamingEvent>`, `Flow.Publisher<String>` | a stream of events, non-blocking |

```java
interface Assistant {

    // synchronous
    String chat(String message);

    // one response, without blocking the caller
    CompletableFuture<String> chatAsync(String message);

    // the answer, streamed token by token
    Flow.Publisher<String> chatStreaming(String message);

    // everything that happens during the interaction, as events
    Flow.Publisher<AiServiceStreamingEvent> chatEvents(String message);
}
```

## Getting one response without blocking

```java
CompletableFuture<String> future = assistant.chatAsync("Tell me a joke");

future.thenAccept(System.out::println);
```

Cancelling the future (`future.cancel(true)`) releases the caller, stops further LLM rounds and makes a
best-effort attempt to abort the in-flight HTTP call.

:::note
The future is completed on the model's own thread — for HTTP models, the transport's I/O worker that reads the
response. A continuation attached without an explicit executor (`thenApply`, `thenAccept`, …) therefore runs on
that thread, where blocking degrades throughput for every in-flight call. Keep continuations non-blocking, or pass
your own `Executor`: `future.thenApplyAsync(fn, executor)`.
:::

## Streaming the answer

A method returning `Flow.Publisher<String>` streams the text of the answer, chunk by chunk:

```java
Flow.Publisher<String> publisher = assistant.chatStreaming("Tell me a joke");
```

The publisher is **cold**: nothing happens until you subscribe, and each subscription starts a new interaction.

## Streaming everything that happens

`Flow.Publisher<AiServiceStreamingEvent>` surfaces the whole interaction, not just the answer text — the same
information the callback-based `TokenStream` gives you:

| Event | Meaning |
|---|---|
| `PartialResponseEvent` | a chunk of the answer |
| `PartialThinkingEvent` | a chunk of the model's reasoning |
| `PartialToolCallEvent`, `CompleteToolCallEvent` | a tool call being assembled, then completed |
| `BeforeToolExecutionEvent`, `AfterToolExecutionEvent` | a tool about to run, and its result |
| `IntermediateResponseEvent` | the response that closed one tool-calling round |
| `RetrievedContentsEvent` | content retrieved by RAG |
| `ToolCompensatedEvent` | a completed tool was compensated after the interaction was cancelled or failed |
| `RawEvent` | a provider-specific event LangChain4j does not map |
| `FinalResponseEvent` | the final answer |

```java
assistant.chatEvents("What is the weather in Munich?").subscribe(new Flow.Subscriber<>() {

    @Override
    public void onSubscribe(Flow.Subscription subscription) {
        subscription.request(Long.MAX_VALUE);
    }

    @Override
    public void onNext(AiServiceStreamingEvent event) {
        // the event types are nested in AiServiceStreamingEvent:
        // import dev.langchain4j.service.AiServiceStreamingEvent.PartialResponseEvent;
        if (event instanceof PartialResponseEvent partial) {
            System.out.print(partial.partialResponse().text());
        }
    }

    @Override
    public void onError(Throwable error) { }

    @Override
    public void onComplete() { }
});
```

New event types may be added over time, so handle unrecognized ones gracefully — do not write an exhaustive type
switch without a default branch.

:::note
Do not block in `onNext`. Events are delivered on whichever thread produced them: the model's transport I/O worker
for the token-level events, or the thread that completed a tool call for the tool-execution events. Offload heavy
per-event work to your own `Executor`. Events are relayed through a bounded buffer, so a subscriber that falls far
enough behind terminates with an `IllegalStateException` instead of buffering without limit; the size defaults to 16384 events and is
configurable with `AiServices.builder(...).streamingBufferSize(int)`.
:::

## Third-party reactive types

AI Service methods return JDK types — `CompletableFuture` and `Flow.Publisher` — so the API does not tie you to
any particular reactive programming library.

:::warning
Returning a library's own type (`Uni`/`Multi`, `Mono`/`Flux`) from a non-blocking AI Service method is **not
supported yet**. The seam exists — the `CompletableFutureAdapter` and `PublisherAdapter` SPIs, discovered via
`ServiceLoader` — but both are internal, and no adapter ships with LangChain4j today. Declaring such a return type
currently fails. Use `CompletableFuture` or `Flow.Publisher` and adapt at the call site.
:::

`Flux<String>` is available through the separate `langchain4j-reactor` module — see
[AI Services](/tutorials/ai-services#flux). That is an adapter over `TokenStream` and predates this feature; it is
not part of the non-blocking path described here.

## What has to be non-blocking

Non-blocking has to hold at every layer: a blocking step anywhere makes the whole call blocking again. Each layer
has an asynchronous counterpart alongside its existing blocking method:

| Layer | Blocking | Non-blocking counterpart |
|---|---|---|
| Chat model | `chat(ChatRequest)` | `chatAsync(ChatRequest)`, and `chat(ChatRequest)` returning a `Publisher` on `StreamingChatModel` |
| Embedding model | `embed(...)` | `embedAsync(...)` |
| Scoring model | `scoreAll(...)` | `scoreAsync(...)` |
| Chat memory | `add`, `messages`, `set` | `addAsync`, `messagesAsync`, `setAsync` |
| Chat memory store | `getMessages`, `updateMessages`, `deleteMessages` | `getMessagesAsync`, `updateMessagesAsync`, `deleteMessagesAsync` |
| Guardrails | `validate(...)` | `validateAsync(...)` |
| Tools | `ToolExecutor.execute(...)` | `ToolExecutor.executeAsync(...)` |
| RAG | `RetrievalAugmentor.augment(...)`, retrievers, routers, aggregators, query transformers | `augmentAsync(...)`, `retrieveAsync(...)`, `routeAsync(...)`, `aggregateAsync(...)`, `transformAsync(...)` |
| Embedding store | `search(...)` | `searchAsync(...)` |
| Web search | `search(...)` | `searchAsync(...)` |
| MCP | `McpClient.executeTool(...)` | `executeToolAsync(...)` |
| Guardrail execution | `ChatExecutor.execute(...)` | `ChatExecutor.executeAsync(...)` |
| HTTP client | `execute(...)` | `executeAsync(...)`, `stream(...)` |

A component that has not implemented its counterpart **fails loudly** rather than silently blocking: the returned
future or publisher fails with an `AsyncNotSupportedException` naming the component and the missing method.
`AsyncNotSupportedException` is an `UnsupportedFeatureException`, so one catch clause covers both flavours of
"not supported", and it is never retried.

## Blocking code you cannot avoid

Tools are the common case: a tool that calls a database or a blocking HTTP API cannot be made non-blocking. Such
tools are offloaded so they never block the model's own thread.

:::note
On Java 21 and later the offload executor creates virtual threads, so a blocking tool parks a *virtual* thread,
which unmounts from its carrier instead of occupying it. LangChain4j targets Java 17, and on Java 17-20 the same
executor is an **unbounded pool of platform threads** — a very different resource profile under load. Supply your
own bounded `Executor` (see below) if that matters to you.
:::

This applies to `@Tool`-annotated methods. A hand-written `ToolExecutor` that does not override `executeAsync`
fails loudly with an `AsyncNotSupportedException` instead, like any other component that has not opted in.

Tools run **concurrently** by default in the asynchronous and reactive modes. To run them one at a time, pass a
single-threaded executor:

```java
AiServices.builder(Assistant.class)
        .chatModel(model)
        .tools(new MyTools())
        .executeToolsConcurrently(Executors.newSingleThreadExecutor())
        .build();
```

For RAG, a retriever that has not implemented `retrieveAsync` fails by default rather than silently blocking. Opt
in to offloading it instead with `offloadBlocking(true)` on `DefaultRetrievalAugmentor` or
`EmbeddingStoreContentRetriever`.

## Defaults that differ from the synchronous modes

The asynchronous and reactive modes are not just the same interaction on another thread — a few defaults are
deliberately different. If you are migrating an existing method, these are the ones to check.

| | Synchronous / `TokenStream` | `CompletableFuture` / `Flow.Publisher` |
|---|---|---|
| Multiple tool calls | executed **sequentially** | executed **concurrently** |
| Tool **execution** error | sent back to the LLM | **fails the invocation** |
| Tool **argument-parse** error | **fails the invocation** | sent back to the LLM |
| `@Moderate` | supported | rejected at AI Service creation |

### Tool errors

The two tool error defaults are reversed on purpose. Sending an *execution* failure to the LLM hides a bug in your
tool from you and invites the model to invent an answer around it, so the asynchronous modes fail the invocation
instead. A malformed *argument* string, on the other hand, is something the model produced and can usually fix
when told, so it is sent back rather than failing the call.

Both remain configurable, and an explicitly configured handler is used by every mode:

```java
AiServices.builder(Assistant.class)
        .chatModel(model)
        .tools(new MyTools())
        .toolExecutionErrorHandler(myExecutionErrorHandler)
        .toolArgumentsErrorHandler(myArgumentsErrorHandler)
        .build();
```

See [Error Handling](/tutorials/tools#error-handling) for what those handlers can do.

### Tool concurrency

Because tools always run on an `Executor` in these modes, several tool calls in one LLM response run at the same
time. If your tools are not safe to run concurrently, pass a single-threaded executor:
`executeToolsConcurrently(Executors.newSingleThreadExecutor())`.

### Event ordering

On the reactive path a tool starts as soon as its `CompleteToolCallEvent` is emitted, so it overlaps the rest of
the round. A round's `BeforeToolExecutionEvent` / `AfterToolExecutionEvent` may therefore arrive **before** that
round's `IntermediateResponseEvent`. Treat `IntermediateResponseEvent` as a per-round marker, not as a barrier
that all of the round's tool events precede. The callback-based `TokenStream` always reports the intermediate
response first.

## Controlling the executor and propagating context

Everywhere LangChain4j runs work off the caller thread — concurrent tool calls, offloaded retrieval, retry backoff
— it takes the executor from one pluggable seam, the `ExecutorProvider` SPI:

```java
public interface ExecutorProvider {
    Executor executor();
}
```

Register one via `ServiceLoader`, or programmatically for tests and non-DI applications:

```java
ExecutorProvider.set(() -> myExecutor);
```

Without one, LangChain4j uses a virtual-thread-per-task executor on Java 21 or later, and an unbounded
platform-thread pool on Java 17-20.

:::note
A single invocation now crosses several threads, so ambient `ThreadLocal` state — MDC logging context, tracing
spans, security context — is **not** automatically propagated the way it is in the fully synchronous mode. Return a
context-propagating executor from your provider to make it follow the work: a `ManagedExecutor` on
Quarkus/MicroProfile, a `TaskDecorator`-wrapped executor on Spring, `Context.taskWrapping(executor)` for
OpenTelemetry, or `ContextSnapshot.wrap(executor)` for Micrometer. On Spring Boot you can point LangChain4j at
the application's executor with one property instead — see [Spring Boot](#spring-boot) below.

`InvocationContext` is unaffected — it is passed explicitly as a parameter, never through a thread-local.
:::

## Model listeners must not block

`ChatModelListener` and `EmbeddingModelListener` callbacks are invoked synchronously on the model's own threads and
are never offloaded. On the asynchronous and reactive APIs that means the transport's I/O worker. A listener that
performs blocking I/O there stalls that worker and degrades throughput for every in-flight call — offload such work
to your own executor from inside the callback. See [Observability](/tutorials/observability).

## Provider support

:::warning
**The non-blocking modes only work with providers that have implemented them.** Declaring a
`CompletableFuture` or `Flow.Publisher` return type against any other provider compiles, and then fails at
runtime with an `AsyncNotSupportedException` naming the component and the missing method. There is no silent
fallback to a blocking call — that is the point, but it means the return type you can use depends on your
provider.
:::

Support is opt-in per provider and is being rolled out gradually. Today:

| Provider | `CompletableFuture` (`chatAsync`) | `Flow.Publisher` (reactive `chat`) |
|---|---|---|
| OpenAI — Chat Completions | ✅ | ✅ |
| OpenAI — Responses | ✅ | ✅ |
| Anthropic | ✅ | ✅ |
| Bedrock | ✅ | ✅ |
| every other provider | ❌ | ❌ |

Beyond chat models: OpenAI implements asynchronous embeddings (`embedAsync`), Cohere asynchronous scoring
(`scoreAsync`) and Tavily asynchronous web search (`searchAsync`).

A provider that has not opted in is unaffected on the synchronous and `TokenStream` APIs — those keep working
exactly as before.

### The same applies to everything else in the call

A chat model that supports the mode is necessary but not sufficient: a single blocking component anywhere in the
interaction fails the call the same way. In practice that means:

| Component | What it must implement | Bundled implementations |
|---|---|---|
| Chat memory | `addAsync` / `messagesAsync` / `setAsync` | `MessageWindowChatMemory` and `InMemoryChatMemoryStore` already do |
| Guardrails | `validateAsync` | none — a guardrail you write must override it, even if it does no I/O |
| Content retriever, query router, aggregator | `retrieveAsync`, `routeAsync`, `aggregateAsync` | opt into offloading instead with `offloadBlocking(true)` |
| Embedding store | `searchAsync` | as above, via the retriever's `offloadBlocking(true)` |
| Custom `ToolExecutor` | `executeAsync` | `@Tool`-annotated methods are offloaded for you |

A guardrail that does no blocking work satisfies the contract in one line:

```java
@Override
public CompletableFuture<InputGuardrailResult> validateAsync(InputGuardrailRequest request) {
    return CompletableFuture.completedFuture(validate(request));
}
```

### Spring Boot

`Flux<String>` keeps working with **every** provider, including those in the ❌ row: it is served by the
`TokenStream`-based adapter in `langchain4j-reactor`, not by the non-blocking path described on this page.

Reactor bindings for the non-blocking modes themselves — `Mono<T>` and `Flux<AiServiceStreamingEvent>` — are not
available yet; see [Third-party reactive types](#third-party-reactive-types) above. When they ship they will carry
the same provider constraint as the JDK types.

To make ambient context follow an asynchronous invocation, let LangChain4j offload to the application's own
executor rather than its default one:

```properties
langchain4j.executor.use-spring-task-executor=true
```

Spring's task executor propagates tracing spans, MDC and security context when a `TaskDecorator` is installed
(Micrometer context propagation and Spring Security both install one), and its pool follows
`spring.task.execution.*`. It is off by default because the setting is process-wide, not scoped to one
application context.
