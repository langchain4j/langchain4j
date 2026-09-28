package dev.langchain4j.model.chat.router;

import static dev.langchain4j.internal.ValidationUtils.ensureNotEmpty;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.model.chat.Capability;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Selects the model of a routing chat model, keeping the rounds of a tool-calling loop on the same model: when a
 * request ends with tool results, it is routed to the model that requested the tools.
 */
final class RouteSelector<M> {

    private static final int MAX_REMEMBERED_TOOL_CALLS = 1000;

    private final Map<String, M> models;
    private final List<ChatModelRoute> routes;
    private final ChatModelRouter router;
    private final String defaultRoute;
    private final Map<AiMessage, String> toolCallRoutes = Collections.synchronizedMap(new LinkedHashMap<>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<AiMessage, String> eldest) {
            return size() > MAX_REMEMBERED_TOOL_CALLS;
        }
    });

    RouteSelector(Map<String, M> models, List<ChatModelRoute> routes, ChatModelRouter router, String defaultRoute) {
        this.models = Map.copyOf(ensureNotEmpty(models, "routes"));
        this.routes = List.copyOf(routes);
        this.router = ensureNotNull(router, "router");
        if (defaultRoute != null && !models.containsKey(defaultRoute)) {
            throw new IllegalArgumentException("The default route '%s' is not one of the routes %s"
                    .formatted(defaultRoute, models.keySet()));
        }
        this.defaultRoute = defaultRoute;
    }

    String select(ChatRequest chatRequest) {
        String routeName = toolCallRoute(chatRequest.messages());
        if (routeName == null) {
            routeName = router.route(new ChatModelRoutingRequest(chatRequest, routes));
        }
        if (routeName == null) {
            routeName = defaultRoute;
        }
        if (routeName == null) {
            throw new IllegalStateException("The router did not select a route and no default route is configured");
        }
        if (!models.containsKey(routeName)) {
            throw new IllegalStateException(
                    "The router selected the unknown route '%s'. Available routes: %s"
                            .formatted(routeName, models.keySet()));
        }
        return routeName;
    }

    M model(String routeName) {
        return models.get(routeName);
    }

    void onResponse(String routeName, ChatResponse response) {
        if (response != null && response.aiMessage() != null && response.aiMessage().hasToolExecutionRequests()) {
            toolCallRoutes.put(response.aiMessage(), routeName);
        }
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

    Set<Capability> supportedCapabilities(Function<M, Set<Capability>> capabilities) {
        Set<Capability> common = null;
        for (M model : models.values()) {
            Set<Capability> supported = capabilities.apply(model);
            if (common == null) {
                common = supported.isEmpty() ? EnumSet.noneOf(Capability.class) : EnumSet.copyOf(supported);
            } else {
                common.retainAll(supported);
            }
        }
        return common == null ? Set.of() : Collections.unmodifiableSet(common);
    }
}
