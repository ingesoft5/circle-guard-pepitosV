# One CircleGuard environment (dev, stage or prod): its network and its single-node k3s cluster.
# The same code serves the three environments; only terraform.tfvars and backend.hcl change.
#   terraform init -reconfigure -backend-config=backend.hcl
#   terraform plan -var-file=terraform.tfvars

# The CI and tools hosts were created by infra/cloud/aws/provision-ci-hosts.sh (Part 1);
# here they are only read, to peer with their VPC and open the registry to the cluster.
data "aws_instance" "ci" {
  filter {
    name   = "tag:Name"
    values = [var.ci_instance_name]
  }
  filter {
    name   = "instance-state-name"
    values = ["running", "stopped"]
  }
}

data "aws_instance" "tools" {
  filter {
    name   = "tag:Name"
    values = [var.tools_instance_name]
  }
  filter {
    name   = "instance-state-name"
    values = ["running", "stopped"]
  }
}

data "aws_subnet" "ci" {
  id = data.aws_instance.ci.subnet_id
}

data "aws_vpc" "ci" {
  id = data.aws_subnet.ci.vpc_id
}

data "aws_security_group" "tools" {
  name   = var.tools_security_group_name
  vpc_id = data.aws_vpc.ci.id
}

locals {
  name = "circleguard-k3s-${var.environment}"
}

module "network" {
  source = "../../modules/network"

  name        = local.name
  environment = var.environment
  vpc_cidr    = var.vpc_cidr
  ci_vpc_id   = data.aws_vpc.ci.id
  ci_vpc_cidr = data.aws_vpc.ci.cidr_block
  # The CI subnet has no explicit association: it uses the main route table of its VPC.
  ci_route_table_id          = data.aws_vpc.ci.main_route_table_id
  ci_private_ip              = data.aws_instance.ci.private_ip
  registry_security_group_id = data.aws_security_group.tools.id
  admin_cidrs                = var.admin_cidrs
}

module "k3s" {
  source = "../../modules/cluster"

  name              = local.name
  subnet_id         = module.network.subnet_id
  security_group_id = module.network.security_group_id
  key_name          = var.key_name
  instance_type     = var.instance_type
  root_volume_gb    = var.root_volume_gb
  k3s_version       = var.k3s_version
  registry          = "${data.aws_instance.tools.private_ip}:5000"

  # Bucket of platform.tf: file-service reads and writes it through the node role.
  storage_bucket_arn = module.storage.bucket_arn
}
