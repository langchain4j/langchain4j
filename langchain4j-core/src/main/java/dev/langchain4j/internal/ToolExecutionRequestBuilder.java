package dev.langchain4j.internal;

import static dev.langchain4j.internal.Utils.isNotNullOrBlank;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import dev.langchain4j.Internal;
import dev.langchain4j.agent.tool.ToolExecutionRequest;

@Internal
// TODO location
// TODO name
public class ToolExecutionRequestBuilder {

    private final AtomicInteger currentIndex;
    // TODO are maps really needed?
    private final Map<Integer, String> indexToId = new ConcurrentHashMap<>();
    private final Map<Integer, String> indexToName = new ConcurrentHashMap<>();
    private final StringBuffer arguments = new StringBuffer();
    private final List<ToolExecutionRequest> toolExecutionRequests = new ArrayList<>();

    public ToolExecutionRequestBuilder() {
        this(0);
    }

    public ToolExecutionRequestBuilder(int currentIndex) {
        this.currentIndex = new AtomicInteger(currentIndex);
    }

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

    public ToolExecutionRequest buildCurrentTool() {
        // TODO store it till complete response?
        ToolExecutionRequest toolExecutionRequest = ToolExecutionRequest.builder()
                .id(indexToId.get(currentIndex.get()))
                .name(indexToName.get(currentIndex.get()))
                .arguments(arguments.toString())
                .build();
        toolExecutionRequests.add(toolExecutionRequest); // TODO method name, rethink
        return toolExecutionRequest;
    }

    public List<ToolExecutionRequest> allToolExecutionRequests() {
        return toolExecutionRequests;
    }

    public boolean hasToolExecutionRequests() {
        return indexToName.size() > 0;
    }
}
