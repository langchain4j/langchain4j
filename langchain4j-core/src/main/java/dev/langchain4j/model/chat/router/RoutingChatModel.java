package dev.langchain4j.model.chat.router;

import static dev.langchain4j.internal.CompletableFutureUtils.propagateCancellation;
import static dev.langchain4j.internal.Exceptions.unwrapCompletionException;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.Experimental;
import dev.langchain4j.model.chat.Capability;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.ChatRequestOptions;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * A {@link ChatModel} that sends each request to one of several chat models, as decided by a
 * {@link ChatModelRouter}. It can be used wherever a {@link ChatModel} is expected (AI Services, agents, RAG), for
 * example to send simple requests to a small, cheap model and complex ones to a larger model:
 * <pre>{@code
 * ChatModel chatModel = RoutingChatModel.builder()
 *         .route("simple", smallModel, "Greetings, short factual questions, simple lookups")
 *         .route("complex", largeModel, "Multi-step reasoning, code, analysis")
 *         .router(new DecisionModelChatModelRouter(decisionModel))
 *         .defaultRoute("complex")
 *         .build();
 * }</pre>
 * The selected model handles the request as if it was called directly: its default parameters and listeners apply.
 * Since the request can go to models of different providers, set only common parameters on requests
 * ({@link dev.langchain4j.model.chat.request.ChatRequestParameters}), not provider-specific ones.
 * <p>
 * The rounds of a tool-calling loop stay on the same model: a request that ends with tool results goes to the model
 * that requested the tools, without asking the router.
 * <p>
 * {@link #supportedCapabilities()} returns the capabilities supported by at least one route. A request that needs a
 * capability (such as a JSON schema response format) is only routed to the routes that support it: the router only
 * sees those routes.
 * <p>
 * The asynchronous method ({@link #chatAsync(ChatRequest)}) selects the route with
 * {@link ChatModelRouter#routeAsync(ChatModelRoutingRequest)}, so it does not block when the router does not.
 *
 * @see RoutingStreamingChatModel
 * @since 1.21.0
 */
@Experimental
public class RoutingChatModel implements ChatModel {

    private final RouteSelector<ChatModel> selector;

    protected RoutingChatModel(Builder builder) {
        this.selector = new RouteSelector<>(
                builder.models,
                builder.routes,
                builder.router,
                builder.defaultRoute,
                ChatModel::supportedCapabilities);
    }

    @Override
    public ChatResponse chat(ChatRequest chatRequest, ChatRequestOptions options) {
        ensureNotNull(chatRequest, "chatRequest");
        String routeName = selector.select(chatRequest);
        ChatResponse response = selector.model(routeName).chat(chatRequest, options);
        selector.onResponse(routeName, response);
        return response;
    }

    @Override
    public ChatResponse doChat(ChatRequest chatRequest) {
        return chat(chatRequest, ChatRequestOptions.EMPTY);
    }

    @Override
    public CompletableFuture<ChatResponse> chatAsync(ChatRequest chatRequest, ChatRequestOptions options) {
        ensureNotNull(chatRequest, "chatRequest");
        CompletableFuture<String> route = selector.selectAsync(chatRequest);
        CompletableFuture<ChatResponse> result = new CompletableFuture<>();
        propagateCancellation(result, route);
        route.whenComplete((routeName, routingError) -> {
            if (routingError != null) {
                result.completeExceptionally(unwrapCompletionException(routingError));
                return;
            }
            CompletableFuture<ChatResponse> response;
            try {
                response = selector.model(routeName).chatAsync(chatRequest, options);
            } catch (Exception e) {
                result.completeExceptionally(e);
                return;
            }
            propagateCancellation(result, response);
            response.whenComplete((chatResponse, error) -> {
                if (error != null) {
                    result.completeExceptionally(unwrapCompletionException(error));
                } else {
                    selector.onResponse(routeName, chatResponse);
                    result.complete(chatResponse);
                }
            });
        });
        return result;
    }

    @Override
    public CompletableFuture<ChatResponse> doChatAsync(ChatRequest chatRequest) {
        return chatAsync(chatRequest, ChatRequestOptions.EMPTY);
    }

    @Override
    public Set<Capability> supportedCapabilities() {
        return selector.supportedCapabilities();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private final Map<String, ChatModel> models = new LinkedHashMap<>();
        private final List<ChatModelRoute> routes = new ArrayList<>();
        private ChatModelRouter router;
        private String defaultRoute;

        /**
         * Adds a route without a description.
         */
        public Builder route(String name, ChatModel model) {
            return route(name, model, null);
        }

        /**
         * Adds a route.
         *
         * @param name        the unique name of the route.
         * @param model       the chat model that handles the requests sent to this route.
         * @param description what kind of requests this route is meant for. Required by routers that decide based on
         *                    the content of the request, such as {@link DecisionModelChatModelRouter}.
         */
        public Builder route(String name, ChatModel model, String description) {
            ensureNotBlank(name, "name");
            if (models.containsKey(name)) {
                throw new IllegalArgumentException("There is already a route named '%s'".formatted(name));
            }
            models.put(name, ensureNotNull(model, "model"));
            routes.add(new ChatModelRoute(name, description));
            return this;
        }

        /**
         * Sets the router that selects the route of each request. Required.
         */
        public Builder router(ChatModelRouter router) {
            this.router = router;
            return this;
        }

        /**
         * Sets the route used when the router does not select one (returns {@code null}). Optional: without a default
         * route, such requests fail.
         */
        public Builder defaultRoute(String defaultRoute) {
            this.defaultRoute = defaultRoute;
            return this;
        }

        public RoutingChatModel build() {
            return new RoutingChatModel(this);
        }
    }
}
