package dev.langchain4j.model.chat.router;

import static dev.langchain4j.model.chat.Capability.RESPONSE_FORMAT_JSON_SCHEMA;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageDeserializer;
import dev.langchain4j.data.message.ChatMessageSerializer;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.Capability;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.ChatRequestOptions;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.ResponseFormatType;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchema;
import dev.langchain4j.model.chat.response.ChatModelStreamingEvent;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.CompleteResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.decision.mock.DecisionModelMock;
import dev.langchain4j.model.decision.response.ChoiceAnswer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Flow;
import org.junit.jupiter.api.Test;

class RoutingChatModelTest {

    static class FakeChatModel implements ChatModel {

        final String name;
        final List<ChatRequest> requests = new CopyOnWriteArrayList<>();
        final List<ChatModelListener> listeners = new ArrayList<>();
        final Set<Capability> capabilities;
        AiMessage reply;

        FakeChatModel(String name, Set<Capability> capabilities) {
            this.name = name;
            this.capabilities = capabilities;
            this.reply = AiMessage.from("answer from " + name);
        }

        @Override
        public ChatResponse doChat(ChatRequest chatRequest) {
            requests.add(chatRequest);
            return ChatResponse.builder().aiMessage(reply).build();
        }

        @Override
        public CompletableFuture<ChatResponse> doChatAsync(ChatRequest chatRequest) {
            return CompletableFuture.completedFuture(doChat(chatRequest));
        }

        @Override
        public List<ChatModelListener> listeners() {
            return listeners;
        }

        @Override
        public Set<Capability> supportedCapabilities() {
            return capabilities;
        }
    }

    final FakeChatModel small = new FakeChatModel("small", Set.of(RESPONSE_FORMAT_JSON_SCHEMA));
    final FakeChatModel large = new FakeChatModel("large", Set.of());

    static ChatModelRouter byLength() {
        return request -> {
            ChatMessage last = request.chatRequest().messages().get(request.chatRequest().messages().size() - 1);
            return last instanceof UserMessage userMessage && userMessage.singleText().length() > 20
                    ? "complex"
                    : "simple";
        };
    }

    RoutingChatModel routingModel(ChatModelRouter router) {
        return RoutingChatModel.builder()
                .route("simple", "Short questions", small)
                .route("complex", "Everything else", large)
                .router(router)
                .defaultRoute("complex")
                .build();
    }

    @Test
    void should_route_requests() {

        ChatModel chatModel = routingModel(byLength());

        assertThat(chatModel.chat("Hi!")).isEqualTo("answer from small");
        assertThat(chatModel.chat("Please analyze the attached quarterly report")).isEqualTo("answer from large");
        assertThat(small.requests).hasSize(1);
        assertThat(large.requests).hasSize(1);
    }

    @Test
    void should_pass_routes_to_router() {

        List<ChatModelRoute> seen = new ArrayList<>();
        ChatModel chatModel = routingModel(request -> {
            seen.addAll(request.routes());
            return "simple";
        });

        chatModel.chat("Hi!");

        assertThat(seen)
                .containsExactly(
                        new ChatModelRoute("simple", "Short questions"),
                        new ChatModelRoute("complex", "Everything else"));
    }

    @Test
    void should_use_default_route_when_router_does_not_select_one() {

        assertThat(routingModel(request -> null).chat("Hi!")).isEqualTo("answer from large");
    }

