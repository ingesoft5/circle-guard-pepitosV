# CircleGuard — Executive Summary

**Project:** Software Engineering V — Final Project  
**Team:** Johan Suarez, Jean Carlo Ocampo, David Chicué Romero, Ismael Barrionuevo  
**Date:** November 2026

## The problem

University campuses need to contain outbreaks quickly without exposing students' identities.
Manual contact tracing is slow and inconsistent, and centralized personal data increases the
risk of leaks. CircleGuard combines campus-native signals with a privacy-first design so
containment can happen in minutes, not days.

## The solution

CircleGuard is a cloud-native contact-tracing platform built as eight Spring Boot
microservices. Kafka carries asynchronous events, Neo4j traverses contact circles, PostgreSQL
stores transactional data, Redis keeps ephemeral health state, OpenLDAP supports campus
authentication, and S3 holds certificates and attachments. A web client provides the student
and health-center experience.

Each environment runs its own single-node k3s cluster on AWS. The same Terraform modules and
Kubernetes Helm chart deploy `dev`, `stage` and `prod`; only values such as VPC CIDR, log
retention and registry settings change. This keeps the three environments reproducible.

## Delivery and operations

- **CI/CD:** Jenkins multibranch pipeline builds, tests, scans and packages only the services
  touched by a change. Releases are promoted dev → stage → prod; production requires an admin
  approval.
- **Infrastructure as code:** Terraform modules for network, cluster, registry, storage and
  observability. State lives in S3 with versioning and encryption, and DynamoDB provides state
  locking.
- **Observability:** Prometheus, Grafana and Alertmanager run in-cluster, with Micrometer
  metrics, JSON logs and distributed tracing across Kafka.
- **Resilience and architecture:** Circuit breaker/retry in the gateway, external configuration
  and feature toggles, and a transactional outbox that prevents event loss when Kafka is
  unavailable.
- **Security:** Sealed Secrets, no credentials in Git, TLS and access controls planned as
  hardening; production approvals are role-restricted.

## Results

The pipeline runs unit, integration and E2E tests, SonarQube quality gates, Trivy scans and
security checks. The team documented the existing architecture patterns and added measurable
implementations: resilience, external configuration, and transactional outbox. Each environment
exposes the application, dashboards and alerts; runbooks cover service failures, alerts,
rollback and secret rotation.

## Cost

A full month of on-demand list pricing for the running infrastructure is estimated at about
**$390 USD** (five instances, 260 GB EBS, public IPv4, logs and supporting services). The
actual usage from November 1–22, 2026 was **$59.72**, fully covered by **$59.72 in credits**, for
a net of approximately **$0**. The difference comes from the partial period, stopped legacy
hosts, empty image/S3 storage and account credits. The environments remain active for the
evaluation; the remaining costs and the future teardown procedure are documented separately.

## Limitations and next steps

The clusters are single-node and use local-path storage, so they are not highly available.
Database backups and disaster recovery are manual, and TLS for exposed tools is a pending
hardening item. The next steps are to add managed backups, complete TLS and RBAC, and move
production workloads to a multi-node, managed Kubernetes platform.
