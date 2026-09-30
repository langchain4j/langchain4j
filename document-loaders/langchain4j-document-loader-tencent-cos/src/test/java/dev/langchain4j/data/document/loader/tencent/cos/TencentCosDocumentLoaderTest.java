package dev.langchain4j.data.document.loader.tencent.cos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.qcloud.cos.COSClient;
import com.qcloud.cos.model.COSObject;
import com.qcloud.cos.model.COSObjectInputStream;
import com.qcloud.cos.model.COSObjectSummary;
import com.qcloud.cos.model.GetObjectRequest;
import com.qcloud.cos.model.ListObjectsRequest;
import com.qcloud.cos.model.ObjectListing;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentParser;
import dev.langchain4j.data.document.parser.TextDocumentParser;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.http.client.methods.HttpRequestBase;
import org.junit.jupiter.api.Test;

class TencentCosDocumentLoaderTest {

    private static final String BUCKET = "test-bucket";

    private final DocumentParser parser = new TextDocumentParser();

    @Test
    void should_throw_when_bucket_is_null() {
        TencentCosDocumentLoader loader = new TencentCosDocumentLoader(mock(COSClient.class));
        assertThatThrownBy(() -> loader.loadDocument(null, "some-key", parser))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void should_throw_when_bucket_is_blank() {
        TencentCosDocumentLoader loader = new TencentCosDocumentLoader(mock(COSClient.class));
        assertThatThrownBy(() -> loader.loadDocument("   ", "some-key", parser))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void should_throw_when_key_is_null() {
        TencentCosDocumentLoader loader = new TencentCosDocumentLoader(mock(COSClient.class));
        assertThatThrownBy(() -> loader.loadDocument("some-bucket", null, parser))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void should_throw_when_key_is_blank() {
        TencentCosDocumentLoader loader = new TencentCosDocumentLoader(mock(COSClient.class));
        assertThatThrownBy(() -> loader.loadDocument("some-bucket", "   ", parser))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void should_load_all_documents_across_paginated_responses() {

        ObjectListing page1 = mock(ObjectListing.class);
        COSObjectSummary summaryA = new COSObjectSummary();
        summaryA.setKey("a.txt");
        summaryA.setSize(10);
        when(page1.getObjectSummaries()).thenReturn(List.of(summaryA));
        when(page1.isTruncated()).thenReturn(true);

        ObjectListing page2 = mock(ObjectListing.class);
        COSObjectSummary summaryB = new COSObjectSummary();
        summaryB.setKey("b.txt");
        summaryB.setSize(20);
        when(page2.getObjectSummaries()).thenReturn(List.of(summaryB));
        when(page2.isTruncated()).thenReturn(false);

        List<String> loadedKeys = new ArrayList<>();
        COSClient cosClient = stubCosClient(Map.of("a.txt", "content-a", "b.txt", "content-b"), loadedKeys);
        when(cosClient.listObjects(any(ListObjectsRequest.class))).thenReturn(page1);
        when(cosClient.listNextBatchOfObjects(page1)).thenReturn(page2);

        TencentCosDocumentLoader loader = new TencentCosDocumentLoader(cosClient);

        List<Document> documents = loader.loadDocuments(BUCKET, parser);

        assertThat(documents).extracting(Document::text).containsExactly("content-a", "content-b");
        assertThat(loadedKeys).containsExactly("a.txt", "b.txt");
    }

    @Test
    void should_load_single_page_when_not_truncated() {

        ObjectListing onlyPage = mock(ObjectListing.class);
        COSObjectSummary summaryA = new COSObjectSummary();
        summaryA.setKey("a.txt");
        summaryA.setSize(10);
        when(onlyPage.getObjectSummaries()).thenReturn(List.of(summaryA));
        when(onlyPage.isTruncated()).thenReturn(false);

        List<String> loadedKeys = new ArrayList<>();
        COSClient cosClient = stubCosClient(Map.of("a.txt", "content-a"), loadedKeys);
        when(cosClient.listObjects(any(ListObjectsRequest.class))).thenReturn(onlyPage);

        TencentCosDocumentLoader loader = new TencentCosDocumentLoader(cosClient);

        List<Document> documents = loader.loadDocuments(BUCKET, parser);

        assertThat(documents).extracting(Document::text).containsExactly("content-a");
    }

    @Test
    void should_return_empty_list_when_no_objects() {

        ObjectListing emptyPage = mock(ObjectListing.class);
        when(emptyPage.getObjectSummaries()).thenReturn(List.of());
        when(emptyPage.isTruncated()).thenReturn(false);

        COSClient cosClient = mock(COSClient.class);
        when(cosClient.listObjects(any(ListObjectsRequest.class))).thenReturn(emptyPage);

        TencentCosDocumentLoader loader = new TencentCosDocumentLoader(cosClient);

        List<Document> documents = loader.loadDocuments(BUCKET, parser);

        assertThat(documents).isEmpty();
    }

    @Test
    void should_skip_directories_and_empty_files() {

        ObjectListing page = mock(ObjectListing.class);
        COSObjectSummary directoryMarker = new COSObjectSummary();
        directoryMarker.setKey("dir/");
        directoryMarker.setSize(0);
        COSObjectSummary emptyFile = new COSObjectSummary();
        emptyFile.setKey("empty.txt");
        emptyFile.setSize(0);
        COSObjectSummary realFile = new COSObjectSummary();
        realFile.setKey("real.txt");
        realFile.setSize(100);
        when(page.getObjectSummaries()).thenReturn(List.of(directoryMarker, emptyFile, realFile));
        when(page.isTruncated()).thenReturn(false);

        List<String> loadedKeys = new ArrayList<>();
        COSClient cosClient = stubCosClient(Map.of("real.txt", "real-content"), loadedKeys);
        when(cosClient.listObjects(any(ListObjectsRequest.class))).thenReturn(page);

        TencentCosDocumentLoader loader = new TencentCosDocumentLoader(cosClient);

        List<Document> documents = loader.loadDocuments(BUCKET, parser);

        assertThat(documents).hasSize(1);
        assertThat(documents.get(0).text()).isEqualTo("real-content");
    }

    private static COSClient stubCosClient(Map<String, String> keyToContent, List<String> loadedKeys) {
        COSClient cosClient = mock(COSClient.class);
        for (var entry : keyToContent.entrySet()) {
            when(cosClient.getObject(argThat((GetObjectRequest req) ->
                            req != null && entry.getKey().equals(req.getKey()))))
                    .thenAnswer(invocation -> {
                        loadedKeys.add(entry.getKey());
                        return objectWithContent(entry.getValue());
                    });
        }
        return cosClient;
    }

    private static COSObject objectWithContent(String content) {
        COSObject cosObject = new COSObject();
        cosObject.setObjectContent(
                new COSObjectInputStream(new ByteArrayInputStream(content.getBytes()), mock(HttpRequestBase.class)));
        return cosObject;
    }
}
