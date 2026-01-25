FROM openjdk:21-slim

ARG JAR_FILE=/build/libs/*.jar

COPY ${JAR_FILE} /app.jar

CMD java -jar app.jar
