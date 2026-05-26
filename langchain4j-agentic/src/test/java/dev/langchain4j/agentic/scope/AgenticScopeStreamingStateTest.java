package dev.langchain4j.agentic.scope;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.internal.DelayedResponse;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.service.AiServiceTokenStream;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class AgenticScopeStreamingStateTest {

    @AfterEach
    void cleanup() {
        AgenticScopePersister.setStore(null);
    }

    @Test
    void frameworkDoesNotPersistAiServiceTokenStreamInState() {
        JsonCapturingAgenticScopeStore store = new JsonCapturingAgenticScopeStore();
        AgenticScopePersister.setStore(store);

        AiServiceStreamingAgent streamingAgent = AgenticServices.agentBuilder(AiServiceStreamingAgent.class)
                .streamingChatModel(new FixedStreamingChatModel())
                .outputKey("result")
                .build();

        StreamingWorkflow workflow = AgenticServices.sequenceBuilder(StreamingWorkflow.class)
                .subAgents(streamingAgent)
                .beforeCall(agenticScope -> agenticScope.writeState("result", "stale"))
                .outputKey("result")
                .build();

        TokenStream tokenStream = workflow.chat("session-1", "hello");

        assertThat(tokenStream).isInstanceOf(AiServiceTokenStream.class);
        assertThat(store.savedScope).isNotNull();
        assertThat(store.savedScope.state()).doesNotContainKey("result");
        assertThat(store.savedScopes)
                .allSatisfy(scope -> assertThat(scope.state()).doesNotContainKey("result"));
        assertThat(store.savedScope.agentInvocations())
                .singleElement()
                .extracting(AgentInvocation::output)
                .isNull();
        assertThatCode(() -> AgenticScopeSerializer.toJson(store.savedScope)).doesNotThrowAnyException();
    }

    @Test
    void frameworkDoesNotPersistAsyncAiServiceTokenStreamInState() {
        JsonCapturingAgenticScopeStore store = new JsonCapturingAgenticScopeStore();
        AgenticScopePersister.setStore(store);

        AiServiceStreamingAgent streamingAgent = AgenticServices.agentBuilder(AiServiceStreamingAgent.class)
                .streamingChatModel(new FixedStreamingChatModel())
                .async(true)
                .outputKey("result")
                .build();

        StreamingWorkflow workflow = AgenticServices.sequenceBuilder(StreamingWorkflow.class)
                .subAgents(streamingAgent)
                .beforeCall(agenticScope -> agenticScope.writeState("result", "stale"))
                .outputKey("result")
                .build();

        TokenStream tokenStream = workflow.chat("session-async", "hello");

        assertThat(tokenStream).isInstanceOf(AiServiceTokenStream.class);
        assertThat(store.savedScope).isNotNull();
        assertThat(store.savedScope.state()).doesNotContainKey("result");
        assertThat(store.savedScopes)
                .allSatisfy(scope -> assertThat(scope.state()).doesNotContainKey("result"));
        assertThat(store.savedScope.agentInvocations())
                .singleElement()
                .extracting(AgentInvocation::output)
                .isNull();
        assertThatCode(() -> AgenticScopeSerializer.toJson(store.savedScope)).doesNotThrowAnyException();
    }

    @Test
    void asyncStreamingSubagentStillWritesTextWhenItIsNotFinalOutput() {
        AiServiceStreamingAgent streamingAgent = AgenticServices.agentBuilder(AiServiceStreamingAgent.class)
                .streamingChatModel(new FixedStreamingChatModel())
                .async(true)
                .outputKey("story")
                .build();

        TextWorkflow workflow = AgenticServices.sequenceBuilder(TextWorkflow.class)
                .subAgents(streamingAgent, new TextAgent())
                .outputKey("result")
                .build();

        assertThat(workflow.chat("hello")).isEqualTo("ok done");
    }

    @Test
    void persistentCheckpointDoesNotPersistAsyncStreamingRuntimeObjects() {
        JsonCapturingAgenticScopeStore store = new JsonCapturingAgenticScopeStore();
        AgenticScopePersister.setStore(store);

        AiServiceStreamingAgent streamingAgent = AgenticServices.agentBuilder(AiServiceStreamingAgent.class)
                .streamingChatModel(new FixedStreamingChatModel())
                .async(true)
                .outputKey("story")
                .build();

        PersistentTextWorkflow workflow = AgenticServices.sequenceBuilder(PersistentTextWorkflow.class)
                .subAgents(streamingAgent, new TextAgent())
                .outputKey("result")
                .build();

        assertThat(workflow.chat("session-async-non-final", "hello")).isEqualTo("ok done");
        assertThat(store.savedScopes)
                .extracting(scope -> scope.state().get("story"))
                .filteredOn(Objects::nonNull)
                .containsOnly("ok")
                .allSatisfy(story ->
                        assertThat(story).isNotInstanceOf(TokenStream.class).isNotInstanceOf(DelayedResponse.class));
    }

    @Test
    void agentInvocationSerializationDoesNotPersistRawStreamingOutput() {
        DefaultAgenticScope scope = new DefaultAgenticScope(DefaultAgenticScope.Kind.PERSISTENT);
        TokenStream tokenStream = mock(TokenStream.class);

        scope.agentInvocations()
                .add(new AgentInvocation(AiServiceStreamingAgent.class, "chat", "agent-1", Map.of(), tokenStream));

        String json = AgenticScopeSerializer.toJson(scope);
        DefaultAgenticScope deserialized = AgenticScopeSerializer.fromJson(json);

        assertThat(deserialized.agentInvocations())
                .singleElement()
                .extracting(AgentInvocation::output)
                .isNull();
    }

    @Test
    void stateSerializationDoesNotPersistRawStreamingOutput() {
        DefaultAgenticScope scope = new DefaultAgenticScope(DefaultAgenticScope.Kind.PERSISTENT);
        TokenStream tokenStream = mock(TokenStream.class);

        scope.writeState("result", tokenStream);

        assertThat(scope.state()).containsEntry("result", tokenStream);

        String json = AgenticScopeSerializer.toJson(scope);
        DefaultAgenticScope deserialized = AgenticScopeSerializer.fromJson(json);

        assertThat(deserialized.state()).doesNotContainKey("result");
    }

    @Test
    void stateSerializationDoesNotPersistNestedStreamingOutput() {
        DefaultAgenticScope scope = new DefaultAgenticScope(DefaultAgenticScope.Kind.PERSISTENT);
        TokenStream tokenStream = mock(TokenStream.class);

        scope.writeState("payload", Map.of("result", tokenStream, "request", "hello"));

        String json = AgenticScopeSerializer.toJson(scope);
        DefaultAgenticScope deserialized = AgenticScopeSerializer.fromJson(json);

        assertThat(deserialized.state().get("payload"))
                .isInstanceOf(Map.class)
                .satisfies(payload -> assertThat((Map<Object, Object>) payload)
                        .doesNotContainKey("result")
                        .containsEntry("request", "hello"));
    }

    @Test
    void stateSerializationDoesNotBlockOnUnfinishedDelayedResponse() {
        DefaultAgenticScope scope = new DefaultAgenticScope(DefaultAgenticScope.Kind.PERSISTENT);

        scope.writeState("result", new UnfinishedDelayedResponse());

        String json = AgenticScopeSerializer.toJson(scope);
        DefaultAgenticScope deserialized = AgenticScopeSerializer.fromJson(json);

        assertThat(deserialized.state()).doesNotContainKey("result");
    }

    @Test
    void checkpointDoesNotBlockOnUnfinishedDelayedResponse() {
        JsonCapturingAgenticScopeStore store = new JsonCapturingAgenticScopeStore();
        AgenticScopePersister.setStore(store);
        AgenticScopeRegistry registry = new AgenticScopeRegistry("test-agent");
        DefaultAgenticScope scope = registry.create("session-delayed-response");

        scope.writeState("result", new UnfinishedDelayedResponse());

        assertThatCode(() -> scope.checkpoint(registry)).doesNotThrowAnyException();
        assertThat(store.savedScope.state()).doesNotContainKey("result");
    }

    @Test
    void agentInvocationSerializationDoesNotPersistStreamingInputFromStateMap() {
        DefaultAgenticScope scope = new DefaultAgenticScope(DefaultAgenticScope.Kind.PERSISTENT);
        TokenStream tokenStream = mock(TokenStream.class);
        Map<String, Object> input = new ConcurrentHashMap<>();
        input.put("result", tokenStream);
        input.put("request", "hello");

        scope.agentInvocations()
                .add(new AgentInvocation(AiServiceStreamingAgent.class, "chat", "agent-1", input, "ok"));

        assertThat(scope.agentInvocations())
                .singleElement()
                .extracting(AgentInvocation::input)
                .satisfies(runtimeInput -> assertThat(runtimeInput)
                        .containsEntry("result", tokenStream)
                        .containsEntry("request", "hello"));

        String json = AgenticScopeSerializer.toJson(scope);
        DefaultAgenticScope deserialized = AgenticScopeSerializer.fromJson(json);

        assertThat(deserialized.agentInvocations())
                .singleElement()
                .extracting(AgentInvocation::input)
                .satisfies(persistentInput -> assertThat(persistentInput)
                        .containsEntry("result", null)
                        .containsEntry("request", "hello"));
    }

    @Test
    void agentInvocationSerializationDoesNotPersistNestedStreamingInput() {
        DefaultAgenticScope scope = new DefaultAgenticScope(DefaultAgenticScope.Kind.PERSISTENT);
        TokenStream tokenStream = mock(TokenStream.class);
        Map<String, Object> input = new ConcurrentHashMap<>();
        input.put("payload", Map.of("result", tokenStream, "request", "hello"));

        scope.agentInvocations()
                .add(new AgentInvocation(AiServiceStreamingAgent.class, "chat", "agent-1", input, "ok"));

        String json = AgenticScopeSerializer.toJson(scope);
        DefaultAgenticScope deserialized = AgenticScopeSerializer.fromJson(json);

        assertThat(deserialized.agentInvocations())
                .singleElement()
                .extracting(AgentInvocation::input)
                .satisfies(persistentInput -> assertThat(persistentInput.get("payload"))
                        .isInstanceOf(Map.class)
                        .satisfies(payload -> assertThat((Map<Object, Object>) payload)
                                .doesNotContainKey("result")
                                .containsEntry("request", "hello")));
    }

    @Test
    void frameworkDoesNotReusePreviousStreamingOutputWhenOptionalStreamingAgentIsSkipped() {
        AiServiceStreamingAgent streamingAgent = AgenticServices.agentBuilder(AiServiceStreamingAgent.class)
                .streamingChatModel(new FixedStreamingChatModel())
                .optional(true)
                .outputKey("result")
                .build();

        StreamingWorkflow workflow = AgenticServices.sequenceBuilder(StreamingWorkflow.class)
                .subAgents(streamingAgent)
                .outputKey("result")
                .build();

        TokenStream firstTokenStream = workflow.chat("session-2", "hello");
        TokenStream secondTokenStream = workflow.chat("session-2", null);

        assertThat(firstTokenStream).isInstanceOf(AiServiceTokenStream.class);
        assertThat(secondTokenStream).isNull();
    }

    public interface StreamingWorkflow {

        @Agent(outputKey = "result")
        TokenStream chat(@MemoryId String memoryId, @V("input") String input);
    }

    public interface AiServiceStreamingAgent {

        @UserMessage("{{input}}")
        @Agent(outputKey = "result")
        TokenStream chat(@V("input") String input);
    }

    public interface TextWorkflow {

        @Agent(outputKey = "result")
        String chat(@V("input") String input);
    }

    public interface PersistentTextWorkflow {

        @Agent(outputKey = "result")
        String chat(@MemoryId String memoryId, @V("input") String input);
    }

    public static class TextAgent {

        @Agent(outputKey = "result")
        public String append(@V("story") String story) {
            return story + " done";
        }
    }

    private static class FixedStreamingChatModel implements StreamingChatModel {

        @Override
        public void doChat(ChatRequest chatRequest, StreamingChatResponseHandler handler) {
            handler.onCompleteResponse(
                    ChatResponse.builder().aiMessage(AiMessage.from("ok")).build());
        }
    }

    private static class UnfinishedDelayedResponse implements DelayedResponse<String> {

        @Override
        public boolean isDone() {
            return false;
        }

        @Override
        public String blockingGet() {
            throw new AssertionError("Persistence must not block on an unfinished response");
        }
    }

    private static class JsonCapturingAgenticScopeStore implements AgenticScopeStore {

        private DefaultAgenticScope savedScope;
        private final List<DefaultAgenticScope> savedScopes = new ArrayList<>();

        @Override
        public boolean save(AgenticScopeKey key, DefaultAgenticScope agenticScope) {
            String json = AgenticScopeSerializer.toJson(agenticScope);
            this.savedScope = AgenticScopeSerializer.fromJson(json);
            this.savedScopes.add(this.savedScope);
            return true;
        }

        @Override
        public Optional<DefaultAgenticScope> load(AgenticScopeKey key) {
            return Optional.empty();
        }

        @Override
        public boolean delete(AgenticScopeKey key) {
            return false;
        }

        @Override
        public java.util.Set<AgenticScopeKey> getAllKeys() {
            return java.util.Set.of();
        }
    }
}
