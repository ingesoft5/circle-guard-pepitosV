output "state_bucket" {
  description = "Bucket for the environment state files (bucket in *.s3.tfbackend)."
  value       = aws_s3_bucket.state.bucket
}

output "lock_table" {
  description = "DynamoDB lock table (dynamodb_table in *.s3.tfbackend)."
  value       = aws_dynamodb_table.locks.name
}
