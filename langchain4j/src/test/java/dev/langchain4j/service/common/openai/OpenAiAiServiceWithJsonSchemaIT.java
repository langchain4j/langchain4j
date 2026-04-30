package dev.langchain4j.service.common.openai;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.exception.HttpException;
import dev.langchain4j.http.client.HttpClient;
import dev.langchain4j.http.client.HttpMethod;
import dev.langchain4j.http.client.HttpRequest;
import dev.langchain4j.http.client.jdk.JdkHttpClientBuilder;
import dev.langchain4j.http.client.log.LoggingHttpClient;
import dev.langchain4j.service.common.AbstractAiServiceWithJsonSchemaIT;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.List;
import java.util.Optional;

import static dev.langchain4j.model.chat.Capability.RESPONSE_FORMAT_JSON_SCHEMA;
import static dev.langchain4j.model.openai.OpenAiChatModelName.GPT_4_O_MINI;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

// TODO move to langchain4j-open-ai module once dependency cycle is resolved
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class OpenAiAiServiceWithJsonSchemaIT extends AbstractAiServiceWithJsonSchemaIT {

    OpenAiChatModel modelWithStrictJsonSchema = OpenAiChatModel.builder()
            .baseUrl(System.getenv("OPENAI_BASE_URL"))
            .apiKey(System.getenv("OPENAI_API_KEY"))
            .organizationId(System.getenv("OPENAI_ORGANIZATION_ID"))
            .modelName(GPT_4_O_MINI)
            .supportedCapabilities(RESPONSE_FORMAT_JSON_SCHEMA)
            .strictJsonSchema(true)
            .temperature(0.0)
            .logRequests(true)
            .logResponses(true)
            .build();

    OpenAiChatModel modelWithStrictJsonSchemaLegacy = OpenAiChatModel.builder()
            .baseUrl(System.getenv("OPENAI_BASE_URL"))
            .apiKey(System.getenv("OPENAI_API_KEY"))
            .organizationId(System.getenv("OPENAI_ORGANIZATION_ID"))
            .modelName(GPT_4_O_MINI)
            .responseFormat("json_schema") // testing backward compatibility
            .strictJsonSchema(true)
            .temperature(0.0)
            .logRequests(true)
            .logResponses(true)
            .build();

    @Override
    protected List<ChatModel> models() {
        return List.of(
                modelWithStrictJsonSchema,
                modelWithStrictJsonSchemaLegacy,
                OpenAiChatModel.builder()
                        .baseUrl(System.getenv("OPENAI_BASE_URL"))
                        .apiKey(System.getenv("OPENAI_API_KEY"))
                        .organizationId(System.getenv("OPENAI_ORGANIZATION_ID"))
                        .modelName(GPT_4_O_MINI)
                        .supportedCapabilities(RESPONSE_FORMAT_JSON_SCHEMA)
                        .strictJsonSchema(false)
                        .temperature(0.0)
                        .logRequests(true)
                        .logResponses(true)
                        .build(),
                OpenAiChatModel.builder()
                        .baseUrl(System.getenv("OPENAI_BASE_URL"))
                        .apiKey(System.getenv("OPENAI_API_KEY"))
                        .organizationId(System.getenv("OPENAI_ORGANIZATION_ID"))
                        .modelName(GPT_4_O_MINI)
                        .responseFormat("json_schema") // testing backward compatibility
                        .strictJsonSchema(false)
                        .temperature(0.0)
                        .logRequests(true)
                        .logResponses(true)
                        .build());
    }

    @Override
    protected boolean supportsRecursion() {
        return true;
    }

    @Override
    protected boolean isStrictJsonSchemaEnabled(ChatModel model) {
        return model == modelWithStrictJsonSchema || model == modelWithStrictJsonSchemaLegacy;
    }

    /**
     * Tripwire test: documents — and proves — why the polymorphic schema generator wraps the
     * LLM-facing {@code anyOf} under a {@code value} property. OpenAI's structured-outputs API
     * currently rejects schemas whose root is not {@code type: "object"}, even though such a
     * schema is itself a perfectly valid JSON Schema. We add the {@code value} envelope to
     * satisfy that constraint.
     *
     * <p><strong>If this test starts failing</strong> (i.e., OpenAI returns 200 instead of 400
     * for a schema with {@code anyOf} at the root), it means OpenAI has relaxed the restriction
     * and the {@code value}/{@code values} envelope can be dropped from the polymorphic schema
     * shape. At that point, simplify the schema and update {@code PojoOutputParser} /
     * {@code PojoCollectionOutputParser} accordingly.</p>
     *
     * <p>Hits the raw HTTP endpoint via langchain4j's {@link HttpClient} to
     * bypass {@code OpenAiChatModel}'s client-side validation.</p>
     */
    @Test
    void openai_rejects_anyOf_at_schema_root() {

        // given
        String body =
                """
                {
                  "model": "gpt-4o-mini",
                  "messages": [{"role": "user", "content": "say hi"}],
                  "response_format": {
                    "type": "json_schema",
                    "json_schema": {
                      "name": "Animal",
                      "strict": true,
                      "schema": {
                        "anyOf": [
                          {"type": "object",
                           "properties": {"kind": {"type": "string", "enum": ["Dog"]}},
                           "required": ["kind"],
                           "additionalProperties": false},
                          {"type": "object",
                           "properties": {"kind": {"type": "string", "enum": ["Cat"]}},
                           "required": ["kind"],
                           "additionalProperties": false}
                        ]
                      }
                    }
                  }
                }
                """;

        String baseUrl = Optional.ofNullable(System.getenv("OPENAI_BASE_URL")).orElse("https://api.openai.com/v1");
        HttpRequest request = HttpRequest.builder()
                .method(HttpMethod.POST)
                .url(baseUrl + "/chat/completions")
                .addHeader("Authorization", "Bearer " + System.getenv("OPENAI_API_KEY"))
                .addHeader("Content-Type", "application/json")
                .body(body)
                .build();

        HttpClient httpClient = new LoggingHttpClient(new JdkHttpClientBuilder().build(), true, true);

        // when
        HttpException error = catchThrowableOfType(HttpException.class, () -> httpClient.execute(request));

        // then
        assertThat(error).isNotNull();
        assertThat(error.statusCode()).isEqualTo(400);
        assertThat(error.getMessage())
                .containsIgnoringCase("invalid schema")
                .containsIgnoringCase("type")
                .containsIgnoringCase("object");
    }
}
