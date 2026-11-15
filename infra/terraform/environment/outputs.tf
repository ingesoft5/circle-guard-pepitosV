output "node_public_ip" {
  description = "Elastic IP of the cluster: API server on 6443 and Ingress host (<ip>.sslip.io)."
  value       = module.k3s.public_ip
}

output "node_private_ip" {
  value = module.k3s.private_ip
}

output "instance_id" {
  value = module.k3s.instance_id
}

output "vpc_id" {
  value = module.network.vpc_id
}
