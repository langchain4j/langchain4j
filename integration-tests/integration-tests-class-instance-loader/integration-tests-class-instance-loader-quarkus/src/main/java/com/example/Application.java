package com.example;

import io.quarkus.runtime.QuarkusApplication;
import io.quarkus.runtime.annotations.QuarkusMain;

@QuarkusMain
public class Application implements QuarkusApplication {
    private final Class1 class1;
    private final Class2 class2;

    public Application(final Class1 class1, final Class2 class2) {
        this.class1 = class1;
        this.class2 = class2;
    }

    @Override
    public int run(final String... args) {
        return 0;
    }
}
