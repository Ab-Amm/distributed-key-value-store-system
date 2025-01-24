#!/bin/bash
set -e

SHARD_MANAGER_URL="http://localhost:8080/shard-manager"
NODE1="http://localhost:8081"
NODE2="http://localhost:8082"
NODE3="http://localhost:8083"
NODE4="http://localhost:8084"

# Test colors
RED='\033[0;31m'
GREEN='\033[0;32m'
NC='\033[0m' # No Color

log_success() {
  echo -e "${GREEN}$1${NC}"
}

log_error() {
  echo -e "${RED}$1${NC}"
}

verify_response() {
  if [[ "$1" != *"$2"* ]]; then
    log_error "Error: Response mismatch. Expected: $2, Got: $1"
    exit 1
  fi
}

verify_shard_registration() {
  echo "=== Verifying Automatic Shard Registration ==="

  # Verify shard1 registration
  shard1_info=$(curl -s "$SHARD_MANAGER_URL/shard/shard1_key")
  verify_json_field "$shard1_info" ".shardId" "shard1"
  verify_json_field_count "$shard1_info" ".nodes" 2

  # Verify shard2 registration
  shard2_info=$(curl -s "$SHARD_MANAGER_URL/shard/shard2_key")
  verify_json_field "$shard2_info" ".shardId" "shard2"
  verify_json_field_count "$shard2_info" ".nodes" 2

  log_success "✓ Automatic shard registration validated"
}

verify_json_field() {
  local json="$1"
  local field="$2"
  local expected="$3"
  local actual=$(echo "$json" | jq -r "$field")

  [[ "$actual" == "$expected" ]] || {
    log_error "Field $field mismatch. Expected: $expected, Actual: $actual"
    exit 1
  }
}
test_leadership_election() {
  echo "=== Testing Raft Leadership ==="

  for node in $NODE1 $NODE2; do
    leader_status=$(curl -s "$node/leader")
    if [[ "$leader_status" == "LEADER" ]]; then
      log_success "✓ Leader found in shard1: $node"
      return 0
    fi
  done

  for node in $NODE3 $NODE4; do
    leader_status=$(curl -s "$node/leader")
    if [[ "$leader_status" == "LEADER" ]]; then
      log_success "✓ Leader found in shard2: $node"
      return 0
    fi
  done

  log_error "No leader elected in either shard"
  exit 1
}

test_key_distribution() {
  echo "=== Testing Key Distribution ==="

  declare -A shard_map
  test_keys=("key1" "key2" "key3" "key4" "key5")

  for key in "${test_keys[@]}"; do
    # Get shard assignment
    shard_info=$(curl -s "$SHARD_MANAGER_URL/shard/$key")
    shard_id=$(echo $shard_info | jq -r '.shardId')
    nodes=$(echo $shard_info | jq -r '.nodes | join(", ")')

    echo "Key '$key' assigned to: $shard_id (nodes: $nodes)"
    shard_map["$key"]=$shard_id

    # Verify consistent mapping
    for i in {1..3}; do
      current_shard=$(curl -s "$SHARD_MANAGER_URL/shard/$key" | jq -r '.shardId')
      if [[ "$current_shard" != "$shard_id" ]]; then
        log_error "Inconsistent shard mapping for $key"
        exit 1
      fi
    done
  done

  # Verify keys are distributed across shards
  unique_shards=($(printf "%s\n" "${shard_map[@]}" | sort -u))
  if [[ ${#unique_shards[@]} -lt 2 ]]; then
    log_error "Keys not distributed across multiple shards"
    exit 1
  fi
  log_success "✓ Key distribution and consistent hashing working"
}

test_data_operations() {
  echo "=== Testing Data Operations ==="

  test_data=(
    {"key":"shard1_key","value":"shard1_value"}
    {"key":"shard2_key","value":"shard2_value"}
  )

  for entry in "${test_data[@]}"; do
    key=$(echo $entry | jq -r '.key')
    value=$(echo $entry | jq -r '.value')

    # Get target shard
    shard_info=$(curl -s "$SHARD_MANAGER_URL/shard/$key")
    shard_id=$(echo $shard_info | jq -r '.shardId')
    nodes=$(echo $shard_info | jq -r '.nodes[0]') # Use first node

    # Write operation
    echo "Writing $key to $shard_id via $nodes"
    curl -s -X PUT "$nodes/api/v1/keys/$key" -d "$value"

    # Read from all nodes in shard
    for node in $(echo $shard_info | jq -r '.nodes[]'); do
      retrieved=$(curl -s "$node/api/v1/keys/$key")
      if [[ "$retrieved" != "$value" ]]; then
        log_error "Data inconsistency in $node: Expected '$value', got '$retrieved'"
        exit 1
      fi
    done
    log_success "✓ $key replicated across $shard_id"
  done
}

test_cross_shard_operations() {
  echo "=== Testing Cross-Shard Operations ==="

  # This key should belong to shard1
  test_key="cross_shard_key"
  shard_info=$(curl -s "$SHARD_MANAGER_URL/shard/$test_key")
  shard_id=$(echo $shard_info | jq -r '.shardId')
  correct_node=$(echo $shard_info | jq -r '.nodes[0]')
  wrong_node=$(echo $shard_info | jq -r '.nodes[1]')

  # Try writing to wrong shard
  echo "Attempting invalid write to non-owner shard..."
  status_code=$(curl -s -o /dev/null -w "%{http_code}" -X PUT "$wrong_node/api/v1/keys/$test_key" -d "value")

  if [[ "$status_code" != "500" && "$status_code" != "400" ]]; then
    log_error "Expected error when writing to wrong shard, got: $status_code"
    exit 1
  fi
  log_success "✓ Cross-shard write prevention working"

  # Valid write to correct shard
  curl -s -X PUT "$correct_node/api/v1/keys/$test_key" -d "correct_value"
  retrieved=$(curl -s "$correct_node/api/v1/keys/$test_key")
  verify_response "$retrieved" "correct_value"
  log_success "✓ Valid write successful"
}

cleanup() {
  echo "=== Cleaning Up Test Data ==="
  curl -s -X DELETE "$NODE1/api/v1/keys/shard1_key"
  curl -s -X DELETE "$NODE3/api/v1/keys/shard2_key"
  curl -s -X DELETE "$NODE1/api/v1/keys/cross_shard_key"
}

# Run tests
verify_shard_registration
test_leadership_election
test_key_distribution
test_data_operations
test_cross_shard_operations
cleanup

echo -e "\n${GREEN}All smoke tests passed successfully!${NC}"