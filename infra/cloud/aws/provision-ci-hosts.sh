#!/usr/bin/env bash
# Crea (de forma idempotente) los dos hosts del stack CI y sus Security Groups:
#   circleguard-ci     Jenkins y los builds          (circleguard-ci-sg)
#   circleguard-tools  SonarQube y Nexus (registro)  (circleguard-tools-sg)
# En el plan gratuito de AWS ninguna instancia puede pasar de m7i-flex.large (2 vCPU, 8 GB):
# separar las herramientas en su propio host duplica la memoria y el disco disponibles.
# Requiere AWS CLI configurado y los parámetros CI_SUBNET_ID, CI_KEY_NAME y ADMIN_CIDR.
set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck disable=SC1091
source "${SCRIPT_DIR}/../../bootstrap/lib/common.sh"

readonly PROJECT_TAG="${PROJECT_TAG:-CircleGuard}"
readonly CI_INSTANCE_NAME="${CI_INSTANCE_NAME:-circleguard-ci}"
readonly CI_SG_NAME="${CI_SG_NAME:-circleguard-ci-sg}"
readonly TOOLS_INSTANCE_NAME="${TOOLS_INSTANCE_NAME:-circleguard-tools}"
readonly TOOLS_SG_NAME="${TOOLS_SG_NAME:-circleguard-tools-sg}"
readonly AWS_REGION="${AWS_REGION:-us-east-1}"
readonly CI_INSTANCE_TYPE="${CI_INSTANCE_TYPE:-m7i-flex.large}"
readonly CI_ROOT_VOLUME_GB="${CI_ROOT_VOLUME_GB:-50}"
readonly CI_ROOT_VOLUME_TYPE="${CI_ROOT_VOLUME_TYPE:-gp3}"
readonly ADMIN_CIDR="${ADMIN_CIDR:?Define ADMIN_CIDR, por ejemplo 203.0.113.0/24}"
readonly CI_SUBNET_ID="${CI_SUBNET_ID:?Define CI_SUBNET_ID}"
readonly CI_KEY_NAME="${CI_KEY_NAME:?Define CI_KEY_NAME}"

export AWS_DEFAULT_REGION="${AWS_REGION}"

# Puertos abiertos a ADMIN_CIDR en cada host.
ci_ports=(22 8080)
tools_ports=(22 9000 8081 5000)

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

# ensure_rule <sg> <port> --cidr <cidr> | --source-group <sg>
ensure_rule() {
  local sg_id="$1" port="$2" output
  shift 2
  if output="$(aws ec2 authorize-security-group-ingress \
    --group-id "${sg_id}" --protocol tcp --port "${port}" "$@" 2>&1)"; then
    info "Regla agregada: ${port}/tcp desde $2"
  elif grep -q "InvalidPermission.Duplicate" <<<"${output}"; then
    info "Regla ya existe: ${port}/tcp desde $2"
  else
    die "No se pudo agregar la regla ${port}/tcp: ${output}"
  fi
}

# ensure_security_group <nombre> <descripción> <puertos para ADMIN_CIDR...>; imprime el id.
ensure_security_group() {
  local sg_name="$1" description="$2" vpc_id sg_id port
  shift 2
  vpc_id="$(aws ec2 describe-subnets --subnet-ids "${CI_SUBNET_ID}" --query 'Subnets[0].VpcId' --output text)"
  [[ -n "${vpc_id}" && "${vpc_id}" != "None" ]] || die "No se pudo resolver la VPC de la subred ${CI_SUBNET_ID}"

  sg_id="$(aws ec2 describe-security-groups \
    --filters "Name=group-name,Values=${sg_name}" "Name=vpc-id,Values=${vpc_id}" \
    --query 'SecurityGroups[0].GroupId' --output text 2>/dev/null || true)"

  if [[ -z "${sg_id}" || "${sg_id}" == "None" ]]; then
    info "Creando Security Group ${sg_name}"
    sg_id="$(aws ec2 create-security-group \
      --group-name "${sg_name}" \
      --description "${description}" \
      --vpc-id "${vpc_id}" \
      --query 'GroupId' --output text)"
    aws ec2 create-tags --resources "${sg_id}" \
      --tags "Key=Name,Value=${sg_name}" "Key=Project,Value=${PROJECT_TAG}" >/dev/null
  else
    info "Security Group ya existe: ${sg_name} (${sg_id})"
  fi

  for port in "$@"; do
    ensure_rule "${sg_id}" "${port}" --cidr "${ADMIN_CIDR}" >&2
  done

  printf '%s\n' "${sg_id}"
}

