package dev.langchain4j.model.embedding;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.message.ContentType;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.model.embedding.request.DefaultEmbeddingRequestParameters;
import dev.langchain4j.model.embedding.request.EmbeddingInput;
import dev.langchain4j.model.embedding.request.EmbeddingInputType;
import dev.langchain4j.model.embedding.request.EmbeddingParameter;
import dev.langchain4j.model.embedding.request.EmbeddingRequest;
import dev.langchain4j.model.embedding.request.EmbeddingRequestParameters;
import dev.langchain4j.model.embedding.response.EmbeddingResponse;
import dev.langchain4j.model.embedding.response.EmbeddingResponseMetadata;
import dev.langchain4j.model.output.TokenUsage;
import java.util.List;
import org.assertj.core.api.WithAssertions;
import org.junit.jupiter.api.Test;

/**
 * Covers the value/builder types added for the embedding request/response API
 * ({@link EmbeddingParameter}, {@link EmbeddingRequest}/{@link EmbeddingRequestParameters},
 * {@link EmbeddingResponse}/{@link EmbeddingResponseMetadata}).
 */
class EmbeddingRequestResponseTypesTest implements WithAssertions {

    @Test
    void embedding_parameter_identity_is_name_based() {
        EmbeddingParameter<String> a = new EmbeddingParameter<>("x", String.class);
        EmbeddingParameter<String> b = new EmbeddingParameter<>("x", String.class);
        EmbeddingParameter<Integer> c = new EmbeddingParameter<>("y", Integer.class);

        assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
        assertThat(a).isEqualTo(a);
        assertThat(a).isNotEqualTo(c).isNotEqualTo(null).isNotEqualTo("x");
        assertThat(a.name()).isEqualTo("x");
        assertThat(a.type()).isEqualTo(String.class);
        assertThat(a.cast("hello")).isEqualTo("hello");
        assertThat(a.cast(null)).isNull();
        assertThat(a).hasToString("x");

        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> new EmbeddingParameter<>(" ", String.class));
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> new EmbeddingParameter<>("x", null));
    }

    @Test
    void default_parameters_generic_accessor_and_null_clearing() {
        DefaultEmbeddingRequestParameters params = DefaultEmbeddingRequestParameters.builder()
                .modelName("m")
                .dimensions(256)
                .dimensions(null) // clears it back out of the present set
                .build();

        assertThat(params.modelName()).isEqualTo("m");
        assertThat(params.dimensions()).isNull();
        assertThat(params.parameter(EmbeddingRequestParameters.MODEL_NAME)).isEqualTo("m");
        assertThat(params.presentParameters()).containsExactly(EmbeddingRequestParameters.MODEL_NAME);
    }

    @Test
    void parameters_equals_hashcode_tostring() {
        EmbeddingRequestParameters a =
                DefaultEmbeddingRequestParameters.builder().modelName("m").build();
        EmbeddingRequestParameters b =
                DefaultEmbeddingRequestParameters.builder().modelName("m").build();
        EmbeddingRequestParameters c =
                DefaultEmbeddingRequestParameters.builder().modelName("n").build();

        assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
        assertThat(a).isEqualTo(a).isNotEqualTo(c).isNotEqualTo(null).isNotEqualTo("m");
        assertThat(a.toString()).contains("modelName");
    }

    @Test
    void override_with_empty_or_null_returns_same_instance() {
        EmbeddingRequestParameters base =
                DefaultEmbeddingRequestParameters.builder().modelName("m").build();

        assertThat(base.overrideWith(null)).isSameAs(base);
        assertThat(base.overrideWith(EmbeddingRequestParameters.EMPTY)).isSameAs(base);
    }

    @Test
    void builder_override_with_copies_present_parameters() {
        EmbeddingRequestParameters source = DefaultEmbeddingRequestParameters.builder()
                .modelName("m")
                .dimensions(64)
                .build();

        EmbeddingRequestParameters copy = DefaultEmbeddingRequestParameters.builder()
                .overrideWith(source)
                .overrideWith(null) // no-op branch
                .build();

        assertThat(copy).isEqualTo(source);
    }

    @Test
    void embedding_input_text_only() {
        EmbeddingInput input = EmbeddingInput.from("hello");

        assertThat(input.text()).isEqualTo("hello");
        assertThat(input.contentTypes()).containsExactly(ContentType.TEXT);
        assertThat(input.contents()).containsExactly(TextContent.from("hello"));
    }

    @Test
    void embedding_input_interleaved_text_and_image() {
        ImageContent image = ImageContent.from("http://example.com/cat.png");
        EmbeddingInput input = EmbeddingInput.from(TextContent.from("a photo of "), image, TextContent.from(" a cat"));

        // one input, multiple parts -> one embedding; both modalities present
        assertThat(input.contentTypes()).containsExactlyInAnyOrder(ContentType.TEXT, ContentType.IMAGE);
        // text() concatenates only the text parts (used by text-only providers / the legacy bridge)
        assertThat(input.text()).isEqualTo("a photo of  a cat");
        assertThat(input.contents()).hasSize(3);
    }

    @Test
    void embedding_input_equals_hashcode_tostring_and_rejects_empty() {
        EmbeddingInput a = EmbeddingInput.from("x");
        EmbeddingInput b = EmbeddingInput.from(TextContent.from("x"));

        assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
        assertThat(a).isEqualTo(a).isNotEqualTo(null).isNotEqualTo("x");
        assertThat(a.toString()).contains("EmbeddingInput");

        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> EmbeddingInput.from(new java.util.ArrayList<>()));
    }

    @Test
    void input_type_is_carried_and_exposed_via_convenience_accessors() {
        EmbeddingRequest request = EmbeddingRequest.builder()
                .input("q")
                .inputType(EmbeddingInputType.QUERY)
                .build();

        assertThat(request.inputType()).isEqualTo(EmbeddingInputType.QUERY);
        assertThat(request.parameters().inputType()).isEqualTo(EmbeddingInputType.QUERY);
        assertThat(request.parameters().presentParameters()).containsExactly(EmbeddingRequestParameters.INPUT_TYPE);
        assertThat(request.parameters().parameter(EmbeddingRequestParameters.INPUT_TYPE))
                .isEqualTo(EmbeddingInputType.QUERY);

        EmbeddingRequestParameters viaParams = DefaultEmbeddingRequestParameters.builder()
                .inputType(EmbeddingInputType.DOCUMENT)
                .build();
        assertThat(viaParams.inputType()).isEqualTo(EmbeddingInputType.DOCUMENT);
    }

    @Test
    void request_accessors_and_equals() {
        EmbeddingRequest a = EmbeddingRequest.builder()
                .inputs("a", "b")
                .modelName("m")
                .dimensions(128)
                .build();

        assertThat(a.inputs().stream().map(EmbeddingInput::text)).containsExactly("a", "b");
        assertThat(a.modelName()).isEqualTo("m");
        assertThat(a.dimensions()).isEqualTo(128);
        assertThat(a.parameters().presentParameters())
                .containsExactlyInAnyOrder(
                        EmbeddingRequestParameters.MODEL_NAME, EmbeddingRequestParameters.DIMENSIONS);

        EmbeddingRequest b = EmbeddingRequest.builder()
                .inputs(List.of(EmbeddingInput.from("a"), EmbeddingInput.from("b")))
                .modelName("m")
                .dimensions(128)
                .build();
        assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
        assertThat(a).isEqualTo(a).isNotEqualTo(null).isNotEqualTo("x");
        assertThat(a.toString()).contains("EmbeddingRequest");
    }

    @Test
    void request_with_explicit_parameters_and_flat_override() {
        EmbeddingRequestParameters explicit =
                DefaultEmbeddingRequestParameters.builder().modelName("base").dimensions(10).build();

        EmbeddingRequest request = EmbeddingRequest.builder()
                .input("x")
                .parameters(explicit)
                .modelName("override") // flat setter wins
                .build();

        assertThat(request.modelName()).isEqualTo("override");
        assertThat(request.dimensions()).isEqualTo(10); // preserved from explicit
    }

    @Test
    void request_rejects_empty_inputs() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> EmbeddingRequest.builder().build());
    }

    @Test
    void request_ignores_null_segments_and_lists() {
        EmbeddingRequest request = EmbeddingRequest.builder()
                .textSegment(null)
                .textSegments(null)
                .inputs((List<EmbeddingInput>) null)
                .input("only")
                .build();

        assertThat(request.inputs().stream().map(EmbeddingInput::text)).containsExactly("only");
    }

    @Test
    void response_defaults_metadata_and_accessors() {
        Embedding embedding = new Embedding(new float[] {1f, 2f});
        EmbeddingResponse withoutMetadata =
                EmbeddingResponse.builder().embeddings(List.of(embedding)).build();

        assertThat(withoutMetadata.embeddings()).containsExactly(embedding);
        assertThat(withoutMetadata.metadata()).isNotNull();
        assertThat(withoutMetadata.metadata().modelName()).isNull();
        assertThat(withoutMetadata.metadata().tokenUsage()).isNull();

        EmbeddingResponse a = EmbeddingResponse.builder()
                .embeddings(List.of(embedding))
                .metadata(EmbeddingResponseMetadata.builder()
                        .modelName("m")
                        .tokenUsage(new TokenUsage(3))
                        .build())
                .build();
        EmbeddingResponse b = EmbeddingResponse.builder()
                .embeddings(List.of(embedding))
                .metadata(EmbeddingResponseMetadata.builder()
                        .modelName("m")
                        .tokenUsage(new TokenUsage(3))
                        .build())
                .build();

        assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
        assertThat(a).isEqualTo(a).isNotEqualTo(null).isNotEqualTo("x").isNotEqualTo(withoutMetadata);
        assertThat(a.toString()).contains("EmbeddingResponse");
    }

    @Test
    void response_metadata_equals_hashcode_tostring() {
        EmbeddingResponseMetadata a = EmbeddingResponseMetadata.builder()
                .modelName("m")
                .tokenUsage(new TokenUsage(3))
                .build();
        EmbeddingResponseMetadata b = EmbeddingResponseMetadata.builder()
                .modelName("m")
                .tokenUsage(new TokenUsage(3))
                .build();
        EmbeddingResponseMetadata c =
                EmbeddingResponseMetadata.builder().modelName("n").build();

        assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
        assertThat(a).isEqualTo(a).isNotEqualTo(c).isNotEqualTo(null).isNotEqualTo("m");
        assertThat(a.modelName()).isEqualTo("m");
        assertThat(a.tokenUsage()).isEqualTo(new TokenUsage(3));
        assertThat(a.toString()).contains("EmbeddingResponseMetadata");
    }
}
