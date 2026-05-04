package dev.langchain4j.model.deliverance;

import io.teknek.deliverance.DType;
import io.teknek.deliverance.model.AutoModelForCausaLm;

import java.nio.file.Path;

public final class DeliveranceModels {

    private DeliveranceModels() {
    }

    public static AutoModelForCausaLm.Builder builder(String modelName) {
        return builder(null, modelName, null);
    }

    public static AutoModelForCausaLm.Builder builder(String modelName, String authToken) {
        return builder(null, modelName, authToken);
    }

    public static AutoModelForCausaLm.Builder builder(Path modelCachePath, String modelName) {
        return builder(modelCachePath, modelName, null);
    }

    public static AutoModelForCausaLm.Builder builder(Path modelCachePath, String modelName, String authToken) {
        return DeliveranceModelSupport.newModelBuilder(modelCachePath, modelName, authToken);
    }
}
