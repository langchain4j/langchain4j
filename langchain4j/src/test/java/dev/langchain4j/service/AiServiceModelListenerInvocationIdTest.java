package dev.langchain4j.service;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.guardrail.InputGuardrail;
import dev.langchain4j.guardrail.InputGuardrailResult;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.test.mock.ChatModelMock;
import dev.langchain4j.test.mock.StreamingChatModelMock;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.embedding.listener.EmbeddingModelListener;
import dev.langchain4j.model.embedding.listener.EmbeddingModelRequestContext;
import dev.langchain4j.model.embedding.listener.EmbeddingModelResponseContext;
import dev.langchain4j.test.mock.EmbeddingModelMock;
import dev.langchain4j.model.scoring.listener.ScoringModelListener;
import dev.langchain4j.model.scoring.listener.ScoringModelRequestContext;
import dev.langchain4j.model.scoring.listener.ScoringModelResponseContext;
import dev.langchain4j.test.mock.ScoringModelMock;
import dev.langchain4j.observability.api.event.AiServiceCompletedEvent;
import dev.langchain4j.observability.api.event.AiServiceStartedEvent;
import dev.langchain4j.observability.api.listener.AiServiceListener;
import dev.langchain4j.rag.DefaultRetrievalAugmentor;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.aggregator.ReRankingContentAggregator;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.rag.content.retriever.listener.ContentRetrieverListener;
import dev.langchain4j.rag.content.retriever.listener.ContentRetrieverRequestContext;
import dev.langchain4j.rag.query.router.LanguageModelQueryRouter;
import dev.langchain4j.rag.query.transformer.ExpandingQueryTransformer;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import dev.langchain4j.store.embedding.listener.EmbeddingStoreListener;
import dev.langchain4j.store.embedding.listener.EmbeddingStoreRequestContext;
import dev.langchain4j.store.embedding.listener.EmbeddingStoreResponseContext;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Flow;
import java.util.function.Function;

class AiServiceModelListenerInvocationIdTest {

    interface Assistant {

        String chat(String message);
    }

    interface AsyncAssistant {

        CompletableFuture<String> chat(String message);
    }

    interface StreamingAssistant {

        TokenStream chat(String message);
    }

    interface ReactiveAssistant {

        Flow.Publisher<String> chat(String message);
    }

    final List<UUID> startedInvocationIds = new CopyOnWriteArrayList<>();
    final List<UUID> modelInvocationIds = new CopyOnWriteArrayList<>();

    @Test
    void own_chat_calls_of_a_synchronous_ai_service_should_get_its_invocation_id() {
        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(ChatModelMock.thatResponds(callToolThenAnswer("doNothing"))
                        .withListeners(recordingListener()))
                .tools(new DoNothingTool())
                .registerListener(startedListener())
                .build();

        assistant.chat("Hello");

        assertThat(startedInvocationIds).singleElement().isNotNull();
        UUID id = startedInvocationIds.get(0);
        assertThat(modelInvocationIds).containsExactly(id, id, id, id);
    }

    @Test
    void own_chat_calls_of_an_asynchronous_ai_service_should_get_its_invocation_id() {
        AsyncAssistant assistant = AiServices.builder(AsyncAssistant.class)
                .chatModel(ChatModelMock.thatResponds(callToolThenAnswer("doNothing"))
                        .withListeners(recordingListener()))
                .tools(new DoNothingTool())
                .registerListener(startedListener())
                .build();

        assertThat(assistant.chat("Hello")).succeedsWithin(5, SECONDS);

        assertThat(startedInvocationIds).singleElement().isNotNull();
        UUID id = startedInvocationIds.get(0);
        assertThat(modelInvocationIds).containsExactly(id, id, id, id);
    }

