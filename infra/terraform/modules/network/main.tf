# Network of one environment: its own VPC with a public subnet, peered with the VPC of the CI
# hosts so Jenkins reaches the cluster API and the nodes pull from the Nexus registry on the
# tools host by private IP. Rules are standalone resources (not inline) so the per-person rules
# that hosts.sh allow adds are not removed by the next apply.

data "aws_availability_zones" "available" {
  state = "available"
}

resource "aws_vpc" "this" {
  cidr_block           = var.vpc_cidr
  enable_dns_support   = true
  enable_dns_hostnames = true
  tags                 = { Name = "${var.name}-vpc" }
}

resource "aws_internet_gateway" "this" {
  vpc_id = aws_vpc.this.id
  tags   = { Name = "${var.name}-igw" }
}

resource "aws_subnet" "public" {
  vpc_id                  = aws_vpc.this.id
  cidr_block              = cidrsubnet(var.vpc_cidr, 8, 1)
  availability_zone       = coalesce(var.availability_zone, data.aws_availability_zones.available.names[0])
  map_public_ip_on_launch = true
  tags                    = { Name = "${var.name}-public" }
}

resource "aws_vpc_peering_connection" "ci" {
  vpc_id      = aws_vpc.this.id
  peer_vpc_id = var.ci_vpc_id
  auto_accept = true
  tags        = { Name = "${var.name}-to-ci" }
}

resource "aws_route_table" "public" {
  vpc_id = aws_vpc.this.id
  tags   = { Name = "${var.name}-public" }

  route {
    cidr_block = "0.0.0.0/0"
    gateway_id = aws_internet_gateway.this.id
  }

  route {
    cidr_block                = var.ci_vpc_cidr
    vpc_peering_connection_id = aws_vpc_peering_connection.ci.id
  }
}

resource "aws_route_table_association" "public" {
  subnet_id      = aws_subnet.public.id
  route_table_id = aws_route_table.public.id
}

# Return path: from the CI VPC to this environment.
resource "aws_route" "ci_to_env" {
  route_table_id            = var.ci_route_table_id
  destination_cidr_block    = var.vpc_cidr
  vpc_peering_connection_id = aws_vpc_peering_connection.ci.id
}

resource "aws_security_group" "node" {
  name        = "${var.name}-sg"
  description = "CircleGuard ${var.environment}: k3s node"
  vpc_id      = aws_vpc.this.id
  tags        = { Name = "${var.name}-sg" }
}

resource "aws_vpc_security_group_egress_rule" "all" {
  security_group_id = aws_security_group.node.id
  ip_protocol       = "-1"
  cidr_ipv4         = "0.0.0.0/0"
  description       = "All outbound traffic"
}

# Public entry: the Ingress controller (HTTP redirects to HTTPS).
resource "aws_vpc_security_group_ingress_rule" "public" {
  for_each          = toset(["80", "443"])
  security_group_id = aws_security_group.node.id
  ip_protocol       = "tcp"
  from_port         = tonumber(each.value)
  to_port           = tonumber(each.value)
  cidr_ipv4         = "0.0.0.0/0"
  description       = "Public ingress"
}

# Jenkins: kubectl/helm against the API server and SSH, from the CI host only.
resource "aws_vpc_security_group_ingress_rule" "ci" {
  for_each          = toset(["22", "6443"])
  security_group_id = aws_security_group.node.id
  ip_protocol       = "tcp"
  from_port         = tonumber(each.value)
  to_port           = tonumber(each.value)
  cidr_ipv4         = "${var.ci_private_ip}/32"
  description       = "jenkins"
}

resource "aws_vpc_security_group_ingress_rule" "admin" {
  for_each = { for pair in setproduct(var.admin_cidrs, ["22", "6443"]) : "${pair[0]}-${pair[1]}" => pair }

  security_group_id = aws_security_group.node.id
  ip_protocol       = "tcp"
  from_port         = tonumber(each.value[1])
  to_port           = tonumber(each.value[1])
  cidr_ipv4         = each.value[0]
  description       = "terraform-admin"
}

# The node pulls images from the registry on the tools host (default VPC) through the peering.
resource "aws_vpc_security_group_ingress_rule" "registry" {
  security_group_id = var.registry_security_group_id
  ip_protocol       = "tcp"
  from_port         = var.registry_port
  to_port           = var.registry_port
  cidr_ipv4         = var.vpc_cidr
  description       = "k3s-${var.environment}"
}
