# Imagem da API. Build em duas etapas: o JDK compila, e só o JRE e o .jar vão para a imagem final.
# docker build -t spbemestar-api .
FROM eclipse-temurin:21.0.12_8-jdk-noble AS build
WORKDIR /src
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN ./mvnw -B -q dependency:go-offline
COPY src src
RUN ./mvnw -B -q -DskipTests package && cp target/*.jar /app.jar

FROM eclipse-temurin:21.0.12_8-jre-noble
# Sem locale UTF-8, o Java lê as variáveis de ambiente como ASCII e "São Paulo" vira "S??o Paulo"
ENV LANG=C.UTF-8 \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=60 -XX:+ExitOnOutOfMemoryError"
RUN useradd --system --uid 10001 --home-dir /app app
WORKDIR /app
COPY --from=build /app.jar app.jar
# distritos de São Paulo com a população do Censo 2022, importados na subida
COPY data/sp-distritos.geojson data/sp-distritos.geojson
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
