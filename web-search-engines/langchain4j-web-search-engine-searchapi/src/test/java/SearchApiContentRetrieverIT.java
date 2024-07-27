import dev.langchain4j.rag.content.retriever.WebSearchContentRetrieverIT;
import dev.langchain4j.web.search.WebSearchEngine;
import dev.langchain4j.web.search.searchapi.SearchApiWebSearchEngine;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "SEARCHAPI_API_KEY", matches = ".+")
class SearchApiContentRetrieverIT extends WebSearchContentRetrieverIT {

    @Override
    protected WebSearchEngine searchEngine() {
        return SearchApiWebSearchEngine.builder()
                .apiKey(System.getenv("SEARCHAPI_API_KEY"))
                .build();
    }
}