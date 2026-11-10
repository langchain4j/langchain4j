package dev.langchain4j.agentic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.agentic.declarative.AgentListenerSupplier;
import dev.langchain4j.agentic.declarative.ChatMemoryProviderSupplier;
import dev.langchain4j.agentic.declarative.ChatMemorySupplier;
import dev.langchain4j.agentic.declarative.ChatModelSupplier;
import dev.langchain4j.agentic.declarative.SequenceAgent;
import dev.langchain4j.agentic.declarative.SupervisorAgent;
import dev.langchain4j.agentic.internal.AgentExecutor;
import dev.langchain4j.agentic.observability.AgentListener;
import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.agentic.scope.AgenticScopeAccess;
import dev.langchain4j.agentic.scope.AgenticScopeKey;
import dev.langchain4j.agentic.scope.AgenticScopePersister;
import dev.langchain4j.agentic.scope.AgenticScopeStore;
import dev.langchain4j.agentic.scope.DefaultAgenticScope;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.test.mock.ChatModelMock;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.V;
import dev.langchain4j.service.memory.ChatMemoryAccess;
import dev.langchain4j.service.memory.ChatMemoryService;
import dev.langchain4j.store.memory.chat.InMemoryChatMemoryStore;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests that evicting an {@link AgenticScope} also evicts the chat memory objects that the agents of the agentic
 * system hold for its memory id, keeping their messages in the stores, and that deleting it clears them too: the AI
 * agents of the agent tree, the AI agents created for the scope only, because their configuration depends on it, and
 * the planner agent of a supervisor.
 */
class AgenticScopeEvictionTest {

    /** A chat memory store that also records the memory ids it was asked to delete, and can refuse to. */
    static class RecordingStore extends InMemoryChatMemoryStore {
        final List<Object> deleted = new CopyOnWriteArrayList<>();
        volatile boolean failing;

        @Override
        public void deleteMessages(Object memoryId) {
            if (failing) {
                throw new IllegalStateException("store down");
            }
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

    /** The number of messages of the greeter for a destroyed scope, as seen by the listener when it is destroyed. */
    static final Map<Object, Integer> MESSAGES_AT_DESTROY = new ConcurrentHashMap<>();

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

        @AgentListenerSupplier
        static AgentListener listener() {
            return new AgentListener() {
                @Override
                public void beforeAgenticScopeDestroyed(AgenticScope agenticScope) {
                    Object memoryId = agenticScope.memoryId();
                    MESSAGES_AT_DESTROY.put(memoryId, GREETER_STORE.getMessages(memoryId).size());
                }
            };
        }
    }

    /** The AI agent with the given name among the subagents of the given agentic system. */
    static ChatMemoryAccess aiAgent(Object agenticSystem, String name) {
        return ((AgentInstance) agenticSystem).subagents().stream()
                .filter(agent -> agent.name().equals(name))
                .map(agent -> (ChatMemoryAccess) ((AgentExecutor) agent).agent())
                .findFirst()
                .orElseThrow();
    }

    @BeforeEach
    void clearStores() {
        STORES.forEach(store -> {
            store.failing = false;
            for (String memoryId : List.of("mario", "dmytro")) {
                store.deleteMessages(memoryId);
            }
            store.deleted.clear();
        });
        MESSAGES_AT_DESTROY.clear();
    }

    @AfterEach
    void resetPersister() {
        AgenticScopePersister.setStore(null);
    }

    @Test
    void evicting_an_agentic_scope_evicts_the_chat_memory_objects_of_its_agents_and_keeps_their_messages() {
        Assistant assistant = AgenticServices.createAgenticSystem(Assistant.class, echoModel());
        assistant.assist("mario", "Mario", "do nothing");
        assistant.assist("dmytro", "Dmytro", "do nothing");
        ChatMemoryAccess greeter = aiAgent(assistant, "greet");
        ChatMemoryAccess replier = aiAgent(assistant, "reply");
        assertThat(greeter.getChatMemory("mario")).isNotNull();
        assertThat(replier.getChatMemory("mario")).isNotNull();

        assertThat(assistant.evictAgenticScope("mario")).isTrue();

        assertThat(assistant.getAgenticScope("mario")).isNull();
        assertThat(greeter.getChatMemory("mario")).isNull();
        assertThat(replier.getChatMemory("mario")).isNull();
        assertThat(greeter.getChatMemory("dmytro")).isNotNull();
        for (RecordingStore store : STORES) {
            assertThat(store.deleted).as(store.toString()).isEmpty();
            assertThat(store.getMessages("mario")).as(store.toString()).isNotEmpty();
        }
        assertThat(assistant.getAgenticScope("dmytro")).isNotNull();
    }

    @Test
    void deleting_an_agentic_scope_deletes_the_chat_memories_of_its_agents_for_that_memory_id() {
        Assistant assistant = AgenticServices.createAgenticSystem(Assistant.class, echoModel());
        assistant.assist("mario", "Mario", "do nothing");
        assistant.assist("dmytro", "Dmytro", "do nothing");
        for (RecordingStore store : STORES) {
            assertThat(store.getMessages("mario")).as(store.toString()).isNotEmpty();
            assertThat(store.getMessages("dmytro")).as(store.toString()).isNotEmpty();
        }

        assertThat(assistant.deleteAgenticScope("mario")).isTrue();

        assertThat(assistant.getAgenticScope("mario")).isNull();
        assertThat(aiAgent(assistant, "greet").getChatMemory("mario")).isNull();
        for (RecordingStore store : STORES) {
            assertThat(store.deleted).as(store.toString()).containsExactly("mario");
            assertThat(store.getMessages("mario")).as(store.toString()).isEmpty();
            assertThat(store.getMessages("dmytro")).as(store.toString()).isNotEmpty();
        }
        assertThat(assistant.getAgenticScope("dmytro")).isNotNull();
        // the listener was notified of the destruction of the scope while the chat memories were still intact
        assertThat(MESSAGES_AT_DESTROY.get("mario")).isPositive();
    }

