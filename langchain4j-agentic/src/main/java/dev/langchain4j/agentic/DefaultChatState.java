package dev.langchain4j.agentic;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.memory.ChatMemory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DefaultChatState implements ChatState {

    private final ChatMemory memory;
    private final Map<String, Object> stateMap;

    public DefaultChatState(ChatMemory memory) {
        this(memory, new HashMap<>());
    }

    public DefaultChatState(ChatMemory memory, Map<String, Object> stateMap) {
        this.memory = memory;
        this.stateMap = stateMap;
    }

    @Override
    public Object id() {
        return memory.id();
    }

    @Override
    public void add(final ChatMessage message) {
        memory.add(message);
    }

    @Override
    public List<ChatMessage> messages() {
        return memory.messages();
    }

    @Override
    public void clear() {
        memory.clear();
    }

    @Override
    public void writeState(String state, Object value) {
        stateMap.put(state, value);
    }

    @Override
    public boolean hasState(final String state) {
        return stateMap.containsKey(state);
    }

    @Override
    public Object readState(final String state) {
        return stateMap.get(state);
    }
}
