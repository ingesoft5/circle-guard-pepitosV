variable "environment" {
  description = "Environment name: dev, stage or prod."
  type        = string

  validation {
    condition     = contains(["dev", "stage", "prod"], var.environment)
    error_message = "environment must be dev, stage or prod."
  }
}

variable "name_prefix" {
  description = "Prefix of the alarm and topic names, e.g. circleguard-k3s-dev."
  type        = string
}

variable "service_names" {
  description = "Services that get their own CloudWatch log group."
  type        = list(string)
}

variable "log_retention_days" {
  description = "CloudWatch retention in days (7, 14, 30...)."
  type        = number
  default     = 14
}

variable "alarm_email" {
  description = "Email subscribed to the alerts topic. Empty disables the subscription."
  type        = string
  default     = ""
}

variable "cpu_alarm_threshold" {
  description = "CPU percentage that triggers the high CPU alarm."
  type        = number
  default     = 80
}

variable "instance_id" {
  description = "EC2 instance of the cluster to monitor."
  type        = string
}

variable "prometheus_ssh_host" {
  description = "Host used to reach the node: the public Elastic IP from a workstation, the private IP from Jenkins."
  type        = string
}

variable "install_prometheus" {
  description = "Install kube-prometheus-stack inside the cluster (HU-26/HU-39 dependency)."
  type        = bool
  default     = true
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
  description = "Private key used to reach the node. Empty uses the SSH agent."
  type        = string
  sensitive   = true
  default     = ""
}
