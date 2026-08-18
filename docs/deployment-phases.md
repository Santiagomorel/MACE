# Deployment Phases

This document describes the three-phase deployment strategy for the Credential Rotation System, from local POC ($0) to production ECS/RDS.

---

## Phase Overview

| Phase | Duration | Cost | Infrastructure |
|-------|----------|------|----------------|
| **Phase 1** — POC | Weeks 1-4 | $0 | Docker Compose local |
| **Phase 2** — AWS Free Tier | Months 1-12 | $0-10/mo | EC2 + RDS |
| **Phase 3** — Production | Ongoing | $57-97/mo | ECS Fargate + RDS Multi-AZ |

---

## Phase 1 — POC ($0)

### Target

Validate the system locally with Docker Compose. No AWS services required.

### Architecture

```
Developer Machine
├── Spring Boot App (H2 in-memory)
└── PostgreSQL 16 (Docker container)
```

Tenant credentials stored in `.env.{tenant}` files. Secrets storage uses PostgreSQL AES-256 encrypted columns.

### Infrastructure

| Component | Technology | Notes |
|-----------|------------|-------|
| Application | Spring Boot | Run via `mvn spring-boot:run` |
| Database | PostgreSQL 16 (Docker) | Flyway migrations |
| Secrets | `ENCRYPTION_MASTER_KEY` env var | AES-256 column encryption |
| Deployment | Docker Compose | `docker-compose up` |

### Configuration

```yaml
# application-dev.yml
spring:
  datasource:
    url: jdbc:postgresql://postgres:5432/rotation
    driver-class-name: org.postgresql.Driver
  jpa:
    hibernate:
      ddl-auto: create-drop
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect

flyway:
  enabled: false  # Use Hibernate DDL in dev
```

### Running Locally

```bash
# 1. Start PostgreSQL
docker-compose up -d postgres

# 2. Run the application
mvn spring-boot:run -pl alert-integrator -Dspring-boot.run.profiles=dev

# 3. Or run the full stack
docker-compose up --build
```

### Tenant Credentials

Store tenant credentials in `.env.{tenant}` files (not committed to git):

```bash
# .env.tenant1
TENANT_1_ACCESS_KEY=AKIAIOSFODNN7EXAMPLE
TENANT_1_SECRET_KEY=wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY
TENANT_1_REGION=us-east-1
```

`.gitignore` entries:
```
.env
.env.*
!.env.example
```

### CI/CD

- GitHub Actions runs `mvn clean verify` on PRs
- No automated deployment (manual only)
- JaCoCo coverage gate enforced (70% general, 80%+ domain)

---

## Phase 2 — AWS Free Tier (12 months, $0-10/mo)

### Target

Deploy to AWS with minimal cost using Free Tier limits.

### Architecture

```
                    ┌─────────────────────────┐
                    │   Amazon EC2 t3.micro    │
                    │  (Spring Boot App)       │
                    └─────────┬───────────────┘
                              │
                    ┌─────────▼───────────────┐
                    │  Amazon RDS db.t3.micro  │
                    │  PostgreSQL (PostgreSQL) │
                    └─────────────────────────┘
```

### Infrastructure

| Component | Technology | Cost/Mo |
|-----------|------------|---------|
| Application | EC2 t3.micro | $0 (Free Tier, 750 hrs/mo) |
| Database | RDS db.t3.micro | $0 (Free Tier, 750 hrs/mo) |
| Secrets Manager | AWS Secrets Manager | ~$1/mo |
| Storage | EBS gp2 (30GB) | $0 (Free Tier, 30GB) |
| **Total** | | **$0-10/mo** |

### Configuration

```yaml
# application-staging.yml
spring:
  datasource:
    url: jdbc:postgresql://${RDS_HOST}:5432/rotation
    username: ${RDS_USERNAME}
    password: ${RDS_PASSWORD}
  jpa:
    hibernate:
      ddl-auto: validate

flyway:
  enabled: true
  locations: classpath:db/migration
```

### Deployment Steps

```bash
# 1. Build Docker image
mvn clean package -DskipTests
docker build -t rotation-app:latest -f alert-integrator/Dockerfile .

# 2. Push to ECR
aws ecr get-login-password --region us-east-1 | docker login --username AWS --password-stdin <account>.dkr.ecr.us-east-1.amazonaws.com
docker tag rotation-app:latest <account>.dkr.ecr.us-east-1.amazonaws.com/rotation-app:latest
docker push <account>.dkr.ecr.us-east-1.amazonaws.com/rotation-app:latest

# 3. Deploy to EC2
ssh ec2-user@<instance-ip> "docker pull <account>.dkr.ecr.us-east-1.amazonaws.com/rotation-app:latest && docker-compose up -d"
```

