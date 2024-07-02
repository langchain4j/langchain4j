package dev.langchain4j.web.search.searchapi.result;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OrganicResult {
    private Integer position;
    private String title;
    private String link;
    private String source;
    private String domain;
    private String displayedLink;
    private String snippet;
    private String favicon;
    private String thumbnail;    
}
