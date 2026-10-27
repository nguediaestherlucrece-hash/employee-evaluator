# ============================
# STAGE 1 - Build Maven
# ============================
FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn clean package -DskipTests -B

# ============================
# STAGE 2 - Image finale légère
# ============================
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Dossier pour les CVs uploadés
RUN mkdir -p /app/uploads/cvs

COPY --from=build /app/target/employee-evaluator-1.0.0.jar app.jar

# Variables d'environnement (à surcharger au runtime)
ENV GROQ_API_KEY=your-groq-api-key
ENV SPRING_PROFILES_ACTIVE=prod

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "-Dspring.profiles.active=${SPRING_PROFILES_ACTIVE}", "app.jar"]
