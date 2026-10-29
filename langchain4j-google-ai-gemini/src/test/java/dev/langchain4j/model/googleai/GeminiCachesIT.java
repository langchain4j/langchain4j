package dev.langchain4j.model.googleai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.abort;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.exception.HttpException;
import dev.langchain4j.model.googleai.GeminiCaches.GeminiCachedContent;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "GOOGLE_AI_GEMINI_API_KEY", matches = ".+")
class GeminiCachesIT {

    private static final String GOOGLE_AI_GEMINI_API_KEY = System.getenv("GOOGLE_AI_GEMINI_API_KEY");

    @Test
    void should_create_get_list_attach_and_delete_cache() {
        GeminiCaches caches =
                GeminiCaches.builder().apiKey(GOOGLE_AI_GEMINI_API_KEY).build();

        // explicit caching requires a minimum number of input tokens; pad the content heavily to clear it
        List<ChatMessage> messages = List.of(
                SystemMessage.from("You are an expert technical writer."),
                UserMessage.from("Large reusable document context. ".repeat(10_000)));

        GeminiCachedContent created;
        try {
            created = caches.createCache("gemini-2.5-flash", messages, Duration.ofMinutes(5));
        } catch (HttpException e) {
            // explicit context caching is not offered on the free tier (storage limit = 0); skip there
            if (e.getMessage() != null && e.getMessage().contains("FreeTier")) {
                abort("Context caching requires a paid tier: " + e.getMessage());
            }
            throw e;
        }

        String name = created.name();
        assertThat(name).startsWith("cachedContents/");
        assertThat(created.usageMetadata().totalTokenCount()).isPositive();

        try {
            assertThat(caches.getCache(name).name()).isEqualTo(name);
            assertThat(caches.listCaches()).anyMatch(cache -> name.equals(cache.name()));

            GoogleAiGeminiChatModel model = GoogleAiGeminiChatModel.builder()
                    .apiKey(GOOGLE_AI_GEMINI_API_KEY)
                    .modelName("gemini-2.5-flash")
                    .cachedContentName(name)
                    .build();
            assertThat(model.chat("Summarize the cached document in one sentence."))
                    .isNotBlank();
        } finally {
            caches.deleteCache(name);
        }
    }
}
