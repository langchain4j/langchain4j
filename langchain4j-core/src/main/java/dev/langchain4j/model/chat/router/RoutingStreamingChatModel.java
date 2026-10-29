package dev.langchain4j.model.chat.router;

import static dev.langchain4j.internal.Exceptions.unwrapCompletionException;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.Experimental;
import dev.langchain4j.model.chat.Capability;
import dev.langchain4j.model.chat.ChatRequestOptions;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatModelStreamingEvent;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.CompleteResponse;
import dev.langchain4j.model.chat.response.CompleteToolCall;
import dev.langchain4j.model.chat.response.PartialResponse;
import dev.langchain4j.model.chat.response.PartialResponseContext;
import dev.langchain4j.model.chat.response.PartialThinking;
import dev.langchain4j.model.chat.response.PartialThinkingContext;
import dev.langchain4j.model.chat.response.PartialToolCall;
import dev.langchain4j.model.chat.response.PartialToolCallContext;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow.Publisher;
import java.util.concurrent.Flow.Subscriber;
import java.util.concurrent.Flow.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The streaming counterpart of {@link RoutingChatModel}: a {@link StreamingChatModel} that sends each request to one
 * of several streaming chat models, as decided by a {@link ChatModelRouter}.
 * <pre>{@code
 * StreamingChatModel streamingChatModel = RoutingStreamingChatModel.builder()
 *         .route("simple", "Greetings, short factual questions, simple lookups", smallStreamingModel)
 *         .route("complex", "Multi-step reasoning, code, analysis", largeStreamingModel)
 *         .router(new DecisionModelChatModelRouter(decisionModel))
 *         .defaultRoute("complex")
 *         .build();
 * }</pre>
 * See {@link RoutingChatModel} for how requests are routed and where the selected route is recorded (with
 * {@link #chat(ChatRequest)}, which takes no options, only in the attributes of the response).
 * <p>
 * With a {@link StreamingChatResponseHandler}, the route is selected on the calling thread with
 * {@link ChatModelRouter#route(ChatModelRoutingRequest)}, before streaming starts. This blocks the calling thread
 * until the route is selected (with {@link DecisionModelChatModelRouter}, until the decision model answers), so do
 * not call it from an event loop thread. The non-blocking
 * {@link #chat(ChatRequest)} returning a {@link Publisher} selects it with
 * {@link ChatModelRouter#routeAsync(ChatModelRoutingRequest)} instead: a router that does not implement it, such as a
 * router written as a lambda, fails the stream with an {@link dev.langchain4j.exception.AsyncNotSupportedException}.
 *
 * @since 1.21.0
 */
@Experimental
public class RoutingStreamingChatModel implements StreamingChatModel {

    private static final Logger log = LoggerFactory.getLogger(RoutingStreamingChatModel.class);

    private final RouteSelector<StreamingChatModel> selector;

    protected RoutingStreamingChatModel(Builder builder) {
        this.selector = new RouteSelector<>(
                builder.routes,
                builder.router,
                builder.defaultRoute,
                StreamingChatModel::supportedCapabilities);
    }

    @Override
    public void chat(ChatRequest request, ChatRequestOptions options, StreamingChatResponseHandler handler) {
        ensureNotNull(request, "request");
        ensureNotNull(handler, "handler");
        String routeName;
        try {
            routeName = selector.select(request, options);
        } catch (Exception e) {
            onError(handler, e);
            return;
        }
        try {
            selector.model(routeName)
                    .chat(request, RouteSelector.withRoute(options, routeName), new RouteRecordingHandler(handler, routeName));
        } catch (Exception e) {
            onError(handler, e);
        }
    }

    private static void onError(StreamingChatResponseHandler handler, Throwable error) {
        try {
            handler.onError(error);
        } catch (Exception e) {
            log.warn("Exception while calling onError() of the streaming response handler", e);
        }
    }

    @Override
    public void doChat(ChatRequest chatRequest, StreamingChatResponseHandler handler) {
        chat(chatRequest, ChatRequestOptions.EMPTY, handler);
    }

    @Override
    public Publisher<ChatModelStreamingEvent> chat(ChatRequest request) {
        ensureNotNull(request, "request");
        return downstream -> {
            DeferredSubscription subscription = new DeferredSubscription(downstream);
            downstream.onSubscribe(subscription);
            CompletableFuture<String> route = selector.selectAsync(request, ChatRequestOptions.EMPTY);
            subscription.setRouting(route);
            route.whenComplete((routeName, routingError) -> {
                if (subscription.isCancelled()) {
                    return;
                }
                if (routingError != null) {
                    downstream.onError(unwrapCompletionException(routingError));
                    return;
                }
                try {
                    selector.model(routeName).chat(request).subscribe(new RouteRecordingSubscriber(downstream, subscription, routeName));
                } catch (Throwable error) {
                    downstream.onError(error);
                }
            });
        };
    }

    private static final class RouteRecordingSubscriber implements Subscriber<ChatModelStreamingEvent> {

        private final Subscriber<? super ChatModelStreamingEvent> downstream;
        private final DeferredSubscription subscription;
        private final String routeName;

        private RouteRecordingSubscriber(
                Subscriber<? super ChatModelStreamingEvent> downstream,
                DeferredSubscription subscription,
                String routeName) {
            this.downstream = downstream;
            this.subscription = subscription;
            this.routeName = routeName;
        }

        @Override
        public void onSubscribe(Subscription upstream) {
            subscription.setUpstream(upstream);
        }

        @Override
        public void onNext(ChatModelStreamingEvent event) {
            if (event instanceof CompleteResponse completeResponse) {
                downstream.onNext(new CompleteResponse(RouteSelector.withRoute(completeResponse.chatResponse(), routeName)));
            } else {
                downstream.onNext(event);
            }
        }

        @Override
        public void onError(Throwable error) {
            downstream.onError(error);
        }

        @Override
        public void onComplete() {
            downstream.onComplete();
        }
    }

    @Override
    public Publisher<ChatModelStreamingEvent> doChat(ChatRequest chatRequest) {
        return chat(chatRequest);
    }

    @Override
    public Set<Capability> supportedCapabilities() {
        return selector.supportedCapabilities();
    }

    /**
     * The subscription given to the subscriber before the route is selected: demand and cancellation are passed on
     * to the subscription of the selected model once it is available. Calls to the upstream subscription are
     * serialized.
     */
    private static final class DeferredSubscription implements Subscription {

        private final Subscriber<? super ChatModelStreamingEvent> downstream;
        private volatile Subscription upstream;
        private CompletableFuture<?> routing;
        private long pendingDemand;
        private boolean cancelled;

        private DeferredSubscription(Subscriber<? super ChatModelStreamingEvent> downstream) {
            this.downstream = downstream;
        }

        @Override
        public void request(long n) {
            Subscription current = upstream;
            if (current != null) {
                current.request(n); // the upstream validates n and serializes its own signals
                return;
            }
            boolean invalid = false;
            synchronized (this) {
                if (cancelled) {
                    return;
                }
                current = upstream;
                if (current == null) {
                    if (n <= 0) {
                        cancelled = true;
                        invalid = true;
                    } else {
                        pendingDemand = pendingDemand + n < 0 ? Long.MAX_VALUE : pendingDemand + n;
                    }
                }
            }
            if (current != null) {
                current.request(n);
            } else if (invalid) {
                downstream.onError(new IllegalArgumentException(
                        "The number of requested elements must be positive, but was " + n));
            }
        }

        @Override
        public void cancel() {
            Subscription current;
            CompletableFuture<?> currentRouting;
            synchronized (this) {
                cancelled = true;
                current = upstream;
                currentRouting = routing;
            }
            if (currentRouting != null) {
                currentRouting.cancel(true);
            }
            if (current != null) {
                current.cancel();
            }
        }

        void setRouting(CompletableFuture<?> routing) {
            boolean cancel;
            synchronized (this) {
                this.routing = routing;
                cancel = cancelled;
            }
            if (cancel) {
                routing.cancel(true);
            }
        }

        synchronized boolean isCancelled() {
            return cancelled;
        }

        void setUpstream(Subscription upstream) {
            long demand;
            boolean cancel;
            synchronized (this) {
                demand = pendingDemand;
                pendingDemand = 0;
                cancel = cancelled;
                this.upstream = upstream;
            }
            if (cancel) {
                upstream.cancel();
            } else if (demand > 0) {
                upstream.request(demand);
            }
        }
    }

    /**
     * The routes, in the order in which they were configured.
     */
    public List<ChatModelRoute> routes() {
        return selector.routes();
    }

    /**
     * The name of the default route, used when the router does not select one.
     */
    public String defaultRoute() {
        return selector.defaultRoute();
    }

    public static Builder builder() {
        return new Builder();
    }

    private static final class RouteRecordingHandler implements StreamingChatResponseHandler {

        private final StreamingChatResponseHandler delegate;
        private final String routeName;

        private RouteRecordingHandler(StreamingChatResponseHandler delegate, String routeName) {
            this.delegate = delegate;
            this.routeName = routeName;
        }

        @Override
        public void onPartialResponse(String partialResponse) {
            delegate.onPartialResponse(partialResponse);
        }

        @Override
        public void onPartialResponse(PartialResponse partialResponse, PartialResponseContext context) {
            delegate.onPartialResponse(partialResponse, context);
        }

        @Override
        public void onPartialThinking(PartialThinking partialThinking) {
            delegate.onPartialThinking(partialThinking);
        }

        @Override
        public void onPartialThinking(PartialThinking partialThinking, PartialThinkingContext context) {
            delegate.onPartialThinking(partialThinking, context);
        }

        @Override
        public void onPartialToolCall(PartialToolCall partialToolCall) {
            delegate.onPartialToolCall(partialToolCall);
        }

        @Override
        public void onPartialToolCall(PartialToolCall partialToolCall, PartialToolCallContext context) {
            delegate.onPartialToolCall(partialToolCall, context);
        }

        @Override
        public void onCompleteToolCall(CompleteToolCall completeToolCall) {
            delegate.onCompleteToolCall(completeToolCall);
        }

        @Override
        public void onUnmappedRawEvent(Object rawEvent) {
            delegate.onUnmappedRawEvent(rawEvent);
        }

        @Override
        public void onCompleteResponse(ChatResponse completeResponse) {
            delegate.onCompleteResponse(RouteSelector.withRoute(completeResponse, routeName));
        }

        @Override
        public void onError(Throwable error) {
            delegate.onError(error);
        }
    }

    public static class Builder {

        private final List<RouteSelector.Route<StreamingChatModel>> routes = new ArrayList<>();
        private ChatModelRouter router;
        private String defaultRoute;

        /**
         * Adds a route without a description, for routers that do not decide based on descriptions.
         *
         * @param name  the unique name of the route.
         * @param model the streaming chat model that handles the requests sent to this route.
         */
        public Builder route(String name, StreamingChatModel model) {
            return route(name, List.of(), model);
        }

        /**
         * Adds a route. At least one route is required.
         *
         * @param name        the unique name of the route. It is stored with the responses (see
         *                    {@link RoutingChatModel#ROUTE_ATTRIBUTE}), so keep it stable.
         * @param description what kind of requests this route is meant for, used by routers that decide based on the
         *                    content of the request, such as {@link DecisionModelChatModelRouter}. Optional: without a
         *                    description, the route is described by its name.
         * @param model       the streaming chat model that handles the requests sent to this route.
         */
        public Builder route(String name, String description, StreamingChatModel model) {
            return route(name, description == null ? List.of() : List.of(description), model);
        }

        /**
         * Adds a route with several descriptions, for a route meant for requests about different topics, for example
         * {@code route("complex", List.of("Writing or debugging code", "Legal contract analysis"), largeModel)}.
         * Routers that decide based on the descriptions, such as {@link DecisionModelChatModelRouter}, consider each
         * description separately, which can make them more confident than one description that mixes all topics.
         * When the decision model is unsure, a route with more descriptions gets more of the probability, so keep the
         * number of descriptions of the routes balanced.
         *
         * @param name         the unique name of the route, see {@link #route(String, String, StreamingChatModel)}.
         * @param descriptions the kinds of requests this route is meant for, for example one per topic.
         * @param model        the streaming chat model that handles the requests sent to this route.
         */
        public Builder route(String name, List<String> descriptions, StreamingChatModel model) {
            routes.add(new RouteSelector.Route<>(name, model, descriptions));
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
         * Sets the route used when the router selects the default route
         * ({@link ChatModelRoutingResult#defaultRoute()}), for example when a router
         * that calls a model is unsure or fails. Required.
         */
        public Builder defaultRoute(String defaultRoute) {
            this.defaultRoute = defaultRoute;
            return this;
        }

        public RoutingStreamingChatModel build() {
            return new RoutingStreamingChatModel(this);
        }
    }
}
