# Stage 1 - Build Frontend
FROM node:20-alpine AS frontend-build
WORKDIR /app/frontend
COPY frontend/package*.json ./
RUN npm install
COPY frontend/ ./
RUN npm run build

# Stage 2 - Build Backend
FROM eclipse-temurin:17-jdk-alpine AS backend-build
WORKDIR /app
COPY backend/openshift-monitor-utility/ ./
COPY --from=frontend-build /app/frontend/dist ./src/main/resources/static/
RUN ./mvnw clean package -DskipTests

# Stage 3 - Final Runtime Image
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=backend-build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
