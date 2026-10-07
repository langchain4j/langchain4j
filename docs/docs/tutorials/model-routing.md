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
        .route("simple", "Greetings, small talk and short factual questions", smallModel)
        .route("complex", "Writing or debugging code, multi-step reasoning, detailed analysis", largeModel)
        .router(new DecisionModelChatModelRouter(decisionModel))
        .defaultRoute("complex")
        .build();

Assistant assistant = AiServices.create(Assistant.class, chatModel);
```

`smallModel` and `largeModel` are any `ChatModel`s, and `decisionModel` is any `DecisionModel`, for example
`TypeSafeDecisionModel` (see [Decision Models](/tutorials/decision-models)). Routers that need no model are shown
below.

For streaming, use `RoutingStreamingChatModel` with `StreamingChatModel`s; it works the same way.

## Routers

A `ChatModelRouter` returns a `ChatModelRoutingResult`: `ChatModelRoutingResult.route(name)` for one of the routes,
or `ChatModelRoutingResult.defaultRoute()` for the default route.
The default route is required, so that there is always a model to send the request to, for example when a router that
calls a model is unsure or fails.

### Decision model router

`DecisionModelChatModelRouter` uses a [decision model](/tutorials/decision-models) to choose the route whose
description fits the request best. It sends the last 3 messages of the conversation (`maxMessages`), so that short
follow-ups such as "yes, go ahead" are understood. Only user messages and text responses of the AI are sent and
counted: system messages, tool calls and tool results are left out. Content other than text, such as an image, is represented by a
marker (for example `[attached image]`), so that a route whose description mentions images can be chosen for it.
Decision models are typically much faster and cheaper than chat models, so routing adds little latency and cost
compared to the chat model call. Describe each route well; a route without a description is described by its name.

It selects the default route when the request has no user message, and when the probability of
the chosen route is below `minProbability`, which is useful to send the requests the decision model is not sure
about to the larger model. `minProbability` requires a decision model that reports probabilities; otherwise the call
fails. When the decision model fails, the default route is used and a warning is logged; set
`fallbackStrategy(FAIL)` to fail the request instead.

```java
ChatModelRouter router = DecisionModelChatModelRouter.builder()
        .decisionModel(decisionModel)
        .minProbability(0.7)
        .build();
```

A route meant for requests about several unrelated topics can have several descriptions:

```java
ChatModel chatModel = RoutingChatModel.builder()
        .route("simple", "Greetings, small talk and short factual questions", smallModel)
        .route("complex", List.of("Writing or debugging code", "Legal contract analysis", "Tax planning"), largeModel)
        .router(new DecisionModelChatModelRouter(decisionModel))
        .defaultRoute("simple")
        .build();
```

The decision model then considers each description as a separate option, and the probability of the route is the
sum of the probabilities of its descriptions. Compared to one description that lists all topics, this can make
the decision more confident, especially with smaller decision models. When the decision model is unsure, it spreads
the probability over all options, so a route with more descriptions gets more of it: keep the number of descriptions
of the routes balanced, unless that route should win unclear cases.

### Custom routers

A router can also be a simple rule:

```java
ChatModel chatModel = RoutingChatModel.builder()
        .route("simple", smallModel)
        .route("complex", largeModel)
        .router(request -> ChatModelRoutingResult.route(
                request.chatRequest().messages().size() > 20 ? "complex" : "simple"))
        .defaultRoute("simple")
        .build();
```

The router receives the `ChatRequest`, the options of the call and the routes that can handle it (`ChatModelRoute`:
name and descriptions).
A router can also check the routes when the routing chat model is created, by implementing `validate(...)`.

### Asynchronous calls

`chat(...)` and streaming with a `StreamingChatResponseHandler` call `route(...)` on the calling thread, which blocks
while the route is selected: with `DecisionModelChatModelRouter`, until the decision model answers. Do not call them
from an event loop thread (for example, in Vert.x or Quarkus reactive endpoints).
The non-blocking methods, `chatAsync(...)` and streaming to a `Publisher` (`StreamingChatModel.chat(ChatRequest)`),
call `routeAsync(...)` instead, so that routing never blocks. `routeAsync(...)` is not implemented by default,
so a router written as a lambda makes these calls fail with an `AsyncNotSupportedException`.
To use such a router in non-blocking calls, implement `routeAsync(...)`:

```java
ChatModelRouter router = new ChatModelRouter() {

    @Override
    public ChatModelRoutingResult route(ChatModelRoutingRequest request) {
        return ChatModelRoutingResult.route(
                request.chatRequest().messages().size() > 20 ? "complex" : "simple");
    }

    @Override
    public CompletableFuture<ChatModelRoutingResult> routeAsync(ChatModelRoutingRequest request) {
        return CompletableFuture.completedFuture(route(request)); // route(...) does not block
    }
};
```

A router that blocks, for example one that looks up the user in a database, can instead run `route(...)` on an
executor of its choice with `CompletableFuture.supplyAsync(() -> route(request), executor)`, or better, use a
non-blocking client. `DecisionModelChatModelRouter` implements `routeAsync(...)` with `DecisionModel.decideAsync(...)`.

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
- If the router returns an unknown route, the request fails with an `IllegalStateException`.
- The selected model is called on the thread that completed the routing: the calling thread, or, in non-blocking
  calls, the thread that completed `routeAsync(...)` (for example, the thread of the decision model's response).
- When routing chat models are nested, the name stored in the `AiMessage` is the one of the outer routing chat model.
  In a tool-calling loop, the inner routing chat model then does not find its own route, so it asks its router again,
  which can select another model. If an inner route has the same name as an outer route, the inner routing chat model
  uses its route of that name. Give the routes of nested routing chat models distinct names.

## Things to keep in mind

- All routes share the conversation: after a new user message, a different route can receive the messages that
  another route produced. Routes of different providers must be able to read each other's messages. This can fail
  with provider-specific content, for example returned thinking with signatures or tool call ids in a format another
  provider does not accept. Routing between models of the same provider avoids this.
- `DecisionModelChatModelRouter` sees the text of the messages, not the content of images or other attachments: it
  only knows that they are attached.
- In an AI Service with RAG, `DecisionModelChatModelRouter` sees the user message with the retrieved content added
  to it, as the chat model does: the retrieved documents take part in the decision, and long content can exceed the
  input limit of the decision model, in which case the default route is used and a warning is logged.
- Prompt caching is per model: a conversation that switches between routes does not benefit from the cache of the
  previous model, which can outweigh the savings of routing in long conversations.
- The routing chat model has no default request parameters of its own, and `provider()` returns `OTHER`: code that
  reads the default request parameters from the chat model (for example to adjust `toolChoice`) sees empty
  parameters, not those of the routes. The default parameters of the selected model still apply to each request.
- When the routing chat model and its routes are all beans of the same type, make sure that the AI Service uses the
  routing chat model, for example in Spring Boot with
  `@AiService(wiringMode = EXPLICIT, chatModel = "routingChatModel")`.
- Listeners are configured on the route models, not on the routing chat model: the listeners of the selected model
  observe each call, and the route name is available in their attributes (`RoutingChatModel.ROUTE_ATTRIBUTE`),
  except with the `Publisher` returned by `StreamingChatModel.chat(ChatRequest)`, which takes no options: then the
  route name is only in the attributes of the response.
- Routing selects a model before the request is sent; it does not retry a failed request on another model.
