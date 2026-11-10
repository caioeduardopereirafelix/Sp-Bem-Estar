FROM eclipse-temurin:24.0.2_12-jdk-noble AS build
WORKDIR /src
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN ./mvnw -B -q dependency:go-offline
COPY src src
RUN ./mvnw -B -q -DskipTests package && cp target/*.jar /app.jar

FROM eclipse-temurin:24.0.2_12-jre-noble
ENV LANG=C.UTF-8 \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=60 -XX:+ExitOnOutOfMemoryError"
RUN useradd --system --uid 10001 --home-dir /app app
WORKDIR /app
COPY --from=build /app.jar app.jar
COPY data/sp-distritos.geojson data/sp-distritos.geojson
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
