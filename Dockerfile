FROM maven:3.9.11-eclipse-temurin-25 AS build
WORKDIR /workspace
COPY pom.xml .
RUN mvn -B -ntp dependency:go-offline
COPY src src
RUN mvn -B -ntp verify

FROM eclipse-temurin:25-jre
WORKDIR /app
RUN groupadd --gid 10001 app && useradd --uid 10001 --gid app --no-create-home app \
    && mkdir -p /app/data/reports && chown -R app:app /app
COPY --from=build --chown=app:app /workspace/target/shirabete-matomeru-kun-backend-0.0.1-SNAPSHOT.jar app.jar
USER 10001:10001
ENV REPORT_STORAGE_DIRECTORY=/app/data/reports
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
