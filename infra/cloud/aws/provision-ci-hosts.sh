#!/usr/bin/env bash
# Crea (de forma idempotente) el host EC2 y el Security Group del stack CI.
# Requiere AWS CLI configurado y los parámetros CI_SUBNET_ID, CI_KEY_NAME y ADMIN_CIDR.
set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck disable=SC1091
source "${SCRIPT_DIR}/../../bootstrap/lib/common.sh"

readonly PROJECT_TAG="${PROJECT_TAG:-CircleGuard}"
readonly CI_INSTANCE_NAME="${CI_INSTANCE_NAME:-circleguard-ci}"
readonly CI_SG_NAME="${CI_SG_NAME:-circleguard-ci-sg}"
readonly AWS_REGION="${AWS_REGION:-us-east-1}"
readonly CI_INSTANCE_TYPE="${CI_INSTANCE_TYPE:-t3.large}"
readonly CI_ROOT_VOLUME_GB="${CI_ROOT_VOLUME_GB:-30}"
readonly CI_ROOT_VOLUME_TYPE="${CI_ROOT_VOLUME_TYPE:-gp3}"
readonly ADMIN_CIDR="${ADMIN_CIDR:?Define ADMIN_CIDR, por ejemplo 203.0.113.0/24}"
readonly CI_SUBNET_ID="${CI_SUBNET_ID:?Define CI_SUBNET_ID}"
readonly CI_KEY_NAME="${CI_KEY_NAME:?Define CI_KEY_NAME}"

export AWS_DEFAULT_REGION="${AWS_REGION}"

ci_ports=(22 8080 9000 8081 5000)

resolve_root_device_name() {
  local ami="$1"
  aws ec2 describe-images \
    --image-ids "${ami}" \
    --query 'Images[0].RootDeviceName' \
    --output text
}

resolve_ami() {
  if [[ -n "${CI_AMI_ID:-}" ]]; then
    printf '%s\n' "${CI_AMI_ID}"
    return 0
  fi
  aws ssm get-parameter \
    --name /aws/service/canonical/ubuntu/server/24.04/stable/current/amd64/hvm/ebs-gp3/ami-id \
    --query 'Parameter.Value' \
    --output text
}

ensure_security_group() {
  local vpc_id sg_id
  vpc_id="$(aws ec2 describe-subnets --subnet-ids "${CI_SUBNET_ID}" --query 'Subnets[0].VpcId' --output text)"
  [[ -n "${vpc_id}" && "${vpc_id}" != "None" ]] || die "No se pudo resolver la VPC de la subred ${CI_SUBNET_ID}"

  sg_id="$(aws ec2 describe-security-groups \
    --filters "Name=group-name,Values=${CI_SG_NAME}" "Name=vpc-id,Values=${vpc_id}" \
    --query 'SecurityGroups[0].GroupId' --output text 2>/dev/null || true)"

  if [[ -z "${sg_id}" || "${sg_id}" == "None" ]]; then
    info "Creando Security Group ${CI_SG_NAME}"
    sg_id="$(aws ec2 create-security-group \
      --group-name "${CI_SG_NAME}" \
      --description "CircleGuard CI stack" \
      --vpc-id "${vpc_id}" \
      --query 'GroupId' --output text)"
    aws ec2 create-tags --resources "${sg_id}" \
      --tags "Key=Name,Value=${CI_SG_NAME}" "Key=Project,Value=${PROJECT_TAG}" >/dev/null
  else
    info "Security Group ya existe: ${sg_id}"
  fi

  local port output
  for port in "${ci_ports[@]}"; do
    if output="$(aws ec2 authorize-security-group-ingress \
      --group-id "${sg_id}" --protocol tcp --port "${port}" --cidr "${ADMIN_CIDR}" 2>&1)"; then
      info "Regla agregada: ${port}/tcp desde ${ADMIN_CIDR}"
    elif grep -q "InvalidPermission.Duplicate" <<<"${output}"; then
      info "Regla ya existe: ${port}/tcp desde ${ADMIN_CIDR}"
    else
      die "No se pudo agregar la regla ${port}/tcp: ${output}"
    fi
  done

  printf '%s\n' "${sg_id}"
}

