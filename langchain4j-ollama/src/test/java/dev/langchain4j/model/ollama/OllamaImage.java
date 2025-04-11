package dev.langchain4j.model.ollama;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.model.Image;
import java.util.List;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.utility.DockerImageName;

public class OllamaImage {

    public static final String OLLAMA_IMAGE = "ollama/ollama:latest";

    public static String localOllamaImage(String modelName) {
        return String.format("tc-%s-%s", OllamaImage.OLLAMA_IMAGE, modelName);
    }

    public static final String TINY_DOLPHIN_MODEL = "tinydolphin";
    public static final String LLAMA_3_1 = "llama3.1";
    public static final String LLAMA_3_2 = "llama3.2";
    public static final String LLAMA_3_2_VISION = "llama3.2-vision";

    public static final String ALL_MINILM_MODEL = "all-minilm";

    public static final String GRANITE_3_GUARDIAN = "granite3-guardian";

    public static DockerImageName resolve(String baseImage, String localImageName) {
        DockerImageName dockerImageName = DockerImageName.parse(baseImage);
        DockerClient dockerClient = DockerClientFactory.instance().client();
        List<Image> images =
                dockerClient.listImagesCmd().withReferenceFilter(localImageName).exec();
        if (images.isEmpty()) {
            return dockerImageName;
        }
        return DockerImageName.parse(localImageName).asCompatibleSubstituteFor(baseImage);
    }
}
