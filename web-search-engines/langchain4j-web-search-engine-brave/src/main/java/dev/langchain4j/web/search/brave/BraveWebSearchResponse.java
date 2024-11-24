package dev.langchain4j.web.search.brave;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Builder
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class BraveWebSearchResponse {

    private String type;
    private String title;
    private String url;
    private String description;
    private boolean familyFriendly;
    private String language;

}
