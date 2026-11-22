output "registry_host" {
  description = "ECR registry host, derived from the first repository URL."
  value       = try(split("/", values(aws_ecr_repository.service)[0].repository_url)[0], "")
}

output "repository_urls" {
  description = "Repository URL per service name."
  value       = { for name, repository in aws_ecr_repository.service : name => repository.repository_url }
}

output "repository_arns" {
  description = "Repository ARN per service name."
  value       = { for name, repository in aws_ecr_repository.service : name => repository.arn }
}
