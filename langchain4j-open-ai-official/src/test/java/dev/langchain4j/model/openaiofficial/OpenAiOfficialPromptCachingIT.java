package dev.langchain4j.model.openaiofficial;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * Exercises {@code gpt-5.6+} prompt caching end to end through the official SDK, on both the Chat
 * Completions and the Responses API: an explicit breakpoint on a long shared system prompt should
 * produce a cache write on the first request and a cache read on the second.
 */
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class OpenAiOfficialPromptCachingIT {

    private static final String MODEL_NAME = "gpt-5.6";

    /**
     * On gpt-5.6 and later the minimum cacheable prefix is 1024 visible input tokens, so the shared
     * system prompt has to be comfortably longer than that.
     */
    private static final String LONG_SHARED_SYSTEM_PROMPT = ("""
            You are a meticulous technical support assistant for a fictional product called Acme Widget Cloud. \
            Answer strictly from the reference material below and never invent behaviour that is not described here. \
            Reference material: Acme Widget Cloud exposes widgets, widget groups, and widget policies. \
            A widget has an id, a display name, a region, a tier, and a lifecycle state. \
            Lifecycle states are provisioning, active, degraded, suspended, and retired. \
            A widget group collects widgets that share a region and a tier, and a widget policy binds a \
            retention window, an escalation path, and a maintenance window to a widget group. \
            Retention windows are expressed in whole days between 1 and 3650. \
            Escalation paths are ordered lists of on-call rotations, and each rotation has a primary and a secondary. \
            Maintenance windows are weekly, expressed in UTC, and may not overlap for widgets in the same group. \
            Provisioning a widget requires a region, a tier, and an owning group that already exists. \
            Retiring a widget requires that it first be suspended, and that no policy reference it. \
            A degraded widget still serves traffic but is excluded from new group assignments. \
            Suspending a widget drains its traffic over a fifteen minute window before marking it suspended. \
            Quotas are enforced per region and per tier, and exceeding a quota fails provisioning with a 409. \
            Tier changes are only permitted while a widget is active and outside its maintenance window. \
            Region changes are never permitted; callers must retire the widget and provision a new one. \
            """).repeat(6);

    private static SystemMessage markedSystemMessage() {
        return SystemMessage.builder()
                .text(LONG_SHARED_SYSTEM_PROMPT)
                .attributes(Map.of(
                        OpenAiOfficialPromptCacheBreakpoint.ATTRIBUTE_KEY,
                        OpenAiOfficialPromptCacheBreakpoint.MODE_EXPLICIT))
                .build();
    }

    /**
     * A fresh key per call, so that the first request is genuinely a cache miss. Kept short: the
     * Responses API rejects a {@code prompt_cache_key} longer than 64 characters.
     */
    private static String freshPromptCacheKey() {
        return "l4j-official-pc-it-" + UUID.randomUUID();
    }

    @Test
    void should_write_and_then_read_the_prompt_cache_on_chat_completions() {

        OpenAiOfficialChatModel model = OpenAiOfficialChatModel.builder()
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .modelName(MODEL_NAME)
                .defaultRequestParameters(OpenAiOfficialChatRequestParameters.builder()
                        .maxCompletionTokens(256)
                        .promptCacheKey(freshPromptCacheKey())
                        .promptCacheOptions(OpenAiOfficialPromptCacheOptions.builder()
                                .mode(OpenAiOfficialPromptCacheOptions.MODE_EXPLICIT)
                                .ttl(OpenAiOfficialPromptCacheOptions.TTL_30M)
                                .build())
                        .build())
                .build();

        SystemMessage sharedPrefix = markedSystemMessage();

        ChatResponse first = model.chat(ChatRequest.builder()
                .messages(sharedPrefix, UserMessage.from("What are the widget lifecycle states?"))
                .build());

        OpenAiOfficialTokenUsage firstUsage = (OpenAiOfficialTokenUsage) first.tokenUsage();
        assertThat(firstUsage.inputTokensDetails()).isNotNull();
        assertThat(firstUsage.inputTokensDetails().cacheWriteTokens())
                .isNotNull()
                .isPositive();

        ChatResponse second = model.chat(ChatRequest.builder()
                .messages(sharedPrefix, UserMessage.from("Can the region of a widget be changed?"))
                .build());

        OpenAiOfficialTokenUsage secondUsage = (OpenAiOfficialTokenUsage) second.tokenUsage();
        assertThat(secondUsage.inputTokensDetails()).isNotNull();
        assertThat(secondUsage.inputTokensDetails().cachedTokens()).isPositive();
    }

    @Test
    void should_write_and_then_read_the_prompt_cache_on_responses() {

        OpenAiOfficialResponsesChatModel model = OpenAiOfficialResponsesChatModel.builder()
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .modelName(MODEL_NAME)
                .maxOutputTokens(256)
                .promptCacheKey(freshPromptCacheKey())
                .promptCacheOptions(OpenAiOfficialPromptCacheOptions.builder()
                        .mode(OpenAiOfficialPromptCacheOptions.MODE_EXPLICIT)
                        .ttl(OpenAiOfficialPromptCacheOptions.TTL_30M)
                        .build())
                .build();

        SystemMessage sharedPrefix = markedSystemMessage();

        ChatResponse first = model.chat(ChatRequest.builder()
                .messages(sharedPrefix, UserMessage.from("What are the widget lifecycle states?"))
                .build());

        OpenAiOfficialTokenUsage firstUsage = (OpenAiOfficialTokenUsage) first.tokenUsage();
        assertThat(firstUsage.inputTokensDetails()).isNotNull();
        assertThat(firstUsage.inputTokensDetails().cacheWriteTokens())
                .isNotNull()
                .isPositive();

        ChatResponse second = model.chat(ChatRequest.builder()
                .messages(sharedPrefix, UserMessage.from("Can the region of a widget be changed?"))
                .build());

        OpenAiOfficialTokenUsage secondUsage = (OpenAiOfficialTokenUsage) second.tokenUsage();
        assertThat(secondUsage.inputTokensDetails()).isNotNull();
        assertThat(secondUsage.inputTokensDetails().cachedTokens()).isPositive();
    }
}
