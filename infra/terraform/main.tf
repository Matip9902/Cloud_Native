terraform {
  required_version = ">= 1.6.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }
}

provider "aws" {
  region = var.aws_region
}

data "aws_vpc" "default" {
  default = true
}

data "aws_subnets" "default" {
  filter {
    name   = "vpc-id"
    values = [data.aws_vpc.default.id]
  }
}

data "aws_ami" "ubuntu" {
  most_recent = true
  owners      = ["099720109477"]

  filter {
    name   = "name"
    values = ["ubuntu/images/hvm-ssd-gp3/ubuntu-noble-24.04-amd64-server-*"]
  }

  filter {
    name   = "virtualization-type"
    values = ["hvm"]
  }
}

resource "aws_security_group" "entry" {
  name        = "${var.project_name}-entry-sg"
  description = "Frontend, API Gateway and Eureka"
  vpc_id      = data.aws_vpc.default.id

  ingress {
    description = "SSH from operator IP"
    from_port   = 22
    to_port     = 22
    protocol    = "tcp"
    cidr_blocks = [var.operator_ip_cidr]
  }

  ingress {
    description = "Frontend Angular"
    from_port   = 4200
    to_port     = 4200
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  ingress {
    description = "API Gateway"
    from_port   = 8085
    to_port     = 8085
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  ingress {
    description     = "Eureka from services EC2"
    from_port       = 8761
    to_port         = 8761
    protocol        = "tcp"
    security_groups = [aws_security_group.services.id]
  }

  ingress {
    description = "Eureka for evidence"
    from_port   = 8761
    to_port     = 8761
    protocol    = "tcp"
    cidr_blocks = [var.operator_ip_cidr]
  }

  egress {
    description = "All outbound"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Name    = "${var.project_name}-entry-sg"
    Project = var.project_name
  }
}

resource "aws_security_group" "services" {
  name        = "${var.project_name}-services-sg"
  description = "Internal microservices"
  vpc_id      = data.aws_vpc.default.id

  ingress {
    description = "SSH from operator IP"
    from_port   = 22
    to_port     = 22
    protocol    = "tcp"
    cidr_blocks = [var.operator_ip_cidr]
  }

  ingress {
    description     = "Inventory from entry EC2"
    from_port       = 8080
    to_port         = 8080
    protocol        = "tcp"
    security_groups = [aws_security_group.entry.id]
  }

  ingress {
    description     = "Customers from entry EC2"
    from_port       = 8081
    to_port         = 8081
    protocol        = "tcp"
    security_groups = [aws_security_group.entry.id]
  }

  ingress {
    description     = "Suppliers from entry EC2"
    from_port       = 8082
    to_port         = 8082
    protocol        = "tcp"
    security_groups = [aws_security_group.entry.id]
  }

  ingress {
    description     = "Notifications from entry EC2"
    from_port       = 8083
    to_port         = 8083
    protocol        = "tcp"
    security_groups = [aws_security_group.entry.id]
  }

  egress {
    description = "All outbound"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Name    = "${var.project_name}-services-sg"
    Project = var.project_name
  }
}

resource "aws_instance" "entry" {
  ami                         = data.aws_ami.ubuntu.id
  instance_type               = var.entry_instance_type
  key_name                    = var.key_name
  subnet_id                   = data.aws_subnets.default.ids[0]
  vpc_security_group_ids      = [aws_security_group.entry.id]
  associate_public_ip_address = true

  root_block_device {
    volume_size = var.root_volume_size
    volume_type = "gp3"
  }

  tags = {
    Name    = "${var.project_name}-entry"
    Project = var.project_name
  }
}

resource "aws_instance" "services" {
  ami                         = data.aws_ami.ubuntu.id
  instance_type               = var.services_instance_type
  key_name                    = var.key_name
  subnet_id                   = data.aws_subnets.default.ids[0]
  vpc_security_group_ids      = [aws_security_group.services.id]
  associate_public_ip_address = true

  root_block_device {
    volume_size = var.root_volume_size
    volume_type = "gp3"
  }

  tags = {
    Name    = "${var.project_name}-services"
    Project = var.project_name
  }
}
