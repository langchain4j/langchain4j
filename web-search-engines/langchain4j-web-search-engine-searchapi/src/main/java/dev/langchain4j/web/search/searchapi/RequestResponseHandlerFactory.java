package dev.langchain4j.web.search.searchapi;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
class RequestResponseHandlerFactory {

    static SearchApiRequestResponseHandler create(SearchApiEngine engine) {
        switch (engine) {
            case GOOGLE_SEARCH:
                return new SearchApiGoogleSearchHandler();
            default:
                throw new RuntimeException("Response handler not found");
        }
    }

}
