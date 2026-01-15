package dev.langchain4j.mcp.client.transport.docker;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.command.PullImageResultCallback;
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.core.NameParser;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import com.github.dockerjava.transport.DockerHttpClient;
import dev.langchain4j.mcp.protocol.McpJsonRpcMessage;
import dev.langchain4j.mcp.protocol.McpInitializationNotification;
import dev.langchain4j.mcp.protocol.McpInitializeRequest;
import dev.langchain4j.mcp.client.transport.McpOperationHandler;
import dev.langchain4j.mcp.client.transport.McpTransport;
import dev.langchain4j.mcp.client.transport.websocket.WebSocketMcpTransport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class DockerMcpTransport  implements McpTransport {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Logger log = LoggerFactory.getLogger(DockerMcpTransport.class);

    private final String dockerHost;
    private final String dockerConfig;
    private final String dockerContext;
    private final String dockerCertPath;
    private final Boolean dockerTlsVerify;
    private final String registryEmail;
    private final String registryUsername;
    private final String registryPassword;
    private final String registryUrl;
    private final String apiVersion;

    private final String image;
    private final List<String> command;
    private final Map<String, String> environment;
    private final boolean logEvents;
    private final Logger logger;
    private final List<String> binds;
    private final Duration attachTimeout;

    private volatile McpOperationHandler messageHandler;
    private volatile String containerId;
    private volatile DockerClient dockerClient;

    public DockerMcpTransport(DockerMcpTransport.Builder builder) {
        this.dockerHost = builder.dockerHost;
        this.dockerConfig = builder.dockerConfig;
        this.dockerContext = builder.dockerContext;
        this.dockerCertPath = builder.dockerCertPath;
        this.dockerTlsVerify = builder.dockerTlsVerify;
        this.registryEmail = builder.registryEmail;
        this.registryUsername = builder.registryUsername;
        this.registryPassword = builder.registryPassword;
        this.registryUrl = builder.registryUrl;
        this.apiVersion = builder.apiVersion;

        this.image = builder.image;
        this.command = builder.command;
        this.environment = builder.environment;
        this.logEvents = builder.logEvents;
        this.logger = builder.logger;
        this.binds = builder.binds;
        this.attachTimeout = builder.attachTimeout;
    }

    @Override
    public void start(McpOperationHandler messageHandler) {
        this.messageHandler = messageHandler;
        log.debug("Starting docker container with host: {}, image: {}, command: {}", dockerHost, image, command);
        DockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder()
                .withDockerHost(dockerHost)
                .withDockerConfig(dockerConfig)
                .withDockerContext(dockerContext)
                .withDockerCertPath(dockerCertPath)
                .withDockerTlsVerify(dockerTlsVerify)
                .withRegistryEmail(registryEmail)
                .withRegistryUsername(registryUsername)
                .withRegistryPassword(registryPassword)
                .withRegistryUrl(registryUrl)
                .withApiVersion(apiVersion)
                .build();
        DockerHttpClient httpClient = new ApacheDockerHttpClient.Builder().dockerHost(config.getDockerHost()).build();
        this.dockerClient = DockerClientImpl.getInstance(config, httpClient);

        var imageNameWithoutTag = getImageNameWithoutTag(image);
        var parsedTagFromImage = NameParser.parseRepositoryTag(image);
        // pullImageCmd without the tag (= repository) to avoid being redundant with withTag below
        // and prevent errors with Podman trying to pull "image:tag:tag"
        try (var pull = dockerClient.pullImageCmd(imageNameWithoutTag)) {
            var tag = !parsedTagFromImage.tag.isEmpty() ? parsedTagFromImage.tag : "latest";
            var repository = pull.getRepository().contains(":") ? pull.getRepository().split(":")[0] : pull.getRepository();
            pull.withTag(tag).exec(new PullImageResultCallback()).awaitCompletion();
            log.trace("Image pulled [{}:{}]", repository, tag);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        HostConfig hostConfig = new HostConfig()
                .withBinds(binds.stream().map(bind -> Bind.parse(bind)).toList());
        CreateContainerCmd container = dockerClient.createContainerCmd(image)
                .withTty(false)
                .withAttachStdin(true)
                .withAttachStdout(true)
                .withAttachStderr(true)
                .withStdinOpen(true)
                .withCmd(command.toArray(new String[0]))
                .withEnv(environment.entrySet().stream().map(r -> r.getKey() + "=" + r.getValue()).toList())
                .withHostConfig(hostConfig);
        try {
            CreateContainerResponse exec = container.exec();
            this.containerId = exec.getId();

            dockerClient.startContainerCmd(containerId).exec();
            dockerClient.waitContainerCmd(containerId).start().awaitStarted();
            log.debug("ID of the started container: {}", exec.getId());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private String getImageNameWithoutTag(String fullImageName) {
        if (fullImageName == null || fullImageName.isEmpty()) {
            return fullImageName;
        }

        int lastColonIndex = fullImageName.lastIndexOf(':');
        int firstSlashIndex = fullImageName.indexOf('/');
        if (lastColonIndex > -1 && (firstSlashIndex == -1 || lastColonIndex > firstSlashIndex)) {
            return fullImageName.substring(0, lastColonIndex);
        } else {
            return fullImageName; // No tag found or the colon is part of the registry host
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
    public CompletableFuture<JsonNode> executeOperationWithResponse(McpJsonRpcMessage operation) {
        try {
            String requestString = OBJECT_MAPPER.writeValueAsString(operation);
            return execute(requestString, operation.getId());
        } catch (JsonProcessingException e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    @Override
    public void executeOperationWithoutResponse(McpJsonRpcMessage operation) {
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
        log.debug("Killing container {}", containerId);
        dockerClient.killContainerCmd(containerId).exec();
        log.debug("Deleting container {}", containerId);
        dockerClient.removeContainerCmd(containerId).exec();
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
                    .exec(new DockerResultCallback(logEvents, logger, messageHandler));
            callback.awaitStarted(attachTimeout.toMillis(), TimeUnit.MILLISECONDS);

            out.write((request + "\n").getBytes());
            out.flush();

            if (id != null) {
                // if there is an ID, wait for a frame to be sent back from the container
                callback.awaitCompletion();
            } else {
                // if we didn't wait for a frame, we still wait a little to be sure the container receive and process the request
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

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String dockerHost;
        private String dockerConfig;
        private String dockerContext;
        private String dockerCertPath;
        private Boolean dockerTlsVerify;
        private String registryEmail;
        private String registryUsername;
        private String registryPassword;
        private String registryUrl;
        private String apiVersion;

        private String image;
        private List<String> command;
        private Map<String, String> environment;
        private boolean logEvents;
        private Logger logger;
        private List<String> binds;
        private Duration attachTimeout;

        public DockerMcpTransport.Builder dockerHost(String dockerHost) {
            this.dockerHost = dockerHost;
            return this;
        }

        public DockerMcpTransport.Builder dockerConfig(String dockerConfig) {
            this.dockerConfig = dockerConfig;
            return this;
        }

        public DockerMcpTransport.Builder dockerContext(String dockerContext) {
            this.dockerContext = dockerContext;
            return this;
        }

        public DockerMcpTransport.Builder dockerCertPath(String dockerCertPath) {
            this.dockerCertPath = dockerCertPath;
            return this;
        }

        public DockerMcpTransport.Builder dockerTslVerify(Boolean dockerTlsVerify) {
            this.dockerTlsVerify = dockerTlsVerify;
            return this;
        }

        public DockerMcpTransport.Builder registryEmail(String registryEmail) {
            this.registryEmail = registryEmail;
            return this;
        }

        public DockerMcpTransport.Builder registryUsername(String registryUsername) {
            this.registryUsername = registryUsername;
            return this;
        }

        public DockerMcpTransport.Builder registryPassword(String registryPassword) {
            this.registryPassword = registryPassword;
            return this;
        }

        public DockerMcpTransport.Builder registryUrl(String registryUrl) {
            this.registryUrl = registryUrl;
            return this;
        }

        public DockerMcpTransport.Builder apiVersion(String apiVersion) {
            this.apiVersion = apiVersion;
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

        /**
         * @param logger an alternate {@link Logger} to be used instead of the default one provided by Langchain4J for traffic logging.
         * @return {@code this}.
         */
        public DockerMcpTransport.Builder logger(Logger logger) {
            this.logger = logger;
            return this;
        }

        public DockerMcpTransport.Builder binds(List<String> binds) {
            this.binds = binds;
            return this;
        }

        public DockerMcpTransport.Builder attachTimeout(Duration attachTimeout) {
            this.attachTimeout = attachTimeout;
            return this;
        }

        public DockerMcpTransport build() {
            if (dockerHost == null || dockerHost.isEmpty()) {
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
            if (binds == null) {
                binds = List.of();
            }
            if (attachTimeout == null) {
                attachTimeout = Duration.ofSeconds(30);
            }
            return new DockerMcpTransport(this);
        }
    }
}
