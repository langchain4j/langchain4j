package dev.langchain4j.mcp.client.transport.docker;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import com.github.dockerjava.transport.DockerHttpClient;
import dev.langchain4j.mcp.client.protocol.McpClientMessage;
import dev.langchain4j.mcp.client.protocol.McpInitializationNotification;
import dev.langchain4j.mcp.client.protocol.McpInitializeRequest;
import dev.langchain4j.mcp.client.transport.McpOperationHandler;
import dev.langchain4j.mcp.client.transport.McpTransport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class DockerMcpTransport  implements McpTransport {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Logger log = LoggerFactory.getLogger(DockerMcpTransport.class);

    private final String image;
    private final String host;
    private final List<String> command;
    private final Map<String, String> environment;
    private final boolean logEvents;

    private volatile McpOperationHandler messageHandler;
    private volatile String containerId;
    private volatile DockerClient dockerClient;

    public DockerMcpTransport(DockerMcpTransport.Builder builder) {
        this.image = builder.image;
        this.host = builder.host;
        this.command = builder.command;
        this.environment = builder.environment;
        this.logEvents = builder.logEvents;
    }

    @Override
    public void start(McpOperationHandler messageHandler) {
        this.messageHandler = messageHandler;
        log.debug("Starting docker container with host: {}, image: {}, command: {}", host, image, command);
        DockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder().withDockerHost(host).build();
        DockerHttpClient httpClient = new ApacheDockerHttpClient.Builder().dockerHost(config.getDockerHost()).build();
        this.dockerClient = DockerClientImpl.getInstance(config, httpClient);
        CreateContainerCmd container = dockerClient.createContainerCmd(image)
                .withTty(false)
                .withAttachStdin(true)
                .withAttachStdout(true)
                .withAttachStderr(true)
                .withStdinOpen(true)
                .withCmd(command.toArray(new String[0]))
                .withEnv(environment.entrySet().stream().map(r -> r.getKey() + "=" + r.getValue()).toList());
        try {
            CreateContainerResponse exec = container.exec();
            this.containerId = exec.getId();

            // FIXME: where should we obtain the thread?
            new Thread(new DockerIOHandler(dockerClient, containerId, messageHandler, logEvents)).start();

            dockerClient.startContainerCmd(containerId).exec();
            dockerClient.waitContainerCmd(containerId).start().awaitStarted();
            log.debug("ID of the started container: {}", exec.getId());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public CompletableFuture<JsonNode> initialize(McpInitializeRequest operation) {
        try {
            String requestString = OBJECT_MAPPER.writeValueAsString(operation);
            String initializationNotification = OBJECT_MAPPER.writeValueAsString(new McpInitializationNotification());
            final CompletableFuture<JsonNode> execute = execute(requestString, operation.getId());
            return execute.thenCompose(originalResponse -> {
                final CompletableFuture<JsonNode> execute1 = execute(initializationNotification, null);
                return execute1.thenCompose(nullNode -> CompletableFuture.completedFuture(originalResponse));
            });
        } catch (JsonProcessingException e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    @Override
    public CompletableFuture<JsonNode> executeOperationWithResponse(McpClientMessage operation) {
        try {
            String requestString = OBJECT_MAPPER.writeValueAsString(operation);
            return execute(requestString, operation.getId());
        } catch (JsonProcessingException e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    @Override
    public void executeOperationWithoutResponse(McpClientMessage operation) {
        try {
            String requestString = OBJECT_MAPPER.writeValueAsString(operation);
            execute(requestString, null);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void checkHealth() {
        final InspectContainerResponse inspectContainer = this.dockerClient.inspectContainerCmd(containerId).exec();
        if (inspectContainer == null || Boolean.FALSE.equals(inspectContainer.getState().getRunning())) {
            throw new IllegalStateException("Container is not alive");
        }
    }

    @Override
    public void onFailure(Runnable actionOnFailure) {
        // ignore, for docker transport, we currently don't do reconnection attempts
    }

    @Override
    public void close() throws IOException {
        // TODO close then kill after a timeout the container?
        dockerClient.stopContainerCmd(containerId).exec();
    }

    private CompletableFuture<JsonNode> execute(String request, Long id) {
        CompletableFuture<JsonNode> future = new CompletableFuture<>();
        if (id != null) {
            messageHandler.startOperation(id, future);
        }

        try (
                PipedOutputStream out = new PipedOutputStream();
                PipedInputStream in = new PipedInputStream(out)
        ) {
            DockerResultCallback callback = dockerClient.attachContainerCmd(containerId)
                    .withStdOut(true)
                    .withStdErr(true)
                    .withFollowStream(true)
                    .withStdIn(in)
                    .exec(new DockerResultCallback());
            callback.awaitStarted(); // TODO timeout?

            log.debug("Request: {}", request);
            out.write((request + "\n").getBytes());
            out.flush();

            if (id != null) {
                // if there is an ID, wait for a frame to be sent back from the container
                callback.awaitCompletion();
            } else {
                // if we didn't wait for a frame, we still wait a little to be sure the container receive and process the request
                // TODO find a better way than a fixed sleep
                Thread.sleep(100);
            }
            callback.close();
            // For messages with null ID, we don't wait for a corresponding response
            if (id == null) {
                future.complete(null);
            }
        } catch (Exception e) {
            future.completeExceptionally(e);
        }

        return future;
    }

    public static class Builder {
        private String image;
        private String host;
        private List<String> command;
        private Map<String, String> environment;
        private boolean logEvents;

        public DockerMcpTransport.Builder host(String host) {
            this.host = host;
            return this;
        }

        public DockerMcpTransport.Builder image(String image) {
            this.image = image;
            return this;
        }

        public DockerMcpTransport.Builder command(List<String> command) {
            this.command = command;
            return this;
        }

        public DockerMcpTransport.Builder environment(Map<String, String> environment) {
            this.environment = environment;
            return this;
        }

        public DockerMcpTransport.Builder logEvents(boolean logEvents) {
            this.logEvents = logEvents;
            return this;
        }

        public DockerMcpTransport build() {
            if (host == null || host.isEmpty()) {
                throw new IllegalArgumentException("Missing host");
            }
            if (image == null || image.isEmpty()) {
                throw new IllegalArgumentException("Missing image");
            }
            if (command == null) {
                command = List.of();
            }
            if (environment == null) {
                environment = Map.of();
            }
            return new DockerMcpTransport(this);
        }
    }
}
