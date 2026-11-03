#!/usr/bin/env bash
# Creates one EC2 host and one Security Group per environment (dev, stage, prod) idempotently.
# Requires a configured AWS CLI and ADMIN_CIDR, ENV_SUBNET_ID and ENV_KEY_NAME.
# Optional: JENKINS_CIDR opens SSH to the Jenkins host, and CI_SG_NAME names the CI Security Group
# that gets a registry rule for each environment (see infra/security/security-groups.md).
set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck disable=SC1091
source "${SCRIPT_DIR}/../../bootstrap/lib/common.sh"

readonly PROJECT_TAG="${PROJECT_TAG:-CircleGuard}"
readonly AWS_REGION="${AWS_REGION:-us-east-1}"
readonly ENV_INSTANCE_TYPE="${ENV_INSTANCE_TYPE:-m7i-flex.large}"
readonly ENV_ROOT_VOLUME_GB="${ENV_ROOT_VOLUME_GB:-30}"
readonly ENV_ROOT_VOLUME_TYPE="${ENV_ROOT_VOLUME_TYPE:-gp3}"
readonly ADMIN_CIDR="${ADMIN_CIDR:?Set ADMIN_CIDR, for example 203.0.113.10/32}"
readonly ENV_SUBNET_ID="${ENV_SUBNET_ID:?Set ENV_SUBNET_ID}"
readonly ENV_KEY_NAME="${ENV_KEY_NAME:?Set ENV_KEY_NAME}"
readonly JENKINS_CIDR="${JENKINS_CIDR:-}"
readonly CI_SG_NAME="${CI_SG_NAME:-circleguard-ci-sg}"
readonly REGISTRY_PORT=5000
readonly ENVIRONMENTS=(dev stage prod)

export AWS_DEFAULT_REGION="${AWS_REGION}"

resolve_ami() {
  if [[ -n "${ENV_AMI_ID:-}" ]]; then
    printf '%s\n' "${ENV_AMI_ID}"
    return 0
  fi
  aws ssm get-parameter \
    --name /aws/service/canonical/ubuntu/server/24.04/stable/current/amd64/hvm/ebs-gp3/ami-id \
    --query 'Parameter.Value' \
    --output text
}

ensure_ingress_rule() {
  local sg_id="$1" from_port="$2" to_port="$3" cidr="$4" output
  if output="$(aws ec2 authorize-security-group-ingress \
    --group-id "${sg_id}" \
    --ip-permissions "IpProtocol=tcp,FromPort=${from_port},ToPort=${to_port},IpRanges=[{CidrIp=${cidr}}]" 2>&1)"; then
    info "Rule added: ${from_port}-${to_port}/tcp from ${cidr}"
  elif grep -q "InvalidPermission.Duplicate" <<<"${output}"; then
    info "Rule already exists: ${from_port}-${to_port}/tcp from ${cidr}"
  else
    die "Could not add rule ${from_port}-${to_port}/tcp: ${output}"
  fi
}

# Every rule uses a CIDR source, so no Security Group ever references another environment's.
ensure_security_group() {
  local env_name="$1" vpc_id="$2"
  local sg_name="circleguard-${env_name}-sg" sg_id

  sg_id="$(aws ec2 describe-security-groups \
    --filters "Name=group-name,Values=${sg_name}" "Name=vpc-id,Values=${vpc_id}" \
    --query 'SecurityGroups[0].GroupId' --output text 2>/dev/null || true)"

  if [[ -z "${sg_id}" || "${sg_id}" == "None" ]]; then
    info "Creating Security Group ${sg_name}"
    sg_id="$(aws ec2 create-security-group \
      --group-name "${sg_name}" \
      --description "CircleGuard ${env_name} environment" \
      --vpc-id "${vpc_id}" \
      --query 'GroupId' --output text)"
    aws ec2 create-tags --resources "${sg_id}" \
      --tags "Key=Name,Value=${sg_name}" "Key=Project,Value=${PROJECT_TAG}" \
      "Key=Environment,Value=${env_name}" >/dev/null
  else
    info "Security Group already exists: ${sg_id}"
  fi

  ensure_ingress_rule "${sg_id}" 22 22 "${ADMIN_CIDR}"
  ensure_ingress_rule "${sg_id}" 80 80 "0.0.0.0/0"
  ensure_ingress_rule "${sg_id}" 443 443 "0.0.0.0/0"
  ensure_ingress_rule "${sg_id}" 8180 8180 "${ADMIN_CIDR}"
  ensure_ingress_rule "${sg_id}" 8082 8088 "${ADMIN_CIDR}"
  if [[ -n "${JENKINS_CIDR}" ]]; then
    ensure_ingress_rule "${sg_id}" 22 22 "${JENKINS_CIDR}"
  fi

  printf '%s\n' "${sg_id}"
}

