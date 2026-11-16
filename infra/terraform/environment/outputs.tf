output "node_public_ip" {
  description = "Elastic IP of the cluster: API server on 6443 and Ingress host (<ip>.sslip.io)."
  value       = module.k3s.public_ip
}

output "node_private_ip" {
  value = module.k3s.private_ip
}

output "instance_id" {
  value = module.k3s.instance_id
}

output "vpc_id" {
  value = module.network.vpc_id
}

output "registry_host" {
  description = "ECR registry host for the environment images."
  value       = module.registry.registry_host
}

output "storage_bucket" {
  description = "S3 bucket for the file service attachments and backups."
  value       = module.storage.bucket_id
}

output "observability_topic_arn" {
  description = "SNS topic that receives the observability alarms."
  value       = module.observability.alarm_topic_arn
}
