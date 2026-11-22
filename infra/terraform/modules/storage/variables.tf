variable "environment" {
  description = "Environment name: dev, stage or prod."
  type        = string

  validation {
    condition     = contains(["dev", "stage", "prod"], var.environment)
    error_message = "environment must be dev, stage or prod."
  }
}

variable "name_prefix" {
  description = "Prefix of the bucket name, e.g. circleguard-k3s-dev."
  type        = string
}

variable "force_destroy" {
  description = "Delete the bucket contents on destroy. Useful in dev, never in prod."
  type        = bool
  default     = false
}

variable "versioning_enabled" {
  description = "Keep previous versions of each object."
  type        = bool
  default     = true
}

variable "noncurrent_version_expiration_days" {
  description = "Days after which a noncurrent object version is expired."
  type        = number
  default     = 30
}
