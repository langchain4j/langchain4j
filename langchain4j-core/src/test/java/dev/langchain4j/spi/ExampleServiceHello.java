package dev.langchain4j.spi;

public class ExampleServiceHello implements ExampleService{
    @Override
    public String getGreeting() {
        return "Hello";
    }
}
