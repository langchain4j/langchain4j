package dev.langchain4j.internal;

import static dev.langchain4j.internal.Utils.isNotNullOrBlank;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import dev.langchain4j.Internal;
import dev.langchain4j.agent.tool.ToolExecutionRequest;

@Internal
// TODO location
// TODO name
public class ToolExecutionRequestBuilder {

    private final AtomicInteger currentIndex = new AtomicInteger(0);
    // TODO are maps really needed?
    private final Map<Integer, String> indexToId = new ConcurrentHashMap<>();
    private final Map<Integer, String> indexToName = new ConcurrentHashMap<>();
    private final StringBuffer arguments = new StringBuffer();

    public int currentIndex() {
        return currentIndex.get();
    }

    public int updateCurrentIndex(Integer index) {
        if (index != null) {
            if (index != currentIndex.get()) {
                arguments.setLength(0);
            }
            currentIndex.set(index);
        }
        return currentIndex.get();
    }

    public String updateId(String id) {
        if (isNotNullOrBlank(id)) {
            indexToId.put(currentIndex.get(), id);
        }
        return indexToId.get(currentIndex.get());
    }

    public String updateName(String name) {
        if (isNotNullOrBlank(name)) {
            indexToName.put(currentIndex.get(), name);
        }
        return indexToName.get(currentIndex.get());
    }

    public void appendArguments(String partialArguments) {
        if (partialArguments != null) {
            arguments.append(partialArguments);
        }
    }

    public ToolExecutionRequest currentTool() {
        // TODO store it till complete response?
        return ToolExecutionRequest.builder()
                .id(indexToId.get(currentIndex.get()))
                .name(indexToName.get(currentIndex.get()))
                .arguments(arguments.toString())
                .build();
    }

    public boolean hasToolExecutionRequests() {
        return indexToName.size() > 0;
    }
}
