package dev.langchain4j.mcp.client.transport.docker;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.tinylog.core.LogEntry;
import org.tinylog.core.LogEntryValue;
import org.tinylog.writers.Writer;

public class DockerCallbackTestWriter implements Writer {

    static final Queue<LogEntry> entries = new ConcurrentLinkedQueue<>();

    public DockerCallbackTestWriter(Map<String, String> properties) {}

    @Override
    public Collection<LogEntryValue> getRequiredLogEntryValues() {
        return List.of(LogEntryValue.CLASS, LogEntryValue.LEVEL, LogEntryValue.MESSAGE);
    }

    @Override
    public void write(LogEntry logEntry) {
        if (DockerResultCallback.class.getName().equals(logEntry.getClassName())) {
            entries.add(logEntry);
        }
    }

    @Override
    public void flush() {}

    @Override
    public void close() {}
}
