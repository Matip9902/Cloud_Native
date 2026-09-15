variable "aws_region" {
  description = "AWS region where the project will be deployed."
  type        = string
  default     = "us-east-1"
}

variable "project_name" {
  description = "Prefix used for AWS resource names."
  type        = string
  default     = "cloud-native"
}

variable "key_name" {
  description = "Existing EC2 key pair name."
  type        = string
  default     = "cloud-native-key"
}

variable "operator_ip_cidr" {
  description = "Public IPv4 allowed to connect by SSH, for example 190.47.165.67/32."
  type        = string
}

variable "entry_instance_type" {
  description = "Instance type for frontend, API Gateway and Eureka."
  type        = string
  default     = "t3.medium"
}

variable "services_instance_type" {
  description = "Instance type for microservices."
  type        = string
  default     = "t3.medium"
}

variable "root_volume_size" {
  description = "Root disk size in GiB."
  type        = number
  default     = 20
}