# ensure_instance <nombre> <ami> <sg>; imprime el id.
ensure_instance() {
  local name="$1" ami="$2" sg_id="$3"
  local instance_id state root_device

  instance_id="$(aws ec2 describe-instances \
    --filters \
      "Name=tag:Name,Values=${name}" \
      "Name=tag:Project,Values=${PROJECT_TAG}" \
      "Name=instance-state-name,Values=pending,running,stopped,stopping" \
    --query 'Reservations[0].Instances[0].InstanceId' --output text 2>/dev/null || true)"

  if [[ -z "${instance_id}" || "${instance_id}" == "None" ]]; then
    root_device="$(resolve_root_device_name "${ami}" 2>/dev/null || true)"
    [[ -n "${root_device}" && "${root_device}" != "None" ]] \
      || die "No se pudo resolver el dispositivo raíz de la AMI ${ami}"

    info "Creando instancia EC2 ${name} (${CI_INSTANCE_TYPE}, ${CI_ROOT_VOLUME_GB} GB ${CI_ROOT_VOLUME_TYPE})"
    instance_id="$(aws ec2 run-instances \
      --image-id "${ami}" \
      --instance-type "${CI_INSTANCE_TYPE}" \
      --key-name "${CI_KEY_NAME}" \
      --subnet-id "${CI_SUBNET_ID}" \
      --security-group-ids "${sg_id}" \
      --associate-public-ip-address \
      --block-device-mappings "DeviceName=${root_device},Ebs={VolumeSize=${CI_ROOT_VOLUME_GB},VolumeType=${CI_ROOT_VOLUME_TYPE},DeleteOnTermination=true}" \
      --tag-specifications "ResourceType=instance,Tags=[{Key=Name,Value=${name}},{Key=Project,Value=${PROJECT_TAG}},{Key=Role,Value=${name#circleguard-}}]" \
      --query 'Instances[0].InstanceId' --output text)"
  else
    info "Instancia ya existe: ${name} (${instance_id})"
    warn "Los cambios de CI_INSTANCE_TYPE o CI_ROOT_VOLUME_GB solo aplican a instancias nuevas; para la existente use resize-ci-host.sh"
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
  local ami ci_sg tools_sg ci_id tools_id ci_ip tools_ip tools_private

  [[ "${CI_ROOT_VOLUME_GB}" =~ ^[0-9]+$ ]] \
    || die "CI_ROOT_VOLUME_GB debe ser un número entero (valor actual: ${CI_ROOT_VOLUME_GB})"

  ami="$(resolve_ami)"
  [[ -n "${ami}" && "${ami}" != "None" ]] || die "No se pudo resolver la AMI de Ubuntu 24.04"

  ci_sg="$(ensure_security_group "${CI_SG_NAME}" "CircleGuard CI: Jenkins" "${ci_ports[@]}")"
  [[ "${ci_sg}" == sg-* ]] || die "No se pudo preparar el Security Group ${CI_SG_NAME}"
  tools_sg="$(ensure_security_group "${TOOLS_SG_NAME}" "CircleGuard tools: SonarQube and Nexus" "${tools_ports[@]}")"
  [[ "${tools_sg}" == sg-* ]] || die "No se pudo preparar el Security Group ${TOOLS_SG_NAME}"

  # Tráfico entre los dos hosts, por Security Group (no por IP): Jenkins analiza en SonarQube
  # y publica en Nexus; SonarQube avisa a Jenkins el resultado del quality gate.
  local port
  for port in 9000 8081 5000; do
    ensure_rule "${tools_sg}" "${port}" --source-group "${ci_sg}"
  done
  ensure_rule "${ci_sg}" 8080 --source-group "${tools_sg}"

  ci_id="$(ensure_instance "${CI_INSTANCE_NAME}" "${ami}" "${ci_sg}")"
  [[ "${ci_id}" == i-* ]] || die "No se pudo crear la instancia ${CI_INSTANCE_NAME}"
  tools_id="$(ensure_instance "${TOOLS_INSTANCE_NAME}" "${ami}" "${tools_sg}")"
  [[ "${tools_id}" == i-* ]] || die "No se pudo crear la instancia ${TOOLS_INSTANCE_NAME}"

  ci_ip="$(public_ip_of "${ci_id}")" || die "La instancia ${ci_id} no tiene IP pública todavía"
  tools_ip="$(public_ip_of "${tools_id}")" || die "La instancia ${tools_id} no tiene IP pública todavía"
  tools_private="$(aws ec2 describe-instances --instance-ids "${tools_id}" \
    --query 'Reservations[0].Instances[0].PrivateIpAddress' --output text)"

  info "Host CI listo:    ${ci_id} (${ci_ip}), puertos para ${ADMIN_CIDR}: ${ci_ports[*]}"
  info "Host tools listo: ${tools_id} (${tools_ip}), puertos para ${ADMIN_CIDR}: ${tools_ports[*]}"
  info "Recursos de cada uno: ${CI_INSTANCE_TYPE}, disco ${CI_ROOT_VOLUME_GB} GB ${CI_ROOT_VOLUME_TYPE}"
  info "En el .env de CI: NEXUS_REGISTRY=${tools_private}:5000, NEXUS_REGISTRY_API=http://${tools_private}:5000, SONAR_HOST_URL=http://${tools_private}:9000"
}

main "$@"
