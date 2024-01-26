package dev.langchain4j.model.localai;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.model.Image;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class AbstractLocalAiInfrastructure {

    private static final String LOCAL_AI_IMAGE = "localai/localai:latest";

    private static final String LOCAL_IMAGE_NAME = "tc-local-ai";

    private static final String LOCAL_LOCAL_AI_IMAGE = String.format("%s:%s", LOCAL_IMAGE_NAME, DockerImageName.parse(LOCAL_AI_IMAGE).getVersionPart());

    private static final List<String[]> CMDS = Arrays.asList(
            new String[]{"curl", "-o", "/build/models/ggml-gpt4all-j", "https://gpt4all.io/models/ggml-gpt4all-j.bin"},
            new String[]{"curl", "-Lo", "/build/models/ggml-model-q4_0", "https://huggingface.co/LangChain4j/localai-embeddings/resolve/main/ggml-model-q4_0"});

    static final LocalAiContainer localAi;

    static {
        localAi = new LocalAiContainer(new LocalAi(LOCAL_AI_IMAGE, LOCAL_LOCAL_AI_IMAGE).resolve());
        localAi.start();
        createImage(localAi, LOCAL_LOCAL_AI_IMAGE);
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

    static class LocalAiContainer extends GenericContainer<LocalAiContainer> {

        public LocalAiContainer(DockerImageName image) {
            super(image);
            withExposedPorts(8080);
            withImagePullPolicy(dockerImageName -> !dockerImageName.getUnversionedPart().startsWith(LOCAL_IMAGE_NAME));
        }

        @Override
        protected void containerIsStarted(InspectContainerResponse containerInfo) {
            if (!DockerImageName.parse(getDockerImageName()).equals(DockerImageName.parse(LOCAL_LOCAL_AI_IMAGE))) {
                try {
                    for (String[] cmd : CMDS) {
                        execInContainer(cmd);
                    }
                    copyFileToContainer(MountableFile.forClasspathResource("ggml-model-q4_0.yaml"), "/build/models/ggml-model-q4_0.yaml");
                } catch (IOException | InterruptedException e) {
                    throw new RuntimeException("Error downloading the model", e);
                }
            }
        }

        public String getBaseUrl() {
            return "http://" + getHost() + ":" + getMappedPort(8080);
        }
    }

    static class LocalAi {

        private final String baseImage;

        private final String localImageName;

        LocalAi(String baseImage, String localImageName) {
            this.baseImage = baseImage;
            this.localImageName = localImageName;
        }

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
