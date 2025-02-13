package dev.langchain4j.model.openaiofficial;

import static dev.langchain4j.model.openaiofficial.OpenAiOfficialStreamingChatModelIT.OPEN_AI_OFFICIAL_STREAMING_CHAT_MODEL_STRICT_SCHEMA;

import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.service.common.AbstractStreamingAiServiceIT;
import java.util.List;

class OpenAiOfficialStreamingAiServiceIT extends AbstractStreamingAiServiceIT {

    @Override
    protected List<StreamingChatLanguageModel> models() {
        return List.of(
                // OPEN_AI_OFFICIAL_STREAMING_CHAT_MODEL, //TODO FIX this doesn't run reliably when generating JSON (as
                // there is no schema)
                OPEN_AI_OFFICIAL_STREAMING_CHAT_MODEL_STRICT_SCHEMA
                // TODO Add a model using OpenAI (NOT Azure OpenAI)
                );
    }
}
