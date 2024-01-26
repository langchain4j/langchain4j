package dev.langchain4j.rag.content.aggregator;

import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.query.Query;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Default implementation of {@link ContentAggregator} intended to be suitable for the majority of use cases.
 * <br>
 * <br>
 * It's important to note that while efforts will be made to avoid breaking changes,
 * the default behavior of this class may be updated in the future if it's found
 * that the current behavior does not adequately serve the majority of use cases.
 * Such changes would be made to benefit both current and future users.
 * <br>
 * <br>
 * This implementation employs Reciprocal Rank Fusion (see {@link ReciprocalRankFuser}) in two stages
 * to aggregate all {@code Collection<List<Content>>} into a single {@code List<Content>}.
 * The {@link Content}s in both the input and output lists are expected to be sorted by relevance,
 * with the most relevant {@link Content}s at the beginning of the {@code List<Content>}.
 * <br>
 * Stage 1: For each {@link Query}, all {@code List<Content>} retrieved with that {@link Query}
 * are merged into a single {@code List<Content>}.
 * <br>
 * Stage 2: All {@code List<Content>} (results from stage 1) are merged into a single {@code List<Content>}.
 * <br>
 * <br>
 * <b>Example:</b>
 * <br>
 * Input (query -&gt; multiple lists with ranked contents):
 * <pre>
 * home animals -&gt; [cat, dog, hamster], [cat, parrot]
 * domestic animals -&gt; [dog, horse], [cat]
 * </pre>
 * After stage 1 (query -&gt; single list with ranked contents):
 * <br>
 * <pre>
 * home animals -&gt; [cat, dog, parrot, hamster]
 * domestic animals -&gt; [dog, cat, horse]
 * </pre>
 * After stage 2 (single list with ranked contents):
 * <br>
 * <pre>
 * [cat, dog, parrot, horse, hamster]
 * </pre>
 *
 * @see ReciprocalRankFuser
 * @see ReRankingContentAggregator
 */
public class DefaultContentAggregator implements ContentAggregator {

    @Override
    public List<Content> aggregate(Map<Query, Collection<List<Content>>> queryToContents) {

        // First, for each query, fuse all contents retrieved from different sources using that query.
        Map<Query, List<Content>> fused = fuse(queryToContents);

        // Then, fuse all contents retrieved using all queries
        return ReciprocalRankFuser.fuse(fused.values());
    }

    protected Map<Query, List<Content>> fuse(Map<Query, Collection<List<Content>>> queryToContents) {
        Map<Query, List<Content>> fused = new LinkedHashMap<>();
        for (Query query : queryToContents.keySet()) {
            Collection<List<Content>> contents = queryToContents.get(query);
            fused.put(query, ReciprocalRankFuser.fuse(contents));
        }
        return fused;
    }
}
