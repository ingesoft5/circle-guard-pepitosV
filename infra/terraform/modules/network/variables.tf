variable "name" {
  description = "Prefix of the resource names, e.g. circleguard-dev."
  type        = string
}

variable "environment" {
  description = "Environment name: dev, stage or prod."
  type        = string
}

variable "vpc_cidr" {
  description = "CIDR of the environment VPC. Must not overlap the CI VPC or another environment."
  type        = string
}

variable "availability_zone" {
  description = "Availability zone of the public subnet; null picks the first available one."
  type        = string
  default     = null
}

variable "ci_vpc_id" {
  description = "VPC of the CI and tools hosts."
  type        = string
}

variable "ci_vpc_cidr" {
  description = "CIDR of the CI VPC."
  type        = string
}

variable "ci_route_table_id" {
  description = "Route table of the CI subnet, which gets the route back to this environment."
  type        = string
}

variable "ci_private_ip" {
  description = "Private IP of the Jenkins host."
  type        = string
}

variable "registry_security_group_id" {
  description = "Security Group of the tools host (Nexus registry)."
  type        = string
}

variable "registry_port" {
  description = "Port of the Docker registry on the tools host."
  type        = number
  default     = 5000
}

variable "admin_cidrs" {
  description = "CIDRs that get SSH and API server access from Terraform. Each person adds their own with hosts.sh allow."
  type        = list(string)
  default     = []
}
