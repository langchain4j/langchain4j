package dev.langchain4j.store.embedding.milvus.v2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.milvus.v2.client.MilvusClientV2;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class MilvusServerVersionTest {

    @ParameterizedTest
    @CsvSource({"2.5.10, 2", "v2.5.10, 2", "pkg/v2.5.4, 2", "2.6.26, 2", "2.6-20261023-b8d2ca4240, 2", "3.0.4, 3", "v3.0.4, 3", "10.1, 10"})
    void should_parse_major_version(String version, int expectedMajorVersion) {
        assertThat(MilvusServerVersion.from(version).majorVersion()).isEqualTo(expectedMajorVersion);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"unknown", "Zilliz Cloud", "x2.5.10", "99999.0"})
    void should_use_milvus_2_when_version_is_unknown(String version) {
        assertThat(MilvusServerVersion.from(version)).isEqualTo(MilvusServerVersion.DEFAULT);
    }

    @Test
    void should_read_the_major_version_from_the_client() {
        MilvusClientV2 client = mock(MilvusClientV2.class);
        when(client.getServerVersion()).thenReturn("3.0.4");

        assertThat(MilvusServerVersion.from(client)).isEqualTo(MilvusServerVersion.from("3.0.4"));
    }

    @Test
    void should_use_milvus_2_when_reading_the_version_throws() {
        MilvusClientV2 client = mock(MilvusClientV2.class);
        when(client.getServerVersion()).thenThrow(new RuntimeException("unavailable"));

        assertThat(MilvusServerVersion.from(client)).isEqualTo(MilvusServerVersion.DEFAULT);
    }

    @Test
    void only_milvus_3_needs_an_extra_backslash_in_like_patterns() {
        assertThat(MilvusServerVersion.DEFAULT.likePatternNeedsExtraBackslash()).isFalse();
        assertThat(MilvusServerVersion.from("2.6.26").likePatternNeedsExtraBackslash()).isFalse();
        assertThat(MilvusServerVersion.from("3.0.4").likePatternNeedsExtraBackslash()).isTrue();
    }
}
