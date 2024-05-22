package dev.langchain4j.model.cohere.internal.api;

import lombok.Getter;
import lombok.ToString;

import java.util.List;


@Getter
@ToString
public class Citation {
    Integer start;

    Integer end;

    String text;

    List<String> documentIds;
}
