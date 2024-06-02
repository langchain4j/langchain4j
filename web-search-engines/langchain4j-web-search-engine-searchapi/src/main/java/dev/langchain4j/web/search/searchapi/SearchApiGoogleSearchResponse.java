package dev.langchain4j.web.search.searchapi;

import lombok.Builder;
import lombok.Getter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Getter
@Builder
class SearchApiGoogleSearchResponse implements SearchApiResponse {

    private Map<String, Object> searchMetadata;
    private Map<String, Object> searchParameters;
    private SearchInformation searchInformation;
    private Map<String, Object> knowledgeGraph;
    private List<SearchResult> organicResults;
    private List<RelatedQuestion> relatedQuestions;
    private List<SearchLink> relatedSearches;
    private Pagination pagination;

}

@Getter
@Builder
class SearchInformation {

    private String queryDisplayed;
    private Long totalResults;
    private Double timeTakenDisplayed;
    private String detectedLocation;
}

@Getter
@Builder
class SearchResult {

    private Integer position;
    private String title;
    private String link;
    private String domain;
    private String displayedLink;
    private String snippet;
    private List<String> snippetHighlightedWords;
    private String date;
    private Map<String, List<Link>> sitelinks;
    private Map<String, AboutResult> aboutThisResult;
    private String aboutPageLink;
    private String cachedPageLink;
    private String favicon;
    private String thumbnail;
    private List<SearchResult> nestedResults; // TODO: can use the same class?
}

@Getter
@Builder
class AboutResult {

    private String name;
    private String type;
    private String description;
    private String linkSource;
    private String link;
    private String favicon;

    Map<String, String> toMetadata() {
        HashMap<String, String> metadata = new HashMap<>();
        metadata.put("name", name);
        metadata.put("type", type);
        metadata.put("description", description);
        metadata.put("linkSource", linkSource);
        metadata.put("link", link);
        metadata.put("favicon", favicon);
        return metadata;
    }
}

@Getter
@Builder
class RelatedQuestion {

    private String question;
    private String answer;
    private String answerHighlight;
    private RelatedQuestionSource source;
    private Link search;
}

@Getter
@Builder
class RelatedQuestionSource {

    private String title;
    private String link;
    private String domain;
    private String displayedLink;
    private String favicon;
}


@Getter
@Builder
class SearchLink {

    private String query;
    private String link;
}

@Getter
@Builder
class Link {

    private String title;
    private String link;
}

@Getter
@Builder
class Pagination {

    private Integer current;
    private String next;
}

