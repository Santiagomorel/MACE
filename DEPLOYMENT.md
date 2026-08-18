# Blue-Green Deployment Strategy

## Overview

This document describes the blue-green deployment strategy for the Credential Rotation System in Phase 3 (ECS/RDS production).

## Architecture

```
                    ┌──────────────────────────────────────────────────┐
                    │                    ALB                          │
                    │       (Health-based routing via ECS services)    │
                    └────────────────┬─────────────────────────────────┘
                                     │
                   ┌─────────────────┴─────────────────┐
                   │                                   │
              ┌────▼────┐                       ┌──────▼──────┐
              │  BLUE   │                       │   GREEN     │
              │ Service │                       │  Service    │
              │ (Active)│                       │ (Active)    │
              └────┬────┘                       └──────┬──────┘
                   │                                   │
              ┌────▼────┐                       ┌──────▼──────┐
              │   RDS   │                       │    RDS      │
              │ Primary │                       │  Replica    │
              └─────────┘                       └─────────────┘
```

## Deployment Process

### Pre-requisites
- ECS cluster with Fargate or EC2 launch types
- Application Load Balancer (ALB) with target groups
- RDS PostgreSQL with read replica
- ECR repository for each service
- GitHub Actions workflow (deploy.yml)

### Step 1: Prepare New Version (Green)

```bash
# 1. Build and push new Docker image
docker build -t public.ecr.aws/rotation-system/<service>:<new-tag> -f Dockerfile .
docker push public.ecr.aws/rotation-system/<service>:<new-tag>

# 2. Deploy to ECS as "Green" service
aws ecs update-service \
  --cluster rotation-cluster \
  --service <service>-green \
  --task-definition <service>:<new-task-def> \
  --desired-count 2 \
  --force-new-deployment
```

### Step 2: Verify Health

```bash
# Check ECS service status
aws ecs describe-services \
  --cluster rotation-cluster \
  --services <service>-green

# Check health endpoints (all services must return healthy)
curl https://green-alb.internal/actuator/health
curl https://green-alb-verification/actuator/health
curl https://green-alb-decision/actuator/health
curl https://green-alb-action/actuator/health
```

### Step 3: Database Migration (If Needed)

```sql
-- Run migration on primary RDS
-- All services must be backward compatible with old schema
-- Use Flyway for versioned migrations
flyway -url=jdbc:postgresql://<rds-primary>:5432/credential_rotation \
       -user=postgres -password=<secret> migrate
```

### Step 4: Switch Traffic (Blue → Green)

```bash
# Update ALB target group to point to Green services
# ECS handles this automatically via service discovery
# or manually via ALB target group swap:

aws elbv2 modify-target-group \
  --target-group-arn arn:aws:elasticloadbalancing:... \
  --health-check-path /actuator/health

# Switch ECS service weights if using blue-green container instances
```

### Step 5: Monitor and Validate

```bash
# Monitor metrics for 15-30 minutes
aws cloudwatch get-metrics-data \
  --metric-data-queries '[
    {
      "Id": "request_count",
      "MetricStat": {
        "Metric": {
          "Namespace": "AWS/ECS",
          "MetricName": "RequestCount"
        },
        "Period": 60,
        "Stat": "Sum"
      }
    }
  ]'

# Check application logs
aws logs tail /ecs/rotation-system/<service> --follow
```

### Step 6: Cleanup Blue (If Successful)

```bash
# After 24 hours of stability, decommission Blue
aws ecs update-service \
  --cluster rotation-cluster \
  --service <service>-blue \
  --desired-count 0

# Remove old task definitions
aws ecs deregister-task-definition \
  --task-definition <old-task-def>
```

## Rollback Procedure

### Automated Rollback
- If health checks fail after traffic switch, ECS will not route traffic
- CI/CD pipeline should trigger automatic rollback if deployment fails

### Manual Rollback

```bash
# 1. Switch traffic back to Blue
aws elbv2 modify-target-group \
  --target-group-arn <green-tg-arn> \
  --default-actions '[{"Type":"forward","TargetGroupArn":"<blue-tg-arn>"}]'

# 2. Scale down Green
aws ecs update-service \
  --cluster rotation-cluster \
  --service <service>-green \
  --desired-count 0

# 3. Re-activate Blue (if scaled down)
aws ecs update-service \
  --cluster rotation-cluster \
  --service <service>-blue \
  --desired-count 2
```

## Service Ports

| Service | Port | Health Check |
|---------|------|--------------|
| alert-integrator | 8082 | /actuator/health |
| verification-engine | 8081 | /actuator/health |
| decision-engine | 8083 | /actuator/health |
| action-executor | 8084 | /actuator/health |

## Cost Considerations

### Infrastructure
- **ECS Fargate**: ~$0.00000833 per vCPU-second
- **RDS Multi-AZ**: 2x RDS instance cost
- **ALB**: $0.0225/hour + $0.008 per LCUs
- **ECR**: $0.10/GB/month stored

### Estimated Monthly (3 services, Fargate)
- 2x micro services (Blue + Green): ~$50/mo
- RDS Multi-AZ (db.t3.medium): ~$150/mo
- ALB: ~$17/mo
- ECR storage: ~$1/mo
- **Total: ~$218/mo**

## Monitoring and Alerting

### CloudWatch Alarms
- ECS Service Task Count < Desired Count
- HTTP 5xx > 1% of requests
- Response time P99 > 1 second
- Database connection pool exhaustion
- Disk usage > 80%

### Key Metrics
- Request count (per service)
- Error rate (HTTP 5xx)
- Response latency (P50, P95, P99)
- Active connections
- Deployment duration

## Best Practices

1. **Database Compatibility**: Always ensure new version is backward compatible
2. **Gradual Rollout**: Use canary deployments (10% → 50% → 100%) before full swap
3. **Health Checks**: Configure liveness/readiness probes for all services
4. **Database Backups**: Automated snapshots + PITR enabled before migration
5. **Rollback Window**: Keep Blue environment for at least 24 hours
6. **Monitoring**: Dashboard with all service health indicators
7. **Runbooks**: Documented rollback procedures for each service
8. **Testing**: Load test new version before full deployment

## Phase 3 Timeline

| Phase | Duration | Activities |
|-------|----------|------------|
| Week 1 | 5 days | Setup ECS cluster, ALB, RDS |
| Week 2 | 5 days | Deploy first service (alert-integrator) |
| Week 3 | 5 days | Deploy remaining services |
| Week 4 | 5 days | Monitoring, optimization, runbook |
| Week 5 | 5 days | Go live, 24/7 monitoring |

## Dependencies Between Services

```
alert-integrator → verification-engine → decision-engine → action-executor
```

When deploying, order services in reverse dependency order:
1. action-executor (leaf)
2. decision-engine
3. verification-engine
4. alert-integrator (entry point)

This ensures that if a service needs rollback, dependent services will continue to work with the old version.
