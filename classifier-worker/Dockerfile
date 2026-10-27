# Imagem do worker de classificação. Construída a partir da raiz do repositório, que tem o Maven Wrapper:
# docker build -f classifier-worker/Dockerfile -t spbemestar-worker .
FROM eclipse-temurin:21.0.12_8-jdk-noble AS build
WORKDIR /src
COPY .mvn .mvn
COPY mvnw ./
COPY classifier-worker/pom.xml classifier-worker/pom.xml
RUN ./mvnw -B -q -f classifier-worker/pom.xml dependency:go-offline
COPY classifier-worker/src classifier-worker/src
RUN ./mvnw -B -q -f classifier-worker/pom.xml -DskipTests package && cp classifier-worker/target/*.jar /app.jar

FROM eclipse-temurin:21.0.12_8-jre-noble
ENV LANG=C.UTF-8 \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=60 -XX:+ExitOnOutOfMemoryError" \
    CLASSIFIER_MODEL=file:/app/models/classifier.onnx
RUN useradd --system --uid 10001 --home-dir /app app
WORKDIR /app
COPY --from=build /app.jar app.jar
# o modelo treinado não vai na imagem: é montado em /app/models (ver deploy/docker-compose.yml)
USER app
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
