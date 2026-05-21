package dev.langchain4j.agentic.scope;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.AgenticServices;
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
import java.util.Optional;
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
