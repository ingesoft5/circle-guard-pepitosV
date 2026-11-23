variable "name" {
  description = "Name of the node and its resources, e.g. circleguard-k3s-dev."
  type        = string
}

variable "subnet_id" {
  description = "Public subnet of the environment."
  type        = string
}

variable "security_group_id" {
  description = "Security Group of the node."
  type        = string
}

variable "key_name" {
  description = "EC2 key pair for SSH administration."
  type        = string
}

variable "instance_type" {
  description = "Instance type. The AWS free plan allows up to m7i-flex.large (2 vCPU, 8 GB)."
  type        = string
  default     = "m7i-flex.large"
}

variable "root_volume_gb" {
  description = "Size of the root volume (images, volumes of the cluster)."
  type        = number
  default     = 40
}

variable "k3s_version" {
  description = "Pinned k3s release, e.g. v1.30.6+k3s1."
  type        = string
}

variable "registry" {
  description = "host:port of the Nexus registry (tools host, private IP)."
  type        = string
}

variable "ami_id" {
  description = "AMI override; null uses the latest Ubuntu 24.04 from SSM."
  type        = string
  default     = null
}

variable "storage_bucket_arn" {
  description = "ARN of the environment bucket (module storage) the node role may read and write."
  type        = string
}
