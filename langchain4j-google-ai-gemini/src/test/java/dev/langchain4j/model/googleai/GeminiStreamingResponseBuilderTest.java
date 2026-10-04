package dev.langchain4j.model.googleai;

import static dev.langchain4j.model.googleai.GeminiGenerateContentResponse.GeminiUrlRetrievalStatus.URL_RETRIEVAL_STATUS_ERROR;
import static dev.langchain4j.model.googleai.GeminiGenerateContentResponse.GeminiUrlRetrievalStatus.URL_RETRIEVAL_STATUS_SUCCESS;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.image.Image;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.googleai.GeminiContent.GeminiPart;
import dev.langchain4j.model.googleai.GeminiContent.GeminiPart.GeminiBlob;
import dev.langchain4j.model.googleai.GeminiGenerateContentResponse.GeminiCandidate;
import dev.langchain4j.model.googleai.GeminiGenerateContentResponse.GeminiUrlContextMetadata;
import dev.langchain4j.model.googleai.GeminiGenerateContentResponse.GeminiUrlMetadata;
import dev.langchain4j.model.googleai.GeminiStreamingResponseBuilder.TextAndTools;
import dev.langchain4j.model.googleai.GroundingMetadata.GroundingChunk;
import dev.langchain4j.model.googleai.GroundingMetadata.GroundingSupport;
import dev.langchain4j.model.googleai.GroundingMetadata.SearchEntryPoint;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class GeminiStreamingResponseBuilderTest {

    private final GeminiStreamingResponseBuilder builder = new GeminiStreamingResponseBuilder(false, null);

    @Test
    void should_return_empty_when_partial_response_is_null() {
        TextAndTools result = builder.append(null);

        assertThat(result.maybeText()).isEmpty();
        assertThat(result.maybeThought()).isEmpty();
        assertThat(result.tools()).isEmpty();
    }

    @Test
    void should_return_empty_when_candidates_is_null() {
        GeminiGenerateContentResponse response = new GeminiGenerateContentResponse(null, null, null, null, null, null);

        TextAndTools result = builder.append(response);

        assertThat(result.maybeText()).isEmpty();
        assertThat(result.maybeThought()).isEmpty();
        assertThat(result.tools()).isEmpty();
    }

    @Test
    void should_return_empty_when_candidates_is_empty() {
        GeminiGenerateContentResponse response =
                new GeminiGenerateContentResponse(null, null, Collections.emptyList(), null, null, null);

        TextAndTools result = builder.append(response);

        assertThat(result.maybeText()).isEmpty();
        assertThat(result.maybeThought()).isEmpty();
        assertThat(result.tools()).isEmpty();
    }

    @Test
    void should_return_text_when_candidate_has_content() {
        GeminiContent content = new GeminiContent(
                List.of(new GeminiContent.GeminiPart("Hello", null, null, null, null, null, null, null, null, null)),
                "model");
        GeminiCandidate candidate = new GeminiCandidate(content, null, null, null, null);
        GeminiGenerateContentResponse response =
                new GeminiGenerateContentResponse("id-1", "gemini-pro", List.of(candidate), null, null, null);

        TextAndTools result = builder.append(response);

        assertThat(result.maybeText()).hasValue("Hello");
        assertThat(result.tools()).isEmpty();
    }

    @Test
    void should_keep_generated_images_from_every_chunk() {
        builder.append(chunkWith(imagePart("AAAA")));
        builder.append(chunkWith(imagePart("BBBB")));

        AiMessage aiMessage = builder.build().aiMessage();

        assertThat(aiMessage.images()).extracting(Image::base64Data).containsExactly("AAAA", "BBBB");
    }

    @Test
    void should_keep_generated_images_when_only_one_chunk_has_them() {
        builder.append(chunkWith(GeminiPart.ofText("Hello")));
        builder.append(chunkWith(imagePart("AAAA")));

        AiMessage aiMessage = builder.build().aiMessage();

        assertThat(aiMessage.images()).extracting(Image::base64Data).containsExactly("AAAA");
    }

    @Test
    void should_keep_accumulating_text_and_tools_across_chunks() {
        builder.append(chunkWith(GeminiPart.ofText("Hello ")));
        builder.append(chunkWith(imagePart("AAAA")));
        builder.append(chunkWith(GeminiPart.ofText("world")));

        AiMessage aiMessage = builder.build().aiMessage();

        assertThat(aiMessage.text()).isEqualTo("Hello world");
        assertThat(aiMessage.images()).hasSize(1);
    }

    @Test
    void should_keep_grounding_metadata_from_the_response() {
        GroundingMetadata grounding =
                GroundingMetadata.builder().webSearchQueries(List.of("who won")).build();
        builder.append(new GeminiGenerateContentResponse(
                "id-1",
                "gemini-pro",
                List.of(new GeminiCandidate(null, null, null, null, null)),
                null,
                grounding,
                null));

        GoogleAiGeminiChatResponseMetadata metadata =
                (GoogleAiGeminiChatResponseMetadata) builder.build().metadata();

        assertThat(metadata.groundingMetadata()).isEqualTo(grounding);
    }

    @Test
    void should_keep_grounding_metadata_reported_on_the_candidate() {
        GroundingMetadata grounding =
                GroundingMetadata.builder().webSearchQueries(List.of("who won")).build();
        builder.append(new GeminiGenerateContentResponse(
                "id-1",
                "gemini-pro",
                List.of(new GeminiCandidate(null, null, null, grounding, null)),
                null,
                null,
                null));

        GoogleAiGeminiChatResponseMetadata metadata =
                (GoogleAiGeminiChatResponseMetadata) builder.build().metadata();

        assertThat(metadata.groundingMetadata()).isEqualTo(grounding);
    }

    @Test
    void should_keep_grounding_metadata_when_a_later_chunk_does_not_repeat_it() {
        GroundingMetadata grounding =
                GroundingMetadata.builder().webSearchQueries(List.of("who won")).build();
        builder.append(new GeminiGenerateContentResponse(
                "id-1",
                "gemini-pro",
                List.of(new GeminiCandidate(null, null, null, null, null)),
                null,
                grounding,
                null));
        builder.append(chunkWith(GeminiPart.ofText("the answer")));

        GoogleAiGeminiChatResponseMetadata metadata =
                (GoogleAiGeminiChatResponseMetadata) builder.build().metadata();

        assertThat(metadata.groundingMetadata()).isEqualTo(grounding);
    }

    @Test
    void should_keep_url_context_metadata_from_the_candidate() {
        GeminiUrlContextMetadata urlContext = new GeminiUrlContextMetadata(
                List.of(new GeminiUrlMetadata("https://example.com", URL_RETRIEVAL_STATUS_SUCCESS)));
        builder.append(new GeminiGenerateContentResponse(
                "id-1",
                "gemini-pro",
                List.of(new GeminiCandidate(null, null, urlContext, null, null)),
                null,
                null,
                null));

        GoogleAiGeminiChatResponseMetadata metadata =
                (GoogleAiGeminiChatResponseMetadata) builder.build().metadata();

        assertThat(metadata.urlContextMetadata()).isNotNull();
        assertThat(metadata.urlContextMetadata().urlMetadata())
                .extracting(UrlContextMetadata.UrlMetadata::retrievedUrl)
                .containsExactly("https://example.com");
    }

    @Test
    void should_leave_grounding_and_url_context_metadata_null_when_the_response_carries_none() {
        builder.append(chunkWith(GeminiPart.ofText("Hello")));

        GoogleAiGeminiChatResponseMetadata metadata =
                (GoogleAiGeminiChatResponseMetadata) builder.build().metadata();

        assertThat(metadata.groundingMetadata()).isNull();
        assertThat(metadata.urlContextMetadata()).isNull();
    }

    @Test
    void should_merge_grounding_metadata_fields_reported_by_different_chunks() {
        builder.append(chunkGroundedWith(GroundingMetadata.builder()
                .webSearchQueries(List.of("who won"))
                .searchEntryPoint(new SearchEntryPoint("<div>suggestions</div>", null))
                .build()));
        builder.append(chunkGroundedWith(GroundingMetadata.builder()
                .groundingChunks(List.of(webChunk("https://a.example")))
                .groundingSupports(List.of(supportOf(0)))
                .build()));

        GroundingMetadata grounding = groundingOf(builder.build());

        assertThat(grounding.webSearchQueries()).containsExactly("who won");
        assertThat(grounding.searchEntryPoint().renderedContent()).isEqualTo("<div>suggestions</div>");
        assertThat(grounding.groundingChunks()).containsExactly(webChunk("https://a.example"));
        assertThat(grounding.groundingSupports()).containsExactly(supportOf(0));
    }

    @Test
    void should_not_duplicate_grounding_metadata_a_later_chunk_repeats() {
        builder.append(chunkGroundedWith(GroundingMetadata.builder()
                .webSearchQueries(List.of("who won"))
                .groundingChunks(List.of(webChunk("https://a.example")))
                .groundingSupports(List.of(supportOf(0)))
                .build()));
        builder.append(chunkGroundedWith(GroundingMetadata.builder()
                .webSearchQueries(List.of("who won"))
                .groundingChunks(List.of(webChunk("https://a.example"), webChunk("https://b.example")))
                .groundingSupports(List.of(supportOf(0), supportOf(1)))
                .build()));

        GroundingMetadata grounding = groundingOf(builder.build());

        assertThat(grounding.webSearchQueries()).containsExactly("who won");
        assertThat(grounding.groundingChunks())
                .containsExactly(webChunk("https://a.example"), webChunk("https://b.example"));
        assertThat(grounding.groundingSupports()).containsExactly(supportOf(0), supportOf(1));
    }

    @Test
    void should_remap_grounding_chunk_indices_when_sources_arrive_across_chunks() {
        builder.append(chunkGroundedWith(GroundingMetadata.builder()
                .groundingChunks(List.of(webChunk("https://a.example")))
                .groundingSupports(List.of(supportOf(0)))
                .build()));
        builder.append(chunkGroundedWith(GroundingMetadata.builder()
                .groundingChunks(List.of(webChunk("https://b.example")))
                .groundingSupports(List.of(supportOf(0)))
                .build()));

        GroundingMetadata grounding = groundingOf(builder.build());

        assertThat(grounding.groundingChunks())
                .containsExactly(webChunk("https://a.example"), webChunk("https://b.example"));
        assertThat(grounding.groundingSupports()).containsExactly(supportOf(0), supportOf(1));
    }

    @Test
    void should_merge_url_context_metadata_reported_by_different_chunks() {
        builder.append(chunkWithUrlContext(new GeminiUrlMetadata("https://a.example", URL_RETRIEVAL_STATUS_SUCCESS)));
        builder.append(chunkWithUrlContext(new GeminiUrlMetadata("https://b.example", URL_RETRIEVAL_STATUS_SUCCESS)));

        GoogleAiGeminiChatResponseMetadata metadata =
                (GoogleAiGeminiChatResponseMetadata) builder.build().metadata();

        assertThat(metadata.urlContextMetadata().urlMetadata())
                .extracting(UrlContextMetadata.UrlMetadata::retrievedUrl)
                .containsExactly("https://a.example", "https://b.example");
    }

    @Test
    void should_report_the_latest_retrieval_status_of_a_url_repeated_across_chunks() {
        builder.append(chunkWithUrlContext(new GeminiUrlMetadata("https://a.example", URL_RETRIEVAL_STATUS_ERROR)));
        builder.append(chunkWithUrlContext(new GeminiUrlMetadata("https://a.example", URL_RETRIEVAL_STATUS_SUCCESS)));

        GoogleAiGeminiChatResponseMetadata metadata =
                (GoogleAiGeminiChatResponseMetadata) builder.build().metadata();

        assertThat(metadata.urlContextMetadata().urlMetadata())
                .containsExactly(new UrlContextMetadata.UrlMetadata(
                        "https://a.example", URL_RETRIEVAL_STATUS_SUCCESS.toString()));
    }

    private static GeminiPart imagePart(String base64Data) {
        return new GeminiPart(
                null, new GeminiBlob("image/png", base64Data), null, null, null, null, null, null, null, null);
    }

    private static GeminiGenerateContentResponse chunkWith(GeminiPart part) {
        GeminiContent content = new GeminiContent(List.of(part), "model");
        GeminiCandidate candidate = new GeminiCandidate(content, null, null, null, null);
        return new GeminiGenerateContentResponse("id-1", "gemini-pro", List.of(candidate), null, null, null);
    }

    private static GroundingMetadata groundingOf(ChatResponse response) {
        return ((GoogleAiGeminiChatResponseMetadata) response.metadata()).groundingMetadata();
    }

    private static GroundingChunk webChunk(String uri) {
        return new GroundingChunk(new GroundingChunk.Web(uri, "title"), null, null);
    }

    private static GroundingSupport supportOf(int groundingChunkIndex) {
        return new GroundingSupport(List.of(groundingChunkIndex), null, null);
    }

    private static GeminiGenerateContentResponse chunkGroundedWith(GroundingMetadata grounding) {
        GeminiCandidate candidate = new GeminiCandidate(null, null, null, grounding, null);
        return new GeminiGenerateContentResponse("id-1", "gemini-pro", List.of(candidate), null, null, null);
    }

    private static GeminiGenerateContentResponse chunkWithUrlContext(GeminiUrlMetadata urlMetadata) {
        GeminiCandidate candidate =
                new GeminiCandidate(null, null, new GeminiUrlContextMetadata(List.of(urlMetadata)), null, null);
        return new GeminiGenerateContentResponse("id-1", "gemini-pro", List.of(candidate), null, null, null);
    }
}
