# Accepted misconfigurations that the plain .trivyignore cannot scope to one resource.
# Review date: 2027-02-17, or when the middleware moves to managed services or other images.
package trivy

import rego.v1

default ignore := false

# Neo4j, the Confluent Zookeeper and Kafka images and OpenLDAP render their configuration
# into the image filesystem at start, so their root filesystem cannot be read-only. Every
# application container and PostgreSQL and Redis run read-only (deploy/k8s/circleguard).
writable_root := {"neo4j", "zookeeper", "kafka", "openldap"}

ignore if {
	input.AVDID == "AVD-KSV-0014"
	some name in writable_root
	contains(input.Message, sprintf("Container '%s' of", [name]))
}
