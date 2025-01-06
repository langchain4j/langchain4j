package dev.langchain4j.service.common.openai;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.service.common.AbstractAiServiceIT;

import java.util.List;

import static dev.langchain4j.service.common.openai.OpenAiChatModelIT.defaultModelBuilder;

// TODO move to langchain4j-open-ai module once dependency cycle is resolved
class OpenAiAiServiceIT extends AbstractAiServiceIT {

    @Override
    protected List<ChatLanguageModel> models() {
        return List.of(
                defaultModelBuilder().build()
                // TODO more configs?
        );
    }

    @Override
    protected List<ChatLanguageModel> modelsSupportingToolsAndJsonResponseFormatWithSchema() {
        return List.of(
                defaultModelBuilder()
                        .responseFormat("json_schema")
                        .strictJsonSchema(true)
                        .build(),
                defaultModelBuilder()
                        .responseFormat("json_schema")
                        .strictJsonSchema(false)
                        .build()
                // TODO more configs?
        );
    }
}
