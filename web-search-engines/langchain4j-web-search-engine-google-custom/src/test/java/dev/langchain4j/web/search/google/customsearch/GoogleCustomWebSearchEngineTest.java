package dev.langchain4j.web.search.google.customsearch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.google.api.services.customsearch.v1.model.Result;
import com.google.api.services.customsearch.v1.model.Search;
import dev.langchain4j.web.search.WebSearchOrganicResult;
import java.util.List;
import org.junit.jupiter.api.Test;

class GoogleCustomWebSearchEngineTest {

    // createUriSafely returns null for this input even after percent-encoding,
    // see GoogleCustomWebSearchUtilsTest#createUriSafely_withSeverelyInvalidUri_returnsNull
    private static final String UNRESOLVABLE_LINK = "ht[tp://example.com with spaces and [brackets] and {braces}";

    private static Result organicItem(String title, String link) {
        return new Result().setTitle(title).setLink(link).setSnippet("snippet of " + title);
    }

    private static Result imageItem(String title, String link, String contextLink, String thumbnailLink) {
        return new Result()
                .setTitle(title)
                .setLink(link)
                .setImage(new Result.Image().setContextLink(contextLink).setThumbnailLink(thumbnailLink));
    }

    @Test
    void toWebSearchOrganicResults_mapsAllValidResults() {
        Search search = new Search()
                .setItems(List.of(
                        organicItem("First", "https://example.com/a"), organicItem("Second", "https://example.com/b")));

        List<WebSearchOrganicResult> results = GoogleCustomWebSearchEngine.toWebSearchOrganicResults(search, false);

        assertThat(results).hasSize(2);
        assertThat(results)
                .extracting(r -> r.url().toString())
                .containsExactly("https://example.com/a", "https://example.com/b");
    }

    @Test
    void toWebSearchOrganicResults_skipsResultWithUnresolvableUriAndKeepsTheRest() {
        Search search = new Search()
                .setItems(List.of(
                        organicItem("Valid before", "https://example.com/a"),
                        organicItem("Broken", UNRESOLVABLE_LINK),
                        organicItem("Valid after", "https://example.com/b")));

        List<WebSearchOrganicResult> results = GoogleCustomWebSearchEngine.toWebSearchOrganicResults(search, false);

        assertThat(results).hasSize(2);
        assertThat(results).extracting(WebSearchOrganicResult::title).containsExactly("Valid before", "Valid after");
    }

    @Test
    void toWebSearchOrganicResults_withNullItems_returnsEmptyList() {
        assertThat(GoogleCustomWebSearchEngine.toWebSearchOrganicResults(new Search(), false))
                .isEmpty();
    }

    @Test
    void toImageSearchResults_mapsAllValidResults() {
        Search search = new Search()
                .setItems(List.of(
                        imageItem(
                                "First",
                                "https://example.com/a.png",
                                "https://example.com/a",
                                "https://example.com/a-thumb.png"),
                        imageItem(
                                "Second",
                                "https://example.com/b.png",
                                "https://example.com/b",
                                "https://example.com/b-thumb.png")));

        List<GoogleCustomWebSearchEngine.ImageSearchResult> results =
                GoogleCustomWebSearchEngine.toImageSearchResults(search);

        assertThat(results).hasSize(2);
        assertThat(results)
                .extracting(r -> r.imageLink().toString())
                .containsExactly("https://example.com/a.png", "https://example.com/b.png");
    }

    @Test
    void toImageSearchResults_skipsResultWithUnresolvableImageLinkAndKeepsTheRest() {
        Search search = new Search()
                .setItems(List.of(
                        imageItem(
                                "Valid",
                                "https://example.com/a.png",
                                "https://example.com/a",
                                "https://example.com/a-thumb.png"),
                        imageItem(
                                "Broken",
                                UNRESOLVABLE_LINK,
                                "https://example.com/b",
                                "https://example.com/b-thumb.png")));

        List<GoogleCustomWebSearchEngine.ImageSearchResult> results =
                GoogleCustomWebSearchEngine.toImageSearchResults(search);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).title()).isEqualTo("Valid");
    }

    @Test
    void toImageSearchResults_keepsResultWhenOnlyContextAndThumbnailLinksAreUnresolvable() {
        Search search = new Search()
                .setItems(List.of(imageItem(
                        "Valid main link", "https://example.com/a.png", UNRESOLVABLE_LINK, UNRESOLVABLE_LINK)));

        List<GoogleCustomWebSearchEngine.ImageSearchResult> results =
                GoogleCustomWebSearchEngine.toImageSearchResults(search);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).imageLink().toString()).isEqualTo("https://example.com/a.png");
        assertThat(results.get(0).contextLink()).isNull();
        assertThat(results.get(0).thumbnailLink()).isNull();
    }

    @Test
    void toImageSearchResults_withNullItems_returnsEmptyList() {
        assertThat(GoogleCustomWebSearchEngine.toImageSearchResults(new Search()))
                .isEmpty();
    }

    @Test
    void mappingDoesNotThrowWhenEveryLinkIsUnresolvable() {
        Search organic = new Search().setItems(List.of(organicItem("Broken", UNRESOLVABLE_LINK)));
        Search images = new Search()
                .setItems(List.of(imageItem("Broken", UNRESOLVABLE_LINK, UNRESOLVABLE_LINK, UNRESOLVABLE_LINK)));

        assertThatCode(() -> {
                    assertThat(GoogleCustomWebSearchEngine.toWebSearchOrganicResults(organic, false))
                            .isEmpty();
                    assertThat(GoogleCustomWebSearchEngine.toImageSearchResults(images))
                            .isEmpty();
                })
                .doesNotThrowAnyException();
    }
}
