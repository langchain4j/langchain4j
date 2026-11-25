package dev.langchain4j.rag.content.retriever;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.query.Query;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Function;

/**
 * Retrieves small child segments and replaces or expands them with larger context.
 *
 * <p>Two expansion strategies are supported:
 * <ul>
 *   <li>{@link ExpansionMode#PARENT}: replace child hits with their parent content.</li>
 *   <li>{@link ExpansionMode#SIBLINGS}: return a configurable window of ordered sibling segments around each hit.
 *       This avoids injecting an entire parent when it is too large.</li>
 * </ul>
 *
 * <p>Context is resolved in one batch for all unique parent IDs. Multiple child hits belonging to the same parent are
 * deduplicated while the ranking of the highest-ranked child is preserved.
 *
 * @param <K> the type used to identify a parent or sibling group
 */
public class SmallToBigContentRetriever<K> implements ContentRetriever {

    public enum ExpansionMode {
        PARENT,
        SIBLINGS
    }

    public enum MissingContextPolicy {
        KEEP_CHILD,
        DROP_CHILD,
        FAIL
    }

    private final ContentRetriever childRetriever;
    private final Function<Content, K> parentIdProvider;
    private final ExpansionMode expansionMode;
    private final Function<Collection<K>, Map<K, Content>> parentContentProvider;
    private final Function<Collection<K>, Map<K, List<Content>>> siblingContentProvider;
    private final int siblingsBefore;
    private final int siblingsAfter;
    private final BiPredicate<Content, Content> childMatcher;
    private final MissingContextPolicy missingContextPolicy;

    private SmallToBigContentRetriever(Builder<K> builder) {
        this.childRetriever = ensureNotNull(builder.childRetriever, "childRetriever");
        this.parentIdProvider = ensureNotNull(builder.parentIdProvider, "parentIdProvider");
        this.expansionMode = ensureNotNull(builder.expansionMode, "expansionMode");
        this.parentContentProvider = builder.parentContentProvider;
        this.siblingContentProvider = builder.siblingContentProvider;
        this.siblingsBefore = ensureNotNegative(builder.siblingsBefore, "siblingsBefore");
        this.siblingsAfter = ensureNotNegative(builder.siblingsAfter, "siblingsAfter");
        this.childMatcher = ensureNotNull(builder.childMatcher, "childMatcher");
        this.missingContextPolicy = ensureNotNull(builder.missingContextPolicy, "missingContextPolicy");

        if (expansionMode == ExpansionMode.PARENT) {
            ensureNotNull(parentContentProvider, "parentContentProvider");
        } else {
            ensureNotNull(siblingContentProvider, "siblingContentProvider");
        }
    }

    @Override
    public List<Content> retrieve(Query query) {
        List<Content> children = childRetriever.retrieve(query);
        if (children.isEmpty()) {
            return children;
        }

        Map<K, List<RankedContent>> childrenByParent = new LinkedHashMap<>();
        List<RankedContent> expanded = new ArrayList<>();
        for (int rank = 0; rank < children.size(); rank++) {
            Content child = children.get(rank);
            K parentId = parentIdProvider.apply(child);
            if (parentId == null) {
                expanded.add(new RankedContent(rank, 0, child));
            } else {
                childrenByParent
                        .computeIfAbsent(parentId, ignored -> new ArrayList<>())
                        .add(new RankedContent(rank, 0, child));
            }
        }

        if (expansionMode == ExpansionMode.PARENT) {
            expandParents(childrenByParent, expanded);
        } else {
            expandSiblings(childrenByParent, expanded);
        }

        return expanded.stream()
                .sorted(Comparator.comparingInt(RankedContent::rank).thenComparingInt(RankedContent::sequence))
                .map(RankedContent::content)
                .toList();
    }

    private void expandParents(Map<K, List<RankedContent>> childrenByParent, List<RankedContent> expanded) {
        Map<K, Content> parents =
                ensureNotNull(parentContentProvider.apply(childrenByParent.keySet()), "parentContentProvider result");
        childrenByParent.forEach((parentId, children) -> {
            Content parent = parents.get(parentId);
            if (parent == null) {
                handleMissing(parentId, children, expanded);
            } else {
                RankedContent bestChild = children.get(0);
                expanded.add(new RankedContent(
                        bestChild.rank(),
                        0,
                        Content.from(parent.textSegment(), bestChild.content().metadata())));
            }
        });
    }

