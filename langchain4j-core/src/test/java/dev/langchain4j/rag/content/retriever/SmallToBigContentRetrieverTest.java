package dev.langchain4j.rag.content.retriever;

import static dev.langchain4j.rag.content.ContentMetadata.SCORE;
import static dev.langchain4j.rag.content.retriever.SmallToBigContentRetriever.ExpansionMode.SIBLINGS;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.query.Query;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class SmallToBigContentRetrieverTest {

    private static final Query QUERY = Query.from("query");

    @Test
    void should_replace_children_with_unique_parents_and_preserve_ranking_metadata() {
        Content firstChild = child("child 1", "parent-1", 0.9);
        Content secondChildOfSameParent = child("child 2", "parent-1", 0.8);
        Content childWithoutParent = Content.from("standalone");
        Content parent = Content.from("parent 1");
        AtomicReference<Collection<String>> requestedIds = new AtomicReference<>();

        SmallToBigContentRetriever<String> retriever = SmallToBigContentRetriever.<String>builder()
                .childRetriever(ignored -> List.of(firstChild, secondChildOfSameParent, childWithoutParent))
                .parentIdProvider(content -> content.textSegment().metadata().getString("parentId"))
                .parentContentProvider(ids -> {
                    requestedIds.set(ids);
                    return Map.of("parent-1", parent);
                })
                .build();

        List<Content> result = retriever.retrieve(QUERY);

        assertThat(requestedIds.get()).containsExactly("parent-1");
        assertThat(result)
                .extracting(content -> content.textSegment().text())
                .containsExactly("parent 1", "standalone");
        assertThat(result.get(0).metadata()).containsEntry(SCORE, 0.9);
    }

    @Test
    void should_expand_to_a_deduplicated_sibling_window_in_document_order() {
        List<Content> siblings = List.of(
                child("zero", "parent-1", 0),
                child("one", "parent-1", 0),
                child("two", "parent-1", 0),
                child("three", "parent-1", 0),
                child("four", "parent-1", 0));
        Content hitTwo = child("two", "parent-1", 0.9);
        Content hitThree = child("three", "parent-1", 0.8);

        SmallToBigContentRetriever<String> retriever = SmallToBigContentRetriever.<String>builder()
                .childRetriever(ignored -> List.of(hitTwo, hitThree))
                .parentIdProvider(content -> content.textSegment().metadata().getString("parentId"))
                .expansionMode(SIBLINGS)
                .siblingContentProvider(ids -> Map.of("parent-1", siblings))
                .siblingWindow(1, 1)
                .build();

        assertThat(retriever.retrieve(QUERY))
                .extracting(content -> content.textSegment().text())
                .containsExactly("one", "two", "three", "four");
    }

    @Test
    void should_resolve_all_parent_groups_in_one_batch_and_keep_missing_children() {
        Content childOne = child("child 1", "parent-1", 0.9);
        Content childTwo = child("child 2", "parent-2", 0.8);
        AtomicReference<Collection<String>> requestedIds = new AtomicReference<>();

        SmallToBigContentRetriever<String> retriever = SmallToBigContentRetriever.<String>builder()
                .childRetriever(ignored -> List.of(childOne, childTwo))
                .parentIdProvider(content -> content.textSegment().metadata().getString("parentId"))
                .parentContentProvider(ids -> {
                    requestedIds.set(ids);
                    Map<String, Content> result = new LinkedHashMap<>();
                    result.put("parent-1", Content.from("parent 1"));
                    return result;
                })
                .build();

        assertThat(retriever.retrieve(QUERY))
                .extracting(content -> content.textSegment().text())
                .containsExactly("parent 1", "child 2");
        assertThat(requestedIds.get()).containsExactly("parent-1", "parent-2");
    }

    private static Content child(String text, String parentId, double score) {
        return Content.from(TextSegment.from(text, Metadata.from("parentId", parentId)), Map.of(SCORE, score));
    }
}
