output "log_group_names" {
  description = "CloudWatch log group name per service."
  value       = { for name, group in aws_cloudwatch_log_group.service : name => group.name }
}

output "alarm_topic_arn" {
  description = "SNS topic that receives the alarms."
  value       = aws_sns_topic.alerts.arn
}

output "cpu_alarm_name" {
  description = "Name of the high CPU alarm."
  value       = aws_cloudwatch_metric_alarm.cpu.alarm_name
}

output "status_check_alarm_name" {
  description = "Name of the EC2 status check alarm."
  value       = aws_cloudwatch_metric_alarm.status_check.alarm_name
}
