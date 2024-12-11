package dev.langchain4j.service.openai.common;

import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.service.common.AbstractStreamingAiServiceIT;

import java.util.List;

import static dev.langchain4j.service.openai.common.OpenAiStreamingChatModelIT.defaultStreamingModelBuilder;

// TODO move to langchain4j-open-ai module once dependency cycle is resolved
class OpenAiStreamingAiServiceIT extends AbstractStreamingAiServiceIT {

    @Override
    protected List<StreamingChatLanguageModel> models() {
        return List.of(
                defaultStreamingModelBuilder().build()
                // TODO more configs?
        );
    }
}
