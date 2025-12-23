#!/bin/bash

# SPDX-FileCopyrightText: 2025 NOI Techpark <digital@noi.bz.it>
#
# SPDX-License-Identifier: CC0-1.0

# Check arguments
if [ "$#" -ne 2 ]; then
    echo "Usage: $0 <basepath> <csv_file>"
    exit 1
fi

BASEPATH="$1"
CSV_FILE="$2"
OUTPUT_CSV="benchmark_report_$(date +%s).csv"

# OAuth configuration
TOKEN_URL="https://auth.opendatahub.testingmachine.eu/auth/realms/noi/protocol/openid-connect/token"

# Get OAuth token
echo "Obtaining OAuth token..."
TOKEN_RESPONSE=$(curl -s -X POST "$ODH_OAUTH_URI" \
    -H "Content-Type: application/x-www-form-urlencoded" \
    -d "grant_type=client_credentials&client_id=${ODH_OAUTH_CLIENT_ID}&client_secret=${ODH_OAUTH_CLIENT_SECRET}&scope=openid")

ACCESS_TOKEN=$(echo "$TOKEN_RESPONSE" | grep -o '"access_token":"[^"]*' | cut -d'"' -f4)

if [ -z "$ACCESS_TOKEN" ]; then
    echo "Failed to obtain OAuth token"
    exit 1
fi

# Initialize arrays for statistics
declare -a times
declare -a sizes
total_time=0
total_size=0
errors=0

# Prepare output CSV
echo "url,http_code,time_total,size_download" > "$OUTPUT_CSV"

echo "Starting benchmark..."
echo ""
echo "=== Benchmark Progress ==="
echo "Processed: 0 requests"
echo "Failed: 0"
echo "Elapsed: 0s"
echo "Avg time: 0s"
echo "Median: 0s"
echo "95th percentile: 0s"
echo "Total size: 0 bytes"

start_total=$(date +%s.%N)

# Skip header and process each line
row=0
tail -n +2 "$CSV_FILE" | while IFS=',' read -r representation stationtype datatype datefrom dateto where limit; do
    ((row++))
    
    # Build URL
    url="${BASEPATH}/${representation}/${stationtype}/${datatype}/${datefrom}/${dateto}"
    
    
    # Add query parameters if present
    query="?select=mvalue"
    [ -n "$where" ] && query="${query}&where=$(urlencode $where)"
    [ -n "$limit" ] && query="${query}&limit=${limit}"
    
    url="${url}${query}"
    
    echo Requesting $url
    
    # Execute request and capture metrics
    response=$(curl -s -w "%{http_code}\n%{time_total}\n%{size_download}" \
        -H "Authorization: Bearer $ACCESS_TOKEN" \
        -H "Referer: benchmark" \
        -o /dev/null \
        "$url")
    
    # Parse response
    http_code=$(echo "$response" | sed -n '1p')
    time_total=$(echo "$response" | sed -n '2p')
    size_download=$(echo "$response" | sed -n '3p')
    
    # Write to CSV
    echo "\"$url\",$http_code,$time_total,$size_download" >> "$OUTPUT_CSV"
    
    # Check for errors
    if [ "$http_code" != "200" ]; then
        echo "ERROR: $url returned HTTP $http_code" >&2
        ((errors++))
    fi
    
    # Store metrics
    times+=("$time_total")
    sizes+=("$size_download")
    total_time=$(echo "$total_time + $time_total" | bc)
    total_size=$(echo "$total_size + $size_download" | bc)
    
    # Calculate current statistics
    count=${#times[@]}
    avg_time=$(echo "scale=6; $total_time / $count" | bc)
    
    # Sort times for percentile calculations
    IFS=$'\n' sorted_times=($(sort -n <<<"${times[*]}"))
    unset IFS
    
    # Calculate median
    mid=$((count / 2))
    if [ $((count % 2)) -eq 0 ]; then
        median=$(echo "scale=6; (${sorted_times[$mid-1]} + ${sorted_times[$mid]}) / 2" | bc)
    else
        median=${sorted_times[$mid]}
    fi
    
    # Calculate 95th percentile
    p95_idx=$(echo "($count * 0.95) / 1" | bc)
    p95=${sorted_times[$p95_idx]}
    
    current_time=$(date +%s.%N)
    elapsed=$(echo "$current_time - $start_total" | bc)
    
    # Clear previous output and print updated summary (8 lines)
    # printf "\033[9A\033[J"
    echo "=== Benchmark Progress ==="
    echo "Processed: $count requests"
    echo "Failed: $errors"
    echo "Elapsed: ${elapsed}s"
    echo "Avg time: ${avg_time}s"
    echo "Median: ${median}s"
    echo "95th percentile: ${p95}s"
    echo "Total size: ${total_size} bytes"
done

end_total=$(date +%s.%N)
wall_time=$(echo "$end_total - $start_total" | bc)

echo "Benchmark done"
echo "Detailed report written to: $OUTPUT_CSV"