    @Test
    void own_chat_calls_of_a_token_stream_ai_service_should_get_its_invocation_id() {
        StreamingAssistant assistant = AiServices.builder(StreamingAssistant.class)
                .streamingChatModel(StreamingChatModelMock.thatStreams(
                                AiMessage.from(ToolExecutionRequest.builder()
                                        .id("1")
                                        .name("doNothing")
                                        .arguments("{}")
                                        .build()),
                                AiMessage.from("answer"))
                        .withListeners(recordingListener()))
                .tools(new DoNothingTool())
                .registerListener(startedListener())
                .build();
        CompletableFuture<String> answer = new CompletableFuture<>();

        assistant
                .chat("Hello")
                .onCompleteResponse(response -> answer.complete(response.aiMessage().text()))
                .onError(answer::completeExceptionally)
                .start();

        assertThat(answer).succeedsWithin(5, SECONDS);
        assertThat(startedInvocationIds).singleElement().isNotNull();
        UUID id = startedInvocationIds.get(0);
        assertThat(modelInvocationIds).containsExactly(id, id, id, id);
    }

    @Test
    void own_chat_calls_of_a_reactive_ai_service_should_get_its_invocation_id() {
        ReactiveAssistant assistant = AiServices.builder(ReactiveAssistant.class)
                .streamingChatModel(StreamingChatModelMock.thatStreams(
                                AiMessage.from(ToolExecutionRequest.builder()
                                        .id("1")
                                        .name("doNothing")
                                        .arguments("{}")
                                        .build()),
                                AiMessage.from("answer"))
                        .withListeners(recordingListener()))
                .tools(new DoNothingTool())
                .registerListener(startedListener())
                .build();

        assertThat(complete(assistant.chat("Hello"))).succeedsWithin(5, SECONDS);

        assertThat(startedInvocationIds).singleElement().isNotNull();
        UUID id = startedInvocationIds.get(0);
        assertThat(modelInvocationIds).containsExactly(id, id, id, id);
    }

    @Test
    void own_chat_calls_of_a_nested_ai_service_should_get_the_nested_invocation_id() {
        Assistant inner = AiServices.builder(Assistant.class)
                .chatModel(ChatModelMock.thatAlwaysResponds("inner answer").withListeners(recordingListener()))
                .registerListener(startedListener())
                .build();
        Assistant outer = AiServices.builder(Assistant.class)
                .chatModel(ChatModelMock.thatResponds(callToolThenAnswer("askInner")))
                .tools(new InnerAssistantTool(inner))
                .registerListener(startedListener())
                .build();

        outer.chat("Hello");

        assertThat(startedInvocationIds).hasSize(2).doesNotContainNull();
        UUID innerId = startedInvocationIds.get(1);
        assertThat(modelInvocationIds).containsExactly(innerId, innerId);
    }

    @Test
    void model_called_by_a_token_stream_handler_on_the_thread_of_the_model_call_should_get_no_invocation_id() {
        ChatModel modelUsedByHandler = ChatModelMock.thatAlwaysResponds("hello").withListeners(recordingListener());
        StreamingAssistant assistant = AiServices.builder(StreamingAssistant.class)
                .streamingChatModel(StreamingChatModelMock.thatAlwaysStreams("an", "swer").streamingOnCallingThread())
                .build();
        CompletableFuture<String> done = new CompletableFuture<>();

        assistant
                .chat("Hello")
                .onPartialResponse(token -> modelUsedByHandler.chat("about " + token))
                .onCompleteResponse(response -> done.complete(modelUsedByHandler.chat("judge this")))
                .onError(done::completeExceptionally)
                .start();

        assertThat(done).succeedsWithin(5, SECONDS);
        assertThat(modelInvocationIds).hasSize(6).containsOnlyNulls();
    }

    @Test
    void model_called_by_a_token_stream_handler_should_get_no_invocation_id() {
        ChatModel modelUsedByHandler = ChatModelMock.thatAlwaysResponds("hello").withListeners(recordingListener());
        StreamingAssistant assistant = AiServices.builder(StreamingAssistant.class)
                .streamingChatModel(StreamingChatModelMock.thatAlwaysStreams("answer"))
                .build();
        CompletableFuture<String> done = new CompletableFuture<>();

        assistant
                .chat("Hello")
                .onCompleteResponse(response -> done.complete(modelUsedByHandler.chat("judge this")))
                .onError(done::completeExceptionally)
                .start();

        assertThat(done).succeedsWithin(5, SECONDS);
        assertThat(modelInvocationIds).hasSize(2).containsOnlyNulls();
    }

