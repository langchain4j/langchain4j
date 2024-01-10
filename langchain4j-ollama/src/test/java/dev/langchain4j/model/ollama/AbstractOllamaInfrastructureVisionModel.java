package dev.langchain4j.model.ollama;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.model.Image;
import lombok.extern.slf4j.Slf4j;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.LazyFuture;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

@Slf4j
public class AbstractOllamaInfrastructureVisionModel {

    private static final String OLLAMA_IMAGE = "ollama/ollama:latest";

    static final String MODEL = "bakllava";

    private static final String LOCAL_OLLAMA_IMAGE = String.format("tc-%s-%s", OLLAMA_IMAGE, MODEL);

    static OllamaContainer ollama;

    static {
        ollama = new OllamaContainer(new OllamaImage(OLLAMA_IMAGE, LOCAL_OLLAMA_IMAGE));
        ollama.start();
        createImage(ollama, LOCAL_OLLAMA_IMAGE);
    }

    String getBaseUrl() {
        return "http://" + ollama.getHost() + ":" + ollama.getMappedPort(11434);
    }

    static void createImage(GenericContainer<?> container, String localImageName) {
        DockerImageName dockerImageName = DockerImageName.parse(container.getDockerImageName());
        if (!dockerImageName.equals(DockerImageName.parse(localImageName))) {
            DockerClient dockerClient = DockerClientFactory.instance().client();
            List<Image> images = dockerClient.listImagesCmd().withReferenceFilter(localImageName).exec();
            if (images.isEmpty()) {
                DockerImageName imageModel = DockerImageName.parse(localImageName);
                dockerClient.commitCmd(container.getContainerId())
                        .withRepository(imageModel.getUnversionedPart())
                        .withLabels(Collections.singletonMap("org.testcontainers.sessionId", ""))
                        .withTag(imageModel.getVersionPart())
                        .exec();
            }
        }
    }

    static class OllamaContainer extends GenericContainer<OllamaContainer> {

        private final DockerImageName dockerImageName;

        OllamaContainer(LazyFuture<DockerImageName> image) {
            super(image.get());
            this.dockerImageName = image.get();


            withExposedPorts(11434);
            withImagePullPolicy(dockerImageName -> !dockerImageName.getVersionPart().endsWith(MODEL));
        }

        @Override
        protected void containerIsStarted(InspectContainerResponse containerInfo) {
            if (!this.dockerImageName.equals(DockerImageName.parse(LOCAL_OLLAMA_IMAGE))) {
                try {
                    log.info("Start pulling the '{}' model ... would take several minutes ...", MODEL);
                    execInContainer("ollama", "pull", MODEL);
                    log.info("Model pulling competed!");
                } catch (IOException | InterruptedException e) {
                    throw new RuntimeException("Error pulling model", e);
                }
            }
        }

    }

    static class OllamaImage extends LazyFuture<DockerImageName> {

        private final String baseImage;

        private final String localImageName;

        OllamaImage(String baseImage, String localImageName) {
            this.baseImage = baseImage;
            this.localImageName = localImageName;
        }

        @Override
        protected DockerImageName resolve() {
            DockerImageName dockerImageName = DockerImageName.parse(this.baseImage);
            DockerClient dockerClient = DockerClientFactory.instance().client();
            List<Image> images = dockerClient.listImagesCmd().withReferenceFilter(this.localImageName).exec();
            if (images.isEmpty()) {
                return dockerImageName;
            }
            return DockerImageName.parse(this.localImageName);
        }

    }

}
