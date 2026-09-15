output "entry_public_ip" {
  description = "Public IP for the EC2 running frontend, API Gateway and Eureka."
  value       = aws_instance.entry.public_ip
}

output "entry_private_ip" {
  description = "Private IP for the EC2 running frontend, API Gateway and Eureka."
  value       = aws_instance.entry.private_ip
}

output "services_public_ip" {
  description = "Public IP for SSH access to the services EC2."
  value       = aws_instance.services.public_ip
}

output "services_private_ip" {
  description = "Private IP for the EC2 running microservices."
  value       = aws_instance.services.private_ip
}

output "entry_security_group_id" {
  description = "Security group ID for the entry EC2."
  value       = aws_security_group.entry.id
}

output "services_security_group_id" {
  description = "Security group ID for the services EC2."
  value       = aws_security_group.services.id
}

output "ssh_entry_command" {
  description = "SSH command for the entry EC2."
  value       = "ssh -i cloud-native-key.pem ubuntu@${aws_instance.entry.public_ip}"
}

output "ssh_services_command" {
  description = "SSH command for the services EC2."
  value       = "ssh -i cloud-native-key.pem ubuntu@${aws_instance.services.public_ip}"
}
