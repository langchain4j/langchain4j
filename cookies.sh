

#export JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-8.jdk/Contents/Home
#export JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home
#export JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-17.jdk/Contents/Home
export JAVA_HOME=/Library/Java/JavaVirtualMachines/amazon-corretto-21.jdk/Contents/Home

clear
./mvnw -verison
mvn -T2C -B -U process-test-classes javadoc:javadoc
#./mvnw clean test-compile javadoc:javadoc
#./mvnw javadoc:aggregate -DskipTests -pl '!code-execution-engines/langchain4j-code-execution-engine-graalvm-polyglot,!langchain4j-neo4j'
./mvnw verify --fail-at-end -pl langchain4j-core,langchain4j -am

#./mvnw clean verify -pl !langchain4j-jlama,!code-execution-engines/langchain4j-code-execution-engine-graalvm-polyglot,!langchain4j-cassandra,!langchain4j-infinispan,!langchain4j-neo4j,!langchain4j-opensearch,!langchain4j-azure-ai-search