# Project Structure

## ADDED Requirements

### Requirement: Multi-module Maven project
The system SHALL be organized as a Maven multi-module project with a parent POM that centralizes version management and dependency declarations.

#### Scenario: Parent POM defines all modules
- **WHEN** building the project with `mvn clean package`
- **THEN** Maven resolves all 6 modules in topological order: `shared/shared-models`, `shared/shared-spi`, `alert-integrator`, `verification-engine`, `decision-engine`, `action-executor`

#### Scenario: Parent POM manages dependency versions
- **WHEN** a module declares a dependency without a version
- **THEN** the parent POM's `dependencyManagement` section provides the version from the BOM

### Requirement: DAG dependency enforcement
The system SHALL enforce a Directed Acyclic Graph (DAG) of module dependencies where no module imports packages from modules that are not in its direct POM dependency list.

#### Scenario: Module depends only on upstream modules
- **WHEN** `alert-integrator` declares dependencies in its POM
- **THEN** it may only depend on `shared/shared-models` and `shared/shared-spi` (upstream modules)

#### Scenario: No backward dependencies
- **WHEN** `verification-engine` declares dependencies in its POM
- **THEN** it may only depend on `shared/shared-models`, `shared/shared-spi`, and `alert-integrator` (never `decision-engine` or `action-executor`)

#### Scenario: Circular dependency detection
- **WHEN** a circular dependency exists between modules
- **THEN** Maven compilation fails with a cycle detection error

### Requirement: Package naming convention
The system SHALL use the package prefix `com.company.rotations.` with sub-packages organized by module and domain.

#### Scenario: Module packages follow convention
- **WHEN** a class is created in `alert-integrator`
- **THEN** its package follows `com.company.rotations.alerting.{subdomain}` (e.g., `com.company.rotations.alerting.controller`, `com.company.rotations.alerting.adapter`)

#### Scenario: Shared models package
- **WHEN** a domain model is created in `shared/shared-models`
- **THEN** its package is `com.company.rotations.models.{entity}` (e.g., `com.company.rotations.models.Alert`)

#### Scenario: Shared SPI package
- **WHEN** an interface is created in `shared/shared-spi`
- **THEN** its package is `com.company.rotations.spi.{domain}` (e.g., `com.company.rotations.spi.alerting.AlertAdapter`)

### Requirement: Contract layer via SPI interfaces
The system SHALL use interfaces in `shared/spi/` as the only mechanism for inter-module communication. Modules MUST NOT import concrete classes from other modules.

#### Scenario: Inter-module communication via SPI
- **WHEN** `verification-engine` needs to call `alert-integrator`
- **THEN** it uses the `AlertAdapter` interface from `shared/spi/alerting/AlertAdapter.java`, not a concrete class from `alert-integrator`

#### Scenario: SPI interface versioning
- **WHEN** an SPI interface is modified with a breaking change (method removed or signature changed)
- **THEN** the interface version advances to the next major version (e.g., `1.x.x` → `2.0.0`)

#### Scenario: Backward-compatible SPI extension
- **WHEN** a new method is added to an SPI interface with a default implementation
- **THEN** the interface version advances only to the next minor version (e.g., `1.0.0` → `1.1.0`)