    @Test
    void should_call_chat_without_options_when_the_model_overrides_chat() {
        List<String> seenByOverride = new CopyOnWriteArrayList<>();
        ChatModel model = new ChatModelMock("answer") {
            @Override
            public ChatResponse chat(ChatRequest chatRequest) {
                seenByOverride.add("chat(ChatRequest)");
                return super.chat(chatRequest);
            }
        };
        Assistant assistant =
                AiServices.builder(Assistant.class).chatModel(model).build();

        assertThat(assistant.chat("Hello")).isEqualTo("answer");
        assertThat(seenByOverride).containsExactly("chat(ChatRequest)");
    }

    @Test
    void ai_service_called_by_the_chat_model_should_get_the_invocation_as_parent() {
        List<AiServiceStartedEvent> started = new CopyOnWriteArrayList<>();
        AiServiceListener<AiServiceStartedEvent> recording = new AiServiceListener<>() {
            @Override
            public Class<AiServiceStartedEvent> getEventClass() {
                return AiServiceStartedEvent.class;
            }

            @Override
            public void onEvent(AiServiceStartedEvent event) {
                started.add(event);
            }
        };
        Assistant inner = AiServices.builder(Assistant.class)
                .chatModel(ChatModelMock.thatAlwaysResponds("inner answer"))
                .registerListener(recording)
                .build();
        Assistant outer = AiServices.builder(Assistant.class)
                .chatModel(ChatModelMock.thatResponds(request -> AiMessage.from(inner.chat("help"))))
                .registerListener(recording)
                .build();

        assertThat(outer.chat("Hello")).isEqualTo("inner answer");

        assertThat(started).hasSize(2);
        assertThat(started.get(0).invocationContext().parentInvocationId()).isNull();
        assertThat(started.get(1).invocationContext().parentInvocationId())
                .isEqualTo(started.get(0).invocationContext().invocationId());
    }

    @Test
    void model_called_by_a_tool_should_get_the_invocation_id_of_the_ai_service() {
        ChatModel modelUsedByTool = ChatModelMock.thatAlwaysResponds("hello").withListeners(recordingListener());
        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(ChatModelMock.thatResponds(callToolThenAnswer("callModel")))
                .tools(new ModelCallingTool(modelUsedByTool))
                .registerListener(startedListener())
                .build();

        assistant.chat("Hello");

        assertThat(startedInvocationIds).singleElement().isNotNull();
        assertThat(modelInvocationIds).containsExactly(startedInvocationIds.get(0), startedInvocationIds.get(0));
    }

    @Test
    void model_called_by_a_tool_of_a_nested_ai_service_should_get_the_invocation_id_of_the_nested_ai_service() {
        ChatModel modelUsedByTool = ChatModelMock.thatAlwaysResponds("hello").withListeners(recordingListener());
        Assistant inner = AiServices.builder(Assistant.class)
                .chatModel(ChatModelMock.thatResponds(callToolThenAnswer("callModel")))
                .tools(new ModelCallingTool(modelUsedByTool))
                .registerListener(startedListener())
                .build();
        Assistant outer = AiServices.builder(Assistant.class)
                .chatModel(ChatModelMock.thatResponds(callToolThenAnswer("askInner")))
                .tools(new InnerAssistantTool(inner))
                .registerListener(startedListener())
                .build();

        outer.chat("Hello");

        assertThat(startedInvocationIds).hasSize(2).doesNotContainNull();
        UUID innerId = startedInvocationIds.get(1);
        assertThat(modelInvocationIds).containsExactly(innerId, innerId);
    }

