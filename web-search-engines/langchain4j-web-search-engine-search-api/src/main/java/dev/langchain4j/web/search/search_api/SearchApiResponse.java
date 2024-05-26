package dev.langchain4j.web.search.search_api;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class SearchApiResponse {

    private SearchMetadata searchMetadata;
    private SearchParameters searchParameters;
    private SearchInformation searchInformation;
    private KnowledgeGraph knowledgeGraph;
    private OrganicResult[] organicResults;
    private RelatedQuestion[] relatedQuestions;
    private RelatedSearch[] relatedSearches;
    private Pagination pagination;
}

@Getter
@Setter
class SearchMetadata {
    private String id;
    private String status;
    private String createdAt;
    private Double requestTimeTaken;
    private Double parsingTimeTaken;
    private Double totalTimeTaken;
    private String requestUrl;
    private String htmlUrl;
    private String jsonUrl;
}

@Getter
@Setter
class SearchParameters {
    private String engine;
    private String q;
    private String googleDomain;
    private String hl;
    private String gl;
}

@Getter
@Setter
class SearchInformation {
    private String queryDisplayed;
    private Long totalResults;
    private Double timeTakenDisplayed;
    private String detectedLocation;
}

@Getter
@Setter
class KnowledgeGraph {
    private String kgmid;
    private String knowledgeGraphType;
    private String title;
    private String type;
    private String description;
    private Source source;
    private String initialReleaseDate;
    private String developers;
    private DeveloperLink[] developersLinks;
    private String engine;
    private String license;
    private String platform;
    private String stableRelease;
    private String writtenIn;
    private WrittenInLink[] writtenInLinks;
    private PeopleAlsoSearchFor[] peopleAlsoSearchFor;
    private String image;
}

@Getter
@Setter
class Source {
    private String name;
    private String link;
}

@Getter
@Setter
class DeveloperLink {
    private String text;
    private String link;
}

@Getter
@Setter
class WrittenInLink {
    private String text;
    private String link;
}

@Getter
@Setter
class PeopleAlsoSearchFor {
    private String name;
    private String link;
    private String image;
}

@Getter
@Setter
class OrganicResult {
    private Integer position;
    private String title;
    private String link;
    private String domain;
    private String displayedLink;
    private String snippet;
    private String[] snippetHighlightedWords;
    private String date;
    private Sitelinks sitelinks;
    private AboutThisResult aboutThisResult;
    private String aboutPageLink;
    private String cachedPageLink;
    private String favicon;
    private String thumbnail;
    private NestedResult[] nestedResults;
}

@Getter
@Setter
class Sitelinks {
    private Inline[] inline;
}

@Getter
@Setter
class Inline {
    private String title;
    private String link;
}

@Getter
@Setter
class AboutThisResult {
    private Source source;
}

@Getter
@Setter
class NestedResult {
    private Integer position;
    private String title;
    private String link;
    private String domain;
    private String displayedLink;
    private String snippet;
    private String[] snippetHighlightedWords;
    private AboutThisResult aboutThisResult;
    private String aboutPageLink;
    private String cachedPageLink;
}

@Getter
@Setter
class RelatedQuestion {
    private String question;
    private String answer;
    private String answerHighlight;
    private Source source;
    private Search search;
}

@Getter
@Setter
class Search {
    private String title;
    private String link;
}

@Getter
@Setter
class RelatedSearch {
    private String query;
    private String link;
}

@Getter
@Setter
class Pagination {
    private Integer current;
    private String next;
}
