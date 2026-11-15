variable "region" {
  description = "AWS region of the state bucket and lock table."
  type        = string
  default     = "us-east-1"
}

variable "lock_table_name" {
  description = "DynamoDB table used by the S3 backend to lock the state."
  type        = string
  default     = "circleguard-tf-locks"
}
