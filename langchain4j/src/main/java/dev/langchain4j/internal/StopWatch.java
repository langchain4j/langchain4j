package dev.langchain4j.internal;

public class StopWatch {

    private final long startTime;

    public StopWatch(long currentTimeMillis) {
        this.startTime = currentTimeMillis;
    }

    public static StopWatch start() {
        return new StopWatch(System.currentTimeMillis());
    }

    public int secondsElapsed() {
        return (int) (System.currentTimeMillis() - startTime) / 1000;
    }
}
