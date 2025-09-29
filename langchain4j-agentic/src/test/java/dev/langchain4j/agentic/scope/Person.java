package dev.langchain4j.agentic.scope;

import com.fasterxml.jackson.annotation.JsonProperty;

public class Person {
    private String name;
    private int age;

    @JsonProperty("is_adult")
    private boolean adult;

    public int getAge() {
        return age;
    }

    public void setAge(final int age) {
        this.age = age;
    }

    public String getName() {
        return name;
    }

    public void setName(final String name) {
        this.name = name;
    }

    public boolean isAdult() {
        return adult;
    }

    public void setAdult(final boolean adult) {
        this.adult = adult;
    }
}
