package dev.langchain4j.data.document.splitter;

import lombok.Getter;

@Getter
public enum MarkdownHeaderLevel {
    H1(1),
    H2(2),
    H3(3),
    H4(4),
    H5(5),
    H6(6);

    private final int level;
    MarkdownHeaderLevel(int level) {
        this.level = level;
    }
}