# Spring Cloud Data Flow Deployment Guide

This guide explains how to deploy your Spring Batch application using Spring Cloud Data Flow (SCDF) and Kubernetes.

## What is Spring Cloud Data Flow?

Spring Cloud Data Flow is a unified platform for building data integration and real-time data processing pipelines. It provides:

- **Web UI** for creating and managing data processing flows
- **Kubernetes integration** for scalable deployment
- **Task orchestration** for batch processing
- **Stream processing** for real-time data pipelines

## Architecture Overview

```
┌─────────────────┐    ┌──────────────────┐    ┌─────────────────┐
│   SCDF Server   │    │   Kubernetes     │    │   Your Batch    │
│   (Web UI)      │───▶│   Cluster        │───▶│   Application   │
└─────────────────┘    └──────────────────┘    └─────────────────┘
```

## Prerequisites

1. **Kubernetes Cluster** (Minikube, Docker Desktop, or cloud provider)
2. **kubectl** configured to access your cluster
3. **Docker** for building container images
4. **Maven** for building the application

## Step 1: Build and Package the Application

```bash
# Build the application
mvn clean package

# Build Docker image
docker build -t spring-batch-tutorial:latest .

# If using Minikube, load the image
minikube image load spring-batch-tutorial:latest
```

## Step 2: Deploy Spring Cloud Data Flow

### Option A: Using Helm (Recommended)

```bash
# Add the Spring Cloud Data Flow Helm repository
helm repo add spring https://spring-petclinic.github.io/spring-cloud-dataflow
helm repo update

# Install SCDF Server
helm install scdf-server spring/spring-cloud-dataflow-server \
  --set server.service.type=NodePort \
  --set server.service.nodePort=30000

# Install SCDF Skipper
helm install scdf-skipper spring/spring-cloud-dataflow-skipper \
  --set skipper.service.type=NodePort \
  --set skipper.service.nodePort=30001
```

### Option B: Using kubectl

```bash
# Apply the SCDF manifests
kubectl apply -f https://raw.githubusercontent.com/spring-cloud/spring-cloud-dataflow/main/spring-cloud-dataflow-server/src/main/resources/kubernetes/server.yaml
kubectl apply -f https://raw.githubusercontent.com/spring-cloud/spring-cloud-dataflow/main/spring-cloud-dataflow-skipper/src/main/resources/kubernetes/skipper.yaml
```

## Step 3: Access the SCDF Dashboard

```bash
# Get the SCDF server URL
kubectl get svc scdf-server

# If using Minikube
minikube service scdf-server --url

# Access the dashboard at: http://localhost:30000
```

## Step 4: Register Your Application

In the SCDF Dashboard:

1. Go to **Apps** → **Tasks**
2. Click **Register Application**
3. Fill in the details:
   - **Name**: `spring-batch-tutorial`
   - **Type**: `Task`
   - **URI**: `docker://spring-batch-tutorial:latest`
   - **Version**: `1.0.0`

## Step 5: Create and Deploy a Task

### Via Web UI:

1. Go to **Tasks** → **Create Task**
2. Select your `spring-batch-tutorial` application
3. Configure task properties:
   ```
   scenario=SUCCESS
   csvPath=/app/input/persons.csv
   skipEvery=0
   retryAttempts=3
   ```
4. Click **Create Task**
5. Deploy the task to Kubernetes

### Via REST API:

```bash
# Create task definition
curl -X POST http://localhost:30000/tasks/definitions \
  -H "Content-Type: application/json" \
  -d '{
    "name": "person-processing-task",
    "definition": "spring-batch-tutorial --scenario=SUCCESS --csvPath=/app/input/persons.csv"
  }'

# Launch the task
curl -X POST http://localhost:30000/tasks/executions \
  -H "Content-Type: application/json" \
  -d '{
    "name": "person-processing-task",
    "properties": {
      "scenario": "SUCCESS",
      "csvPath": "/app/input/persons.csv"
    }
  }'
```

## Step 6: Monitor Task Execution

### Via Web UI:
1. Go to **Tasks** → **Executions**
2. View task status, logs, and metrics

### Via REST API:
```bash
# Get task executions
curl http://localhost:30000/tasks/executions

# Get specific execution details
curl http://localhost:30000/tasks/executions/{executionId}
```

## Step 7: Create Data Pipelines (Advanced)

You can create more complex data processing pipelines:

### Example: File Processing Pipeline
```
file-ingest → spring-batch-tutorial → file-output
```

1. Register additional applications (file source/sink)
2. Create a stream definition
3. Deploy the stream

## Configuration Options

### Environment Variables
- `SCENARIO`: Processing scenario (SUCCESS, FAIL, PARTIAL, RETRYABLE)
- `CSV_PATH`: Path to input CSV file
- `SKIP_EVERY`: Skip every Nth record (for testing)
- `RETRY_ATTEMPTS`: Number of retry attempts

### Kubernetes Resources
- **Memory**: 512Mi (request) / 1024Mi (limit)
- **CPU**: 500m (request) / 1000m (limit)
- **Storage**: 1Gi for input/output volumes

## Troubleshooting

### Common Issues:

1. **Image not found**: Ensure the Docker image is available in your cluster
2. **Permission denied**: Check Kubernetes RBAC settings
3. **Storage issues**: Verify PersistentVolumeClaims are bound
4. **Network connectivity**: Check service endpoints and network policies

### Debug Commands:

```bash
# Check pod status
kubectl get pods -l app=spring-batch-tutorial

# View pod logs
kubectl logs -l app=spring-batch-tutorial

# Check task execution status
kubectl get taskexecutions

# Access the application directly
kubectl port-forward svc/spring-batch-tutorial-service 8080:8080
```

## Scaling and Production Considerations

### Horizontal Scaling:
- Deploy multiple task instances
- Use Kubernetes HorizontalPodAutoscaler
- Implement proper resource limits

### Data Persistence:
- Use external databases (PostgreSQL, MySQL)
- Configure persistent storage for input/output files
- Implement backup strategies

### Monitoring:
- Enable Prometheus metrics
- Configure Grafana dashboards
- Set up alerting rules

## Next Steps

1. **Add more data sources**: Database, message queues, APIs
2. **Implement error handling**: Dead letter queues, retry policies
3. **Add monitoring**: Metrics, logging, tracing
4. **Security**: RBAC, network policies, secrets management
5. **CI/CD**: Automated deployment pipelines

## Resources

- [Spring Cloud Data Flow Documentation](https://dataflow.spring.io/)
- [Spring Cloud Task Documentation](https://docs.spring.io/spring-cloud-task/docs/current/reference/html/)
- [Kubernetes Documentation](https://kubernetes.io/docs/)
- [Spring Batch Documentation](https://docs.spring.io/spring-batch/docs/current/reference/html/)
