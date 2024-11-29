package dev.langchain4j.model.googleai.common;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.common.AbstractChatModelIT;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;

import java.util.List;

class GoogleAiGeminiChatModelIT extends AbstractChatModelIT {

    static final GoogleAiGeminiChatModel GOOGLE_AI_GEMINI_CHAT_MODEL = GoogleAiGeminiChatModel.builder()
            .apiKey(System.getenv("GOOGLE_AI_GEMINI_API_KEY"))
            .modelName("gemini-1.5-flash-8b")
            .build();

    @Override
    protected List<ChatLanguageModel> models() {
        return List.of(GOOGLE_AI_GEMINI_CHAT_MODEL);
    }

    @Override
    protected String modelName() {
        return "gemini-1.5-flash";
    }

    @Override
    protected ChatRequest createModelSpecificChatRequest(int maxOutputTokens, UserMessage userMessage) {
        return ChatRequest.builder()
                .maxOutputTokens(maxOutputTokens)
                .messages(userMessage)
                .build();
    }

    @Override
    protected boolean supportsToolChoiceRequiredWithMultipleTools() {
        return false; // TODO implement
    }

    @Override
    protected boolean supportsSingleImageInputAsPublicURL() {
        return false; // TODO check if supported
    }

    @Override
    protected boolean assertResponseId() {
        return false; // TODO implement
    }

    @Override
    protected boolean assertResponseModel() {
        return false; // TODO implement
    }

    protected boolean assertFinishReason() {
        return false; // TODO fix
    }

    @Override
    protected boolean assertExceptionType() {
        return false; // TODO fix
    }
}