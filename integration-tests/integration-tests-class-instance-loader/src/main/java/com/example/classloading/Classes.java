package com.example.classloading;

public final class Classes {
    private static final Classes INSTANCE = new Classes();

    private Classes() {}

    public static Classes getInstance() {
        return INSTANCE;
    }

    public <T> T getInstance(Class<T> clazz) {
        if (clazz == Class1.class) {
            return (T) new Class1();
        }

        if (clazz == Class2.class) {
            return (T) new Class2();
        }

        throw new IllegalArgumentException("Unknown class: %s".formatted(clazz.getName()));
    }

    public static class Class1 {}

    public static class Class2 {}

    public static class Class3 {}
}
