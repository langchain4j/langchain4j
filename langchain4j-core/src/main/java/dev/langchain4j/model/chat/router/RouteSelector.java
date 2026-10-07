package dev.langchain4j.model.chat.router;

import static dev.langchain4j.internal.CompletableFutureUtils.propagateCancellation;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotEmpty;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static dev.langchain4j.model.chat.Capability.RESPONSE_FORMAT_JSON_SCHEMA;
import static dev.langchain4j.model.chat.router.RoutingChatModel.ROUTE_ATTRIBUTE;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.model.chat.Capability;
import dev.langchain4j.model.chat.ChatRequestOptions;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.ResponseFormatType;
import dev.langchain4j.model.chat.response.ChatResponse;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * Selects the route of a routing chat model:
 * <ul>
 *     <li>the rounds of a tool-calling loop stay on the same model: the route that produced an {@link AiMessage} is
 *     stored in its {@link AiMessage#attributes() attributes} (under {@link RoutingChatModel#ROUTE_ATTRIBUTE}), so
 *     a request that ends with tool results goes to the route that requested the tools, even after the message was
 *     stored in and loaded from a chat memory;</li>
 *     <li>the router only sees the routes whose model supports the capabilities the request needs (e.g. a JSON
 *     schema response format), if at least one route declares them; otherwise it sees all routes.</li>
 * </ul>
 */
final class RouteSelector<M> {

    record Route<M>(String name, M model, List<String> descriptions) {}

    private final Map<String, M> models;
    private final List<ChatModelRoute> routes;
    private final ChatModelRouter router;
    private final String defaultRoute;
    private final Function<M, Set<Capability>> capabilities;

    RouteSelector(
            List<Route<M>> routes,
            ChatModelRouter router,
            String defaultRoute,
            Function<M, Set<Capability>> capabilities) {
        Map<String, M> models = new LinkedHashMap<>();
        List<ChatModelRoute> chatModelRoutes = new ArrayList<>();
        for (Route<M> route : ensureNotEmpty(routes, "routes")) {
            ensureNotBlank(route.name(), "route name");
            if (models.containsKey(route.name())) {
                throw new IllegalArgumentException("There is more than one route named '%s'".formatted(route.name()));
            }
            models.put(route.name(), ensureNotNull(route.model(), "model of route '%s'".formatted(route.name())));
            chatModelRoutes.add(new ChatModelRoute(route.name(), route.descriptions()));
        }
        ensureNotBlank(defaultRoute, "defaultRoute");
        if (!models.containsKey(defaultRoute)) {
            throw new IllegalArgumentException("The default route '%s' is not one of the routes %s"
                    .formatted(defaultRoute, models.keySet()));
        }
        this.models = Collections.unmodifiableMap(models);
        this.routes = List.copyOf(chatModelRoutes);
        this.router = ensureNotNull(router, "router");
        this.defaultRoute = defaultRoute;
        this.capabilities = capabilities;
        router.validate(this.routes);
    }

    String select(ChatRequest chatRequest, ChatRequestOptions options) {
        String previousRoute = toolCallRoute(chatRequest.messages());
        if (previousRoute != null) {
            return previousRoute;
        }
        List<ChatModelRoute> candidates = candidates(chatRequest);
        return validate(router.route(routingRequest(chatRequest, candidates, options)), candidates);
    }

    CompletableFuture<String> selectAsync(ChatRequest chatRequest, ChatRequestOptions options) {
        try {
            String previousRoute = toolCallRoute(chatRequest.messages());
            if (previousRoute != null) {
                return CompletableFuture.completedFuture(previousRoute);
            }
            List<ChatModelRoute> candidates = candidates(chatRequest);
            ChatModelRoutingRequest routingRequest = routingRequest(chatRequest, candidates, options);
            CompletableFuture<ChatModelRoutingResult> routed = router.routeAsync(routingRequest);
            CompletableFuture<String> result = routed.thenApply(routingResult -> validate(routingResult, candidates));
            propagateCancellation(result, routed);
            return result;
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    M model(String routeName) {
        return models.get(routeName);
    }

    List<ChatModelRoute> routes() {
        return routes;
    }

    String defaultRoute() {
        return defaultRoute;
    }

    /**
     * Adds the selected route to the listener attributes, so that listeners of the selected model can report it.
     */
    static ChatRequestOptions withRoute(ChatRequestOptions options, String routeName) {
        Map<Object, Object> attributes =
                new LinkedHashMap<>(options == null ? Map.of() : options.listenerAttributes());
        attributes.put(ROUTE_ATTRIBUTE, routeName);
        return ChatRequestOptions.builder().listenerAttributes(attributes).build();
    }

    /**
     * Stores the route that produced the response in the attributes of its {@link AiMessage}.
     */
    static ChatResponse withRoute(ChatResponse response, String routeName) {
        if (response == null || response.aiMessage() == null) {
            return response;
        }
        AiMessage aiMessage = response.aiMessage();
        Map<String, Object> attributes = new LinkedHashMap<>(aiMessage.attributes());
        attributes.put(ROUTE_ATTRIBUTE, routeName);
        return response.toBuilder()
                .aiMessage(aiMessage.toBuilder().attributes(attributes).build())
                .build();
    }

    /**
     * The capabilities supported by at least one route: a request that needs one of them is routed to a route that
     * supports it.
     */
    Set<Capability> supportedCapabilities() {
        Set<Capability> supported = EnumSet.noneOf(Capability.class);
        models.values().forEach(model -> supported.addAll(capabilities.apply(model)));
        return Collections.unmodifiableSet(supported);
    }

    private static ChatModelRoutingRequest routingRequest(
            ChatRequest chatRequest, List<ChatModelRoute> candidates, ChatRequestOptions options) {
        return ChatModelRoutingRequest.builder()
                .chatRequest(chatRequest)
                .routes(candidates)
                .options(options)
                .build();
    }

    private List<ChatModelRoute> candidates(ChatRequest chatRequest) {
        Set<Capability> required = requiredCapabilities(chatRequest);
        if (required.isEmpty()) {
            return routes;
        }
        List<ChatModelRoute> candidates = routes.stream()
                .filter(route -> capabilities.apply(models.get(route.name())).containsAll(required))
                .toList();
        return candidates.isEmpty() ? routes : candidates;
    }

    private static Set<Capability> requiredCapabilities(ChatRequest chatRequest) {
        ResponseFormat responseFormat = chatRequest.responseFormat();
        if (responseFormat != null
                && responseFormat.type() == ResponseFormatType.JSON
                && responseFormat.jsonSchema() != null) {
            return EnumSet.of(RESPONSE_FORMAT_JSON_SCHEMA);
        }
        return Set.of();
    }

    private String validate(ChatModelRoutingResult routingResult, List<ChatModelRoute> candidates) {
        if (routingResult == null) {
            throw new IllegalStateException("The router returned null. Use ChatModelRoutingResult.defaultRoute() to "
                    + "use the default route");
        }
        String routeName = routingResult.routeName();
        if (routingResult.isDefaultRoute()) {
            return isCandidate(defaultRoute, candidates)
                    ? defaultRoute
                    : candidates.get(0).name();
        }
        if (!models.containsKey(routeName)) {
            throw new IllegalStateException("The router selected the unknown route '%s'. Available routes: %s"
                    .formatted(routeName, models.keySet()));
        }
        if (!isCandidate(routeName, candidates)) {
            throw new IllegalStateException(
                    "Route '%s' does not support the capabilities the request needs. Routes that support them: %s"
                            .formatted(routeName, candidates.stream().map(ChatModelRoute::name).toList()));
        }
        return routeName;
    }

    private static boolean isCandidate(String routeName, List<ChatModelRoute> candidates) {
        return candidates.stream().anyMatch(route -> route.name().equals(routeName));
    }

    private String toolCallRoute(List<ChatMessage> messages) {
        if (messages.isEmpty() || !(messages.get(messages.size() - 1) instanceof ToolExecutionResultMessage)) {
            return null;
        }
        for (int i = messages.size() - 1; i >= 0; i--) {
            if (messages.get(i) instanceof AiMessage aiMessage) {
                return aiMessage.attributes().get(ROUTE_ATTRIBUTE) instanceof String routeName && models.containsKey(routeName)
                        ? routeName
                        : null;
            }
        }
        return null;
    }
}
