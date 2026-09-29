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

### Decision model router

`DecisionModelChatModelRouter` uses a [decision model](/tutorials/decision-models) to choose the route whose
description fits the last user message best. Decision models are typically much faster and cheaper than chat
models, so routing adds little latency and cost compared to the chat model call. Every route needs a description.

It returns `null` (so the default route is used) when the request has no user message, and when the probability of
the chosen route is below `minProbability` (or no probabilities are reported), which is useful to send the requests
the decision model is not sure about to the larger model. When the decision model fails, the default route is used
and a warning is logged; set `fallbackStrategy(FAIL)` to fail the request instead.
The routes' descriptions are checked when the routing chat model is created.

```java
ChatModelRouter router = DecisionModelChatModelRouter.builder()
        .decisionModel(decisionModel)
        .minProbability(0.7)
        .build();
```

### Custom routers

A router can also be a simple rule:

```java
ChatModel chatModel = RoutingChatModel.builder()
        .route("simple", smallModel)
        .route("complex", largeModel)
        .router(request -> request.chatRequest().messages().size() > 20 ? "complex" : "simple")
        .build();
```

The router receives the `ChatRequest` and the routes that can handle it (`ChatModelRoute`: name and description).
A router that calls a remote service should also implement `routeAsync(...)`, which is used by the asynchronous and
streaming methods; by default, it calls `route(...)`. A router can also check the routes when the routing chat model
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
  capability (for example, a JSON schema response format) is only routed to the routes that support it:
  the router only sees those routes, and if the default route does not support it, the first route that does is used.
- `chatAsync(...)` and streaming select the route with `ChatModelRouter.routeAsync(...)`, so they don't block the
  calling thread when the router doesn't. `DecisionModelChatModelRouter` uses `DecisionModel.decideAsync(...)`; if the
  decision model does not support asynchronous calls, the router is called on the default executor instead.
- If the router returns an unknown route, or no route when there is no default route, the request fails with an
  `IllegalStateException`.
