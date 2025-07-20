package dev.langchain4j.model.ollama;

import static dev.langchain4j.internal.Utils.isNullOrEmpty;

import com.github.dockerjava.api.command.InspectContainerResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.ollama.OllamaContainer;
import org.testcontainers.utility.DockerImageName;

public class LC4jOllamaContainer extends OllamaContainer {

    private static final Logger log = LoggerFactory.getLogger(LC4jOllamaContainer.class);

    private List<String> models;

    public LC4jOllamaContainer(DockerImageName dockerImageName) {
        super(dockerImageName);
        this.models = new ArrayList<>();
    }

    public LC4jOllamaContainer withModel(String model) {
        this.models.add(model);
        return this;
    }

    public LC4jOllamaContainer withModels(List<String> models) {
        this.models = models;
        return this;
    }

    @Override
    protected void containerIsStarted(InspectContainerResponse containerInfo) {
        if (!isNullOrEmpty(models)) {
            for (String model : models) {
                try {
                    log.info("Start pulling the '{}' model ... would take several minutes ...", model);
                    ExecResult r = execInContainer("ollama", "pull", model);
                    log.info("Model pulling competed! {}", r);
                } catch (IOException | InterruptedException e) {
                    throw new RuntimeException("Error pulling model", e);
                }
            }
        }
    }
}
