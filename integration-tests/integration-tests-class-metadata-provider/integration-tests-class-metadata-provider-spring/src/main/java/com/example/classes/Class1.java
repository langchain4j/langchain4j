package com.example.classes;

import dev.langchain4j.Experimental;
import org.springframework.stereotype.Component;

@Experimental("This is plain and boring!")
@Component
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
