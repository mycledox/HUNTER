FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /build
COPY pom.xml .
COPY src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:21-jre-jammy

RUN apt-get update \
    && apt-get install -y --no-install-recommends tesseract-ocr tesseract-ocr-hin curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system hunter \
    && useradd --system --gid hunter --home-dir /app hunter \
    && mkdir -p /app/data/uploads \
    && chown -R hunter:hunter /app

WORKDIR /app
COPY --from=build --chown=hunter:hunter /build/target/hunter-api-0.1.0.jar /app/hunter-api.jar

USER hunter
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/hunter-api.jar"]