    @Test
    void model_called_by_an_input_guardrail_should_get_the_invocation_id_of_the_ai_service() {
        ChatModel judge = ChatModelMock.thatAlwaysResponds("ok").withListeners(recordingListener());
        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(ChatModelMock.thatAlwaysResponds("answer"))
                .inputGuardrails(new InputGuardrail() {
                    @Override
                    public InputGuardrailResult validate(UserMessage userMessage) {
                        judge.chat("is this question ok?");
                        return success();
                    }
                })
                .registerListener(startedListener())
                .build();

        assistant.chat("Hello");

        assertThat(startedInvocationIds).singleElement().isNotNull();
        assertThat(modelInvocationIds).containsExactly(startedInvocationIds.get(0), startedInvocationIds.get(0));
    }

    @Test
    void content_retriever_should_get_the_invocation_id_of_the_ai_service() {
        ContentRetriever retriever = ((ContentRetriever) query -> List.of())
                .addListener(new ContentRetrieverListener() {
                    @Override
                    public void onRequest(ContentRetrieverRequestContext context) {
                        modelInvocationIds.add(context.invocationId());
                    }
                });
        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(ChatModelMock.thatAlwaysResponds("answer"))
                .contentRetriever(retriever)
                .registerListener(startedListener())
                .build();

        assistant.chat("Hello");

        assertThat(startedInvocationIds).singleElement().isNotNull();
        assertThat(modelInvocationIds).containsExactly(startedInvocationIds.get(0));
    }

    @Test
    void embedding_store_used_by_a_retriever_should_get_the_invocation_id_of_the_ai_service() {
        EmbeddingStore<TextSegment> store = new InMemoryEmbeddingStore<TextSegment>().addListener(recordingStoreListener());
        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(ChatModelMock.thatAlwaysResponds("answer"))
                .contentRetriever(EmbeddingStoreContentRetriever.builder()
                        .embeddingStore(store)
                        .embeddingModel(EmbeddingModelMock.thatAlwaysEmbeds(1f, 2f, 3f))
                        .build())
                .registerListener(startedListener())
                .build();

        assistant.chat("Hello");

        assertThat(startedInvocationIds).singleElement().isNotNull();
        assertThat(modelInvocationIds).containsExactly(startedInvocationIds.get(0), startedInvocationIds.get(0));
    }

    @Test
    void embedding_store_used_by_a_tool_should_get_the_invocation_id_of_the_ai_service() {
        EmbeddingStore<TextSegment> store = new InMemoryEmbeddingStore<TextSegment>().addListener(recordingStoreListener());
        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(ChatModelMock.thatResponds(callToolThenAnswer("addEmbedding")))
                .tools(new EmbeddingStoreTool(store))
                .registerListener(startedListener())
                .build();

        assistant.chat("Hello");
        store.add(Embedding.from(new float[] {1f, 2f, 3f}));

        assertThat(startedInvocationIds).singleElement().isNotNull();
        assertThat(modelInvocationIds)
                .containsExactly(startedInvocationIds.get(0), startedInvocationIds.get(0), null, null);
    }

    @Test
    void scoring_model_used_by_a_re_ranking_aggregator_should_get_the_invocation_id_of_the_ai_service() {
        ScoringModelMock scoringModel = ScoringModelMock.thatAlwaysScores(0.9).withListeners(new ScoringModelListener() {
            @Override
            public void onRequest(ScoringModelRequestContext context) {
                modelInvocationIds.add(context.invocationId());
            }

            @Override
            public void onResponse(ScoringModelResponseContext context) {
                modelInvocationIds.add(context.invocationId());
            }
        });
        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(ChatModelMock.thatAlwaysResponds("answer"))
                .retrievalAugmentor(DefaultRetrievalAugmentor.builder()
                        .contentRetriever(query -> List.of(Content.from("some content")))
                        .contentAggregator(new ReRankingContentAggregator(scoringModel))
                        .build())
                .registerListener(startedListener())
                .build();

        assistant.chat("Hello");

        assertThat(startedInvocationIds).singleElement().isNotNull();
        assertThat(modelInvocationIds).containsExactly(startedInvocationIds.get(0), startedInvocationIds.get(0));
    }

