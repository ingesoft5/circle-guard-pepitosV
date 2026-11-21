output "instance_id" {
  value = aws_instance.this.id
}

output "public_ip" {
  description = "Elastic IP: API server (6443) and Ingress host."
  value       = aws_eip.this.public_ip

  depends_on = [aws_eip_association.this]
}

output "private_ip" {
  value = aws_instance.this.private_ip
}
