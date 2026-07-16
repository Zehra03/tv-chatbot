# syntax=docker/dockerfile:1.7
FROM maven:3.9.7-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
# .m2 lives in a BuildKit cache mount, NOT baked into the image layer — this keeps
# the build stage layer tiny (a ~3GB .m2 committed into a layer was crashing the
# containerd content store on this host) and makes repeat builds reuse the cache.
#
# The id is mandatory and must stay in Railway's `s/<service id>-<target>` form: their
# builder rejects an id-less or differently-prefixed cache mount and the build fails
# before Maven runs. The UUID is the Railway backend service and is hardcoded on
# purpose — mount flags take no variable expansion, so ${RAILWAY_SERVICE_ID} is invalid
# syntax here. Locally the id is just an opaque cache key, so this stays portable.
RUN --mount=type=cache,id=s/d3d1b9fe-d8d1-48a2-9188-b00741549890-/root/.m2,target=/root/.m2 \
    mvn -q -DskipTests dependency:go-offline
COPY src ./src
RUN --mount=type=cache,id=s/d3d1b9fe-d8d1-48a2-9188-b00741549890-/root/.m2,target=/root/.m2 \
    mvn -q -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/paxassist-backend-0.1.0-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
