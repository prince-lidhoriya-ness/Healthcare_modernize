# Build the Spring Boot jar
FROM maven:3.9.5-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q dependency:go-offline
COPY src ./src
RUN mvn -q -DskipTests package

# Run it
FROM eclipse-temurin:21.0.1_12-jdk-jammy
WORKDIR /app
COPY --from=build /app/target/clinic-backend-1.0.0.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
