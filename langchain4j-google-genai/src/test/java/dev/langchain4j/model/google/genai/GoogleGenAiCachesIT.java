package dev.langchain4j.model.google.genai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.abort;

import com.google.genai.errors.ClientException;
import com.google.genai.types.CachedContent;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "GOOGLE_AI_GEMINI_API_KEY", matches = ".+")
class GoogleGenAiCachesIT {

    private static final String GOOGLE_AI_GEMINI_API_KEY = System.getenv("GOOGLE_AI_GEMINI_API_KEY");

    @Test
    void should_create_get_list_update_and_delete_cache() {
        GoogleGenAiCaches caches =
                GoogleGenAiCaches.builder().apiKey(GOOGLE_AI_GEMINI_API_KEY).build();

        // explicit caching requires a minimum number of input tokens; pad the content heavily to clear it
        List<ChatMessage> messages = List.of(
                SystemMessage.from("You are an expert technical writer."),
                UserMessage.from("Large reusable document context. ".repeat(10_000)));

        CachedContent created;
        try {
            created = caches.createCache("gemini-2.5-flash", messages, Duration.ofMinutes(5));
        } catch (ClientException e) {
            // explicit context caching is not offered on the free tier (storage limit = 0); skip there
            if (e.getMessage() != null && e.getMessage().contains("FreeTier")) {
                abort("Context caching requires a paid tier: " + e.getMessage());
            }
            throw e;
        }

        String name = created.name().orElseThrow();
        assertThat(name).startsWith("cachedContents/");

        try {
            assertThat(caches.getCache(name).name()).contains(name);
            assertThat(caches.listCaches())
                    .anyMatch(cache -> cache.name().filter(name::equals).isPresent());
            assertThat(caches.updateCacheTtl(name, Duration.ofMinutes(10)).name())
                    .contains(name);
        } finally {
            caches.deleteCache(name);
        }
    }
}
