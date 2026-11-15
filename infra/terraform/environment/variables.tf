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
