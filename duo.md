Analyze the existing constitution.md template and generate a new constitution document using the EXACT SAME FORMAT, SECTION ORDER, HEADING STYLE, TABLE STYLE, AND WRITING TONE.

Project Name: Logging Framework

Do not introduce new sections unless necessary.

Maintain this exact structure:

# Project Constitution: logging-framework

Ratified: <today>
Team: <team-name>
Last Amended: <today>

## Principle I — AOP Logging Conventions

Define:
- Spring Boot 3.x
- Java 21
- Spring AOP
- Constructor injection via Lombok
- SLF4J logging
- No System.out.println()
- Use Around, Before, AfterReturning, AfterThrowing advice
- Structured logging

## Principle II — Package Structure

Provide package hierarchy similar to:

com.company.logging.aspect
com.company.logging.annotation
com.company.logging.config
com.company.logging.dto
com.company.logging.util
com.company.logging.exception

with short descriptions.

## Principle III — Error Handling

Define logging standards for:
- Exceptions
- Validation errors
- Runtime failures
- Correlation IDs

## Principle IV — Testing Standards

Include:
- JUnit 5
- Mockito
- Aspect testing
- Coverage expectations
- Naming conventions

## Principle V — Library Usage

Create a markdown table with columns:
| Library | Status | Key Pattern |

Include:
- spring-boot-starter-aop
- lombok
- slf4j-api
- logback-classic
- jackson-databind

## Principle VI — Simplicity

Keep aspects lightweight.
No business logic in aspects.
Prefer reusable annotations.
Centralize configuration.

## Build & CI

Include:
- Maven
- Java 21
- Spring Boot 3.x
- JaCoCo
- SonarQube
- GitLab CI

Generate the final output as a complete constitution.md file.
