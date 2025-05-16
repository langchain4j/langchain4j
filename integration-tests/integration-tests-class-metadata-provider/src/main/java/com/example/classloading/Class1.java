package com.example.classloading;

import dev.langchain4j.Experimental;

@Experimental("This is plain and boring!")
public class Class1 {
    public void hello() {}

    @Experimental("Just trying things out")
    public String goodbye() {
        return "Goodbye!";
    }

    public static String wave() {
        return "Wave!";
    }
}