    @Test
    void the_scope_is_deleted_even_when_the_store_of_a_chat_memory_fails_and_a_retry_deletes_the_rest() {
        Assistant assistant = AgenticServices.createAgenticSystem(Assistant.class, echoModel());
        assistant.assist("mario", "Mario", "do nothing");
        REPLIER_STORE.failing = true;

        assertThatThrownBy(() -> assistant.deleteAgenticScope("mario"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("store down");

        assertThat(assistant.getAgenticScope("mario")).isNull();
        for (RecordingStore store : List.of(GREETER_STORE, SUMMARIZER_STORE, SUPERVISOR_STORE)) {
            assertThat(store.deleted).as(store.toString()).containsExactly("mario");
        }
        // the chat memory that could not be cleared stays cached, so that a later call deletes it
        assertThat(REPLIER_STORE.getMessages("mario")).isNotEmpty();
        assertThat(aiAgent(assistant, "reply").getChatMemory("mario")).isNotNull();

        REPLIER_STORE.failing = false;
        assertThat(assistant.deleteAgenticScope("mario")).isFalse();

        assertThat(REPLIER_STORE.deleted).containsExactly("mario");
        assertThat(REPLIER_STORE.getMessages("mario")).isEmpty();
        assertThat(aiAgent(assistant, "reply").getChatMemory("mario")).isNull();
    }

    /** A scope store whose saved scopes cannot be read back, as after an incompatible change of their classes. */
    static class UnreadableStore implements AgenticScopeStore {
        final Map<AgenticScopeKey, DefaultAgenticScope> scopes = new ConcurrentHashMap<>();
        final List<AgenticScopeKey> deleted = new CopyOnWriteArrayList<>();

        @Override
        public boolean save(AgenticScopeKey key, DefaultAgenticScope agenticScope) {
            scopes.put(key, agenticScope);
            return true;
        }

        @Override
        public Optional<DefaultAgenticScope> load(AgenticScopeKey key) {
            if (scopes.containsKey(key)) {
                throw new IllegalStateException("cannot read the scope " + key);
            }
            return Optional.empty();
        }

        @Override
        public boolean delete(AgenticScopeKey key) {
            deleted.add(key);
            return scopes.remove(key) != null;
        }

        @Override
        public Set<AgenticScopeKey> getAllKeys() {
            return scopes.keySet();
        }
    }

    @Test
    void evicting_a_persisted_agentic_scope_that_is_not_in_memory_does_not_load_it() {
        UnreadableStore store = new UnreadableStore();
        AgenticScopePersister.setStore(store);
        Assistant assistant = AgenticServices.createAgenticSystem(Assistant.class, echoModel());
        assistant.assist("mario", "Mario", "do nothing");
        assertThat(store.scopes).hasSize(1);
        // another instance of the agentic system, as after a restart: the scope is only in the store
        Assistant restarted = AgenticServices.createAgenticSystem(Assistant.class, echoModel());

        assertThat(restarted.evictAgenticScope("mario")).isTrue();

        assertThat(store.scopes).isEmpty();
        assertThat(store.deleted).extracting(AgenticScopeKey::memoryId).containsExactly("mario");
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
    void evicting_the_single_chat_memory_of_an_agent_fails_like_for_an_ai_service() {
        SingleMemoryGreeter greeter = AgenticServices.agentBuilder(SingleMemoryGreeter.class)
                .chatModel(echoModel())
                .build();

        assertThatThrownBy(() -> ((ChatMemoryAccess) greeter).evictChatMemory("mario"))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("single chat memory");
    }

    @Test
    void deleting_an_agentic_scope_leaves_the_single_chat_memory_of_an_agent_alone() {
        SingleMemoryAssistant assistant =
                AgenticServices.createAgenticSystem(SingleMemoryAssistant.class, echoModel());
        assistant.assist("mario", "Mario");

        assertThat(assistant.deleteAgenticScope("mario")).isTrue();

        assertThat(assistant.getAgenticScope("mario")).isNull();
        // the chat memory is shared by all the memory ids, so it is not the one of the deleted scope
        ChatMemory shared = aiAgent(assistant, "greet").getChatMemory(ChatMemoryService.DEFAULT);
        assertThat(shared.messages()).isNotEmpty();
    }

    @Test
    void evicting_an_unknown_agentic_scope_touches_no_chat_memory() {
        Assistant assistant = AgenticServices.createAgenticSystem(Assistant.class, echoModel());
        assistant.assist("mario", "Mario", "do nothing");

        assertThat(assistant.evictAgenticScope("nobody")).isFalse();
        assertThat(assistant.deleteAgenticScope("nobody")).isFalse();

        for (RecordingStore store : STORES) {
            assertThat(store.deleted).isEmpty();
            assertThat(store.getMessages("mario")).isNotEmpty();
        }
    }
}
