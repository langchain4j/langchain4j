package dev.langchain4j.model.googleai.common;

import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.service.common.AbstractStreamingAiServiceIT;

import java.util.List;

import static dev.langchain4j.model.googleai.common.GoogleAiGeminiStreamingChatModelIT.GOOGLE_AI_GEMINI_STREAMING_CHAT_MODEL;

class GoogleAiGeminiStreamingAiServiceIT extends AbstractStreamingAiServiceIT {

    @Override
    protected List<StreamingChatLanguageModel> models() {
        return List.of(
                GOOGLE_AI_GEMINI_STREAMING_CHAT_MODEL
        );
    }
}
