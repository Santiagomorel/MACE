## Purpose

TBD

## Requirements

### Requirement: Docker multi-stage builds
The system SHALL produce Docker images using multi-stage builds to minimize final image size.

#### Scenario: Build stage
- **WHEN** the Docker image is built
- **THEN** the first stage uses a Maven 3.9 + Java 21 base image to compile and package the project
- **AND** the build runs tests and only proceeds to the second stage if tests pass

#### Scenario: Runtime stage
- **WHEN** the second stage starts
- **THEN** it uses a slim Java 21 base image (e.g., `eclipse-temurin:21-jre-alpine`)
- **AND** only the built JAR file from the first stage is copied into the final image

#### Scenario: Non-root user
- **WHEN** the Docker image runs
- **THEN** the application runs as a non-root user inside the container
- **AND** the container has minimal permissions (no shell, no package manager)

### Requirement: Docker Compose for local and staging
The system SHALL provide a `docker-compose.yml` file for local development and staging environments.

#### Scenario: Local development stack
- **WHEN** a developer runs `docker-compose up`
- **THEN** the stack includes: the Spring Boot application, PostgreSQL 16, and Redis (optional, for Caffeine cache fallback testing)
- **AND** the application connects to PostgreSQL via the compose network

#### Scenario: Environment configuration
- **WHEN** the application container starts via Docker Compose
- **THEN** environment variables are loaded from a `.env` file or compose file overrides
- **AND** the `spring.profiles.active` is set to `dev` for local development

#### Scenario: Health check integration
- **WHEN** the Docker Compose stack is running
- **THEN** the application container includes a health check against `/actuator/health`
- **AND** dependent services (e.g., PostgreSQL) are marked as healthy before the application starts

### Requirement: GitHub Actions CI/CD pipeline
The system SHALL use GitHub Actions for continuous integration and deployment.

#### Scenario: Pull request build
- **WHEN** a pull request is opened or updated
- **THEN** GitHub Actions runs `mvn clean verify` with Java 21
- **AND** the pipeline fails if compilation, tests, or coverage gates fail

#### Scenario: Main branch deployment
- **WHEN** a commit is merged to the main branch
- **THEN** GitHub Actions builds the Docker image and pushes it to a container registry
- **AND** the deployment stage applies the image to the target environment (staging or production)

#### Scenario: Coverage gate in CI
- **WHEN** the CI pipeline runs tests
- **THEN** JaCoCo coverage is enforced: 70% general minimum, 80%+ domain minimum
- **AND** the pipeline fails if coverage thresholds are not met

### Requirement: Multi-environment deployment profiles
The system SHALL support deployment to dev, staging, and production environments via Spring profiles.

#### Scenario: Dev environment
- **WHEN** the application runs with the `dev` profile
- **THEN** it uses H2 in-memory database with auto-DDL (`create-drop`)
- **AND** logging level is DEBUG with pretty-print JSON output

#### Scenario: Staging environment
- **WHEN** the application runs with the `staging` profile
- **THEN** it connects to a PostgreSQL instance (RDS or external)
- **And** `hibernate.ddl-auto` is set to `validate` (schema migrations managed separately)
- **And** logging level is INFO

#### Scenario: Production environment
- **WHEN** the application runs with the `prod` profile
- **THEN** it connects to a PostgreSQL instance (RDS) with connection pooling
- **And** `hibernate.ddl-auto` is set to `validate`
- **And** logging level is WARN for the framework, INFO for the application
- **And** Spring Actuator `show-details` on health is set to `when-authorized`

### Requirement: Deployment phases strategy
The system SHALL follow a phased deployment strategy: Phase 1 (POC $0), Phase 2 (AWS Free Tier), Phase 3 (Production scale).

#### Scenario: Phase 1 — POC ($0)
- **WHEN** deploying in POC mode
- **THEN** the system runs locally via Docker Compose or directly on developer machines
- **And** GitHub Actions is used for CI only (no automated deployment)
- **And** tenant credentials are stored in `.env.{tenant}` files (not in git, added to `.gitignore`)
- **And** no AWS services are required

#### Scenario: Phase 2 — AWS Free Tier (12 months)
- **WHEN** deploying to AWS Free Tier
- **THEN** the system uses EC2 t3.micro (or AWS Lightsail) for the application
- **And** RDS PostgreSQL db.t3.micro for the database
- **And** AWS Secrets Manager for secret storage
- **And** estimated cost: $0-$10/month within free tier limits

#### Scenario: Phase 3 — Production ($57-$97/month)
- **WHEN** deploying to production
- **THEN** the system uses Amazon ECS (Fargate) for container orchestration
- **And** RDS PostgreSQL (single-AZ) for the database
- **And** AWS Secrets Manager with automatic rotation
- **And** estimated cost: $57-$97/month depending on traffic

### Requirement: Blue-green deployment (production)
The system SHALL support blue-green deployment strategy for zero-downtime production releases.

#### Scenario: Blue-green deployment process
- **WHEN** a new version is deployed to production
- **THEN** the new version (green) is deployed alongside the current version (blue)
- **And** traffic is switched from blue to green after the green instance passes health checks
- **And** the blue instance is kept for 5 minutes as a rollback target

#### Scenario: Rollback on deployment failure
- **WHEN** health checks fail on the green deployment
- **THEN** traffic is kept on the blue instance
- **And** the green deployment is flagged as failed and removed

### Requirement: Infrastructure as Code (Phase 3+)
The system SHALL use Infrastructure as Code (Terraform or AWS CDK) for production resource provisioning.

#### Scenario: Terraform resource definitions
- **WHEN** the production infrastructure is provisioned
- **THEN** Terraform defines: ECS cluster, task definition, service, RDS instance, Secrets Manager secrets, IAM roles, and CloudWatch log groups
- **And** the Terraform state is stored in a remote S3 backend with DynamoDB state locking

#### Scenario: Environment-specific Terraform
- **WHEN** multiple environments exist (staging, production)
- **THEN** Terraform uses separate state files per environment
- **And** variable files (`staging.tfvars`, `prod.tfvars`) define environment-specific values
