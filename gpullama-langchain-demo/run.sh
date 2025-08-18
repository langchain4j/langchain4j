tornado --enable-preview --add-modules jdk.incubator.vector \
       -cp target/lc4j-ollama-tornado-1.0-SNAPSHOT.jar:\
$(mvn -q -pl '' -am dependency:build-classpath -DincludeScope=runtime \
     -Dmdep.outputAbsoluteArtifactFilename=true \
     | tail -n1) \
       com.example.App
