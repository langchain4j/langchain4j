package dev.langchain4j.rag;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.aggregator.ContentAggregator;
import dev.langchain4j.rag.content.injector.ContentInjector;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Metadata;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.rag.query.router.DefaultQueryRouter;
import dev.langchain4j.rag.query.router.QueryRouter;
import dev.langchain4j.rag.query.transformer.QueryTransformer;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.stream.Stream;

import static java.util.Arrays.asList;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class DefaultRetrievalAugmentorTest {

    @ParameterizedTest
    @MethodSource
    void should_augment_user_message(Executor executor) {

        // given
        Query query1 = Query.from("query 1");
        Query query2 = Query.from("query 2");
        QueryTransformer queryTransformer = spy(new TestQueryTransformer(query1, query2));

        Content content1 = Content.from("content 1");
        Content content2 = Content.from("content 2");
        ContentRetriever contentRetriever1 = spy(new TestContentRetriever(content1, content2));

        Content content3 = Content.from("content 3");
        Content content4 = Content.from("content 4");
        ContentRetriever contentRetriever2 = spy(new TestContentRetriever(content3, content4));

        QueryRouter queryRouter = spy(new DefaultQueryRouter(contentRetriever1, contentRetriever2));

        ContentAggregator contentAggregator = spy(new TestContentAggregator());

        ContentInjector contentInjector = spy(new TestContentInjector());

        RetrievalAugmentor retrievalAugmentor = DefaultRetrievalAugmentor.builder()
                .queryTransformer(queryTransformer)
                .queryRouter(queryRouter)
                .contentAggregator(contentAggregator)
                .contentInjector(contentInjector)
                .executor(executor)
                .build();

        UserMessage userMessage = UserMessage.from("query");

        Metadata metadata = Metadata.from(userMessage, null, null);

        // when
        UserMessage augmented = retrievalAugmentor.augment(userMessage, metadata);

        // then
        assertThat(augmented.text()).isEqualTo(
                "query\n" +
                        "content 1\n" +
                        "content 2\n" +
                        "content 3\n" +
                        "content 4\n" +
                        "content 1\n" +
                        "content 2\n" +
                        "content 3\n" +
                        "content 4"
        );

        verify(queryTransformer).transform(Query.from("query", metadata));
        verifyNoMoreInteractions(queryTransformer);

        verify(queryRouter).route(query1);
        verify(queryRouter).route(query2);
        verifyNoMoreInteractions(queryRouter);

        verify(contentRetriever1).retrieve(query1);
        verify(contentRetriever1).retrieve(query2);
        verifyNoMoreInteractions(contentRetriever1);

        verify(contentRetriever2).retrieve(query1);
        verify(contentRetriever2).retrieve(query2);
        verifyNoMoreInteractions(contentRetriever2);

        Map<Query, Collection<List<Content>>> queryToContents = new HashMap<>();
        queryToContents.put(query1, asList(
                asList(content1, content2),
                asList(content3, content4)

        ));
        queryToContents.put(query2, asList(
                asList(content1, content2),
                asList(content3, content4)

        ));
        verify(contentAggregator).aggregate(queryToContents);
        verifyNoMoreInteractions(contentAggregator);

        verify(contentInjector).inject(asList(
                content1, content2, content3, content4,
                content1, content2, content3, content4
        ), userMessage);
        verifyNoMoreInteractions(contentInjector);
    }

    static Stream<Arguments> should_augment_user_message() {
        return Stream.<Arguments>builder()
                .add(Arguments.of(Executors.newCachedThreadPool()))
                .add(Arguments.of(Executors.newFixedThreadPool(1)))
                .add(Arguments.of(Executors.newFixedThreadPool(2)))
                .add(Arguments.of(Executors.newFixedThreadPool(3)))
                .add(Arguments.of(Executors.newFixedThreadPool(4)))
                .build();
    }

    static class TestQueryTransformer implements QueryTransformer {

        private final List<Query> queries;

        TestQueryTransformer(Query... queries) {
            this.queries = asList(queries);
        }

        @Override
        public Collection<Query> transform(Query query) {
            return queries;
        }
    }

    static class TestContentRetriever implements ContentRetriever {

        private final List<Content> contents;

        TestContentRetriever(Content... contents) {
            this.contents = asList(contents);
        }

        @Override
        public List<Content> retrieve(Query query) {
            return contents;
        }
    }

    static class TestContentAggregator implements ContentAggregator {

        @Override
        public List<Content> aggregate(Map<Query, Collection<List<Content>>> queryToContents) {
            return queryToContents.values()
                    .stream()
                    .flatMap(Collection::stream)
                    .flatMap(List::stream)
                    .collect(toList());
        }
    }

    static class TestContentInjector implements ContentInjector {

        @Override
        public UserMessage inject(List<Content> contents, UserMessage userMessage) {
            String joinedContents = contents.stream()
                    .map(it -> it.textSegment().text())
                    .collect(joining("\n"));
            return UserMessage.from(userMessage.text() + "\n" + joinedContents);
        }
    }
}