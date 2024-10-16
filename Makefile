all: clean test verify

#export JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-8.jdk/Contents/Home

test:
	export JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-17.jdk/Contents/Home
#	export JAVA_HOME=/Library/Java/JavaVirtualMachines/amazon-corretto-21.jdk/Contents/Home
	./mvnw -version
	./mvnw clean test -T4C

#    export JAVA_HOME=/Library/Java/JavaVirtualMachines/amazon-corretto-21.jdk/Contents/Home
verify:
	./mvnw -version
	./mvnw verify -T4C --fail-at-end

clean:
	./mvnw clean -T4C

