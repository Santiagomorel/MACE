# Decision Engine

## ADDED Requirements

### Requirement: Drools-based rules engine
The system SHALL use the Drools/KIE rules engine to evaluate verification results and determine whether rotation is needed.

#### Scenario: Rules engine initialization
- **WHEN** the application starts
- **THEN** a `KieContainer` is created with the default Drools rules from `src/main/resources/rules/`

#### Scenario: Rule evaluation returns decision
- **WHEN** an alert with `verified = true` and `severity = high` enters the rules engine
- **THEN** the rule engine returns `DecisionOutput` with `decision = "rotate"` and `severity = "high"`

#### Scenario: Rule evaluation returns no action
- **WHEN** an alert with `verified = false` and reason `"credential already expired"` enters the rules engine
- **THEN** the rule engine returns `DecisionOutput` with `decision = "no_action"`

### Requirement: Per-tenant KieContainer with hot-reload
The system SHALL maintain a separate `KieContainer` per tenant to support tenant-specific rules without restarting the application.

#### Scenario: Tenant-specific rule deployment
- **WHEN** a new `.drl` rule file is submitted for a specific tenant via the admin API
- **THEN** the system rebuilds the `KieContainer` for that tenant without affecting other tenants

#### Scenario: Default KieContainer fallback
- **WHEN** a tenant has no custom rules deployed
- **THEN** the system uses the default `KieContainer` with built-in rules

### Requirement: YAML playbook manager
The system SHALL load playbooks from YAML configuration files that define rotation procedures per credential type.

#### Scenario: Playbook loading
- **WHEN** the application starts
- **THEN** playbooks from `src/main/resources/playbooks/` are loaded into the `PlaybookManager`

#### Scenario: Playbook lookup by credential type
- **WHEN** the system receives an alert for `credentialType = AWS_ACCESS_KEY`
- **THEN** the `PlaybookManager` returns the playbook with steps: `invalidate_old_key`, `create_new_key`, `verify_new_key`, `update_configurations`, `notify`

### Requirement: Severity floor calculation
The system SHALL calculate the final severity as the maximum of the Drools rule result and the playbook-defined floor.

#### Scenario: Playbook floor exceeds Drools result
- **WHEN** Drools returns `severity = "medium"` but the playbook floor is `"high"`
- **THEN** the final severity is `"high"`

#### Scenario: Drools result exceeds playbook floor
- **WHEN** Drools returns `severity = "critical"` and the playbook floor is `"high"`
- **THEN** the final severity is `"critical"`

### Requirement: Decision output model
The system SHALL produce a `DecisionOutput` model containing the decision, severity, reason, and associated playbook.

#### Scenario: Decision output completeness
- **WHEN** a decision is produced by the rules engine
- **THEN** the `DecisionOutput` contains: `alertId`, `decision` (rotate/no_action/escalate), `severity`, `reason`, and `playbookName`
