terraform {
  required_version = ">= 1.9.0"
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }

  # Partial configuration: bucket, key and lock table come from backend.hcl,
  #   terraform init -reconfigure -backend-config=backend.hcl
  backend "s3" {}
}

provider "aws" {
  region = var.region
  default_tags {
    tags = {
      Project     = "CircleGuard"
      Environment = var.environment
      ManagedBy   = "terraform"
    }
  }
}
