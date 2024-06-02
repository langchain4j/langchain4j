package dev.langchain4j.web.search.searchapi;

import lombok.Getter;

@Getter
public enum SearchApiEngine {

    GOOGLE_SEARCH("google");

    private final String value;

    SearchApiEngine(String value) {
        this.value = value;
    }

}
