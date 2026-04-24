#!/bin/bash

# LBMH-Miner Clean Script
# This script removes all compiled .class files from the project

echo "=========================================="
echo "  LBMH-Miner Clean Script"
echo "=========================================="

# Count .class files before cleaning
class_count=$(find . -name "*.class" -type f | wc -l | tr -d ' ')

if [ "$class_count" -eq 0 ]; then
    echo "✓ No .class files found - project is already clean"
else
    echo "Found $class_count .class file(s)"
    echo "Cleaning..."
    
    # Remove all .class files recursively
    find . -name "*.class" -type f -delete
    
    if [ $? -eq 0 ]; then
        echo "✓ Successfully removed $class_count .class file(s)"
    else
        echo "✗ Error during cleanup"
        exit 1
    fi
fi

echo "=========================================="
echo "  Cleanup completed!"
echo "=========================================="
