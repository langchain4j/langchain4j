This contains other full "projects" that can use various LangChain4j features independently but yet aren't necessarily "integration tests".
Think of these are separate applications that may be testing some kind of functionality within LangChain4j.

Think of where `ServiceLoader`s may be invoked - creating a `src/test/META-INF/services` for the service in one of the modules would then override the service being loaded for all tests, which isn't what's intended.

Instead, we can create isolated projects in here for that.
