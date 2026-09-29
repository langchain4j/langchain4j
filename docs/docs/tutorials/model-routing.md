---
sidebar_position: 40
---

# Model Routing

:::note
Model routing is experimental and may change in future releases.
:::

Different requests need different models: a greeting or a short factual question can be answered by a small, fast
and cheap model, while writing code or a detailed analysis needs a larger one. With model routing, each request is
sent to the model that fits it, without changing the rest of the application.

`RoutingChatModel` is a `ChatModel` that sends each request to one of several chat models (routes), as decided by a
`ChatModelRouter`. Since it is a `ChatModel`, it can be used everywhere a chat model is used: AI Services, agents,
RAG, etc.

```java
ChatModel chatModel = RoutingChatModel.builder()
        .route("simple", smallModel, "Greetings, small talk and short factual questions")
        .route("complex", largeModel, "Writing or debugging code, multi-step reasoning, detailed analysis")
        .router(new DecisionModelChatModelRouter(decisionModel))
        .defaultRoute("complex")
        .build();

Assistant assistant = AiServices.create(Assistant.class, chatModel);
```

For streaming, use `RoutingStreamingChatModel` with `StreamingChatModel`s; it works the same way.

## Routers

A `ChatModelRouter` returns the name of the route for a request, or `null` to use the default route.
The default route is required, so that there is always a model to send the request to, for example when a router that
calls a model is unsure or fails.

### Decision model router

`DecisionModelChatModelRouter` uses a [decision model](/tutorials/decision-models) to choose the route whose
description fits the last user message best. Content other than text, such as an image, is represented by a marker
(for example `[attached image]`), so that a route whose description mentions images can be chosen for it. Decision models are typically much faster and cheaper than chat
models, so routing adds little latency and cost compared to the chat model call. Every route needs a description.

It returns `null` (so the default route is used) when the request has no user message, and when the probability of
the chosen route is below `minProbability` (or no probabilities are reported), which is useful to send the requests
the decision model is not sure about to the larger model. When the decision model fails, the default route is used
and a warning is logged; set `fallbackStrategy(FAIL)` to fail the request instead.
The routes' descriptions are checked when the routing chat model is created.

By default, only the last user message is taken into account. A short follow-up such as "yes, go ahead" only makes
sense together with the previous messages: set `maxMessages` to also send the previous messages of the conversation
to the decision model.

```java
ChatModelRouter router = DecisionModelChatModelRouter.builder()
        .decisionModel(decisionModel)
        .minProbability(0.7)
        .maxMessages(3)
        .build();
```

### Custom routers

A router can also be a simple rule:

```java
ChatModel chatModel = RoutingChatModel.builder()
        .route("simple", smallModel)
        .route("complex", largeModel)
        .router(request -> request.chatRequest().messages().size() > 20 ? "complex" : "simple")
        .defaultRoute("simple")
        .build();
```

The router receives the `ChatRequest` and the routes that can handle it (`ChatModelRoute`: name and description).
The asynchronous and streaming methods use `routeAsync(...)`. By default, it is not implemented, so `route(...)` is
called on an executor: a router that blocks (for example, one that looks up the user in a database) never blocks the
calling thread. A router that can route without blocking, for example by calling a remote service asynchronously, can
implement `routeAsync(...)` to avoid the thread switch. A router can also check the routes when the routing chat model
is created, by implementing `validate(...)`.

## How requests are routed

- The selected model handles the request as if it was called directly: its default parameters and listeners apply.
- The requests can go to models of different providers, so set only common request parameters
  (`ChatRequestParameters`), not provider-specific ones.
- The name of the selected route is added to the listener attributes of the call
  (under `RoutingChatModel.ROUTE_ATTRIBUTE`), so that the listeners of the selected model can report it, and stored
  in the attributes of the returned `AiMessage`.
- The rounds of a tool-calling loop stay on the same model: a request that ends with tool results goes to the route
  stored in the `AiMessage` that requested the tools, without asking the router. This also works with a persistent
  chat memory, as long as it keeps the attributes of the messages (the default serialization does).
- `supportedCapabilities()` returns the capabilities supported by at least one route. A request that needs a
  capability (for example, a JSON schema response format) is only routed to the routes that declare it:
  the router only sees those routes, and if the default route does not declare it, the first route that does is used.
  If no route declares the capability, all routes remain candidates, and the selected model accepts or rejects the
  request itself, as when it is called directly.
- `chatAsync(...)` and streaming select the route with `ChatModelRouter.routeAsync(...)`.
  `DecisionModelChatModelRouter` implements it with `DecisionModel.decideAsync(...)`; routers that don't implement it,
  and decision models that do not support asynchronous calls, are called on the default executor instead
  (or the one configured with `executor(...)`).
- If the router returns an unknown route, the request fails with an `IllegalStateException`.
- The selected model is called on the thread that completed the routing: the calling thread for synchronous routers,
  the thread of the decision model's response, or the executor configured with `executor(...)`.
- When routing chat models are nested, the name stored in the `AiMessage` is the one of the outer routing chat model.

## Things to keep in mind

- All routes share the conversation: after a new user message, a different route can receive the messages that
  another route produced. Routes of different providers must be able to read each other's messages. This can fail
  with provider-specific content, for example returned thinking with signatures or tool call ids in a format another
  provider does not accept. Routing between models of the same provider avoids this.
- `DecisionModelChatModelRouter` sees the text of the messages, not the content of images or other attachments: it
  only knows that they are attached.
- The routing chat model has no default request parameters of its own, and `provider()` returns `OTHER`: code that
  reads the default request parameters from the chat model (for example to adjust `toolChoice`) sees empty
  parameters, not those of the routes. The default parameters of the selected model still apply to each request.
- When the routing chat model and its routes are all beans of the same type (for example in Spring or Quarkus),
  mark the routing chat model as the primary or default one, so that it is the one injected.
- Routing selects a model before the request is sent; it does not retry a failed request on another model.
