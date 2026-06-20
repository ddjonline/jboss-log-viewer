#!/bin/bash

# Test API for tree with size and lastModified
curl -s "http://localhost:8080/jboss/logs/viewer/api/tree?set=server" |
  jq '.children[] | {name: .name, size: .size, lastModified: .lastModified}'