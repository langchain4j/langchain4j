package dev.langchain4j.web.search.searchapi.result;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Pagination {
	private Integer current;
    private String next;
}