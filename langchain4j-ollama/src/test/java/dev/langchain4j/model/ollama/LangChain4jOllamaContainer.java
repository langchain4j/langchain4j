package dev.langchain4j.model.ollama;

import com.github.dockerjava.api.command.InspectContainerResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.ollama.OllamaContainer;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;

public class LangChain4jOllamaContainer extends OllamaContainer {

    private static final Logger log = LoggerFactory.getLogger(LangChain4jOllamaContainer.class);

    private final DockerImageName dockerImageName;

    private String model;

    LangChain4jOllamaContainer(DockerImageName dockerImageName) {
        super(dockerImageName);
        this.dockerImageName = dockerImageName;
    }

    LangChain4jOllamaContainer withModel(String model) {
        this.model = model;
        return this;
    }

    @Override
    protected void containerIsStarted(InspectContainerResponse containerInfo) {
        if (this.model != null) {
            try {
                log.info("Start pulling the '{}' model ... would take several minutes ...", this.model);
                execInContainer("ollama", "pull", this.model);
                log.info("Model pulling competed!");
            } catch (IOException | InterruptedException e) {
                throw new RuntimeException("Error pulling model", e);
            }
        }
    }
}