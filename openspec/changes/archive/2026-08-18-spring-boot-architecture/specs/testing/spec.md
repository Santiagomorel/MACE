# Testing

## ADDED Requirements

### Requirement: JUnit 5 + Mockito + AssertJ testing stack
The system SHALL use JUnit 5 as the test framework, Mockito for mocking, and AssertJ for fluent assertions.

#### Scenario: Unit test with Mockito
- **WHEN** a service class is tested in isolation
- **THEN** dependencies are mocked using `@Mock` and `@ExtendWith(MockitoExtension.class)`
- **AND** the class under test is instantiated with `@InjectMocks`

#### Scenario: AssertJ fluent assertions
- **WHEN** a test validates an outcome
- **THEN** it uses AssertJ fluent assertions (e.g., `assertThat(result).isNotNull().isEqualTo(expected)`)
- **AND** assertions are chainable and type-safe

#### Scenario: No JUnit Vintage
- **WHEN** the `spring-boot-starter-test` dependency is included
- **THEN** the `junit-vintage-engine` is excluded (no JUnit 4 support)
- **AND** all tests use JUnit 5 annotations (`@Test`, `@BeforeEach`, `@AfterEach`, `@Nested`)

### Requirement: Integration tests with Testcontainers PostgreSQL
The system SHALL use Testcontainers to run integration tests against a real PostgreSQL database.

#### Scenario: Test container lifecycle
- **WHEN** an integration test class is annotated with `@Testcontainers`
- **THEN** a PostgreSQL 16 container starts before any test methods run
- **AND** the container stops after all tests in the class complete

#### Scenario: Dynamic property source
- **WHEN** the Testcontainers PostgreSQL container is running
- **THEN** the `spring.datasource.url`, `spring.datasource.username`, and `spring.datasource.password` are dynamically configured from the container's connection details
- **AND** the `@DynamicPropertySource` annotation injects these properties into the Spring context

#### Scenario: Real PostgreSQL dialect
- **WHEN** JPA entities are persisted during an integration test
- **THEN** the database uses PostgreSQL 16 dialect (not H2 or an emulated dialect)
- **AND** PostgreSQL-specific features (JSONB, GIN indexes) are exercised in tests

### Requirement: Web tests with MockMvc
The system SHALL use MockMvc to test REST endpoints without starting an embedded server.

#### Scenario: Controller test with MockMvc
- **WHEN** a controller is tested with `@WebMvcTest`
- **THEN** MockMvc performs HTTP requests against the controller
- **AND** the service dependencies are mocked with `@MockBean`

#### Scenario: HTTP status verification
- **WHEN** a valid request is sent to an endpoint
- **THEN** the response status is verified (e.g., 201 for creation, 200 for success)
- **AND** the response body JSON is validated with `jsonPath` assertions

#### Scenario: Validation error response
- **WHEN** a request with invalid input is sent
- **THEN** the response status is 400 (Bad Request)
- **AND** the error response follows the `ErrorResponse` format with field-level validation messages

#### Scenario: HMAC signature validation
- **WHEN** a webhook request is sent with a valid HMAC-SHA256 signature
- **THEN** the endpoint processes the request normally
- **AND** when the signature is invalid or missing, the endpoint returns 401 Unauthorized

### Requirement: Drools rule testing
The system SHALL test Drools rules using `KieContainer` and `KieFileSystem` for dynamic rule loading.

#### Scenario: Rule engine test with KieContainer
- **WHEN** a Drools rule engine test runs
- **THEN** a `KieContainer` is created and a `KieSession` is opened to fire rules
- **AND** the session is disposed after rule evaluation

#### Scenario: Dynamic rule loading in tests
- **WHEN** a test needs custom rules
- **THEN** rules are loaded via `KieFileSystem` from `.drl` files in `src/test/resources/rules/`
- **AND** the `KieContainer` is built from the file system for test isolation

#### Scenario: Rule outcome verification
- **WHEN** facts (Alert, VerificationResult) are inserted into a KieSession
- **THEN** the fired rules produce the expected `DecisionOutput`
- **AND** the decision matches the severity calculation (playbook floor + Drools result)

### Requirement: JaCoCo coverage gates
The system SHALL enforce code coverage minimums using JaCoCo Maven plugin.

#### Scenario: General coverage gate
- **WHEN** the test phase completes
- **THEN** JaCoCo generates a coverage report
- **AND** the build fails if general line coverage is below 70%

#### Scenario: Domain coverage gate
- **WHEN** the test phase completes
- **THEN** the domain layer (`src/main/java` of service/processor/calculator classes) must meet 80%+ line coverage
- **AND** the build fails if domain coverage is below the threshold

#### Scenario: Per-type coverage targets
- **WHEN** the JaCoCo report is generated
- **THEN** controllers/adapters have a minimum of 60% coverage
- **AND** models/exceptions have a minimum of 90% coverage

### Requirement: Test profiles and configuration
The system SHALL use distinct Spring profiles for different test environments.

#### Scenario: Test profile configuration
- **WHEN** the application runs with the `test` profile active
- **THEN** it uses Testcontainers PostgreSQL (via `jdbc:tc:postgresql:16` URL)
- **AND** `hibernate.ddl-auto` is set to `create-drop` for clean test data

#### Scenario: Dev profile for local development
- **WHEN** the application runs with the `dev` profile active
- **THEN** it uses H2 in-memory database with `create-drop` DDL auto
- **AND** logging level is set to DEBUG for `com.company.rotations`

#### Scenario: Test-specific application.yml
- **WHEN** each module is tested
- **THEN** it has its own `src/test/resources/application-test.yml` with module-specific overrides
- **AND** the test profile is activated via `@ActiveProfiles("test")` on integration test classes

### Requirement: Module-specific test structure
Each module SHALL have its own test directory structure mirroring the main source structure.

#### Scenario: alert-integrator tests
- **WHEN** tests exist for `alert-integrator`
- **THEN** they are organized under `src/test/java/.../controller/`, `src/test/java/.../adapter/`, `src/test/java/.../dedup/`, `src/test/java/.../worker/`
- **AND** test resources include sample webhook payloads for adapter tests

#### Scenario: decision-engine tests
- **WHEN** tests exist for `decision-engine`
- **THEN** they include `.drl` rule files in `src/test/resources/rules/`
- **AND** test classes cover `RuleEngineTest`, `PlaybookManagerTest`, and `DroolsRuleEngineTest`

#### Scenario: action-executor tests
- **WHEN** tests exist for `action-executor`
- **THEN** they cover `RotationStateMachineTest`, `AwsRotationServiceTest`, and `NotificationDispatcherTest`
- **AND** the state machine transitions are verified for each rotation lifecycle path
