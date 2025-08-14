package dev.langchain4j.rag;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.aggregator.ContentAggregator;
import dev.langchain4j.rag.content.injector.ContentInjector;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Metadata;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.rag.query.router.DefaultQueryRouter;
import dev.langchain4j.rag.query.router.QueryRouter;
import dev.langchain4j.rag.query.transformer.DefaultQueryTransformer;
import dev.langchain4j.rag.query.transformer.QueryTransformer;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.MockedStatic;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class DefaultRetrievalAugmentorTest {

    private static MockedStatic<LoggerFactory> loggerFactoryMock;

    @BeforeAll
    static void mockLogger() {
        loggerFactoryMock = mockStatic(LoggerFactory.class);
        Logger logger = mock(Logger.class);
        when(LoggerFactory.getLogger(DefaultRetrievalAugmentor.class)).thenReturn(logger);
        when(logger.isTraceEnabled()).thenReturn(true);
    }

    @AfterAll
    static void releaseLogger() {
        loggerFactoryMock.close();
    }

    @ParameterizedTest
    @MethodSource("executors")
    void should_augment_user_message__multiple_queries_multiple_retrievers(Executor executor) {

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
        AugmentationResult result = retrievalAugmentor.augment(new AugmentationRequest(userMessage, metadata));

        // then
        UserMessage augmented = (UserMessage) result.chatMessage();
        assertThat(augmented.singleText())
                .isEqualTo(
                        """
                query
                content 1
                content 2
                content 3
                content 4
                content 1
                content 2
                content 3
                content 4""");

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
        queryToContents.put(query1, asList(asList(content1, content2), asList(content3, content4)));

        queryToContents.put(query2, asList(asList(content1, content2), asList(content3, content4)));

        verify(contentAggregator).aggregate(queryToContents);
        verifyNoMoreInteractions(contentAggregator);

        verify(contentInjector)
                .inject(
                        asList(content1, content2, content3, content4, content1, content2, content3, content4),
                        userMessage);
        verify(contentInjector)
                .inject(
                        asList(content1, content2, content3, content4, content1, content2, content3, content4),
                        (ChatMessage) userMessage);
        verifyNoMoreInteractions(contentInjector);
    }

    @Test
    void should_augment_user_message__single_query_multiple_retrievers() {

        // given
        QueryTransformer queryTransformer = spy(new DefaultQueryTransformer());

        Content content1 = Content.from("content 1");
        Content content2 = Content.from("content 2");
        ContentRetriever contentRetriever1 = spy(new TestContentRetriever(content1, content2));

        Content content3 = Content.from("content 3");
        Content content4 = Content.from("content 4");
        ContentRetriever contentRetriever2 = spy(new TestContentRetriever(content3, content4));

        QueryRouter queryRouter = spy(new DefaultQueryRouter(contentRetriever1, contentRetriever2));

        ContentAggregator contentAggregator = spy(new TestContentAggregator());

        ContentInjector contentInjector = spy(new TestContentInjector());

        Executor executor = spy(new TestExecutor());

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
        AugmentationResult result = retrievalAugmentor.augment(new AugmentationRequest(userMessage, metadata));

        // then
        UserMessage augmented = (UserMessage) result.chatMessage();
        assertThat(augmented.singleText())
                .isEqualTo(
                        """
                query
                content 1
                content 2
                content 3
                content 4""");

        Query query = Query.from("query", metadata);
        verify(queryTransformer).transform(query);
        verifyNoMoreInteractions(queryTransformer);

        verify(queryRouter).route(query);
        verifyNoMoreInteractions(queryRouter);

        verify(contentRetriever1).retrieve(query);
        verifyNoMoreInteractions(contentRetriever1);

        verify(contentRetriever2).retrieve(query);
        verifyNoMoreInteractions(contentRetriever2);

        Map<Query, Collection<List<Content>>> queryToContents = new HashMap<>();
        queryToContents.put(query, asList(asList(content1, content2), asList(content3, content4)));

        verify(contentAggregator).aggregate(queryToContents);
        verifyNoMoreInteractions(contentAggregator);

        verify(contentInjector).inject(asList(content1, content2, content3, content4), userMessage);
        verify(contentInjector).inject(asList(content1, content2, content3, content4), (ChatMessage) userMessage);
        verifyNoMoreInteractions(contentInjector);

        verify(executor, times(2)).execute(any());
        verifyNoMoreInteractions(executor);
    }

    private static class TestExecutor implements Executor {

        @Override
        public void execute(Runnable command) {
            command.run();
        }
    }

    @Test
    void should_augment_user_message__single_query_single_retriever() {

        // given
        QueryTransformer queryTransformer = spy(new DefaultQueryTransformer());

        Content content1 = Content.from("content 1");
        Content content2 = Content.from("content 2");
        ContentRetriever contentRetriever = spy(new TestContentRetriever(content1, content2));

        QueryRouter queryRouter = spy(new DefaultQueryRouter(contentRetriever));

        ContentAggregator contentAggregator = spy(new TestContentAggregator());

        ContentInjector contentInjector = spy(new TestContentInjector());

        Executor executor = mock(Executor.class);

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
        AugmentationResult result = retrievalAugmentor.augment(new AugmentationRequest(userMessage, metadata));

        // then
        UserMessage augmented = (UserMessage) result.chatMessage();
        assertThat(augmented.singleText())
                .isEqualTo("""
                query
                content 1
                content 2""");

        Query query = Query.from("query", metadata);
        verify(queryTransformer).transform(query);
        verifyNoMoreInteractions(queryTransformer);

        verify(queryRouter).route(query);
        verifyNoMoreInteractions(queryRouter);

        verify(contentRetriever).retrieve(query);
        verifyNoMoreInteractions(contentRetriever);

        Map<Query, Collection<List<Content>>> queryToContents = new HashMap<>();
        queryToContents.put(query, singletonList(asList(content1, content2)));
        verify(contentAggregator).aggregate(queryToContents);
        verifyNoMoreInteractions(contentAggregator);

        verify(contentInjector).inject(asList(content1, content2), userMessage);
        verify(contentInjector).inject(asList(content1, content2), (ChatMessage) userMessage);
        verifyNoMoreInteractions(contentInjector);

        verifyNoInteractions(executor);
    }

    @ParameterizedTest
    @MethodSource("executors")
    void should_not_augment_when_router_does_not_return_retrievers(Executor executor) {

        // given
        List<ContentRetriever> retrievers = emptyList();
        QueryRouter queryRouter = spy(new TestQueryRouter(retrievers));

        RetrievalAugmentor retrievalAugmentor = DefaultRetrievalAugmentor.builder()
                .queryRouter(queryRouter)
                .executor(executor)
                .build();

        UserMessage userMessage = UserMessage.from("query");

        Metadata metadata = Metadata.from(userMessage, null, null);

        // when
        AugmentationResult result = retrievalAugmentor.augment(new AugmentationRequest(userMessage, metadata));

        // then
        UserMessage augmented = (UserMessage) result.chatMessage();
        assertThat(augmented).isEqualTo(userMessage);

        verify(queryRouter).route(Query.from("query", metadata));
        verifyNoMoreInteractions(queryRouter);
    }

    static Stream<Executor> executors() {
        return Stream.<Executor>builder()
                .add(Executors.newCachedThreadPool())
                .add(Executors.newFixedThreadPool(1))
                .add(Executors.newFixedThreadPool(2))
                .add(Executors.newFixedThreadPool(3))
                .add(Executors.newFixedThreadPool(4))
                .add(Runnable::run) // same thread executor
                .add(null) // to use default Executor in DefaultRetrievalAugmentor
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

    static class TestQueryRouter implements QueryRouter {

        private final Collection<ContentRetriever> retrievers;

        TestQueryRouter(Collection<ContentRetriever> retrievers) {
            this.retrievers = retrievers;
        }

        @Override
        public Collection<ContentRetriever> route(Query query) {
            return retrievers;
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
            return queryToContents.values().stream()
                    .flatMap(Collection::stream)
                    .flatMap(List::stream)
                    .collect(toList());
        }
    }

    static class TestContentInjector implements ContentInjector {

        @Override
        public ChatMessage inject(List<Content> contents, ChatMessage chatMessage) {
            String joinedContents =
                    contents.stream().map(it -> it.textSegment().text()).collect(joining("\n"));
            return UserMessage.from(((UserMessage) chatMessage).singleText() + "\n" + joinedContents);
        }
    }
}
