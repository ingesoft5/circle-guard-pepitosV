output "bucket_id" {
  description = "Name of the S3 bucket."
  value       = aws_s3_bucket.files.id
}

output "bucket_arn" {
  description = "ARN of the S3 bucket."
  value       = aws_s3_bucket.files.arn
}

output "bucket_domain_name" {
  description = "Regional bucket domain name."
  value       = aws_s3_bucket.files.bucket_domain_name
}
