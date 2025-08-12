package dev.langchain4j.store.memory.chat.coherence;

import static dev.langchain4j.internal.Utils.isNullOrBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotEmpty;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import com.tangosol.net.Coherence;
import com.tangosol.net.NamedMap;
import com.tangosol.net.Session;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageDeserializer;
import dev.langchain4j.data.message.ChatMessageSerializer;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import java.util.ArrayList;
import java.util.List;

/**
 * A {@link ChatMemoryStore} backed by an Oracle Coherence named map.
 * <p>
 * The {@link CoherenceChatMemoryStore} supports memory identifiers of any type
 * that is a valid Coherence {@link NamedMap} key. The key type must properly
 * implement {@code equals()} and {@code hashCode()} and be serializable by
 * the configured Coherence serializer.
 */
public class CoherenceChatMemoryStore implements ChatMemoryStore {

    /**
     * The default {@link NamedMap} name.
     */
    public static final String DEFAULT_MAP_NAME = "chatMemory";

    /**
     * The {@link NamedMap} used to store the chat messages.
     */
    protected final NamedMap<Object, String> chatMemory;

    /**
     * Create a {@link CoherenceChatMemoryStore}.
     * <p>
     * This method is protected, instances of {@link CoherenceChatMemoryStore}
     * are created using the builder.
     *
     * @param chatMemory  the {@link NamedMap} to store the chat history.
     */
    protected CoherenceChatMemoryStore(NamedMap<Object, String> chatMemory) {
        this.chatMemory = chatMemory;
    }

    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        validateId(memoryId);
        String json = chatMemory.get(memoryId);
        return json == null ? new ArrayList<>() : ChatMessageDeserializer.messagesFromJson(json);
    }

    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        validateId(memoryId);
        String json = ChatMessageSerializer.messagesToJson(ensureNotEmpty(messages, "messages"));
        chatMemory.put(memoryId, json);
    }

    @Override
    public void deleteMessages(Object memoryId) {
        validateId(memoryId);
        chatMemory.remove(memoryId);
    }

    private void validateId(Object memoryId) {
        ensureNotNull(memoryId, "memoryId");
    }

    /**
     * Create a default {@link CoherenceChatMemoryStore}.
     *
     * @return a default {@link CoherenceChatMemoryStore}
     */
    public static CoherenceChatMemoryStore create() {
        return builder().build();
    }

    /**
     * Create a {@link CoherenceChatMemoryStore} that uses the
     * specified Coherence {@link NamedMap} name.
     *
     * @param name  the name of the Coherence {@link NamedMap} used to store chat messages
     *
     * @return a {@link CoherenceChatMemoryStore}
     */
    public static CoherenceChatMemoryStore create(String name) {
        return builder().name(name).build();
    }

    /**
     * Create a {@link CoherenceChatMemoryStore} that uses the
     * specified Coherence {@link NamedMap} name.
     *
     * @param map  the {@link NamedMap} used to store chat messages
     *
     * @return a {@link CoherenceChatMemoryStore}
     */
    public static CoherenceChatMemoryStore create(NamedMap<Object, String> map) {
        return new CoherenceChatMemoryStore(map);
    }

    /**
     * Return a {@link Builder} to use to build a {@link CoherenceChatMemoryStore}.
     *
     * @return  a {@link Builder} to use to build a {@link CoherenceChatMemoryStore}
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * A builder to create {@link CoherenceChatMemoryStore} instances.
     */
    public static class Builder {
        /**
         * The name of the {@link NamedMap} to contain the serialized {@link ChatMessage chat messages}.
         */
        private String name = DEFAULT_MAP_NAME;

        /**
         * The name of the {@link Session} to use to obtain the {@link NamedMap}.
         */
        private String sessionName;

        /**
         * The {@link Session} to use to obtain the {@link NamedMap}.
         */
        private Session session;

        /**
         * Create a {@link Builder}.
         */
        protected Builder() {}

        /**
         * Set the name of the {@link NamedMap} that will hold the
         * serialized {@link ChatMessage chat messages}.
         *
         * @param name the name of the {@link NamedMap} to store serialized {@link ChatMessage chat messages}
         * @return this builder for fluent method calls
         */
        public Builder name(String name) {
            this.name = isNullOrBlank(name) ? DEFAULT_MAP_NAME : name;
            return this;
        }

        /**
         * Set the name of the {@link Session} to use to obtain the
         * document chunk {@link NamedMap}.
         *
         * @param sessionName  the session name
         *
         * @return this builder for fluent method calls
         */
        public Builder session(String sessionName) {
            this.sessionName = sessionName;
            this.session = null;
            return this;
        }

        /**
         * Set the {@link Session} to use to obtain the
         * document chunk {@link NamedMap}.
         *
         * @param session  the {@link Session} to use
         *
         * @return this builder for fluent method calls
         */
        public Builder session(Session session) {
            this.session = session;
            this.sessionName = null;
            return this;
        }

        /**
         * Creates a new instance of {@link CoherenceChatMemoryStore} using the current
         * configuration of this {@code Builder}.
         * <p>
         * If a {@link Session} has not been explicitly set via the builder, this method
         * will attempt to resolve it by {@code sessionName} using {@link Coherence#getSession(String)}.
         * If no {@code sessionName} is provided, the default session will be used via
         * {@link Coherence#getSession()}.
         * <p>
         * Once a {@link Session} is resolved, the configured {@link NamedMap} (or the default
         * {@link #DEFAULT_MAP_NAME} if none was set) is retrieved and used to create a new
         * {@link CoherenceChatMemoryStore} instance.
         *
         * @return a new instance of {@link CoherenceChatMemoryStore}
         */
        public CoherenceChatMemoryStore build() {
            Session session = this.session;
            if (session == null) {
                if (sessionName != null) {
                    session = Coherence.getInstance().getSession(sessionName);
                } else {
                    session = Coherence.getInstance().getSession();
                }
            }
            NamedMap<Object, String> map = session.getMap(name);
            return new CoherenceChatMemoryStore(map);
        }
    }
}