    private void expandSiblings(Map<K, List<RankedContent>> childrenByParent, List<RankedContent> expanded) {
        Map<K, List<Content>> siblingsByParent =
                ensureNotNull(siblingContentProvider.apply(childrenByParent.keySet()), "siblingContentProvider result");
        childrenByParent.forEach((parentId, children) -> {
            List<Content> siblings = siblingsByParent.get(parentId);
            if (siblings == null || siblings.isEmpty()) {
                handleMissing(parentId, children, expanded);
                return;
            }

            Set<Integer> selectedIndexes = new LinkedHashSet<>();
            List<RankedContent> unmatchedChildren = new ArrayList<>();
            for (RankedContent child : children) {
                int siblingIndex = findSiblingIndex(siblings, child.content());
                if (siblingIndex < 0) {
                    unmatchedChildren.add(child);
                    continue;
                }
                int from = Math.max(0, siblingIndex - siblingsBefore);
                int to = Math.min(siblings.size() - 1, siblingIndex + siblingsAfter);
                for (int i = from; i <= to; i++) {
                    selectedIndexes.add(i);
                }
            }

            if (selectedIndexes.isEmpty()) {
                handleMissing(parentId, children, expanded);
                return;
            }

            RankedContent bestChild = children.get(0);
            int sequence = 0;
            for (int index : selectedIndexes.stream().sorted().toList()) {
                Content sibling = siblings.get(index);
                expanded.add(new RankedContent(
                        bestChild.rank(),
                        sequence++,
                        Content.from(sibling.textSegment(), bestChild.content().metadata())));
            }
            expanded.addAll(unmatchedChildren);
        });
    }

    private int findSiblingIndex(List<Content> siblings, Content child) {
        for (int i = 0; i < siblings.size(); i++) {
            if (childMatcher.test(child, siblings.get(i))) {
                return i;
            }
        }
        return -1;
    }

    private void handleMissing(K parentId, List<RankedContent> children, List<RankedContent> expanded) {
        switch (missingContextPolicy) {
            case KEEP_CHILD -> expanded.addAll(children);
            case DROP_CHILD -> {
                // Intentionally empty.
            }
            case FAIL -> throw new IllegalStateException("No larger context found for parent ID: " + parentId);
        }
    }

    private static int ensureNotNegative(int value, String name) {
        if (value < 0) {
            throw new IllegalArgumentException(name + " cannot be negative");
        }
        return value;
    }

    private record RankedContent(int rank, int sequence, Content content) {}

    public static <K> Builder<K> builder() {
        return new Builder<>();
    }

    public static class Builder<K> {

        private ContentRetriever childRetriever;
        private Function<Content, K> parentIdProvider;
        private ExpansionMode expansionMode = ExpansionMode.PARENT;
        private Function<Collection<K>, Map<K, Content>> parentContentProvider;
        private Function<Collection<K>, Map<K, List<Content>>> siblingContentProvider;
        private int siblingsBefore = 1;
        private int siblingsAfter = 1;
        private BiPredicate<Content, Content> childMatcher = Content::equals;
        private MissingContextPolicy missingContextPolicy = MissingContextPolicy.KEEP_CHILD;

        public Builder<K> childRetriever(ContentRetriever childRetriever) {
            this.childRetriever = childRetriever;
            return this;
        }

        /** Extracts the parent or sibling-group ID from each retrieved child. Returning {@code null} keeps the child. */
        public Builder<K> parentIdProvider(Function<Content, K> parentIdProvider) {
            this.parentIdProvider = parentIdProvider;
            return this;
        }

        public Builder<K> expansionMode(ExpansionMode expansionMode) {
            this.expansionMode = expansionMode;
            return this;
        }

        /** Sets a batch provider used by {@link ExpansionMode#PARENT}. */
        public Builder<K> parentContentProvider(Function<Collection<K>, Map<K, Content>> parentContentProvider) {
            this.parentContentProvider = parentContentProvider;
            return this;
        }

        /** Sets a batch provider of siblings in document order, used by {@link ExpansionMode#SIBLINGS}. */
        public Builder<K> siblingContentProvider(
                Function<Collection<K>, Map<K, List<Content>>> siblingContentProvider) {
            this.siblingContentProvider = siblingContentProvider;
            return this;
        }

        /** Sets how many siblings before and after each child hit are returned. */
        public Builder<K> siblingWindow(int siblingsBefore, int siblingsAfter) {
            this.siblingsBefore = siblingsBefore;
            this.siblingsAfter = siblingsAfter;
            return this;
        }

        /**
         * Sets how a retrieved child is located in the ordered sibling list. The default uses
         * {@link Object#equals(Object)}.
         */
        public Builder<K> childMatcher(BiPredicate<Content, Content> childMatcher) {
            this.childMatcher = childMatcher;
            return this;
        }

        public Builder<K> missingContextPolicy(MissingContextPolicy missingContextPolicy) {
            this.missingContextPolicy = missingContextPolicy;
            return this;
        }

        public SmallToBigContentRetriever<K> build() {
            return new SmallToBigContentRetriever<>(this);
        }
    }
}
