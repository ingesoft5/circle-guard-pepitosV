# One single-node k3s cluster (server + workloads) for an environment, with an Elastic IP so the
# API server certificate and the Ingress host (TLS) keep the same address across stop/start.
# k3s installs from user_data on first boot; changing the version or the registry recreates the
# node (user_data_replace_on_change), so the cluster is rebuilt from code, not patched by hand.

data "aws_ssm_parameter" "ubuntu" {
  name = "/aws/service/canonical/ubuntu/server/24.04/stable/current/amd64/hvm/ebs-gp3/ami-id"
}

resource "aws_eip" "this" {
  domain = "vpc"
  tags   = { Name = "${var.name}-eip" }
}

# Identity of the node. Pods reach it through IMDSv2 (hop limit 2, below), so file-service reads
# and writes the environment bucket without keys. The policy covers only that bucket.
data "aws_iam_policy_document" "assume" {
  statement {
    actions = ["sts:AssumeRole"]
    principals {
      type        = "Service"
      identifiers = ["ec2.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "node" {
  name               = "${var.name}-node"
  description        = "k3s node of ${var.name}: access to the environment bucket"
  assume_role_policy = data.aws_iam_policy_document.assume.json
}

data "aws_iam_policy_document" "storage" {
  statement {
    sid       = "ListBucket"
    actions   = ["s3:ListBucket"]
    resources = [var.storage_bucket_arn]
  }
  statement {
    sid       = "ReadWriteObjects"
    actions   = ["s3:GetObject", "s3:PutObject", "s3:DeleteObject"]
    resources = ["${var.storage_bucket_arn}/*"]
  }
}

resource "aws_iam_role_policy" "storage" {
  name   = "${var.name}-files"
  role   = aws_iam_role.node.id
  policy = data.aws_iam_policy_document.storage.json
}

resource "aws_iam_instance_profile" "node" {
  name = "${var.name}-node"
  role = aws_iam_role.node.name
}

resource "aws_instance" "this" {
  ami = var.ami_id != null ? var.ami_id : data.aws_ssm_parameter.ubuntu.value
  # The first line of user-data.sh.tftpl keeps the historical "module k3s-node" name on
  # purpose: any cosmetic edit changes the user_data hash and would replace a live cluster
  # (and its local-path volumes). Rename it only together with a planned node replacement.
  instance_type               = var.instance_type
  subnet_id                   = var.subnet_id
  vpc_security_group_ids      = [var.security_group_id]
  key_name                    = var.key_name
  iam_instance_profile        = aws_iam_instance_profile.node.name
  user_data_replace_on_change = true

  user_data = templatefile("${path.module}/user-data.sh.tftpl", {
    k3s_version = var.k3s_version
    node_name   = var.name
    public_ip   = aws_eip.this.public_ip
    registry    = var.registry
  })

  root_block_device {
    volume_size           = var.root_volume_gb
    volume_type           = "gp3"
    delete_on_termination = true
    encrypted             = true
  }

  # Hop limit 2: the extra hop of the pod network, so pods get the node role from IMDSv2.
  # Both settings change in place; the node is not replaced.
  metadata_options {
    http_tokens                 = "required"
    http_put_response_hop_limit = 2
  }

  tags = { Name = var.name, Role = "k3s" }

  lifecycle {
    # A newer Ubuntu image in SSM must not recreate a running cluster.
    ignore_changes = [ami]
  }
}

resource "aws_eip_association" "this" {
  instance_id   = aws_instance.this.id
  allocation_id = aws_eip.this.id
}
