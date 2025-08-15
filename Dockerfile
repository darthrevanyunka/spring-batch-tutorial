FROM openjdk:17-jdk-slim

WORKDIR /app

# Copy the JAR file
COPY target/spring-batch-tutorial-1.0.0.jar app.jar

# Create directories for input/output
RUN mkdir -p /app/input /app/output

# Copy the entire input directory
COPY input/ /app/input/

# List the files to verify they were copied (for debugging)
RUN ls -la /app/input/

# Expose port
EXPOSE 8080

# Set environment variables
ENV SPRING_PROFILES_ACTIVE=kubernetes
ENV SPRING_BATCH_JOB_ENABLED=false

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]
