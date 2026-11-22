# Observability of one environment: CloudWatch log groups, node alarms and an SNS topic,
# plus the in-cluster install of Prometheus, Grafana, Loki and Promtail.

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

locals {
  prometheus_values_file    = "${path.module}/../../../../observability/prometheus/values.yaml"
  prometheus_install_script = "${path.module}/../../../../infra/bootstrap/install-observability.sh"
  loki_values_file          = "${path.module}/../../../../observability/loki/values.yaml"
  promtail_values_file      = "${path.module}/../../../../observability/promtail/values.yaml"
  loki_install_script       = "${path.module}/../../../../infra/bootstrap/install-loki.sh"
  grafana_datasources_file  = "${path.module}/../../../../observability/grafana/datasources.yaml"
}

# The Helm chart runs inside the cluster, so Terraform uploads the script and the values over
# SSH and runs them on the node. This keeps the AWS provider-only setup of the module; the
# release itself is idempotent (`helm upgrade --install`) and is retried when the node or the
# chart version changes.
resource "terraform_data" "prometheus" {
  count = var.install_prometheus ? 1 : 0

  triggers_replace = [
    var.instance_id,
    var.prometheus_ssh_host,
    var.prometheus_namespace,
    var.prometheus_chart_version,
    filemd5(local.prometheus_values_file),
    filemd5(local.prometheus_install_script),
  ]

  connection {
    type        = "ssh"
    host        = var.prometheus_ssh_host
    user        = "ubuntu"
    agent       = var.prometheus_ssh_private_key == ""
    private_key = var.prometheus_ssh_private_key == "" ? null : var.prometheus_ssh_private_key
    timeout     = "10m"
  }

  provisioner "file" {
    source      = local.prometheus_install_script
    destination = "/tmp/install-observability.sh"
  }

  provisioner "file" {
    source      = local.prometheus_values_file
    destination = "/tmp/kube-prometheus-stack-values.yaml"
  }

  provisioner "remote-exec" {
    inline = [
      "sudo env KUBECONFIG=/etc/rancher/k3s/k3s.yaml bash /tmp/install-observability.sh '${var.prometheus_namespace}' '${var.prometheus_chart_version}' '/tmp/kube-prometheus-stack-values.yaml' '${base64encode(var.prometheus_grafana_admin_password)}'",
    ]
  }
}

# Loki and Promtail follow the same path as Prometheus: the chart runs inside the cluster,
# so Terraform uploads the script and the values and runs them on the node.
resource "terraform_data" "loki" {
  count = var.install_prometheus ? 1 : 0

  triggers_replace = [
    var.instance_id,
    var.prometheus_ssh_host,
    var.prometheus_namespace,
    var.environment,
    var.loki_chart_version,
    var.promtail_chart_version,
    filemd5(local.loki_values_file),
    filemd5(local.promtail_values_file),
    filemd5(local.loki_install_script),
    filemd5(local.grafana_datasources_file),
  ]

  connection {
    type        = "ssh"
    host        = var.prometheus_ssh_host
    user        = "ubuntu"
    agent       = var.prometheus_ssh_private_key == ""
    private_key = var.prometheus_ssh_private_key == "" ? null : var.prometheus_ssh_private_key
    timeout     = "10m"
  }

  provisioner "file" {
    source      = local.loki_install_script
    destination = "/tmp/install-loki.sh"
  }

  provisioner "file" {
    source      = local.loki_values_file
    destination = "/tmp/loki-values.yaml"
  }

  provisioner "file" {
    source      = local.promtail_values_file
    destination = "/tmp/promtail-values.yaml"
  }

  provisioner "file" {
    source      = local.grafana_datasources_file
    destination = "/tmp/grafana-datasources.yaml"
  }

  provisioner "remote-exec" {
    inline = [
      "sudo env KUBECONFIG=/etc/rancher/k3s/k3s.yaml bash /tmp/install-loki.sh '${var.prometheus_namespace}' '${var.environment}' '${var.loki_chart_version}' '${var.promtail_chart_version}' /tmp/loki-values.yaml /tmp/promtail-values.yaml /tmp/grafana-datasources.yaml",
    ]
  }
}
