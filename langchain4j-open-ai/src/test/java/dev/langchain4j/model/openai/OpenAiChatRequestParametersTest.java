package dev.langchain4j.model.openai;

import static dev.langchain4j.model.openai.OpenAiChatModelName.GPT_4_O;
import static dev.langchain4j.model.openai.OpenAiChatModelName.GPT_4_O_MINI;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.model.chat.request.ChatRequestParameters;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OpenAiChatRequestParametersTest {

    @Test
    void override_with() {

        // given
        Map<String, Integer> originalLogitBias = new HashMap<>();
        originalLogitBias.put("token1", 1);

        Map<String, String> originalMetadata = new HashMap<>();
        originalMetadata.put("key1", "value1");

        OpenAiChatRequestParameters original = OpenAiChatRequestParameters.builder()
                .modelName(GPT_4_O)
                .temperature(0.7)
                .maxCompletionTokens(500)
                .logitBias(originalLogitBias)
                .parallelToolCalls(true)
                .seed(42)
                .user("user1")
                .store(true)
                .metadata(originalMetadata)
                .serviceTier("tier1")
                .reasoningEffort("low")
                .build();

        Map<String, Integer> overrideLogitBias = new HashMap<>();
        overrideLogitBias.put("token2", 2);

        Map<String, String> overrideMetadata = new HashMap<>();
        overrideMetadata.put("key2", "value2");

        OpenAiChatRequestParameters override = OpenAiChatRequestParameters.builder()
                .modelName(GPT_4_O_MINI)
                .maxCompletionTokens(1000)
                .logitBias(overrideLogitBias)
                .metadata(overrideMetadata)
                .build();

        // when
        ChatRequestParameters result = original.overrideWith(override);

        // then
        assertThat(result).isInstanceOf(OpenAiChatRequestParameters.class);
        OpenAiChatRequestParameters openAiResult = (OpenAiChatRequestParameters) result;

        assertThat(openAiResult.modelName()).isEqualTo(GPT_4_O_MINI.toString());
        assertThat(openAiResult.temperature()).isEqualTo(0.7);
        assertThat(openAiResult.maxCompletionTokens()).isEqualTo(1000);
        assertThat(openAiResult.logitBias()).isEqualTo(overrideLogitBias);
        assertThat(openAiResult.parallelToolCalls()).isTrue();
        assertThat(openAiResult.seed()).isEqualTo(42);
        assertThat(openAiResult.user()).isEqualTo("user1");
        assertThat(openAiResult.store()).isTrue();
        assertThat(openAiResult.metadata()).isEqualTo(overrideMetadata);
        assertThat(openAiResult.serviceTier()).isEqualTo("tier1");
        assertThat(openAiResult.reasoningEffort()).isEqualTo("low");
    }

    @Test
    void null_model_name() {
        OpenAiChatRequestParameters params = OpenAiChatRequestParameters.builder()
                .modelName((OpenAiChatModelName) null)
                .build();

        assertThat(params.modelName()).isNull();
    }

    @Test
    void should_override_prompt_cache_key_and_prompt_cache_options() {
        OpenAiChatRequestParameters defaults = OpenAiChatRequestParameters.builder()
                .modelName("gpt-5.6")
                .promptCacheKey("default_key")
                .promptCacheOptions(OpenAiPromptCacheOptions.implicit())
                .build();

        OpenAiChatRequestParameters override = OpenAiChatRequestParameters.builder()
                .promptCacheKey("override_key")
                .promptCacheOptions(OpenAiPromptCacheOptions.explicit())
                .build();

        OpenAiChatRequestParameters merged = defaults.overrideWith(override);
        assertThat(merged.promptCacheKey()).isEqualTo("override_key");
        assertThat(merged.promptCacheOptions()).isEqualTo(OpenAiPromptCacheOptions.explicit());

        OpenAiChatRequestParameters notOverridden =
                defaults.overrideWith(OpenAiChatRequestParameters.builder().build());
        assertThat(notOverridden.promptCacheKey()).isEqualTo("default_key");
        assertThat(notOverridden.promptCacheOptions()).isEqualTo(OpenAiPromptCacheOptions.implicit());
    }

    @Test
    void should_include_prompt_cache_fields_in_equals_and_hash_code() {
        OpenAiChatRequestParameters withPromptCache = OpenAiChatRequestParameters.builder()
                .promptCacheKey("key")
                .promptCacheOptions(OpenAiPromptCacheOptions.explicit())
                .build();
        OpenAiChatRequestParameters withoutPromptCache =
                OpenAiChatRequestParameters.builder().build();

        assertThat(withPromptCache)
                .isNotEqualTo(withoutPromptCache)
                .doesNotHaveSameHashCodeAs(withoutPromptCache)
                .isEqualTo(OpenAiChatRequestParameters.builder()
                        .promptCacheKey("key")
                        .promptCacheOptions(OpenAiPromptCacheOptions.explicit())
                        .build());
        assertThat(withPromptCache.toString())
                .contains("promptCacheKey=\"key\"")
                .contains("promptCacheOptions=OpenAiPromptCacheOptions{mode=\"explicit\", ttl=null}");
    }

    @Test
    void should_store_prompt_cache_fields_in_model_default_request_parameters() {
        OpenAiChatModel chatModel = OpenAiChatModel.builder()
                .apiKey("test")
                .modelName("gpt-5.6")
                .promptCacheKey("key")
                .promptCacheOptions(OpenAiPromptCacheOptions.explicit())
                .build();
        OpenAiStreamingChatModel streamingChatModel = OpenAiStreamingChatModel.builder()
                .apiKey("test")
                .modelName("gpt-5.6")
                .promptCacheKey("key")
                .promptCacheOptions(OpenAiPromptCacheOptions.explicit())
                .build();

        for (OpenAiChatRequestParameters parameters : List.of(
                (OpenAiChatRequestParameters) chatModel.defaultRequestParameters(),
                (OpenAiChatRequestParameters) streamingChatModel.defaultRequestParameters())) {
            assertThat(parameters.promptCacheKey()).isEqualTo("key");
            assertThat(parameters.promptCacheOptions()).isEqualTo(OpenAiPromptCacheOptions.explicit());
        }
    }
}
