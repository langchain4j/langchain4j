package dev.langchain4j.mcp.client.transport.docker;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.api.model.StreamType;
import dev.langchain4j.mcp.client.transport.McpOperationHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import static dev.langchain4j.internal.Utils.getOrDefault;

class DockerResultCallback extends ResultCallback.Adapter<Frame> {
    private static final Logger LOG = LoggerFactory.getLogger(DockerResultCallback.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Logger DEFAULT_TRAFFIC_LOG = LoggerFactory.getLogger("MCP");
    private static final Pattern NEWLINE_PATTERN = Pattern.compile("([^\\r\\n]+)[\\r\\n]+");

    private final boolean logEvents;
    private final Logger trafficLog;
    private final McpOperationHandler messageHandler;

    private final CountDownLatch countDownLatch = new CountDownLatch(1);
    private final StringBuilder logAggregator = new StringBuilder();

    public DockerResultCallback(boolean logEvents, McpOperationHandler messageHandler) {
        this(logEvents, null, messageHandler);
    }

    public DockerResultCallback(boolean logEvents, Logger logger, McpOperationHandler messageHandler) {
        this.logEvents = logEvents;
        this.messageHandler = messageHandler;
        this.trafficLog = getOrDefault(logger, DEFAULT_TRAFFIC_LOG);
    }

    @Override
    public void onNext(Frame frame) {
        String frameStr = new String(frame.getPayload());
        if (frame.getStreamType() == StreamType.STDERR) {
            LOG.debug("[ERROR] {}", frameStr);
        } else if (frame.getStreamType() == StreamType.STDOUT) {
            this.send(frameStr);
        }
    }

    @Override
    public Adapter<Frame> awaitCompletion() throws InterruptedException {
        countDownLatch.await();
        return this;
    }

    @Override
    public boolean awaitCompletion(final long timeout, final TimeUnit timeUnit) throws InterruptedException {
        return countDownLatch.await(timeout, timeUnit);
    }

    private void send(String line) {
        if (line !=null && !line.isBlank()) {
            logAggregator.append(line);

            // we aggregate until we have a newline char at then end of the line
            if (NEWLINE_PATTERN.matcher(line).matches()) {
                innerSend();
            }
        } else {
            // an empty line with a non-empty aggregator means we have something to send so we send it
            if (!logAggregator.isEmpty()) {
                innerSend();
            }
        }
    }

    private void innerSend() {
        String message = logAggregator.toString();
        if (logEvents) {
            trafficLog.debug("< {}", message);
        }

        try {
            messageHandler.handle(OBJECT_MAPPER.readTree(message));
            logAggregator.setLength(0);
            countDownLatch.countDown();
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }
}
