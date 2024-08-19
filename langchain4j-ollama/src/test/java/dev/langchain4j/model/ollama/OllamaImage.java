package dev.langchain4j.model.ollama;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.model.Image;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.utility.DockerImageName;

import java.util.List;

public class OllamaImage {

    static final String OLLAMA_IMAGE = "ollama/ollama:latest";

    static final String BAKLLAVA_MODEL = "bakllava";

    static final String TINY_DOLPHIN_MODEL = "tinydolphin";
    static final String TOOL_MODEL = "mistral";

    static final String ALL_MINILM_MODEL = "all-minilm";

    static DockerImageName resolve(String baseImage, String localImageName) {
        DockerImageName dockerImageName = DockerImageName.parse(baseImage);
        DockerClient dockerClient = DockerClientFactory.instance().client();
        List<Image> images = dockerClient.listImagesCmd().withReferenceFilter(localImageName).exec();
        if (images.isEmpty()) {
            return dockerImageName;
        }
        return DockerImageName.parse(localImageName).asCompatibleSubstituteFor(baseImage);
    }
}
