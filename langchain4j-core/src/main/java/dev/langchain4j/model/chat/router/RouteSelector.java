package dev.langchain4j.model.chat.router;

import static dev.langchain4j.internal.ValidationUtils.ensureNotEmpty;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static dev.langchain4j.model.chat.Capability.RESPONSE_FORMAT_JSON_SCHEMA;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.model.chat.Capability;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.ResponseFormatType;
import dev.langchain4j.model.chat.response.ChatResponse;
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
 *     <li>the rounds of a tool-calling loop stay on the same model: when a request ends with tool results, it is
 *     routed to the model that requested the tools, without asking the router;</li>
 *     <li>the router only sees the routes whose model supports the capabilities the request needs (e.g. a JSON
 *     schema response format).</li>
 * </ul>
 */
final class RouteSelector<M> {

    private static final int MAX_REMEMBERED_TOOL_CALLS = 1000;

    private final Map<String, M> models;
    private final List<ChatModelRoute> routes;
    private final ChatModelRouter router;
    private final String defaultRoute;
    private final Function<M, Set<Capability>> capabilities;
    private final Map<AiMessage, String> toolCallRoutes = Collections.synchronizedMap(new LinkedHashMap<>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<AiMessage, String> eldest) {
            return size() > MAX_REMEMBERED_TOOL_CALLS;
        }
    });

    RouteSelector(
            Map<String, M> models,
            List<ChatModelRoute> routes,
            ChatModelRouter router,
            String defaultRoute,
            Function<M, Set<Capability>> capabilities) {
        this.models = Map.copyOf(ensureNotEmpty(models, "routes"));
        this.routes = List.copyOf(routes);
        this.router = ensureNotNull(router, "router");
        if (defaultRoute != null && !models.containsKey(defaultRoute)) {
            throw new IllegalArgumentException("The default route '%s' is not one of the routes %s"
                    .formatted(defaultRoute, models.keySet()));
        }
        this.defaultRoute = defaultRoute;
        this.capabilities = capabilities;
    }

    String select(ChatRequest chatRequest) {
        String toolCallRoute = toolCallRoute(chatRequest.messages());
        if (toolCallRoute != null) {
            return toolCallRoute;
        }
        List<ChatModelRoute> candidates = candidates(chatRequest);
        return validate(router.route(new ChatModelRoutingRequest(chatRequest, candidates)), candidates);
    }

    CompletableFuture<String> selectAsync(ChatRequest chatRequest) {
        try {
            String toolCallRoute = toolCallRoute(chatRequest.messages());
            if (toolCallRoute != null) {
                return CompletableFuture.completedFuture(toolCallRoute);
            }
            List<ChatModelRoute> candidates = candidates(chatRequest);
            return router.routeAsync(new ChatModelRoutingRequest(chatRequest, candidates))
                    .thenApply(routeName -> validate(routeName, candidates));
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    M model(String routeName) {
        return models.get(routeName);
    }

    void onResponse(String routeName, ChatResponse response) {
        if (response != null && response.aiMessage() != null && response.aiMessage().hasToolExecutionRequests()) {
            toolCallRoutes.put(response.aiMessage(), routeName);
        }
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

    private List<ChatModelRoute> candidates(ChatRequest chatRequest) {
        Set<Capability> required = requiredCapabilities(chatRequest);
        if (required.isEmpty()) {
            return routes;
        }
        List<ChatModelRoute> candidates = routes.stream()
                .filter(route -> capabilities.apply(models.get(route.name())).containsAll(required))
                .toList();
        if (candidates.isEmpty()) {
            throw new IllegalStateException("None of the routes %s supports %s, which the request needs"
                    .formatted(models.keySet(), required));
        }
        return candidates;
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

    private String validate(String routeName, List<ChatModelRoute> candidates) {
        if (routeName == null) {
            routeName = defaultRoute;
            if (routeName == null) {
                throw new IllegalStateException(
                        "The router did not select a route and no default route is configured");
            }
        }
        if (!models.containsKey(routeName)) {
            throw new IllegalStateException("The router selected the unknown route '%s'. Available routes: %s"
                    .formatted(routeName, models.keySet()));
        }
        String selected = routeName;
        if (candidates.stream().noneMatch(route -> route.name().equals(selected))) {
            throw new IllegalStateException(
                    "Route '%s' does not support the capabilities the request needs. Routes that support them: %s"
                            .formatted(selected, candidates.stream().map(ChatModelRoute::name).toList()));
        }
        return routeName;
    }

    private String toolCallRoute(List<ChatMessage> messages) {
        if (messages.isEmpty() || !(messages.get(messages.size() - 1) instanceof ToolExecutionResultMessage)) {
            return null;
        }
        for (int i = messages.size() - 1; i >= 0; i--) {
            if (messages.get(i) instanceof AiMessage aiMessage) {
                return toolCallRoutes.get(aiMessage);
            }
        }
        return null;
    }
}
