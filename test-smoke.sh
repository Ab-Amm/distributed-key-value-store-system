#!/bin/bash
# Enable colors for logs
GREEN='\033[0;32m'
BLUE='\033[1;34m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

# Helper function to log commands
log_cmd() {
    echo -e "${BLUE}>> $1${NC}"
}

# Helper function to log informational messages
log_info() {
    echo -e "${YELLOW}[INFO] $1${NC}"
}

# Helper function to log successes
log_success() {
    echo -e "${GREEN}[SUCCESS] $1${NC}"
}

# Helper function to log errors
log_error() {
    echo -e "${RED}[ERROR] $1${NC}"
}

# Helper function to check API response
check_response() {
    local response="$1"
    local expected="$2"
    if [[ "$response" != "$expected" ]]; then
        log_error "Expected '$expected', got '$response'"
        exit 1
    fi
}

# Configuration
LB_URL="http://localhost:8087"
SHARD_MANAGER="http://localhost:8080"

# Define URLs and Docker container names for each shard
SHARD1_NODES=("http://localhost:8081" "http://localhost:8082" "http://localhost:8083")
SHARD1_NAMES=("shard1-node1" "shard1-node2" "shard1-node3")

SHARD2_NODES=("http://localhost:8084" "http://localhost:8085" "http://localhost:8086")
SHARD2_NAMES=("shard2-node1" "shard2-node2" "shard2-node3")

TEST_KEY1="fruit"
TEST_VALUE1="apple"
TEST_KEY2="vehicle"
TEST_VALUE2="truck"

echo -e "\n${YELLOW}========== Starting Distributed System Smoke Tests ==========${NC}\n"
echo
echo
# Test 1: Verify Shard Mapping
log_info "Testing Shard Mapping..."
log_cmd "curl -s \"$SHARD_MANAGER/shard-manager/shard/$TEST_KEY1\" | jq -r '.shardId'"
SHARD1_HASH=$(curl -s "$SHARD_MANAGER/shard-manager/shard/$TEST_KEY1" | jq -r '.shardId')
log_cmd "curl -s \"$SHARD_MANAGER/shard-manager/shard/$TEST_KEY2\" | jq -r '.shardId'"
SHARD2_HASH=$(curl -s "$SHARD_MANAGER/shard-manager/shard/$TEST_KEY2" | jq -r '.shardId')

echo -e "${YELLOW}Shard for '$TEST_KEY1':${NC} $SHARD1_HASH"
echo -e "${YELLOW}Shard for '$TEST_KEY2':${NC} $SHARD2_HASH"

if [[ "$SHARD1_HASH" == "$SHARD2_HASH" ]]; then
    log_error "Both keys mapped to the same shard!"
    exit 1
fi
log_success "Shard mapping verified!"

echo
echo
# Test 2: Write Data to Shards
log_info "Writing Test Data..."
log_cmd "curl --location --request PUT \"$LB_URL/api/v1/keys/$TEST_KEY1\" --header 'Content-Type: text/plain' --data \"$TEST_VALUE1\""
curl --location --request PUT "$LB_URL/api/v1/keys/$TEST_KEY1" --header 'Content-Type: text/plain' --data "$TEST_VALUE1"
log_cmd "curl --location --request PUT \"$LB_URL/api/v1/keys/$TEST_KEY2\" --header 'Content-Type: text/plain' --data \"$TEST_VALUE2\""
curl --location --request PUT "$LB_URL/api/v1/keys/$TEST_KEY2" --header 'Content-Type: text/plain' --data "$TEST_VALUE2"
log_success "Test data written successfully!"

echo
echo
# Test 3: Verify Replication in Shard 1
log_info "Verifying Replication in Shard 1..."
for i in "${!SHARD1_NODES[@]}"; do
    node="${SHARD1_NODES[$i]}"
    name="${SHARD1_NAMES[$i]}"
    log_cmd "curl -s \"$node/internal/api/v1/keys/$TEST_KEY1\""
    response=$(curl -s "$node/internal/api/v1/keys/$TEST_KEY1")
    check_response "$response" "$TEST_VALUE1"
    log_success "$name ($node) has the correct value for '$TEST_KEY1'"
done

echo
echo
# Test 4: Verify Replication in Shard 2
log_info "Verifying Replication in Shard 2..."
for i in "${!SHARD2_NODES[@]}"; do
    node="${SHARD2_NODES[$i]}"
    name="${SHARD2_NAMES[$i]}"
    log_cmd "curl -s \"$node/internal/api/v1/keys/$TEST_KEY2\""
    response=$(curl -s "$node/internal/api/v1/keys/$TEST_KEY2")
    check_response "$response" "$TEST_VALUE2"
    log_success "$name ($node) has the correct value for '$TEST_KEY2'"
done

