package dev.langchain4j.agentic;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agentic.declarative.ChatMemoryProviderSupplier;
import dev.langchain4j.agentic.declarative.ChatMemorySupplier;
import dev.langchain4j.agentic.declarative.ChatModelSupplier;
import dev.langchain4j.agentic.declarative.SequenceAgent;
import dev.langchain4j.agentic.declarative.SupervisorAgent;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.agentic.scope.AgenticScopeAccess;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.mock.ChatModelMock;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.V;
import dev.langchain4j.store.memory.chat.InMemoryChatMemoryStore;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests that evicting an {@link AgenticScope} also evicts the chat memories that the
 * agents of the agentic system created for its memory id: the AI agents of the agent tree, the AI agents created for
 * the scope only, because their configuration depends on it, and the planner agent of a supervisor.
 */
class AgenticScopeEvictionTest {

    /** A chat memory store that also records the memory ids it was asked to delete. */
    static class RecordingStore extends InMemoryChatMemoryStore {
        final List<Object> deleted = new CopyOnWriteArrayList<>();

        @Override
        public void deleteMessages(Object memoryId) {
            deleted.add(memoryId);
            super.deleteMessages(memoryId);
        }

        ChatMemory memoryOf(Object memoryId) {
            return MessageWindowChatMemory.builder()
                    .id(memoryId)
                    .maxMessages(10)
                    .chatMemoryStore(this)
                    .build();
        }
    }

    static final RecordingStore GREETER_STORE = new RecordingStore();
    static final RecordingStore REPLIER_STORE = new RecordingStore();
    static final RecordingStore SUMMARIZER_STORE = new RecordingStore();
    static final RecordingStore SUPERVISOR_STORE = new RecordingStore();

    static final List<RecordingStore> STORES =
            List.of(GREETER_STORE, REPLIER_STORE, SUMMARIZER_STORE, SUPERVISOR_STORE);

    /** Replies with the last user message it receives. */
    static ChatModel echoModel() {
        return ChatModelMock.thatResponds(chatRequest -> {
            ChatMessage last = chatRequest.messages().get(chatRequest.messages().size() - 1);
            return AiMessage.from(last instanceof UserMessage userMessage ? userMessage.singleText() : "");
        });
    }

    public interface Greeter {

        @dev.langchain4j.service.UserMessage("Greet {{name}}")
        @Agent(outputKey = "greeting")
        String greet(@MemoryId String memoryId, @V("name") String name);

        @ChatMemoryProviderSupplier
        static ChatMemory chatMemory(Object memoryId) {
            return GREETER_STORE.memoryOf(memoryId);
        }
    }

    public interface Replier {

        @dev.langchain4j.service.UserMessage("Reply to {{greeting}}")
        @Agent(outputKey = "reply")
        String reply(@MemoryId String memoryId, @V("greeting") String greeting);

        @ChatMemoryProviderSupplier
        static ChatMemory chatMemory(Object memoryId) {
            return REPLIER_STORE.memoryOf(memoryId);
        }
    }

    /** Depends on the scope: it summarizes the context of the greeter, so an agent is created for each scope. */
    public interface Summarizer {

        @dev.langchain4j.service.UserMessage("Summarize {{reply}}")
        @Agent(outputKey = "summary", summarizedContext = "greet")
        String summarize(@MemoryId String memoryId, @V("reply") String reply);

        @ChatMemoryProviderSupplier
        static ChatMemory chatMemory(Object memoryId) {
            return SUMMARIZER_STORE.memoryOf(memoryId);
        }
    }

    public interface Supervisor {

        @SupervisorAgent(outputKey = "supervised", subAgents = Replier.class)
        String supervise(@V("request") String request);

        @ChatModelSupplier
        static ChatModel plannerModel() {
            return ChatModelMock.thatAlwaysResponds("""
                {"agentName": "done", "arguments": {"response": "nothing to do"}}
                """);
        }

        @ChatMemoryProviderSupplier
        static ChatMemory chatMemory(Object memoryId) {
            return SUPERVISOR_STORE.memoryOf(memoryId);
        }
    }

    public interface Assistant extends AgenticScopeAccess {

        @SequenceAgent(
                outputKey = "summary",
                subAgents = {Greeter.class, Replier.class, Summarizer.class, Supervisor.class})
        String assist(@MemoryId String memoryId, @V("name") String name, @V("request") String request);
    }

    @BeforeEach
    void clearStores() {
        STORES.forEach(store -> {
            store.deleted.clear();
            for (String memoryId : List.of("mario", "dmytro")) {
                store.deleteMessages(memoryId);
            }
            store.deleted.clear();
        });
    }

    @Test
    void evicting_an_agentic_scope_evicts_the_chat_memories_of_its_agents_for_that_memory_id() {
        Assistant assistant = AgenticServices.createAgenticSystem(Assistant.class, echoModel());

        assistant.assist("mario", "Mario", "do nothing");
        assistant.assist("dmytro", "Dmytro", "do nothing");

        for (RecordingStore store : STORES) {
            assertThat(store.getMessages("mario")).as(store.toString()).isNotEmpty();
            assertThat(store.getMessages("dmytro")).as(store.toString()).isNotEmpty();
        }
        assertThat(assistant.getAgenticScope("mario")).isNotNull();

        assertThat(assistant.evictAgenticScope("mario")).isTrue();

        assertThat(assistant.getAgenticScope("mario")).isNull();
        for (RecordingStore store : STORES) {
            assertThat(store.deleted).as(store.toString()).containsExactly("mario");
            assertThat(store.getMessages("mario")).as(store.toString()).isEmpty();
            assertThat(store.getMessages("dmytro")).as(store.toString()).isNotEmpty();
        }
        assertThat(assistant.getAgenticScope("dmytro")).isNotNull();
    }

    public interface SingleMemoryGreeter {

        @dev.langchain4j.service.UserMessage("Greet {{name}}")
        @Agent(outputKey = "greeting")
        String greet(@V("name") String name);

        @ChatMemorySupplier
        static ChatMemory chatMemory() {
            return MessageWindowChatMemory.withMaxMessages(10);
        }
    }

    public interface SingleMemoryAssistant extends AgenticScopeAccess {

        @SequenceAgent(outputKey = "greeting", subAgents = SingleMemoryGreeter.class)
        String assist(@MemoryId String memoryId, @V("name") String name);
    }

    @Test
    void evicting_an_agentic_scope_with_an_agent_having_a_single_chat_memory_evicts_the_scope() {
        SingleMemoryAssistant assistant =
                AgenticServices.createAgenticSystem(SingleMemoryAssistant.class, echoModel());
        assistant.assist("mario", "Mario");

        assertThat(assistant.evictAgenticScope("mario")).isTrue();

        assertThat(assistant.getAgenticScope("mario")).isNull();
    }

    @Test
    void evicting_an_unknown_agentic_scope_touches_no_chat_memory() {
        Assistant assistant = AgenticServices.createAgenticSystem(Assistant.class, echoModel());
        assistant.assist("mario", "Mario", "do nothing");

        assertThat(assistant.evictAgenticScope("nobody")).isFalse();

        for (RecordingStore store : STORES) {
            assertThat(store.deleted).isEmpty();
            assertThat(store.getMessages("mario")).isNotEmpty();
        }
    }
}
