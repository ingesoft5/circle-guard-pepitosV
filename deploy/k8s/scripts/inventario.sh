#!/usr/bin/env bash
# Read-only inventory of one k3s node (HU-54): prints what is deployed and changes nothing.
# Run it on the node: ssh ubuntu@<ip> 'bash -s' < deploy/k8s/scripts/inventario.sh
# Secrets are listed by name and type only, never their data.
set -u
k() { sudo k3s kubectl "$@" 2>&1; }
h() { sudo env KUBECONFIG=/etc/rancher/k3s/k3s.yaml helm "$@" 2>&1; }
section() { printf '\n## %s\n' "$1"; }

section "k3s";            sudo k3s --version 2>&1 | head -1
section "nodes";          k get nodes -o wide
section "namespaces";     k get ns
section "helm releases";  h list -A
section "workloads";      k get deploy,statefulset,daemonset -A -o wide
section "images";         k get pods -A --no-headers -o custom-columns='NS:.metadata.namespace,POD:.metadata.name,IMAGES:.spec.containers[*].image'
section "pods";           k get pods -A -o wide
section "services";       k get svc -A
section "ingress";        k get ingress -A
section "traefik";        k get ingressroute,middleware -A
section "tls";            k get certificate,clusterissuer,issuer -A
section "storage";        k get pvc -A
section "autoscaling";    k get hpa -A
section "jobs";           k get cronjob,job -A
section "monitoring";     k get servicemonitor,podmonitor,prometheusrule,alertmanagerconfig -A
section "sealed secrets"; k get sealedsecret -A
section "secrets";        k get secrets -A --no-headers -o custom-columns='NS:.metadata.namespace,NAME:.metadata.name,TYPE:.type'
section "configmaps";     k get configmap -A --no-headers -o custom-columns='NS:.metadata.namespace,NAME:.metadata.name'
section "networkpolicy";  k get networkpolicy -A
section "crds";           k get crd -o name
