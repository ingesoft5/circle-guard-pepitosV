variable "environment" {
  description = "Environment name: dev, stage or prod."
  type        = string

  validation {
    condition     = contains(["dev", "stage", "prod"], var.environment)
    error_message = "environment must be dev, stage or prod."
  }
}

variable "name_prefix" {
  description = "Prefix of the repository names, e.g. circleguard-k3s-dev."
  type        = string
}

variable "repository_names" {
  description = "Service names that get their own ECR repository."
  type        = list(string)
}

variable "image_tag_mutability" {
  description = "MUTABLE lets dev reuse tags; IMMUTABLE is safer for prod."
  type        = string
  default     = "MUTABLE"

  validation {
    condition     = contains(["MUTABLE", "IMMUTABLE"], var.image_tag_mutability)
    error_message = "image_tag_mutability must be MUTABLE or IMMUTABLE."
  }
}

variable "scan_on_push" {
  description = "Scan images for vulnerabilities when they are pushed."
  type        = bool
  default     = true
}

variable "keep_last_images" {
  description = "Number of images kept per repository by the lifecycle policy."
  type        = number
  default     = 30
}
