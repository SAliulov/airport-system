FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
COPY backend/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]