    @Test
    void should_fail_when_router_selects_unknown_route() {

        assertThatThrownBy(() -> routingModel(request -> "medium").chat("Hi!"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("unknown route 'medium'");
    }

    @Test
    void should_invoke_listeners_of_selected_model() {

        List<ChatRequest> observed = new ArrayList<>();
        small.listeners.add(new ChatModelListener() {
            @Override
            public void onRequest(ChatModelRequestContext requestContext) {
                observed.add(requestContext.chatRequest());
            }
        });

        routingModel(byLength()).chat("Hi!");

        assertThat(observed).hasSize(1);
    }

    @Test
    void should_keep_tool_calling_loop_on_the_same_model() {

        ToolExecutionRequest toolCall = ToolExecutionRequest.builder()
                .id("1")
                .name("weather")
                .arguments("{}")
                .build();
        small.reply = AiMessage.from(toolCall);
        List<String> routed = new ArrayList<>();
        ChatModel chatModel = routingModel(request -> {
            routed.add("asked");
            return routed.size() == 1 ? "simple" : "complex";
        });

        UserMessage userMessage = UserMessage.from("Weather?");
        AiMessage toolCallMessage =
                chatModel.chat(ChatRequest.builder().messages(userMessage).build()).aiMessage();
        // the route is stored in the message, so it survives a round trip through a persistent chat memory
        assertThat(toolCallMessage.attributes()).containsEntry(RoutingChatModel.ROUTE_ATTRIBUTE, "simple");
        toolCallMessage = (AiMessage) ChatMessageDeserializer.messageFromJson(
                ChatMessageSerializer.messageToJson(toolCallMessage));
        small.reply = AiMessage.from("It is sunny");
        ChatResponse response = chatModel.chat(ChatRequest.builder()
                .messages(userMessage, toolCallMessage, ToolExecutionResultMessage.from(toolCall, "sunny"))
                .build());

        assertThat(response.aiMessage().text()).isEqualTo("It is sunny");
        assertThat(routed).hasSize(1);
        assertThat(small.requests).hasSize(2);
        assertThat(large.requests).isEmpty();
    }

    @Test
    void should_record_route_in_listener_attributes_and_response() {

        List<Object> routes = new ArrayList<>();
        small.listeners.add(new ChatModelListener() {
            @Override
            public void onRequest(ChatModelRequestContext requestContext) {
                routes.add(requestContext.attributes().get(RoutingChatModel.ROUTE_ATTRIBUTE));
                routes.add(requestContext.attributes().get("tenant"));
            }
        });

        ChatResponse response = routingModel(byLength())
                .chat(
                        ChatRequest.builder().messages(UserMessage.from("Hi!")).build(),
                        ChatRequestOptions.builder()
                                .addListenerAttribute("tenant", "acme")
                                .build());

        assertThat(routes).containsExactly("simple", "acme");
        assertThat(response.aiMessage().attributes()).containsEntry(RoutingChatModel.ROUTE_ATTRIBUTE, "simple");
    }

    @Test
    void should_pass_options_to_router() {

        List<ChatRequestOptions> options = new ArrayList<>();
        ChatModel chatModel = routingModel(request -> {
            options.add(request.options());
            return "simple";
        });

        chatModel.chat(
                ChatRequest.builder().messages(UserMessage.from("Hi!")).build(),
                ChatRequestOptions.builder().addListenerAttribute("tenant", "acme").build());

        assertThat(options).singleElement().satisfies(option -> assertThat(option.listenerAttributes())
                .containsEntry("tenant", "acme"));
    }

    @Test
    void should_ask_router_again_when_tool_calling_message_has_no_route() {

        ToolExecutionRequest toolCall = ToolExecutionRequest.builder()
                .id("1")
                .name("weather")
                .arguments("{}")
                .build();
        List<String> routed = new ArrayList<>();
        ChatModel chatModel = routingModel(request -> {
            routed.add("asked");
            return "complex";
        });

        chatModel.chat(ChatRequest.builder()
                .messages(
                        UserMessage.from("Weather?"),
                        AiMessage.from(toolCall),
                        ToolExecutionResultMessage.from(toolCall, "sunny"))
                .build());

        assertThat(routed).hasSize(1);
        assertThat(large.requests).hasSize(1);
    }

    static ToolExecutionRequest toolCall() {
        return ToolExecutionRequest.builder()
                .id("1")
                .name("weather")
                .arguments("{}")
                .build();
    }

    static AiMessage toolCallFrom(String route) {
        return AiMessage.builder()
                .toolExecutionRequests(List.of(toolCall()))
                .attributes(Map.of(RoutingChatModel.ROUTE_ATTRIBUTE, route))
                .build();
    }

    @Test
    void should_ask_router_again_when_stored_route_is_no_longer_configured() {

        List<String> routed = new ArrayList<>();
        ChatModel chatModel = routingModel(request -> {
            routed.add("asked");
            return "complex";
        });

        chatModel.chat(ChatRequest.builder()
                .messages(
                        UserMessage.from("Weather?"),
                        toolCallFrom("removed-route"),
                        ToolExecutionResultMessage.from(toolCall(), "sunny"))
                .build());

        assertThat(routed).hasSize(1);
        assertThat(large.requests).hasSize(1);
    }

    @Test
    void should_record_route_in_async_response() {

        ChatResponse response = routingModel(byLength())
                .chatAsync(ChatRequest.builder().messages(UserMessage.from("Hi!")).build())
                .join();

        assertThat(response.aiMessage().attributes()).containsEntry(RoutingChatModel.ROUTE_ATTRIBUTE, "simple");
    }

    @Test
    void should_offload_routing_to_configured_executor() {

        List<Runnable> tasks = new ArrayList<>();
        ChatModel chatModel = RoutingChatModel.builder()
                .route("simple", small)
                .route("complex", large)
                .router(request -> "complex")
                .defaultRoute("simple")
                .executor(task -> {
                    tasks.add(task);
                    task.run();
                })
                .build();

        ChatResponse response = chatModel
                .chatAsync(ChatRequest.builder().messages(UserMessage.from("Hi!")).build())
                .join();

        assertThat(tasks).hasSize(1);
        assertThat(response.aiMessage().text()).isEqualTo("answer from large");
    }

    @Test
    void should_validate_routes_with_router_when_created() {

        ChatModelRouter router = new ChatModelRouter() {
            @Override
            public String route(ChatModelRoutingRequest request) {
                return "simple";
            }

            @Override
            public void validate(List<ChatModelRoute> routes) {
                throw new IllegalArgumentException("Routes " + routes.stream().map(ChatModelRoute::name).toList());
            }
        };

        assertThatThrownBy(() -> RoutingChatModel.builder()
                        .route("simple", small)
                        .route("complex", large)
                        .router(router)
                        .defaultRoute("complex")
                        .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Routes [simple, complex]");
    }

    @Test
    void should_expose_routes() {

        RoutingChatModel chatModel = routingModel(byLength());

        assertThat(chatModel.routes())
                .containsExactly(
                        new ChatModelRoute("simple", "Short questions"),
                        new ChatModelRoute("complex", "Everything else"));
        assertThat(chatModel.defaultRoute()).isEqualTo("complex");
    }

    @Test
    void should_route_asynchronously() {

        ChatModel chatModel = routingModel(byLength());

        ChatResponse response = chatModel
                .chatAsync(ChatRequest.builder().messages(UserMessage.from("Hi!")).build())
                .join();

        assertThat(response.aiMessage().text()).isEqualTo("answer from small");
    }

    @Test
    void should_support_capabilities_of_any_route() {

        assertThat(routingModel(byLength()).supportedCapabilities()).containsExactly(RESPONSE_FORMAT_JSON_SCHEMA);
        assertThat(RoutingChatModel.builder()
                        .route("complex", large)
                        .router(request -> "complex")
                        .defaultRoute("complex")
                        .build()
                        .supportedCapabilities())
                .isEmpty();
    }

    static ChatRequest jsonSchemaRequest(String userMessage) {
        return ChatRequest.builder()
                .messages(UserMessage.from(userMessage))
                .responseFormat(ResponseFormat.builder()
                        .type(ResponseFormatType.JSON)
                        .jsonSchema(JsonSchema.builder()
                                .name("Person")
                                .rootElement(JsonObjectSchema.builder()
                                        .addStringProperty("name")
                                        .build())
                                .build())
                        .build())
                .build();
    }

    @Test
    void should_route_requests_needing_a_capability_only_to_routes_supporting_it() {

        List<ChatModelRoute> seen = new ArrayList<>();
        ChatModel chatModel = routingModel(request -> {
            seen.addAll(request.routes());
            return request.routes().get(0).name();
        });

        ChatResponse response = chatModel.chat(jsonSchemaRequest("Please analyze the attached quarterly report"));

        assertThat(response.aiMessage().text()).isEqualTo("answer from small");
        assertThat(seen).containsExactly(new ChatModelRoute("simple", "Short questions"));
    }

    @Test
    void should_fail_when_selected_route_does_not_support_the_needed_capability() {

        assertThatThrownBy(() -> routingModel(request -> "complex").chat(jsonSchemaRequest("Hi!")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Route 'complex' does not support");

    }

    @Test
    void should_route_requests_needing_a_capability_to_all_routes_when_no_route_declares_it() {

        List<ChatModelRoute> seen = new ArrayList<>();
        ChatModel chatModel = RoutingChatModel.builder()
                .route("simple", "Short questions", new FakeChatModel("small", Set.of()))
                .route("complex", "Everything else", large)
                .router(request -> {
                    seen.addAll(request.routes());
                    return "complex";
                })
                .defaultRoute("simple")
                .build();

        ChatResponse response = chatModel.chat(jsonSchemaRequest("Hi!"));

        assertThat(response.aiMessage().text()).isEqualTo("answer from large");
        assertThat(seen).extracting(ChatModelRoute::name).containsExactly("simple", "complex");
    }

    @Test
    void should_use_first_capable_route_when_default_route_does_not_support_needed_capability() {

        ChatResponse response = routingModel(request -> null).chat(jsonSchemaRequest("Hi!"));

        assertThat(response.aiMessage().text()).isEqualTo("answer from small");
    }

    @Test
    void should_route_on_default_executor_when_router_does_not_support_async() {

        DecisionModelMock decisionModel = DecisionModelMock.thatAlwaysAnswers(Map.of(
                        "route", ChoiceAnswer.builder().value("simple").build()))
                .withoutAsyncSupport();
        ChatModel chatModel = routingModel(new DecisionModelChatModelRouter(decisionModel));

        ChatResponse response = chatModel
                .chatAsync(ChatRequest.builder()
                        .messages(UserMessage.from("Please analyze the attached quarterly report"))
                        .build())
                .join();

        assertThat(response.aiMessage().text()).isEqualTo("answer from small");
        assertThat(decisionModel.requests()).hasSize(1);
    }

    @Test
    void should_cancel_routing_when_async_call_is_cancelled() {

        CompletableFuture<String> route = new CompletableFuture<>();
        ChatModelRouter asyncRouter = new ChatModelRouter() {
            @Override
            public String route(ChatModelRoutingRequest request) {
                throw new AssertionError("must not block");
            }

            @Override
            public CompletableFuture<String> routeAsync(ChatModelRoutingRequest request) {
                return route;
            }
        };

        routingModel(asyncRouter)
                .chatAsync(ChatRequest.builder().messages(UserMessage.from("Hi!")).build())
                .cancel(true);

        assertThat(route).isCancelled();
        assertThat(small.requests).isEmpty();
        assertThat(large.requests).isEmpty();
    }

    @Test
    void should_route_asynchronously_with_async_router() {

        CompletableFuture<String> route = new CompletableFuture<>();
        ChatModelRouter asyncRouter = new ChatModelRouter() {
            @Override
            public String route(ChatModelRoutingRequest request) {
                throw new AssertionError("must not block");
            }

            @Override
            public CompletableFuture<String> routeAsync(ChatModelRoutingRequest request) {
                return route;
            }
        };

        CompletableFuture<ChatResponse> response = routingModel(asyncRouter)
                .chatAsync(ChatRequest.builder().messages(UserMessage.from("Hi!")).build());

        assertThat(response).isNotDone();
        route.complete("complex");
        assertThat(response.join().aiMessage().text()).isEqualTo("answer from large");
    }

    @Test
    void should_fail_async_call_when_routing_fails() {

        CompletableFuture<ChatResponse> response = routingModel(request -> "medium")
                .chatAsync(ChatRequest.builder().messages(UserMessage.from("Hi!")).build());

        assertThat(response).failsWithin(java.time.Duration.ofSeconds(1))
                .withThrowableOfType(java.util.concurrent.ExecutionException.class)
                .withCauseInstanceOf(IllegalStateException.class);
    }

    @Test
    void should_validate_configuration() {

        assertThatThrownBy(() -> RoutingChatModel.builder()
                        .route("simple", small)
                        .route("simple", large)
                        .router(request -> "simple")
                        .defaultRoute("simple")
                        .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("more than one route named 'simple'");
        assertThatThrownBy(() -> RoutingChatModel.builder()
                        .route("simple", small)
                        .router(request -> "simple")
                        .defaultRoute("complex")
                        .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("default route 'complex'");
        assertThatThrownBy(() -> RoutingChatModel.builder()
                        .route("simple", small)
                        .router(request -> "simple")
                        .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("defaultRoute");
        assertThatThrownBy(() -> RoutingChatModel.builder()
                        .route("simple", small)
                        .defaultRoute("simple")
                        .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("router");
        assertThatThrownBy(() -> RoutingChatModel.builder().router(request -> "x").build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("routes");
    }

    // streaming

    static class FakeStreamingChatModel implements StreamingChatModel {

        final String name;
        final List<ChatRequest> requests = new CopyOnWriteArrayList<>();

        FakeStreamingChatModel(String name) {
            this.name = name;
        }

        @Override
        public void doChat(ChatRequest chatRequest, StreamingChatResponseHandler handler) {
            requests.add(chatRequest);
            handler.onPartialResponse("answer from ");
            handler.onPartialResponse(name);
            handler.onCompleteResponse(ChatResponse.builder()
                    .aiMessage(AiMessage.from("answer from " + name))
                    .build());
        }

        @Override
        public Flow.Publisher<ChatModelStreamingEvent> doChat(ChatRequest chatRequest) {
            requests.add(chatRequest);
            return subscriber -> subscriber.onSubscribe(new Flow.Subscription() {
                boolean done;

                @Override
                public void request(long n) {
                    if (!done) {
                        done = true;
                        subscriber.onNext(new CompleteResponse(ChatResponse.builder()
                                .aiMessage(AiMessage.from("answer from " + name))
                                .build()));
                        subscriber.onComplete();
                    }
                }

                @Override
                public void cancel() {}
            });
        }
    }

    final FakeStreamingChatModel smallStreaming = new FakeStreamingChatModel("small");
    final FakeStreamingChatModel largeStreaming = new FakeStreamingChatModel("large");

    RoutingStreamingChatModel routingStreamingModel(ChatModelRouter router) {
        return RoutingStreamingChatModel.builder()
                .route("simple", "Short questions", smallStreaming)
                .route("complex", "Everything else", largeStreaming)
                .router(router)
                .defaultRoute("complex")
                .executor(Runnable::run) // routers written as lambdas run on the executor, here on the calling thread
                .build();
    }

    @Test
    void should_route_streaming_requests() {

        StringBuilder partials = new StringBuilder();
        List<ChatResponse> completed = new ArrayList<>();

        routingStreamingModel(byLength()).chat("Hi!", new StreamingChatResponseHandler() {
            @Override
            public void onPartialResponse(String partialResponse) {
                partials.append(partialResponse);
            }

            @Override
            public void onCompleteResponse(ChatResponse completeResponse) {
                completed.add(completeResponse);
            }

            @Override
            public void onError(Throwable error) {
                throw new AssertionError(error);
            }
        });

        assertThat(partials).hasToString("answer from small");
        assertThat(completed).hasSize(1);
        assertThat(largeStreaming.requests).isEmpty();
    }

    @Test
    void should_record_route_and_keep_tool_calling_loop_when_streaming() {

        List<ChatResponse> completed = new ArrayList<>();
        StreamingChatResponseHandler handler = new StreamingChatResponseHandler() {
            @Override
            public void onCompleteResponse(ChatResponse completeResponse) {
                completed.add(completeResponse);
            }

            @Override
            public void onError(Throwable error) {
                throw new AssertionError(error);
            }
        };
        RoutingStreamingChatModel model = routingStreamingModel(request -> "complex");

        model.chat(
                ChatRequest.builder()
                        .messages(
                                UserMessage.from("Weather?"),
                                toolCallFrom("simple"),
                                ToolExecutionResultMessage.from(toolCall(), "sunny"))
                        .build(),
                handler);

        assertThat(smallStreaming.requests).hasSize(1);
        assertThat(largeStreaming.requests).isEmpty();
        assertThat(completed)
                .singleElement()
                .satisfies(response -> assertThat(response.aiMessage().attributes())
                        .containsEntry(RoutingChatModel.ROUTE_ATTRIBUTE, "simple"));
    }

    @Test
    void should_report_routing_errors_to_streaming_handler() {

        List<Throwable> errors = new ArrayList<>();

        routingStreamingModel(request -> "medium").chat("Hi!", new StreamingChatResponseHandler() {
            @Override
            public void onCompleteResponse(ChatResponse completeResponse) {}

            @Override
            public void onError(Throwable error) {
                errors.add(error);
            }
        });

        assertThat(errors).singleElement().isInstanceOf(IllegalStateException.class);
    }

    @Test
    void should_route_streaming_requests_with_async_router() {

        CompletableFuture<String> route = new CompletableFuture<>();
        ChatModelRouter asyncRouter = new ChatModelRouter() {
            @Override
            public String route(ChatModelRoutingRequest request) {
                throw new AssertionError("must not block");
            }

            @Override
            public CompletableFuture<String> routeAsync(ChatModelRoutingRequest request) {
                return route;
            }
        };
        List<ChatResponse> completed = new ArrayList<>();

        routingStreamingModel(asyncRouter).chat("Hi!", new StreamingChatResponseHandler() {
            @Override
            public void onCompleteResponse(ChatResponse completeResponse) {
                completed.add(completeResponse);
            }

            @Override
            public void onError(Throwable error) {
                throw new AssertionError(error);
            }
        });

        assertThat(completed).isEmpty();
        route.complete("complex");
        assertThat(completed).singleElement().satisfies(response -> assertThat(response.aiMessage().text())
                .isEqualTo("answer from large"));
    }

    @Test
    void should_pass_demand_to_selected_model_after_async_routing() {

        CompletableFuture<String> route = new CompletableFuture<>();
        ChatModelRouter asyncRouter = new ChatModelRouter() {
            @Override
            public String route(ChatModelRoutingRequest request) {
                throw new AssertionError("must not block");
            }

            @Override
            public CompletableFuture<String> routeAsync(ChatModelRoutingRequest request) {
                return route;
            }
        };
        List<ChatModelStreamingEvent> events = new ArrayList<>();

        routingStreamingModel(asyncRouter)
                .chat(ChatRequest.builder().messages(UserMessage.from("Hi!")).build())
                .subscribe(new Flow.Subscriber<>() {
                    @Override
                    public void onSubscribe(Flow.Subscription subscription) {
                        subscription.request(1);
                    }

                    @Override
                    public void onNext(ChatModelStreamingEvent item) {
                        events.add(item);
                    }

                    @Override
                    public void onError(Throwable throwable) {
                        throw new AssertionError(throwable);
                    }

                    @Override
                    public void onComplete() {}
                });

        assertThat(events).isEmpty();
        route.complete("simple");
        assertThat(events).singleElement().isInstanceOf(CompleteResponse.class);
    }

    @Test
    void should_report_error_of_selected_streaming_model_to_handler() {

        StreamingChatModel failing = new StreamingChatModel() {
            @Override
            public void doChat(ChatRequest chatRequest, StreamingChatResponseHandler handler) {
                throw new IllegalArgumentException("unsupported parameter");
            }
        };
        List<Throwable> errors = new ArrayList<>();

        RoutingStreamingChatModel.builder()
                .route("only", failing)
                .router(request -> "only")
                .defaultRoute("only")
                .executor(Runnable::run)
                .build()
                .chat("Hi!", new StreamingChatResponseHandler() {
                    @Override
                    public void onCompleteResponse(ChatResponse completeResponse) {}

                    @Override
                    public void onError(Throwable error) {
                        errors.add(error);
                    }
                });

        assertThat(errors).singleElement().isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void should_signal_error_on_non_positive_demand_before_routing() {

        CompletableFuture<String> route = new CompletableFuture<>();
        ChatModelRouter asyncRouter = new ChatModelRouter() {
            @Override
            public String route(ChatModelRoutingRequest request) {
                throw new AssertionError("must not block");
            }

            @Override
            public CompletableFuture<String> routeAsync(ChatModelRoutingRequest request) {
                return route;
            }
        };
        List<Throwable> errors = new ArrayList<>();

        routingStreamingModel(asyncRouter)
                .chat(ChatRequest.builder().messages(UserMessage.from("Hi!")).build())
                .subscribe(new Flow.Subscriber<>() {
                    @Override
                    public void onSubscribe(Flow.Subscription subscription) {
                        subscription.request(0);
                    }

                    @Override
                    public void onNext(ChatModelStreamingEvent item) {}

                    @Override
                    public void onError(Throwable throwable) {
                        errors.add(throwable);
                    }

                    @Override
                    public void onComplete() {}
                });
        route.complete("simple");

        assertThat(errors).singleElement().isInstanceOf(IllegalArgumentException.class);
        assertThat(smallStreaming.requests).isEmpty();
    }

    @Test
    void should_cancel_routing_and_not_call_model_when_cancelled_before_routing() {

        CompletableFuture<String> route = new CompletableFuture<>();
        ChatModelRouter asyncRouter = new ChatModelRouter() {
            @Override
            public String route(ChatModelRoutingRequest request) {
                throw new AssertionError("must not block");
            }

            @Override
            public CompletableFuture<String> routeAsync(ChatModelRoutingRequest request) {
                return route;
            }
        };

        routingStreamingModel(asyncRouter)
                .chat(ChatRequest.builder().messages(UserMessage.from("Hi!")).build())
                .subscribe(new Flow.Subscriber<>() {
                    @Override
                    public void onSubscribe(Flow.Subscription subscription) {
                        subscription.request(1);
                        subscription.cancel();
                    }

                    @Override
                    public void onNext(ChatModelStreamingEvent item) {}

                    @Override
                    public void onError(Throwable throwable) {}

                    @Override
                    public void onComplete() {}
                });

        assertThat(route).isCancelled();
        assertThat(smallStreaming.requests).isEmpty();
    }

    @Test
    void should_route_reactive_streaming_requests_on_subscription() {

        List<String> routed = new ArrayList<>();
        Flow.Publisher<ChatModelStreamingEvent> publisher = routingStreamingModel(request -> {
                    routed.add("asked");
                    return "complex";
                })
                .chat(ChatRequest.builder()
                        .messages(UserMessage.from("Please analyze the attached quarterly report"))
                        .build());

        assertThat(routed).isEmpty();

        List<ChatModelStreamingEvent> events = new ArrayList<>();
        publisher.subscribe(new Flow.Subscriber<>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(ChatModelStreamingEvent item) {
                events.add(item);
            }

            @Override
            public void onError(Throwable throwable) {
                throw new AssertionError(throwable);
            }

            @Override
            public void onComplete() {}
        });

        assertThat(routed).hasSize(1);
        assertThat(events).singleElement().isInstanceOf(CompleteResponse.class);
        assertThat(((CompleteResponse) events.get(0)).chatResponse().aiMessage().attributes())
                .containsEntry(RoutingChatModel.ROUTE_ATTRIBUTE, "complex");
        assertThat(largeStreaming.requests).hasSize(1);
    }
}