### CI/CD

- GitHub Actions builds Docker image and pushes to ECR on merge to `main`
- Automated deployment to EC2 via GitHub Actions
- Database migrations run via Flyway on application startup

---

## Phase 3 — Production ($57-97/mo)

### Target

Production-grade deployment with container orchestration, high availability, and zero-downtime releases.

### Architecture

```
                      ┌─────────────────────────┐
                      │  Application Load Balancer │
                      └───────────┬──────────────┘
                                  │
               ┌──────────────────┼──────────────────┐
               │                  │                  │
          ┌────▼────┐      ┌─────▼─────┐     ┌──────▼──────┐
          │ ECS Blue │      │ ECS Green │     │ ECS Blue    │
          │ (v1.2)   │      │ (v1.3)    │     │ (v1.2)      │
          └────┬─────┘      └───────────┘     └──────┬──────┘
               │                                    │
          ┌────▼────────────────────────────────────▼──┐
          │        Amazon RDS PostgreSQL               │
          │        Multi-AZ (Primary + Replica)        │
          └────────────────────────────────────────────┘
```

### Infrastructure

| Component | Technology | Cost/Mo |
|-----------|------------|---------|
| ECS Fargate | 3 services x 2 tasks (vCPU=1, memory=2GB) | ~$50/mo |
| RDS Multi-AZ | db.t3.medium (PostgreSQL) | ~$150/mo |
| ALB | Application Load Balancer | ~$17/mo |
| Secrets Manager | AWS Secrets Manager | ~$3/mo |
| CloudWatch | Logs + Alarms | ~$10/mo |
| **Total** | | **~$218/mo** |

### Blue-Green Deployment

```bash
# 1. Deploy new version as "Green" (alongside "Blue")
aws ecs update-service --cluster rotation-cluster --service alert-integrator-green \
  --task-definition alert-integrator:12 \
  --desired-count 2 --force-new-deployment

# 2. Verify health
curl https://green-alb.internal/actuator/health

# 3. Switch traffic from Blue to Green
aws elbv2 set-weight --target-group-arn <tg-arn> --changes TargetGroupArn=<green-tg-arn>,Weight=100

# 4. After 24h stability, decommission Blue
aws ecs update-service --cluster rotation-cluster --service alert-integrator-blue \
  --desired-count 0
```

### Configuration

```yaml
# application-prod.yml
spring:
  datasource:
    url: jdbc:postgresql://${RDS_HOST}:5432/rotation
    username: ${RDS_USERNAME}
    password: ${RDS_PASSWORD}
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
  jpa:
    hibernate:
      ddl-auto: validate
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect
        jdbc:
          time_zone: UTC

flyway:
  enabled: true
  locations: classpath:db/migration

management:
  endpoints:
    web:
      exposure:
        include: health,info,prometheus
  endpoint:
    health:
      show-details: when-authorized

app:
  cors:
    enabled: true
    allowed-origins: ${ALLOWED_ORIGINS}
```

### Monitoring

| Metric | Threshold | Alert |
|--------|-----------|-------|
| ECS Task Count < Desired | - | CloudWatch Alarm |
| HTTP 5xx > 1% of requests | 1% | CloudWatch Alarm |
| Response time P99 | > 1s | CloudWatch Alarm |
| Database connection pool | > 90% | CloudWatch Alarm |
| Disk usage | > 80% | CloudWatch Alarm |

---

## Migration Guide (Phase 1 → Phase 2)

1. **Provision AWS account** with Free Tier
2. **Create ECR repository** for each service
3. **Create RDS instance** (db.t3.micro)
4. **Create Secrets Manager secrets** for tenant credentials and DB passwords
5. **Deploy to EC2** via GitHub Actions
6. **Run Flyway migrations** before application startup
7. **Update DNS** to point to EC2 public IP or ALB

## Migration Guide (Phase 2 → Phase 3)

1. **Provision ECS cluster** with Fargate
2. **Migrate RDS** to Multi-AZ deployment
3. **Create ALB** with blue-green target groups
4. **Migrate Secrets Manager** secrets
5. **Deploy all 4 services** to ECS (blue and green)
6. **Switch traffic** to green after health checks pass
7. **Set up monitoring** with CloudWatch alarms
8. **Remove EC2 instance** after green is stable
