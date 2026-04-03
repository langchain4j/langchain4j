package dev.langchain4j.agentic.supervisor;

public class ResponseScore {

    private double score1;
    private double score2;

    public double getScore1() {
        return score1;
    }

    public void setScore1(double score1) {
        this.score1 = score1;
    }

    public double getScore2() {
        return score2;
    }

    public void setScore2(double score2) {
        this.score2 = score2;
    }

    @Override
    public String toString() {
        return "ResponseScore{" +
                "score1=" + score1 +
                ", score2=" + score2 +
                '}';
    }
}
