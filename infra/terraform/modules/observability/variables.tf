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
