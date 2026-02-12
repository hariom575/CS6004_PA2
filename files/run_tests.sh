#!/bin/bash

# Redundant Load Analysis - Test Suite Runner
# This script sets up and runs all test cases

echo "=========================================="
echo "Redundant Load Analysis - Test Suite"
echo "=========================================="
echo ""

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Check if soot jar exists
if [ ! -f "soot-4.6.0-jar-with-dependencies.jar" ]; then
    echo -e "${RED}Error: soot-4.6.0-jar-with-dependencies.jar not found${NC}"
    echo "Please download it from: https://github.com/soot-oss/soot/releases"
    exit 1
fi

# Compile PA2.java if needed
if [ ! -f "PA2.class" ] || [ "PA2.java" -nt "PA2.class" ]; then
    echo -e "${YELLOW}Compiling PA2.java...${NC}"
    javac -cp .:soot-4.6.0-jar-with-dependencies.jar PA2.java
    if [ $? -ne 0 ]; then
        echo -e "${RED}Failed to compile PA2.java${NC}"
        exit 1
    fi
    echo -e "${GREEN}PA2.java compiled successfully${NC}"
    echo ""
fi

# Test cases
declare -a testcases=("TestCase1" "TestCase2" "TestCase3" "TestCase4" "TestCase5" 
                      "TestCase6" "TestCase7" "TestCase8" "TestCase9" "TestCase10")

# Function to setup and run a test case
run_test() {
    local testname=$1
    local testfile="${testname}.java"
    local testdir="Test${testname#TestCase}"
    
    echo -e "${YELLOW}======================================${NC}"
    echo -e "${YELLOW}Running: $testname${NC}"
    echo -e "${YELLOW}======================================${NC}"
    
    # Create test directory
    mkdir -p "$testdir"
    
    # Copy test file
    if [ -f "$testfile" ]; then
        cp "$testfile" "$testdir/Test.java"
    else
        echo -e "${RED}Warning: $testfile not found, skipping...${NC}"
        return
    fi
    
    # Compile test case
    echo "Compiling $testdir/Test.java..."
    (cd "$testdir" && javac -g Test.java 2>&1)
    if [ $? -ne 0 ]; then
        echo -e "${RED}Failed to compile $testdir/Test.java${NC}"
        return
    fi
    
    # Run analysis
    echo "Running analysis on $testdir..."
    java -cp .:soot-4.6.0-jar-with-dependencies.jar PA2 "$testdir" 2>/dev/null
    
    echo ""
}

# Main execution
echo "Setting up and running all test cases..."
echo ""

# Run all tests
for testcase in "${testcases[@]}"; do
    run_test "$testcase"
done

echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}All tests completed!${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""
echo "Test directories created: Test1 through Test10"
echo "To run individual tests:"
echo "  java -cp .:soot-4.6.0-jar-with-dependencies.jar PA2 Test1"
echo ""
echo "To clean up:"
echo "  rm -rf Test1 Test2 Test3 Test4 Test5 Test6 Test7 Test8 Test9 Test10"
