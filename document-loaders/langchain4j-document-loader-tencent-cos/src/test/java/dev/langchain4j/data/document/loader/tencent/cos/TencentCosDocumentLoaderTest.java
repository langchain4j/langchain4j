package dev.langchain4j.data.document.loader.tencent.cos;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.qcloud.cos.COSClient;
import dev.langchain4j.data.document.DocumentParser;
import org.junit.jupiter.api.Test;

class TencentCosDocumentLoaderTest {

    private final COSClient cosClient = mock(COSClient.class);
    private final DocumentParser parser = mock(DocumentParser.class);
    private final TencentCosDocumentLoader loader = new TencentCosDocumentLoader(cosClient);

    @Test
    void should_throw_when_bucket_is_null() {
        assertThatThrownBy(() -> loader.loadDocument(null, "some-key", parser))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void should_throw_when_bucket_is_blank() {
        assertThatThrownBy(() -> loader.loadDocument("   ", "some-key", parser))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void should_throw_when_key_is_null() {
        assertThatThrownBy(() -> loader.loadDocument("some-bucket", null, parser))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void should_throw_when_key_is_blank() {
        assertThatThrownBy(() -> loader.loadDocument("some-bucket", "   ", parser))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
