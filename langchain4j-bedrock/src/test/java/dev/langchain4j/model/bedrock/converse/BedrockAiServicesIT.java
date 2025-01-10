package dev.langchain4j.model.bedrock.converse;

import static dev.langchain4j.model.bedrock.converse.TestedModels.AWS_NOVA_LITE;
import static dev.langchain4j.model.bedrock.converse.TestedModels.AWS_NOVA_MICRO;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.service.common.AbstractAiServiceIT;
import java.util.List;

public class BedrockAiServicesIT extends AbstractAiServiceIT {
    @Override
    protected List<ChatLanguageModel> models() {
        return List.of(AWS_NOVA_MICRO, AWS_NOVA_LITE);
    }
}
