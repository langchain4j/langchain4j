package dev.langchain4j.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.net.URL;
import java.net.URLClassLoader;
import org.junit.jupiter.api.Test;

class ProviderJsonTest {

    static class Dto {

        @JsonProperty
        public String name;

        @JsonProperty
        public Integer maxOutputTokens;
    }

    @Test
    void tolerates_properties_the_provider_added_later() {
        // A provider adding a field must never break deserialization, in any module.
        Json.JsonCodec codec = ProviderJson.codec(ProviderJsonSpec.builder().build());

        Dto dto = codec.fromJson("{\"name\":\"a\",\"brandNewField\":123}", Dto.class);

        assertThat(dto.name).isEqualTo("a");
    }

    @Test
    void tolerates_unknown_properties_under_snake_case_too() {
        Json.JsonCodec codec = ProviderJson.codec(ProviderJsonSpec.builder()
                .propertyNaming(ProviderJsonSpec.PropertyNaming.SNAKE_CASE)
                .build());

        Dto dto = codec.fromJson("{\"name\":\"a\",\"brand_new_field\":123}", Dto.class);

        assertThat(dto.name).isEqualTo("a");
    }

    @Test
    void snake_case_naming_is_applied_on_serialization() {
        Json.JsonCodec codec = ProviderJson.codec(ProviderJsonSpec.builder()
                .propertyNaming(ProviderJsonSpec.PropertyNaming.SNAKE_CASE)
                .build());

        Dto dto = new Dto();
        dto.maxOutputTokens = 5;

        assertThat(codec.toJson(dto)).contains("max_output_tokens");
    }

    @Test
    void inclusion_controls_whether_nulls_are_written() {
        Dto dto = new Dto();

        assertThat(ProviderJson.codec(ProviderJsonSpec.builder().build()).toJson(dto))
                .contains("\"name\":null");

        assertThat(ProviderJson.codec(ProviderJsonSpec.builder()
                                .inclusion(ProviderJsonSpec.Inclusion.NON_NULL)
                                .build())
                        .toJson(dto))
                .isEqualToIgnoringWhitespace("{}");
    }

    @Test
    void reuses_a_codec_for_the_same_spec() {
        ProviderJsonSpec spec = ProviderJsonSpec.builder()
                .propertyNaming(ProviderJsonSpec.PropertyNaming.SNAKE_CASE)
                .prettyPrint(true)
                .build();

        // Equal specs describe the same wire format, so they can share a codec. Callers that build
        // one per instance would otherwise run a ServiceLoader scan and build a mapper every time.
        assertThat(ProviderJson.codec(spec)).isSameAs(ProviderJson.codec(spec));
        assertThat(ProviderJson.codec(ProviderJsonSpec.builder()
                        .propertyNaming(ProviderJsonSpec.PropertyNaming.SNAKE_CASE)
                        .prettyPrint(true)
                        .build()))
                .isSameAs(ProviderJson.codec(spec));
    }

    @Test
    void gives_a_different_codec_to_a_different_spec() {
        assertThat(ProviderJson.codec(ProviderJsonSpec.builder().prettyPrint(true).build()))
                .isNotSameAs(ProviderJson.codec(ProviderJsonSpec.builder().prettyPrint(false).build()));
        assertThat(ProviderJson.codec(ProviderJsonSpec.builder()
                        .propertyNaming(ProviderJsonSpec.PropertyNaming.SNAKE_CASE)
                        .build()))
                .isNotSameAs(ProviderJson.codec(ProviderJsonSpec.builder().build()));
    }


    @Test
    void a_codec_is_reused_for_the_same_spec_and_class_loader() {
        assertThat(ProviderJson.codec(ProviderJsonSpec.builder().build()))
                .isSameAs(ProviderJson.codec(ProviderJsonSpec.builder().build()));
    }

    /**
     * The codec is resolved through the thread-context class loader, so caching one for every
     * caller would hand the first application's codec to the rest - the case being several
     * deployments sharing one copy of LangChain4j from a container's common directory.
     */
    @Test
    void a_different_class_loader_gets_its_own_codec() throws Exception {
        ProviderJsonSpec spec = ProviderJsonSpec.builder().build();
        ClassLoader original = Thread.currentThread().getContextClassLoader();
        try (URLClassLoader other = new URLClassLoader(new URL[0], original)) {
            Json.JsonCodec mine = ProviderJson.codec(spec);

            Thread.currentThread().setContextClassLoader(other);
            Json.JsonCodec theirs = ProviderJson.codec(spec);

            assertThat(theirs).isNotSameAs(mine);
            assertThat(ProviderJson.codec(spec)).isSameAs(theirs);
        } finally {
            Thread.currentThread().setContextClassLoader(original);
        }
    }
}
