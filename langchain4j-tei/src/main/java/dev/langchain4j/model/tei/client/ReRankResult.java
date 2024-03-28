package dev.langchain4j.model.tei.client;


import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class ReRankResult {

    private int index;

    private String text;

    private double score;

}
