package dev.langchain4j.model.chat.router;

import static dev.langchain4j.internal.InternalFlowUtils.EMPTY_SUBSCRIPTION;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Flow.Publisher;
import java.util.concurrent.Flow.Subscriber;
import java.util.concurrent.Flow.Subscription;

/**
 * The streaming counterpart of {@link RoutingChatModel}: a {@link StreamingChatModel} that sends each request to one
 * of several streaming chat models, as decided by a {@link ChatModelRouter}.
 * <pre>{@code
 * StreamingChatModel streamingChatModel = RoutingStreamingChatModel.builder()
 *         .route("simple", smallStreamingModel, "Greetings, short factual questions, simple lookups")
 *         .route("complex", largeStreamingModel, "Multi-step reasoning, code, analysis")
 *         .router(new DecisionModelChatModelRouter(decisionModel))
 *         .defaultRoute("complex")
 *         .build();
 * }</pre>
 * See {@link RoutingChatModel} for how requests are routed.
 *
 * @since 1.21.0
 */
@Experimental
public class RoutingStreamingChatModel implements StreamingChatModel {

    private final RouteSelector<StreamingChatModel> selector;

    protected RoutingStreamingChatModel(Builder builder) {
        this.selector = new RouteSelector<>(builder.models, builder.routes, builder.router, builder.defaultRoute);
    }

    @Override
    public void chat(ChatRequest request, ChatRequestOptions options, StreamingChatResponseHandler handler) {
        ensureNotNull(request, "request");
        ensureNotNull(handler, "handler");
        String routeName;
        try {
            routeName = selector.select(request);
        } catch (Exception e) {
            handler.onError(e);
            return;
        }
        selector.model(routeName).chat(request, options, new RouteRecordingHandler(handler, routeName));
    }

    @Override
    public void doChat(ChatRequest chatRequest, StreamingChatResponseHandler handler) {
        chat(chatRequest, ChatRequestOptions.EMPTY, handler);
    }

    @Override
    public Publisher<ChatModelStreamingEvent> chat(ChatRequest request) {
        ensureNotNull(request, "request");
        return downstream -> {
            String routeName;
            Publisher<ChatModelStreamingEvent> publisher;
            try {
                routeName = selector.select(request);
                publisher = selector.model(routeName).chat(request);
            } catch (Throwable error) {
                downstream.onSubscribe(EMPTY_SUBSCRIPTION);
                downstream.onError(error);
                return;
            }
            publisher.subscribe(new Subscriber<>() {

                @Override
                public void onSubscribe(Subscription subscription) {
                    downstream.onSubscribe(subscription);
                }

                @Override
                public void onNext(ChatModelStreamingEvent event) {
                    if (event instanceof CompleteResponse completeResponse) {
                        selector.onResponse(routeName, completeResponse.chatResponse());
                    }
                    downstream.onNext(event);
                }

                @Override
                public void onError(Throwable error) {
                    downstream.onError(error);
                }

                @Override
                public void onComplete() {
                    downstream.onComplete();
                }
            });
        };
    }

    @Override
    public Publisher<ChatModelStreamingEvent> doChat(ChatRequest chatRequest) {
        return chat(chatRequest);
    }

    @Override
    public Set<Capability> supportedCapabilities() {
        return selector.supportedCapabilities(StreamingChatModel::supportedCapabilities);
    }

    public static Builder builder() {
        return new Builder();
    }

    private class RouteRecordingHandler implements StreamingChatResponseHandler {

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
            selector.onResponse(routeName, completeResponse);
            delegate.onCompleteResponse(completeResponse);
        }

        @Override
        public void onError(Throwable error) {
            delegate.onError(error);
        }
    }

    public static class Builder {

        private final Map<String, StreamingChatModel> models = new LinkedHashMap<>();
        private final List<ChatModelRoute> routes = new ArrayList<>();
        private ChatModelRouter router;
        private String defaultRoute;

        /**
         * Adds a route without a description.
         */
        public Builder route(String name, StreamingChatModel model) {
            return route(name, model, null);
        }

        /**
         * Adds a route.
         *
         * @param name        the unique name of the route.
         * @param model       the streaming chat model that handles the requests sent to this route.
         * @param description what kind of requests this route is meant for. Required by routers that decide based on
         *                    the content of the request, such as {@link DecisionModelChatModelRouter}.
         */
        public Builder route(String name, StreamingChatModel model, String description) {
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

        public RoutingStreamingChatModel build() {
            return new RoutingStreamingChatModel(this);
        }
    }
}
