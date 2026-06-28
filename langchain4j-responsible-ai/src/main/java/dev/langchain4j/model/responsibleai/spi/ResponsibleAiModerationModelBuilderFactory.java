package dev.langchain4j.model.responsibleai.spi;

import dev.langchain4j.Internal;
import dev.langchain4j.model.responsibleai.ResponsibleAiModerationModel;
import java.util.function.Supplier;

@Internal
public interface ResponsibleAiModerationModelBuilderFactory
        extends Supplier<ResponsibleAiModerationModel.ResponsibleAiModerationModelBuilder> {}
