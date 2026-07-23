---
sidebar_position: 31
---

# JSON Codec

LangChain4j ships an internal JSON serializer (defaults to Jackson) that is used by "tools" and "structured output" functionalities.

The default serializer works for most cases. However, in certain environments the default Jackson serializer might generate errors based on other dependencies. An example has been developers of Jetbrains/IntelliJ plugins.

If you require providing your own JSON serializer (a.k.a. JSON Codec), you can follow these steps:

1. Create an implementation of `dev.langchain4j.spi.json.JsonCodecFactory` in your project

For this example, let's say that your factory class is: `example.MyJsonCodecFactory`

You can check the `dev.langchain4j.internal.JacksonJsonCodec` as the default codec that is used internally by LangChain4j and adapt it to your needs.

2. Add an SPI provider configuration file

In your resources folder (e.g. `src/main/resources`) add a `META-INF/services` folder and create a file called: `dev.langchain4j.spi.json.JsonCodecFactory` and the content of that file must be the FQDN of your factory implementation, in our example, it should be:

```
example.MyJsonCodecFactory
```

## A word of caution

The `dev.langchain4j.spi.json.JsonCodecFactory` is marked for internal use on LangChain4j—this approach should only be used in environments where a customized JSON Codec is definitely required.
