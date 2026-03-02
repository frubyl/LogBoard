FROM gradle:8-jdk21 AS build
WORKDIR /app
COPY . .
RUN gradle bootJar --no-daemon

FROM openjdk:21-slim
COPY --from=build /app/build/libs/*.jar /app.jar
CMD ["java", "-jar", "/app.jar"]
