# Build stage
FROM maven:3.9-eclipse-temurin-21-alpine AS build
WORKDIR /build
COPY pom.xml .
COPY shared/shared-models/pom.xml shared/shared-models/pom.xml
COPY shared/shared-spi/pom.xml shared/shared-spi/pom.xml
COPY alert-integrator/pom.xml alert-integrator/pom.xml
COPY logging/pom.xml logging/pom.xml
COPY verification-engine/pom.xml verification-engine/pom.xml
COPY decision-engine/pom.xml decision-engine/pom.xml
COPY action-executor/pom.xml action-executor/pom.xml
COPY shared/shared-models/src shared/shared-models/src
COPY shared/shared-spi/src shared/shared-spi/src
COPY alert-integrator/src alert-integrator/src
COPY logging/src logging/src
COPY verification-engine/src verification-engine/src
COPY decision-engine/src decision-engine/src
COPY action-executor/src action-executor/src
RUN mvn clean package -DskipTests

# Runtime stage
FROM eclipse-temurin:21-jre-alpine AS runtime
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
WORKDIR /app
COPY --from=build /build/alert-integrator/target/*.jar app.jar
COPY --from=build /build/verification-engine/target/*.jar app.jar
COPY --from=build /build/decision-engine/target/*.jar app.jar
COPY --from=build /build/action-executor/target/*.jar app.jar
COPY --from=build /build/logging/target/*.jar app.jar
RUN chown -R appuser:appgroup /app
USER appuser
ENV JAVA_OPTS="-Xmx512m -Xms256m"
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
