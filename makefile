PROJECT_NAME := distributedkeyvalue_v3
VERSION := latest
DOCKER_COMPOSE_FILE := docker-compose.yml
MAVEN_CMD := mvn
DOCKER_CMD := docker
DOCKER_COMPOSE_CMD := docker-compose

.PHONY: all build start test help

all: build start

# Build the project and Docker image
build:
	@echo "\nBuilding Docker image... "
	$(DOCKER_CMD) build -t $(PROJECT_NAME):$(VERSION) .

# Start the cluster
start: build
	@echo "\nStarting cluster... "
	$(DOCKER_COMPOSE_CMD) up -d
	@echo "\nCluster started. Use 'make logs' to view logs."

# Stop and remove containers
down:
	@echo "Stopping cluster... "
	$(DOCKER_COMPOSE_CMD) down
	@echo "Cluster stopped and removed."

# View logs (follow)
logs:
	$(DOCKER_COMPOSE_CMD) logs -f

# Run basic smoke tests
test: start
	@echo "\nWaiting for cluster to initialize..."
	sleep 10
	@echo "\nRunning smoke tests..."
	./test-smoke.sh
	@echo "\nTests completed."


# Show help
help:
	@echo "\nDistributed Key-Value Store Management"
	@echo "Usage: make [target]\n"
	@echo "Targets:"
	@echo "  build     Build project and Docker image"
	@echo "  start     Start the cluster (detached)"
	@echo "  down      Stop and remove containers"
	@echo "  logs      View container logs"
	@echo "  test      Build, start, and run smoke tests"
	@echo "  clean     Remove all artifacts and Docker images"
	@echo "  all       Build and start (default)"
	@echo "  help      Show this help message"