    @Test
    void every_model_and_store_call_of_an_asynchronous_retrieval_should_get_the_invocation_id_of_the_ai_service() {
        // The models complete on other threads, so later stages run in continuations on those threads
        ChatModel queryModel = ChatModelMock.thatAlwaysResponds("first query\nsecond query")
                .withListeners(recordingListener());
        ChatModel routingModel = ChatModelMock.thatAlwaysResponds("1").withListeners(recordingListener());
        EmbeddingModelMock embeddingModel = EmbeddingModelMock.thatAlwaysEmbeds(1f, 2f, 3f).withListeners(new EmbeddingModelListener() {
            @Override
            public void onRequest(EmbeddingModelRequestContext context) {
                modelInvocationIds.add(context.invocationId());
            }

            @Override
            public void onResponse(EmbeddingModelResponseContext context) {
                modelInvocationIds.add(context.invocationId());
            }
        });
        EmbeddingStore<TextSegment> store = new InMemoryEmbeddingStore<TextSegment>().addListener(recordingStoreListener());
        store.add(Embedding.from(new float[] {1f, 2f, 3f}), TextSegment.from("some content"));
        modelInvocationIds.clear();
        ContentRetriever retriever = EmbeddingStoreContentRetriever.builder()
                .embeddingModel(embeddingModel)
                .embeddingStore(store)
                .offloadBlocking(true)
                .build();
        ScoringModelMock scoringModel = ScoringModelMock.thatAlwaysScores(0.9).withListeners(new ScoringModelListener() {
            @Override
            public void onRequest(ScoringModelRequestContext context) {
                modelInvocationIds.add(context.invocationId());
            }

            @Override
            public void onResponse(ScoringModelResponseContext context) {
                modelInvocationIds.add(context.invocationId());
            }
        });
        AsyncAssistant assistant = AiServices.builder(AsyncAssistant.class)
                .chatModel(ChatModelMock.thatAlwaysResponds("answer"))
                .retrievalAugmentor(DefaultRetrievalAugmentor.builder()
                        .queryTransformer(new ExpandingQueryTransformer(queryModel))
                        .queryRouter(new LanguageModelQueryRouter(routingModel, Map.of(retriever, "documents")))
                        .contentAggregator(ReRankingContentAggregator.builder()
                                .scoringModel(scoringModel)
                                .querySelector(queryToContents -> queryToContents.keySet().iterator().next())
                                .build())
                        .build())
                .registerListener(startedListener())
                .build();

        assertThat(assistant.chat("Hello")).succeedsWithin(5, SECONDS);

        assertThat(startedInvocationIds).singleElement().isNotNull();
        // query transformation (2), routing of 2 queries (4), embedding (4) and search (4) of 2 queries, re-ranking (2)
        assertThat(modelInvocationIds).hasSize(16).containsOnly(startedInvocationIds.get(0));
    }

    @Test
    void model_called_by_a_model_listener_should_get_no_invocation_id() {
        ChatModel modelUsedByListener = ChatModelMock.thatAlwaysResponds("judged").withListeners(recordingListener());
        List<UUID> toolModelInvocationIds = new CopyOnWriteArrayList<>();
        ChatModel modelUsedByTool = ChatModelMock.thatAlwaysResponds("hello").withListeners(new ChatModelListener() {
            @Override
            public void onResponse(ChatModelResponseContext context) {
                toolModelInvocationIds.add(context.invocationId());
                modelUsedByListener.chat("judge this");
            }
        });
        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(ChatModelMock.thatResponds(callToolThenAnswer("callModel")))
                .tools(new ModelCallingTool(modelUsedByTool))
                .registerListener(startedListener())
                .build();

        assistant.chat("Hello");

        assertThat(startedInvocationIds).singleElement().isNotNull();
        assertThat(toolModelInvocationIds).containsExactly(startedInvocationIds.get(0));
        assertThat(modelInvocationIds).hasSize(2).containsOnlyNulls();
    }

