FROM eclipse-temurin:25-jre

WORKDIR /app

RUN addgroup --system app && adduser --system --ingroup app app

COPY build/libs/notification-service-*.jar app.jar

USER app:app

EXPOSE 8084

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
