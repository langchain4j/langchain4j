package dev.langchain4j.model.gpullama3;

import org.beehive.gpullama3.LlamaApp;
import org.beehive.gpullama3.Options;
import org.beehive.gpullama3.inference.sampler.Sampler;
import org.beehive.gpullama3.model.Model;
import org.beehive.gpullama3.model.loader.ModelLoader;

import java.io.IOException;
import java.nio.file.Path;

public class GPULlama3Provider {

    private final Path modelPath;
    private final Model model;
    private final Sampler sampler;
    private final GPULlama3BaseModel baseModel;

    public GPULlama3Provider(Path modelPath, GPULlama3BaseModel baseModel) {
        this.modelPath = modelPath;
        this.baseModel = baseModel;

        baseModel.bu

        try {
            this.model = ModelLoader.loadModel(modelPath, builder.maxTokens, true);
            this.sampler = LlamaApp.selectSampler(
                    model.configuration().vocabularySize(), (float) temperature, topP, seed
            );
        } catch (IOException e) {
            throw new RuntimeException("Failed to load model from " + modelPath, e);
        }
    }



    public Model getModel() {
        return model;
    }

    public Sampler getSampler() {
        return sampler;
    }


}
