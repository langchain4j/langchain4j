package dev.langchain4j.model.google.genai;

import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.service.common.AbstractStreamingAiServiceIT;
import java.util.List;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "GOOGLE_AI_GEMINI_API_KEY", matches = ".+")
class GoogleGenAiStreamingAiServiceIT extends AbstractStreamingAiServiceIT {

    @Override
    protected List<StreamingChatModel> models() {
        return List.of(GoogleGenAiStreamingChatModel.builder()
                .apiKey(System.getenv("GOOGLE_AI_GEMINI_API_KEY"))
                .modelName("gemini-2.5-flash")
                .build());
    }

    @Override
    protected Class<? extends ChatResponseMetadata> chatResponseMetadataType(StreamingChatModel model) {
        return GoogleGenAiChatResponseMetadata.class;
    }

    @Override
    protected void assertTotalTokenCount(TokenUsage tokenUsage) {
        // total token count can be more than input+output due to thinking/reasoning
        assertThat(tokenUsage.totalTokenCount())
                .isGreaterThanOrEqualTo(tokenUsage.inputTokenCount() + tokenUsage.outputTokenCount());
    }
}
