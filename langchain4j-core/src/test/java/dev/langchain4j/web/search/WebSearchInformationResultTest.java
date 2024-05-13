package dev.langchain4j.web.search;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WebSearchInformationResultTest {

    @Test
    void should_return_webSearchInformationResult_with_default_values(){
        WebSearchInformationResult webSearchInformationResult = new WebSearchInformationResult(1L);

        assertThat(webSearchInformationResult.totalResults()).isEqualTo(1L);
        assertThat(webSearchInformationResult.pageNumber()).isNull();
        assertThat(webSearchInformationResult.metadata()).isNull();

        assertThat(webSearchInformationResult).hasToString("WebSearchInformationResult{totalResults=1, pageNumber=null, metadata=null}");
    }

    @Test
    void should_return_webSearchInformationResult_with_informationResult(){
        WebSearchInformationResult webSearchInformationResult = WebSearchInformationResult.from(1L);

        assertThat(webSearchInformationResult.totalResults()).isEqualTo(1L);
        assertThat(webSearchInformationResult.pageNumber()).isNull();
        assertThat(webSearchInformationResult.metadata()).isNull();

        assertThat(webSearchInformationResult).hasToString("WebSearchInformationResult{totalResults=1, pageNumber=null, metadata=null}");
    }

    @Test
    void test_equals_and_hash(){
        WebSearchInformationResult wsi1 = WebSearchInformationResult.from(1L);
        WebSearchInformationResult wsi2 = WebSearchInformationResult.from(1L);

        assertThat(wsi1)
                .isEqualTo(wsi1)
                .isNotEqualTo(null)
                .isNotEqualTo(new Object())
                .isEqualTo(wsi2)
                .hasSameHashCodeAs(wsi2);

        assertThat(WebSearchInformationResult.from(2L))
                .isNotEqualTo(wsi1);
    }

    @Test
    void should_throw_illegalArgumentException(){
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> WebSearchInformationResult.from(null));
        assertThat(exception.getMessage()).isEqualTo("totalResults cannot be null");
    }
}