ensure_instance() {
  local env_name="$1" ami="$2" sg_id="$3"
  local instance_name="circleguard-${env_name}" instance_id state root_device

  instance_id="$(aws ec2 describe-instances \
    --filters \
      "Name=tag:Name,Values=${instance_name}" \
      "Name=tag:Project,Values=${PROJECT_TAG}" \
      "Name=instance-state-name,Values=pending,running,stopped,stopping" \
    --query 'Reservations[0].Instances[0].InstanceId' --output text 2>/dev/null || true)"

  if [[ -z "${instance_id}" || "${instance_id}" == "None" ]]; then
    root_device="$(aws ec2 describe-images --image-ids "${ami}" \
      --query 'Images[0].RootDeviceName' --output text)"
    [[ -n "${root_device}" && "${root_device}" != "None" ]] \
      || die "Could not resolve the root device of AMI ${ami}"

    info "Creating instance ${instance_name} (${ENV_INSTANCE_TYPE}, ${ENV_ROOT_VOLUME_GB} GB ${ENV_ROOT_VOLUME_TYPE})"
    instance_id="$(aws ec2 run-instances \
      --image-id "${ami}" \
      --instance-type "${ENV_INSTANCE_TYPE}" \
      --key-name "${ENV_KEY_NAME}" \
      --subnet-id "${ENV_SUBNET_ID}" \
      --security-group-ids "${sg_id}" \
      --associate-public-ip-address \
      --block-device-mappings "DeviceName=${root_device},Ebs={VolumeSize=${ENV_ROOT_VOLUME_GB},VolumeType=${ENV_ROOT_VOLUME_TYPE},DeleteOnTermination=true}" \
      --tag-specifications "ResourceType=instance,Tags=[{Key=Name,Value=${instance_name}},{Key=Project,Value=${PROJECT_TAG}},{Key=Environment,Value=${env_name}}]" \
      --query 'Instances[0].InstanceId' --output text)"
  else
    info "Instance already exists: ${instance_id}"
    state="$(aws ec2 describe-instances --instance-ids "${instance_id}" \
      --query 'Reservations[0].Instances[0].State.Name' --output text)"
    if [[ "${state}" == "stopped" ]]; then
      info "Starting stopped instance ${instance_id}"
      aws ec2 start-instances --instance-ids "${instance_id}" >/dev/null
    fi
  fi

  printf '%s\n' "${instance_id}"
}

allow_registry_access() {
  local env_name="$1" vpc_id="$2" instance_id="$3" ci_sg_id private_ip
  ci_sg_id="$(aws ec2 describe-security-groups \
    --filters "Name=group-name,Values=${CI_SG_NAME}" "Name=vpc-id,Values=${vpc_id}" \
    --query 'SecurityGroups[0].GroupId' --output text)"
  if [[ -z "${ci_sg_id}" || "${ci_sg_id}" == "None" ]]; then
    warn "Security Group ${CI_SG_NAME} not found: ${env_name} cannot reach the registry yet"
    return 0
  fi
  private_ip="$(aws ec2 describe-instances --instance-ids "${instance_id}" \
    --query 'Reservations[0].Instances[0].PrivateIpAddress' --output text)"
  ensure_ingress_rule "${ci_sg_id}" "${REGISTRY_PORT}" "${REGISTRY_PORT}" "${private_ip}/32"
}

public_ip_of() {
  local instance_id="$1" ip attempt
  for attempt in 1 2 3 4 5 6 7 8 9 10; do
    ip="$(aws ec2 describe-instances --instance-ids "${instance_id}" \
      --query 'Reservations[0].Instances[0].PublicIpAddress' --output text)"
    if [[ -n "${ip}" && "${ip}" != "None" ]]; then
      printf '%s\n' "${ip}"
      return 0
    fi
    sleep 6
  done
  return 1
}

main() {
  require_cmd aws
  [[ "${ENV_ROOT_VOLUME_GB}" =~ ^[0-9]+$ ]] \
    || die "ENV_ROOT_VOLUME_GB must be an integer (current value: ${ENV_ROOT_VOLUME_GB})"

  local ami vpc_id env_name sg_id instance_id public_ip
  ami="$(resolve_ami)"
  [[ -n "${ami}" && "${ami}" != "None" ]] || die "Could not resolve the Ubuntu 24.04 AMI"

  vpc_id="$(aws ec2 describe-subnets --subnet-ids "${ENV_SUBNET_ID}" \
    --query 'Subnets[0].VpcId' --output text)"
  [[ -n "${vpc_id}" && "${vpc_id}" != "None" ]] || die "Could not resolve the VPC of subnet ${ENV_SUBNET_ID}"

  for env_name in "${ENVIRONMENTS[@]}"; do
    sg_id="$(ensure_security_group "${env_name}" "${vpc_id}")"
    [[ "${sg_id}" == sg-* ]] || die "Could not prepare the Security Group of ${env_name}"
    instance_id="$(ensure_instance "${env_name}" "${ami}" "${sg_id}")"
    [[ "${instance_id}" == i-* ]] || die "Could not create the instance of ${env_name}"
    allow_registry_access "${env_name}" "${vpc_id}" "${instance_id}"
    public_ip="$(public_ip_of "${instance_id}")" \
      || die "Instance ${instance_id} does not have a public IP yet"
    info "Host ${env_name} ready: ${instance_id} (${public_ip}), Security Group ${sg_id}"
  done
}

main "$@"
