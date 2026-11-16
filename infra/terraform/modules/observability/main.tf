# Observability of one environment on the AWS side: a CloudWatch log group per service, alarms
# for the k3s node and an SNS topic for notifications. The in-cluster stack (Prometheus,
# Grafana, Loki) is installed with Helm in its own stories; this module only declares what
# Terraform can own without duplicating that stack.

resource "aws_sns_topic" "alerts" {
  name = "${var.name_prefix}-alerts"
}

resource "aws_sns_topic_subscription" "alerts_email" {
  for_each = toset(var.alarm_email != "" ? [var.alarm_email] : [])

  topic_arn = aws_sns_topic.alerts.arn
  protocol  = "email"
  endpoint  = each.value
}

resource "aws_cloudwatch_log_group" "service" {
  for_each = toset(var.service_names)

  name              = "/circleguard/${var.environment}/${each.value}"
  retention_in_days = var.log_retention_days
}

resource "aws_cloudwatch_metric_alarm" "cpu" {
  alarm_name          = "${var.name_prefix}-cpu-high"
  alarm_description   = "CPU above ${var.cpu_alarm_threshold}% on the k3s node"
  namespace           = "AWS/EC2"
  metric_name         = "CPUUtilization"
  comparison_operator = "GreaterThanThreshold"
  threshold           = var.cpu_alarm_threshold
  evaluation_periods  = 3
  period              = 300
  statistic           = "Average"

  dimensions = {
    InstanceId = var.instance_id
  }

  alarm_actions = [aws_sns_topic.alerts.arn]
  ok_actions    = [aws_sns_topic.alerts.arn]
}

resource "aws_cloudwatch_metric_alarm" "status_check" {
  alarm_name          = "${var.name_prefix}-status-check-failed"
  alarm_description   = "EC2 status check failed on the k3s node"
  namespace           = "AWS/EC2"
  metric_name         = "StatusCheckFailed"
  comparison_operator = "GreaterThanThreshold"
  threshold           = 0
  evaluation_periods  = 2
  period              = 60
  statistic           = "Maximum"

  dimensions = {
    InstanceId = var.instance_id
  }

  alarm_actions = [aws_sns_topic.alerts.arn]
  ok_actions    = [aws_sns_topic.alerts.arn]
}
