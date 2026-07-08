# syntax=docker/dockerfile:1.7
FROM maven:3.9.7-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
# .m2 lives in a BuildKit cache mount, NOT baked into the image layer — this keeps
# the build stage layer tiny (a ~3GB .m2 committed into a layer was crashing the
# containerd content store on this host) and makes repeat builds reuse the cache.
RUN --mount=type=cache,target=/root/.m2 mvn -q -DskipTests dependency:go-offline
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -q -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/paxassist-backend-0.1.0-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
