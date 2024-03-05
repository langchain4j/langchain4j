package dev.langchain4j.web.search;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WebSearchPaginationTest {

    @Test
    void should_return_webSearchPagination_with_default_values(){
        WebSearchPagination webSearchPagination = new WebSearchPagination(1);

        assertThat(webSearchPagination.current()).isEqualTo(1);
        assertThat(webSearchPagination.next()).isNull();
        assertThat(webSearchPagination.previous()).isNull();
        assertThat(webSearchPagination.otherPages()).isNull();

        assertThat(webSearchPagination).hasToString("WebSearchPagination{current=1, next='null', previous='null', otherPages=null}");

    }

    @Test
    void should_return_webSearchPagination_with_pagination(){
        WebSearchPagination webSearchPagination = WebSearchPagination.pagination(1);

        assertThat(webSearchPagination.current()).isEqualTo(1);
        assertThat(webSearchPagination.next()).isNull();
        assertThat(webSearchPagination.previous()).isNull();
        assertThat(webSearchPagination.otherPages()).isNull();

        assertThat(webSearchPagination).hasToString("WebSearchPagination{current=1, next='null', previous='null', otherPages=null}");

    }

    @Test
    void test_equals_and_hash(){
        WebSearchPagination wsp1 = WebSearchPagination.pagination(1);
        WebSearchPagination wsp2 = WebSearchPagination.pagination(1);

        assertThat(wsp1)
                .isEqualTo(wsp1)
                .isNotEqualTo(null)
                .isNotEqualTo(new Object())
                .isEqualTo(wsp2)
                .hasSameHashCodeAs(wsp2);

        assertThat(WebSearchPagination.pagination(2))
                .isNotEqualTo(wsp1);
    }

    @Test
    void should_throw_illegalArgumentException(){
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> WebSearchPagination.pagination(0));
        assertThat(exception.getMessage()).isEqualTo("current must be greater than zero, but is: 0");
    }
}