ensure_instance() {
  local ami="$1" sg_id="$2"
  local instance_id state root_device

  instance_id="$(aws ec2 describe-instances \
    --filters \
      "Name=tag:Name,Values=${CI_INSTANCE_NAME}" \
      "Name=tag:Project,Values=${PROJECT_TAG}" \
      "Name=instance-state-name,Values=pending,running,stopped,stopping" \
    --query 'Reservations[0].Instances[0].InstanceId' --output text 2>/dev/null || true)"

  if [[ -z "${instance_id}" || "${instance_id}" == "None" ]]; then
    root_device="$(resolve_root_device_name "${ami}" 2>/dev/null || true)"
    [[ -n "${root_device}" && "${root_device}" != "None" ]] \
      || die "No se pudo resolver el dispositivo raíz de la AMI ${ami}"

    info "Creando instancia EC2 ${CI_INSTANCE_NAME} (${CI_INSTANCE_TYPE}, ${CI_ROOT_VOLUME_GB} GB ${CI_ROOT_VOLUME_TYPE})"
    instance_id="$(aws ec2 run-instances \
      --image-id "${ami}" \
      --instance-type "${CI_INSTANCE_TYPE}" \
      --key-name "${CI_KEY_NAME}" \
      --subnet-id "${CI_SUBNET_ID}" \
      --security-group-ids "${sg_id}" \
      --associate-public-ip-address \
      --block-device-mappings "DeviceName=${root_device},Ebs={VolumeSize=${CI_ROOT_VOLUME_GB},VolumeType=${CI_ROOT_VOLUME_TYPE},DeleteOnTermination=true}" \
      --tag-specifications "ResourceType=instance,Tags=[{Key=Name,Value=${CI_INSTANCE_NAME}},{Key=Project,Value=${PROJECT_TAG}}]" \
      --query 'Instances[0].InstanceId' --output text)"
  else
    info "Instancia ya existe: ${instance_id}"
    warn "Los cambios de CI_INSTANCE_TYPE o CI_ROOT_VOLUME_GB solo aplican a instancias nuevas"
    state="$(aws ec2 describe-instances --instance-ids "${instance_id}" \
      --query 'Reservations[0].Instances[0].State.Name' --output text)"
    if [[ "${state}" == "stopped" ]]; then
      info "Iniciando instancia detenida ${instance_id}"
      aws ec2 start-instances --instance-ids "${instance_id}" >/dev/null
    fi
  fi

  printf '%s\n' "${instance_id}"
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
  local ami sg_id instance_id public_ip

  [[ "${CI_ROOT_VOLUME_GB}" =~ ^[0-9]+$ ]] \
    || die "CI_ROOT_VOLUME_GB debe ser un número entero (valor actual: ${CI_ROOT_VOLUME_GB})"

  ami="$(resolve_ami)"
  [[ -n "${ami}" && "${ami}" != "None" ]] || die "No se pudo resolver la AMI de Ubuntu 24.04"

  sg_id="$(ensure_security_group)"
  instance_id="$(ensure_instance "${ami}" "${sg_id}")"
  public_ip="$(public_ip_of "${instance_id}")" \
    || die "La instancia ${instance_id} no tiene IP pública todavía"

  info "Host CI listo: ${instance_id} (${public_ip})"
  info "SSH: ssh -i <clave>.pem ubuntu@${public_ip}"
  info "Recursos: ${CI_INSTANCE_TYPE}, disco ${CI_ROOT_VOLUME_GB} GB ${CI_ROOT_VOLUME_TYPE}"
  info "Puertos expuestos a ${ADMIN_CIDR}: ${ci_ports[*]}"
}

main "$@"
