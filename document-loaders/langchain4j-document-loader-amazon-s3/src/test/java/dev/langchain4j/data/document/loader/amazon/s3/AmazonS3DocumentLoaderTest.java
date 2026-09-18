package dev.langchain4j.data.document.loader.amazon.s3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentParser;
import dev.langchain4j.data.document.parser.TextDocumentParser;
import java.io.ByteArrayInputStream;
import java.util.List;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.http.AbortableInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.S3Object;
import software.amazon.awssdk.services.s3.paginators.ListObjectsV2Iterable;

class AmazonS3DocumentLoaderTest {

    private static final String BUCKET = "test-bucket";

    private final S3Client s3Client = mock(S3Client.class);
    private final AmazonS3DocumentLoader loader = new AmazonS3DocumentLoader(s3Client);
    private final DocumentParser parser = new TextDocumentParser();

    @Test
    void should_load_all_documents_across_paginated_responses() {

        // given: a truncated first page followed by a second page,
        // simulating a bucket whose objects span more than one ListObjectsV2 response (the >1000 keys case)
        ListObjectsV2Response page1 = ListObjectsV2Response.builder()
                .contents(S3Object.builder().key("a.txt").size(10L).build())
                .isTruncated(true)
                .nextContinuationToken("token")
                .build();
        ListObjectsV2Response page2 = ListObjectsV2Response.builder()
                .contents(S3Object.builder().key("b.txt").size(10L).build())
                .isTruncated(false)
                .build();

        // drive the real paginator so the actual continuation-token loop is exercised
        when(s3Client.listObjectsV2Paginator(any(ListObjectsV2Request.class)))
                .thenAnswer(invocation -> new ListObjectsV2Iterable(s3Client, invocation.getArgument(0)));
        when(s3Client.listObjectsV2(any(ListObjectsV2Request.class))).thenReturn(page1, page2);

        when(s3Client.getObject(
                        argThat((GetObjectRequest request) -> request != null && "a.txt".equals(request.key()))))
                .thenReturn(responseInputStream("content-a"));
        when(s3Client.getObject(
                        argThat((GetObjectRequest request) -> request != null && "b.txt".equals(request.key()))))
                .thenReturn(responseInputStream("content-b"));

        // when
        List<Document> documents = loader.loadDocuments(BUCKET, parser);

        // then: both pages are traversed and both objects are loaded
        assertThat(documents).extracting(Document::text).containsExactly("content-a", "content-b");
    }

    @Test
    void should_load_single_page_when_not_truncated() {

        // given
        ListObjectsV2Response onlyPage = ListObjectsV2Response.builder()
                .contents(S3Object.builder().key("a.txt").size(10L).build())
                .isTruncated(false)
                .build();

        when(s3Client.listObjectsV2Paginator(any(ListObjectsV2Request.class)))
                .thenAnswer(invocation -> new ListObjectsV2Iterable(s3Client, invocation.getArgument(0)));
        when(s3Client.listObjectsV2(any(ListObjectsV2Request.class))).thenReturn(onlyPage);
        when(s3Client.getObject(any(GetObjectRequest.class))).thenReturn(responseInputStream("content-a"));

        // when
        List<Document> documents = loader.loadDocuments(BUCKET, parser);

        // then
        assertThat(documents).extracting(Document::text).containsExactly("content-a");
    }

    @Test
    void should_return_empty_list_when_no_objects() {

        // given
        ListObjectsV2Response emptyPage =
                ListObjectsV2Response.builder().isTruncated(false).build();

        when(s3Client.listObjectsV2Paginator(any(ListObjectsV2Request.class)))
                .thenAnswer(invocation -> new ListObjectsV2Iterable(s3Client, invocation.getArgument(0)));
        when(s3Client.listObjectsV2(any(ListObjectsV2Request.class))).thenReturn(emptyPage);

        // when
        List<Document> documents = loader.loadDocuments(BUCKET, parser);

        // then
        assertThat(documents).isEmpty();
    }

    private static ResponseInputStream<GetObjectResponse> responseInputStream(String content) {
        return new ResponseInputStream<>(
                GetObjectResponse.builder().build(),
                AbortableInputStream.create(new ByteArrayInputStream(content.getBytes())));
    }
}
