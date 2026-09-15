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

output "entry_env_command" {
  description = "Command to create the .env file in the entry EC2."
  value       = <<-EOT
cat > .env <<'EOF'
CORS_ALLOWED_ORIGINS=http://localhost:4200
JWT_ISSUER_URI=https://login.microsoftonline.com/94b15b07-9bad-4661-8554-aad0e62b18de/v2.0
JWT_AUDIENCES=api://0f18b5b0-285b-4050-a959-245884ab7670,0f18b5b0-285b-4050-a959-245884ab7670
ENTRY_PUBLIC_IP=${aws_instance.entry.public_ip}
SERVICES_PRIVATE_IP=${aws_instance.services.private_ip}
EOF
EOT
}

output "services_env_command" {
  description = "Command to create the .env file in the services EC2."
  value       = <<-EOT
cat > .env <<'EOF'
EUREKA_CLIENT_SERVICE_URL_DEFAULTZONE=http://${aws_instance.entry.private_ip}:8761/eureka/
EUREKA_SERVER_URL=http://${aws_instance.entry.private_ip}:8761/eureka/
SERVICES_EC2_PRIVATE_IP=${aws_instance.services.private_ip}
CORS_ALLOWED_ORIGINS=http://localhost:4200
JWT_ISSUER_URI=https://login.microsoftonline.com/94b15b07-9bad-4661-8554-aad0e62b18de/v2.0
JWT_AUDIENCES=api://0f18b5b0-285b-4050-a959-245884ab7670,0f18b5b0-285b-4050-a959-245884ab7670
EOF
EOT
}
