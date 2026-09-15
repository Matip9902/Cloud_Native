# Terraform AWS

Esta carpeta permite crear una infraestructura base con dos instancias EC2:

```text
EC2 entry:
- Frontend Angular
- API Gateway
- Eureka Server

EC2 services:
- Inventory Service
- Customer Service
- Supplier Service
- Notification Service
```

La base de datos RDS se deja para un paso posterior, cuando las dos EC2 esten funcionando.

## Antes de ejecutar

Crear un archivo local `terraform.tfvars` copiando el ejemplo:

```bash
cp terraform.tfvars.example terraform.tfvars
```

Actualizar:

```text
operator_ip_cidr = "TU_IPV4_PUBLICA/32"
key_name         = "cloud-native-key"
```

La llave `cloud-native-key` debe existir previamente en EC2 Key Pairs.

## Comandos

```bash
terraform init
terraform plan
terraform apply
```

Al terminar, Terraform mostrara las IPs publicas y privadas de ambas EC2.
Tambien mostrara dos comandos listos para crear los archivos `.env`:

```bash
terraform output -raw entry_env_command
terraform output -raw services_env_command
```

Cada comando ya viene con las IPs reales creadas por Terraform.

## Uso con Docker Compose

En la EC2 de entrada:

```bash
terraform output -raw entry_env_command
docker compose -f docker-compose.entry.yml up --build -d
```

En la EC2 de servicios:

```bash
terraform output -raw services_env_command
docker compose -f docker-compose.services.yml --env-file .env up --build -d
```