echo
echo
# Test 5: Check Leadership Using /leader/shard* Endpoints
log_info "Checking Leadership..."
LEADER1_URL=""
LEADER1_NAME=""
for i in "${!SHARD1_NODES[@]}"; do
    node="${SHARD1_NODES[$i]}"
    name="${SHARD1_NAMES[$i]}"
    log_cmd "curl -s \"$node/leader/shard1\""
    status=$(curl -s "$node/leader/shard1")
    echo -e "${YELLOW}Shard 1 - $name ($node) reports:${NC} $status"
    if [[ "$status" == "LEADER" ]]; then
        LEADER1_URL="$node"
        LEADER1_NAME="$name"
    fi
done

if [[ -z "$LEADER1_URL" ]]; then
    log_error "No leader detected in Shard 1!"
    exit 1
fi

LEADER2_URL=""
LEADER2_NAME=""
for i in "${!SHARD2_NODES[@]}"; do
    node="${SHARD2_NODES[$i]}"
    name="${SHARD2_NAMES[$i]}"
    log_cmd "curl -s \"$node/leader/shard2\""
    status=$(curl -s "$node/leader/shard2")
    echo -e "${YELLOW}Shard 2 - $name ($node) reports:${NC} $status"
    if [[ "$status" == "LEADER" ]]; then
        LEADER2_URL="$node"
        LEADER2_NAME="$name"
    fi
done

if [[ -z "$LEADER2_URL" ]]; then
    log_error "No leader detected in Shard 2!"
    exit 1
fi

echo -e "${YELLOW}Shard 1 Leader:${NC} $LEADER1_NAME ($LEADER1_URL)"
echo -e "${YELLOW}Shard 2 Leader:${NC} $LEADER2_NAME ($LEADER2_URL)"
log_success "Leadership verified!"

echo
echo
# Test 6: Simulate Leader Failure in Shard 1
log_info "Simulating Leader Failure in Shard 1..."
FAILED_LEADER_URL="$LEADER1_URL"
FAILED_LEADER_NAME="$LEADER1_NAME"
echo -e "${YELLOW}Stopping leader container:${NC} $FAILED_LEADER_NAME"
log_cmd "docker-compose stop \"$FAILED_LEADER_NAME\""
docker-compose stop "$FAILED_LEADER_NAME"

# Wait for leader re-election
log_info "Waiting for leader re-election (10 seconds)..."
sleep 10

# Find the new leader for shard 1
NEW_LEADER_URL=""
NEW_LEADER_NAME=""
for i in "${!SHARD1_NODES[@]}"; do
    # Skip the node that we just stopped
    if [[ "${SHARD1_NODES[$i]}" == "$FAILED_LEADER_URL" ]]; then
        continue
    fi
    log_cmd "curl -s \"${SHARD1_NODES[$i]}/leader/shard1\""
    status=$(curl -s "${SHARD1_NODES[$i]}/leader/shard1")
    echo -e "${YELLOW}After failure, ${SHARD1_NAMES[$i]} (${SHARD1_NODES[$i]}) reports:${NC} $status"
    if [[ "$status" == "LEADER" ]]; then
        NEW_LEADER_URL="${SHARD1_NODES[$i]}"
        NEW_LEADER_NAME="${SHARD1_NAMES[$i]}"
    fi
done

if [[ -z "$NEW_LEADER_URL" || "$NEW_LEADER_URL" == "$FAILED_LEADER_URL" ]]; then
    log_error "Leader did not change after failure!"
    exit 1
fi

echo -e "${YELLOW}New Shard 1 Leader:${NC} $NEW_LEADER_NAME ($NEW_LEADER_URL)"
log_success "Leader failure simulated and re-election confirmed!"

echo
echo
# Test 8: Verify Data Persistence After Failure
log_info "Verifying Data Persistence..."
log_cmd "curl -s \"$LB_URL/api/v1/keys/$TEST_KEY1\""
response=$(curl -s "$LB_URL/api/v1/keys/$TEST_KEY1")
check_response "$response" "$TEST_VALUE1"
log_success "$response -Data persisted after leader failure!"

echo
echo
# Test 9: Restart the Failed Leader and Verify Rejoin
log_info "Restarting failed leader and verifying rejoin..."
log_cmd "docker-compose start \"$FAILED_LEADER_NAME\""
docker-compose start "$FAILED_LEADER_NAME"
sleep 15

log_cmd "curl -s \"$FAILED_LEADER_URL/leader/shard1\""
REJOIN_STATUS=$(curl -s "$FAILED_LEADER_URL/leader/shard1")
if [[ "$REJOIN_STATUS" != "FOLLOWER" ]]; then
    log_error "Failed leader did not rejoin as follower! Status: $REJOIN_STATUS"
    exit 1
fi
log_success "Node ($FAILED_LEADER_NAME) rejoined cluster successfully!"

echo -e "\n${GREEN}========== All Smoke Tests Passed! ==========${NC}\n"
