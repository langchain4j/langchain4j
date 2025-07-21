package dev.langchain4j.internal;

import static dev.langchain4j.internal.Utils.isNotNullOrBlank;
import static dev.langchain4j.internal.Utils.isNotNullOrEmpty;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import dev.langchain4j.Internal;
import dev.langchain4j.model.chat.response.CompleteToolCall;
import dev.langchain4j.agent.tool.ToolExecutionRequest;

/**
 * Internal helper that helps to build partial and complete tool calls during streaming.
 * <p>
 * Volatile fields, StringBuffer, and ConcurrentLinkedQueue are used to ensure safe access,
 * as incoming SSE events may be processed by different threads depending on the underlying HTTP client implementation.
 *
 * @since 1.2.0
 */
@Internal
public class ToolCallBuilder {

    private volatile int index;

    private volatile String id;
    private volatile String name;
    private final StringBuffer arguments = new StringBuffer();

    private final Queue<ToolExecutionRequest> toolExecutionRequests = new ConcurrentLinkedQueue<>();

    public ToolCallBuilder() {
        this(0);
    }

    public ToolCallBuilder(int index) {
        this.index = index;
    }

    public int index() {
        return index;
    }

    public void updateIndex(Integer index) {
        if (index != null) {
            this.index = index;
        }
    }

    public String id() {
        return id;
    }

    public String updateId(String id) {
        if (isNotNullOrBlank(id)) {
            this.id = id;
        }
        return this.id;
    }

    public String name() {
        return name;
    }

    public String updateName(String name) {
        if (isNotNullOrBlank(name)) {
            this.name = name;
        }
        return this.name;
    }

    public void appendArguments(String partialArguments) {
        if (isNotNullOrEmpty(partialArguments)) {
            arguments.append(partialArguments);
        }
    }

    public CompleteToolCall buildAndReset() {
        String arguments = this.arguments.toString();
        if (arguments.isEmpty()) {
            arguments = "{}";
        }

        ToolExecutionRequest toolExecutionRequest = ToolExecutionRequest.builder()
                .id(this.id)
                .name(this.name)
                .arguments(arguments)
                .build();
        toolExecutionRequests.add(toolExecutionRequest);

        reset();

        return new CompleteToolCall(this.index, toolExecutionRequest);
    }

    private void reset() {
        id = null;
        name = null;
        arguments.setLength(0);
    }

    public boolean hasRequests() {
        return !toolExecutionRequests.isEmpty() || name != null;
    }

    public List<ToolExecutionRequest> allRequests() {
        return new ArrayList<>(toolExecutionRequests);
    }
}
