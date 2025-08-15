#!/bin/bash

# Spring Cloud Data Flow Deployment Script
# This script automates the deployment of the Spring Batch application to SCDF

set -e

echo "🚀 Starting Spring Cloud Data Flow deployment..."

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration
APP_NAME="spring-batch-tutorial"
APP_VERSION="1.0.0"
IMAGE_NAME="${APP_NAME}:${APP_VERSION}"
SCDF_SERVER_URL="http://localhost:30000"

# Function to print colored output
print_status() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

print_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Check prerequisites
check_prerequisites() {
    print_status "Checking prerequisites..."
    
    if ! command -v mvn &> /dev/null; then
        print_error "Maven is not installed. Please install Maven first."
        exit 1
    fi
    
    if ! command -v docker &> /dev/null; then
        print_error "Docker is not installed. Please install Docker first."
        exit 1
    fi
    
    if ! command -v kubectl &> /dev/null; then
        print_error "kubectl is not installed. Please install kubectl first."
        exit 1
    fi
    
    print_success "All prerequisites are satisfied"
}

# Build the application
build_application() {
    print_status "Building Spring Boot application..."
    
    if mvn clean package -DskipTests; then
        print_success "Application built successfully"
    else
        print_error "Failed to build application"
        exit 1
    fi
}

# Build Docker image
build_docker_image() {
    print_status "Building Docker image..."
    
    if docker build -t ${IMAGE_NAME} .; then
        print_success "Docker image built successfully: ${IMAGE_NAME}"
    else
        print_error "Failed to build Docker image"
        exit 1
    fi
}

# Load image to Minikube (if using Minikube)
load_image_to_minikube() {
    if command -v minikube &> /dev/null && minikube status &> /dev/null; then
        print_status "Loading image to Minikube..."
        if minikube image load ${IMAGE_NAME}; then
            print_success "Image loaded to Minikube successfully"
        else
            print_warning "Failed to load image to Minikube"
        fi
    else
        print_warning "Minikube not detected, skipping image loading"
    fi
}

# Deploy to Kubernetes
deploy_to_kubernetes() {
    print_status "Deploying to Kubernetes..."
    
    # Create namespace if it doesn't exist
    kubectl create namespace batch-processing --dry-run=client -o yaml | kubectl apply -f -
    
    # Apply Kubernetes manifests
    if kubectl apply -f k8s/deployment.yaml; then
        print_success "Kubernetes deployment applied successfully"
    else
        print_error "Failed to apply Kubernetes deployment"
        exit 1
    fi
    
    # Wait for deployment to be ready
    print_status "Waiting for deployment to be ready..."
    kubectl wait --for=condition=available --timeout=300s deployment/${APP_NAME} -n default
    print_success "Deployment is ready"
}

# Check SCDF server availability
check_scdf_server() {
    print_status "Checking SCDF server availability..."
    
    if curl -s ${SCDF_SERVER_URL}/management/health > /dev/null; then
        print_success "SCDF server is accessible"
    else
        print_warning "SCDF server is not accessible at ${SCDF_SERVER_URL}"
        print_warning "Please ensure SCDF is running and accessible"
    fi
}

# Register application with SCDF
register_with_scdf() {
    print_status "Registering application with SCDF..."
    
    # Check if SCDF server is available
    if ! curl -s ${SCDF_SERVER_URL}/management/health > /dev/null; then
        print_warning "SCDF server not available, skipping registration"
        return
    fi
    
    # Register the task application
    local registration_url="${SCDF_SERVER_URL}/apps/task/${APP_NAME}"
    local registration_data="{\"uri\":\"docker://${IMAGE_NAME}\",\"metadata-uri\":\"\"}"
    
    if curl -X POST ${registration_url} \
        -H "Content-Type: application/json" \
        -d "${registration_data}" \
        -s > /dev/null; then
        print_success "Application registered with SCDF successfully"
    else
        print_warning "Failed to register application with SCDF"
    fi
}

# Create task definition
create_task_definition() {
    print_status "Creating task definition..."
    
    if ! curl -s ${SCDF_SERVER_URL}/management/health > /dev/null; then
        print_warning "SCDF server not available, skipping task definition"
        return
    fi
    
    local task_name="person-processing-task"
    local task_definition="${APP_NAME} --scenario=SUCCESS --csvPath=/app/input/persons.csv"
    
    local create_url="${SCDF_SERVER_URL}/tasks/definitions"
    local create_data="{\"name\":\"${task_name}\",\"definition\":\"${task_definition}\"}"
    
    if curl -X POST ${create_url} \
        -H "Content-Type: application/json" \
        -d "${create_data}" \
        -s > /dev/null; then
        print_success "Task definition created: ${task_name}"
    else
        print_warning "Failed to create task definition"
    fi
}

# Display deployment information
display_deployment_info() {
    echo ""
    print_success "Deployment completed successfully!"
    echo ""
    echo "📋 Deployment Information:"
    echo "   Application: ${APP_NAME}"
    echo "   Version: ${APP_VERSION}"
    echo "   Image: ${IMAGE_NAME}"
    echo ""
    echo "🔗 Access Points:"
    echo "   SCDF Dashboard: ${SCDF_SERVER_URL}"
    echo "   Application API: http://localhost:8080"
    echo ""
    echo "📝 Next Steps:"
    echo "   1. Access SCDF Dashboard at ${SCDF_SERVER_URL}"
    echo "   2. Go to Tasks → Create Task"
    echo "   3. Select 'person-processing-task'"
    echo "   4. Configure parameters and deploy"
    echo ""
    echo "🔍 Monitoring:"
    echo "   kubectl get pods -l app=${APP_NAME}"
    echo "   kubectl logs -l app=${APP_NAME}"
    echo "   kubectl port-forward svc/${APP_NAME}-service 8080:8080"
}

# Main deployment process
main() {
    echo "🎯 Spring Cloud Data Flow Deployment Script"
    echo "=========================================="
    echo ""
    
    check_prerequisites
    build_application
    build_docker_image
    load_image_to_minikube
    deploy_to_kubernetes
    check_scdf_server
    register_with_scdf
    create_task_definition
    display_deployment_info
}

# Run the main function
main "$@"
