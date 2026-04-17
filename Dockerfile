
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /build


COPY pom.xml .
COPY .mvn .mvn
COPY mvnw .


RUN ./mvnw dependency:resolve -B


COPY src ./src


RUN ./mvnw package -DskipTests -B


FROM eclipse-temurin:21-jre-alpine


RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

WORKDIR /app


COPY --from=builder /build/target/*.jar app.jar


EXPOSE 8080

ENTRYPOINT ["java",
  "-XX:+UseContainerSupport",
  "-XX:MaxRAMPercentage=75.0",
  "-XX:+UseG1GC",
  "-Djava.security.egd=file:/dev/./urandom",
  "-jar", "app.jar"
]