FROM amazoncorretto:17

ARG JAR_FILE=./build/libs/spring-kafka-avro-0.0.1-SNAPSHOT.jar
COPY ${JAR_FILE} app.jar

EXPOSE 80

ENTRYPOINT ["java","-jar","/app.jar"]