FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app
COPY gradle* gradlew ./
COPY gradle ./gradle
RUN ./gradlew --version
COPY build.gradle settings.gradle ./
RUN ./gradlew dependencies --no-daemon -q || true
COPY src ./src
RUN ./gradlew bootJar --no-daemon -x test

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
