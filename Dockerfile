FROM maven:eclipse-temurin
LABEL maintainer="Baptiste Mathus <batmat@batmat.net>"

RUN mkdir /project
VOLUME /project/work
ADD . /project
WORKDIR /project
RUN mvn clean package && \
    cd target && \
    mv deprecated-usage-in-plugins-*-SNAPSHOT-jar-with-dependencies.jar deprecated-usage-fat.jar

ENTRYPOINT ["java", "-jar", "target/deprecated-usage-fat.jar"]
