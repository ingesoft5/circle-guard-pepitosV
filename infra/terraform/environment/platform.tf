# Reusable platform modules of one environment: image registry, object storage and AWS-side
# observability. They share the same code for dev, stage and prod; only the tfvars change.

module "registry" {
  source = "../modules/registry"

  environment      = var.environment
  name_prefix      = local.name
  repository_names = var.service_names
  keep_last_images = var.registry_keep_last_images
}

module "storage" {
  source = "../modules/storage"

  environment                        = var.environment
  name_prefix                        = local.name
  force_destroy                      = var.storage_force_destroy
  versioning_enabled                 = var.storage_versioning_enabled
  noncurrent_version_expiration_days = var.storage_noncurrent_version_expiration_days
}

module "observability" {
  source = "../modules/observability"

  environment         = var.environment
  name_prefix         = local.name
  service_names       = var.service_names
  log_retention_days  = var.log_retention_days
  alarm_email         = var.alarm_email
  cpu_alarm_threshold = var.cpu_alarm_threshold
  instance_id         = module.k3s.instance_id
}
