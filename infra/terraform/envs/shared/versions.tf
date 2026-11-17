terraform {
  required_version = ">= 1.9.0"
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }

  # Partial configuration: bucket, key and lock table come from <env>.s3.tfbackend,
  #   terraform init -reconfigure -backend-config=dev.s3.tfbackend
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
