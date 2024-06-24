package dev.langchain4j.model.ollama;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.model.Image;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.utility.DockerImageName;

import java.util.List;

public class OllamaImage {

    public static final String OLLAMA_IMAGE = "ollama/ollama:latest";

    public static final String BAKLLAVA_MODEL = "bakllava";

    public static final String TINY_DOLPHIN_MODEL = "tinydolphin";

    public static final String ALL_MINILM_MODEL = "all-minilm";

    public static DockerImageName resolve(String baseImage, String localImageName) {
        DockerImageName dockerImageName = DockerImageName.parse(baseImage);
        DockerClient dockerClient = DockerClientFactory.instance().client();
        List<Image> images = dockerClient.listImagesCmd().withReferenceFilter(localImageName).exec();
        if (images.isEmpty()) {
            return dockerImageName;
        }
        return DockerImageName.parse(localImageName).asCompatibleSubstituteFor(baseImage);
    }
}
