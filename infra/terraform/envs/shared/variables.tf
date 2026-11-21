variable "environment" {
  description = "dev, stage or prod."
  type        = string
  validation {
    condition     = contains(["dev", "stage", "prod"], var.environment)
    error_message = "environment must be dev, stage or prod."
  }
}

variable "region" {
  type    = string
  default = "us-east-1"
}

variable "vpc_cidr" {
  description = "CIDR of the environment VPC (10.10/16 dev, 10.20/16 stage, 10.30/16 prod)."
  type        = string
}

variable "instance_type" {
  type    = string
  default = "m7i-flex.large"
}

variable "root_volume_gb" {
  type    = number
  default = 40
}

variable "k3s_version" {
  type    = string
  default = "v1.30.6+k3s1"
}

variable "key_name" {
  description = "EC2 key pair of the team (the same one as the CI hosts)."
  type        = string
  default     = "circleguard-admin-key"
}

variable "admin_cidrs" {
  description = "CIDRs with SSH and API server access set by Terraform; others are added with hosts.sh allow."
  type        = list(string)
  default     = []
}

variable "ci_instance_name" {
  type    = string
  default = "circleguard-ci"
}

variable "tools_instance_name" {
  type    = string
  default = "circleguard-tools"
}

variable "tools_security_group_name" {
  type    = string
  default = "circleguard-tools-sg"
}

variable "service_names" {
  description = "Services with their own ECR repository and CloudWatch log group."
  type        = list(string)
  default = [
    "auth",
    "identity",
    "promotion",
    "notification",
    "form",
    "file",
    "gateway",
    "dashboard",
  ]
}

variable "registry_keep_last_images" {
  description = "Images kept per ECR repository by the lifecycle policy."
  type        = number
  default     = 30
}

variable "storage_force_destroy" {
  description = "Delete the storage bucket contents on destroy. Enable only in dev."
  type        = bool
  default     = false
}

variable "storage_versioning_enabled" {
  description = "Keep previous versions of the objects in the storage bucket."
  type        = bool
  default     = true
}

variable "storage_noncurrent_version_expiration_days" {
  description = "Days after which a noncurrent object version is expired."
  type        = number
  default     = 30
}

variable "log_retention_days" {
  description = "CloudWatch retention for the service log groups."
  type        = number
  default     = 14
}

variable "alarm_email" {
  description = "Email subscribed to the observability alerts topic. Empty disables it."
  type        = string
  default     = ""
}

variable "cpu_alarm_threshold" {
  description = "CPU percentage that triggers the node alarm."
  type        = number
  default     = 80
}

variable "install_prometheus" {
  description = "Install kube-prometheus-stack in the cluster (HU-26/HU-39 dependency)."
  type        = bool
  default     = true
}

variable "prometheus_ssh_host" {
  description = "Host used to reach the k3s node. Empty uses the public Elastic IP; Jenkins sets the private IP."
  type        = string
  default     = ""
}

variable "prometheus_namespace" {
  description = "Namespace of kube-prometheus-stack."
  type        = string
  default     = "monitoring"
}

variable "prometheus_chart_version" {
  description = "Pinned version of the kube-prometheus-stack Helm chart."
  type        = string
  default     = "65.5.1"
}

variable "prometheus_grafana_admin_password" {
  description = "Grafana admin password. Empty keeps the chart default; pass it with TF_VAR_ and never commit it."
  type        = string
  sensitive   = true
  default     = ""
}

variable "prometheus_ssh_private_key" {
  description = "Private key used to reach the k3s node. Empty uses the SSH agent."
  type        = string
  sensitive   = true
  default     = ""
}
