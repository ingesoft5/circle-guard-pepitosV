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

variable "noncurrent_state_expiration_days" {
  description = "Days to keep noncurrent versions of the state before they expire."
  type        = number
  default     = 90

  validation {
    condition     = var.noncurrent_state_expiration_days >= 1
    error_message = "noncurrent_state_expiration_days must be at least 1."
  }
}
