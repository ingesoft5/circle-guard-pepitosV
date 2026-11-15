#!/usr/bin/env bash
# Brings the existing CI host to the size declared in provision-ci-hosts.sh, idempotently.
#   infra/cloud/aws/resize-ci-host.sh
#   CI_INSTANCE_NAME=circleguard-tools CI_ROOT_VOLUME_GB=60 infra/cloud/aws/resize-ci-host.sh
# On the AWS free plan the instance type cannot change (FreeTierRestrictionError) and nothing
# above m7i-flex.large can be launched; the volume can still grow.
# - Instance type: only when it differs. Needs a stop/start; the Elastic IP is kept.
# - Root volume: only grows (EBS cannot shrink). The filesystem is grown over SSH afterwards.
# Run it with no build running in Jenkins: the stop interrupts it.
set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck disable=SC1091
source "${SCRIPT_DIR}/../../bootstrap/lib/common.sh"

readonly CI_INSTANCE_NAME="${CI_INSTANCE_NAME:-circleguard-ci}"
readonly CI_INSTANCE_TYPE="${CI_INSTANCE_TYPE:-m7i-flex.large}"
readonly CI_ROOT_VOLUME_GB="${CI_ROOT_VOLUME_GB:-50}"
readonly SSH_KEY="${SSH_KEY:-${HOME}/.ssh/circleguard-admin-key.pem}"
readonly SSH_USER="${SSH_USER:-ubuntu}"
export AWS_DEFAULT_REGION="${AWS_REGION:-us-east-1}"
export AWS_PAGER=""

instance_field() {
  aws ec2 describe-instances --instance-ids "$1" \
    --query "Reservations[0].Instances[0].$2" --output text
}

ensure_instance_type() {
  local id="$1" current state
  current="$(instance_field "${id}" InstanceType)"
  if [[ "${current}" == "${CI_INSTANCE_TYPE}" ]]; then
    info "Instance type already ${CI_INSTANCE_TYPE}"
    return 0
  fi
  info "Changing instance type ${current} -> ${CI_INSTANCE_TYPE} (stop/start)"
  state="$(instance_field "${id}" State.Name)"
  if [[ "${state}" != "stopped" ]]; then
    aws ec2 stop-instances --instance-ids "${id}" >/dev/null
    aws ec2 wait instance-stopped --instance-ids "${id}"
  fi
  aws ec2 modify-instance-attribute --instance-id "${id}" \
    --instance-type "{\"Value\":\"${CI_INSTANCE_TYPE}\"}"
}

# Prints "grow" when the EBS volume was enlarged and the filesystem still has to follow.
ensure_volume_size() {
  local id="$1" volume size
  volume="$(instance_field "${id}" 'BlockDeviceMappings[0].Ebs.VolumeId')"
  size="$(aws ec2 describe-volumes --volume-ids "${volume}" --query 'Volumes[0].Size' --output text)"
  if (( size >= CI_ROOT_VOLUME_GB )); then
    info "Root volume already ${size} GB (>= ${CI_ROOT_VOLUME_GB})"
    return 0
  fi
  info "Growing root volume ${volume}: ${size} -> ${CI_ROOT_VOLUME_GB} GB"
  aws ec2 modify-volume --volume-id "${volume}" --size "${CI_ROOT_VOLUME_GB}" >/dev/null
  echo grow
}

ensure_running() {
  local id="$1"
  if [[ "$(instance_field "${id}" State.Name)" != "running" ]]; then
    aws ec2 start-instances --instance-ids "${id}" >/dev/null
  fi
  aws ec2 wait instance-status-ok --instance-ids "${id}"
}

# growpart/resize2fs are no-ops when the partition already fills the disk, so this always runs.
grow_filesystem() {
  local ip="$1"
  info "Growing the root filesystem on the CI host"
  ssh -o ConnectTimeout=20 -o StrictHostKeyChecking=accept-new -i "${SSH_KEY}" "${SSH_USER}@${ip}" '
    set -e
    dev="$(findmnt -no SOURCE /)"
    disk="/dev/$(lsblk -no PKNAME "$dev")"
    part="${dev##*[!0-9]}"
    sudo growpart "$disk" "$part" || true
    sudo resize2fs "$dev" >/dev/null
    df -h / | tail -1
  '
}

main() {
  require_cmd aws ssh
  [[ "${CI_ROOT_VOLUME_GB}" =~ ^[0-9]+$ ]] || die "CI_ROOT_VOLUME_GB must be an integer"
  local id ip
  id="$(aws ec2 describe-instances \
    --filters "Name=tag:Name,Values=${CI_INSTANCE_NAME}" "Name=tag:Project,Values=CircleGuard" \
      "Name=instance-state-name,Values=pending,running,stopping,stopped" \
    --query 'Reservations[0].Instances[0].InstanceId' --output text)"
  [[ -n "${id}" && "${id}" != "None" ]] || die "No CI instance named ${CI_INSTANCE_NAME}"

  ensure_instance_type "${id}"
  ensure_volume_size "${id}" >/dev/null
  ensure_running "${id}"
  ip="$(instance_field "${id}" PublicIpAddress)"
  grow_filesystem "${ip}"
  info "CI host: $(instance_field "${id}" InstanceType), root volume ${CI_ROOT_VOLUME_GB} GB"
}

main "$@"
