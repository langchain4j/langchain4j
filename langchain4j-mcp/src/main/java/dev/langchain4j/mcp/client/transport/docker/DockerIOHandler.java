package dev.langchain4j.mcp.client.transport.docker;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.api.model.StreamType;
import dev.langchain4j.mcp.client.transport.McpOperationHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class DockerIOHandler implements Runnable {
    private static final Pattern NEWLINE_PATTERN = Pattern.compile("([^\\r\\n]+)[\\r\\n]+");
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Logger log = LoggerFactory.getLogger(DockerIOHandler.class);
    private static final Logger trafficLog = LoggerFactory.getLogger("MCP");

    private final DockerClient dockerClient;
    private final String containerId;
    private final boolean logEvents;
    private final McpOperationHandler messageHandler;

    public DockerIOHandler(DockerClient dockerClient, String containerId, McpOperationHandler messageHandler, boolean logEvents) {
        this.dockerClient = dockerClient;
        this.containerId = containerId;
        this.logEvents = logEvents;
        this.messageHandler = messageHandler;
    }

    @Override
    public void run() {
        dockerClient.logContainerCmd(containerId)
                .withFollowStream(true)
                .withStdErr(true)
                .withStdOut(true)
                .exec(new ResultCallback.Adapter<Frame>() {
                    private final Map<StreamType, StringBuilder> logBuffers = new HashMap<>();

                    @Override
                    public void onNext(Frame frame) {
                        String frameStr = new String(frame.getPayload());
                        log.debug("Received frame: {} => {}", frame.getStreamType(), frameStr);

                        Matcher newLineMatcher = NEWLINE_PATTERN.matcher(frameStr);
                        logBuffers.computeIfAbsent(frame.getStreamType(), streamType -> new StringBuilder());

                        int lastIndex = 0;
                        while (newLineMatcher.find()) {
                            String fragment = newLineMatcher.group(0);
                            logBuffers.get(frame.getStreamType()).append(fragment);

                            StringBuilder logBuffer = logBuffers.get(frame.getStreamType());
                            if (frame.getStreamType() == StreamType.STDERR) {
                                throw new RuntimeException(logBuffer.toString());
                            } else if (frame.getStreamType() == StreamType.STDOUT) {
                                this.send(logBuffer.toString());
                            }

                            logBuffer.setLength(0);

                            lastIndex = newLineMatcher.end();
                        }

                        if (lastIndex < frameStr.length()) {
                            logBuffers.get(frame.getStreamType()).append(frameStr.substring(lastIndex));
                        }
                    }

                    private void send(String line) {
                        if (line !=null && !line.isBlank()) {
                            if (logEvents) {
                                trafficLog.debug("< {}", line);
                            }

                            try {
                                messageHandler.handle(OBJECT_MAPPER.readTree(line));
                            } catch (JsonProcessingException e) {
                                throw new RuntimeException(e);
                            }
                        }
                    }

                    @Override
                    public void onComplete() {
                        // Still flush the last line even if there is no newline at the end
                        logBuffers.entrySet().stream().filter(entry -> !entry.getValue().isEmpty()).forEach(entry -> {
                            String log = entry.getValue().toString();
                            this.send(log);
                        });
                    }
                });
    }
}