    @Test
    void model_called_by_an_ai_service_listener_should_get_no_invocation_id() {
        ChatModel modelUsedByListener = ChatModelMock.thatAlwaysResponds("hello").withListeners(recordingListener());
        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(ChatModelMock.thatAlwaysResponds("answer"))
                .registerListener(new AiServiceListener<AiServiceCompletedEvent>() {
                    @Override
                    public Class<AiServiceCompletedEvent> getEventClass() {
                        return AiServiceCompletedEvent.class;
                    }

                    @Override
                    public void onEvent(AiServiceCompletedEvent event) {
                        modelUsedByListener.chat("judge this");
                    }
                })
                .build();

        assistant.chat("Hello");

        assertThat(modelInvocationIds).hasSize(2).containsOnlyNulls();
    }

    private ChatModelListener recordingListener() {
        return new ChatModelListener() {
            @Override
            public void onRequest(ChatModelRequestContext context) {
                modelInvocationIds.add(context.invocationId());
            }

            @Override
            public void onResponse(ChatModelResponseContext context) {
                modelInvocationIds.add(context.invocationId());
            }

            @Override
            public void onError(ChatModelErrorContext context) {
                modelInvocationIds.add(context.invocationId());
            }
        };
    }

    private EmbeddingStoreListener recordingStoreListener() {
        return new EmbeddingStoreListener() {
            @Override
            public void onRequest(EmbeddingStoreRequestContext<?> requestContext) {
                modelInvocationIds.add(requestContext.invocationId());
            }

            @Override
            public void onResponse(EmbeddingStoreResponseContext<?> responseContext) {
                modelInvocationIds.add(responseContext.invocationId());
            }
        };
    }

    private AiServiceListener<AiServiceStartedEvent> startedListener() {
        return new AiServiceListener<>() {
            @Override
            public Class<AiServiceStartedEvent> getEventClass() {
                return AiServiceStartedEvent.class;
            }

            @Override
            public void onEvent(AiServiceStartedEvent event) {
                startedInvocationIds.add(event.invocationContext().invocationId());
            }
        };
    }

    private static Function<ChatRequest, AiMessage> callToolThenAnswer(String toolName) {
        return request -> {
            List<ChatMessage> messages = request.messages();
            if (messages.get(messages.size() - 1) instanceof ToolExecutionResultMessage) {
                return AiMessage.from("answer");
            }
            return AiMessage.from(ToolExecutionRequest.builder()
                    .id(toolName)
                    .name(toolName)
                    .arguments("{}")
                    .build());
        };
    }

    static class DoNothingTool {

        @Tool
        String doNothing() {
            return "done";
        }
    }

    private static CompletableFuture<Void> complete(Flow.Publisher<String> publisher) {
        CompletableFuture<Void> done = new CompletableFuture<>();
        publisher.subscribe(new Flow.Subscriber<>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(String item) {}

            @Override
            public void onError(Throwable error) {
                done.completeExceptionally(error);
            }

            @Override
            public void onComplete() {
                done.complete(null);
            }
        });
        return done;
    }

    static class ModelCallingTool {

        private final ChatModel model;

        ModelCallingTool(ChatModel model) {
            this.model = model;
        }

        @Tool
        String callModel() {
            return model.chat("question from the tool");
        }
    }

    static class EmbeddingStoreTool {

        private final EmbeddingStore<TextSegment> store;

        EmbeddingStoreTool(EmbeddingStore<TextSegment> store) {
            this.store = store;
        }

        @Tool
        String addEmbedding() {
            return store.add(Embedding.from(new float[] {1f, 2f, 3f}));
        }
    }

    static class InnerAssistantTool {

        private final Assistant inner;

        InnerAssistantTool(Assistant inner) {
            this.inner = inner;
        }

        @Tool
        String askInner() {
            return inner.chat("inner question");
        }
    }
}
