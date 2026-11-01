FROM mcr.microsoft.com/openjdk/jdk:21-ubuntu AS build

WORKDIR /app

COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -DskipTests dependency:go-offline

COPY src ./src
RUN ./mvnw -B -DskipTests package

FROM mcr.microsoft.com/openjdk/jdk:21-ubuntu

WORKDIR /app

COPY --from=build /app/target/healthmonitor-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8081

ENTRYPOINT ["sh", "-c", "java ${JAVA_OPTS} -jar app.jar"]
