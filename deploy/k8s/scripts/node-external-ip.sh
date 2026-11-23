#!/usr/bin/env bash
# Prints the first node's ExternalIP, or nothing when the node has no public address.
set -eu
kubectl get nodes -o jsonpath='{.items[0].status.addresses[?(@.type=="ExternalIP")].address}'
echo
