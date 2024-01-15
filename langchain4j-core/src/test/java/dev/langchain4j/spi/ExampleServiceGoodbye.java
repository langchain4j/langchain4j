package dev.langchain4j.spi;

public class ExampleServiceGoodbye implements ExampleService{
    @Override
    public String getGreeting() {
        return "Goodbye";
    }
}
