package com.curio;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Enforces hexagonal architecture dependency rules.
 *
 * Allowed direction of dependencies:
 *   controller (driving adapter)
 *     → port.in (inbound port / use-case interface)
 *     → dto, entity, java.*, org.springframework.*, jakarta.*
 *
 *   service (use-case implementation)
 *     → port.in, port.out, entity, dto
 *     ✗ must NOT depend on ..repository.. (use ports instead)
 *     ✗ must NOT depend on ..adapter..
 *
 *   port (interfaces only)
 *     ✗ must NOT depend on ..adapter..
 *
 *   adapter.persistence
 *     → port.out, repository, entity
 *     ✗ must NOT depend on other adapters
 *
 * <p>Written as plain JUnit 5 tests (not {@code @ArchTest} fields): the ArchUnit
 * JUnit engine stopped being discovered by Surefire after the Spring Boot 3.5 /
 * JUnit Platform upgrade — the class silently reported "Tests run: 0", which
 * disables the rules without failing anything. Plain {@code @Test} methods can't
 * silently vanish.
 */
public class HexagonalArchitectureTest {

    private static JavaClasses productionClasses;

    @BeforeAll
    static void importClasses() {
        productionClasses = new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("com.curio");
    }

    /**
     * Port interfaces must not import adapter implementations.
     * Ports are pure interfaces; adapters implement them — never the other way around.
     */
    @Test
    void ports_must_not_depend_on_adapters() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("com.curio..port..")
                .should().dependOnClassesThat().resideInAPackage("com.curio..adapter..")
                .as("Port interfaces must not depend on adapter implementations");
        rule.check(productionClasses);
    }

    /**
     * Services (application layer) must not import Spring Data JPA repositories directly.
     * They must go through outbound port interfaces (port.out).
     */
    @Test
    void services_must_not_import_repositories() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("com.curio..service..")
                .should().dependOnClassesThat().resideInAPackage("com.curio..repository..")
                .as("Services must use outbound port interfaces, not JPA repositories directly");
        rule.check(productionClasses);
    }

    /**
     * Controllers (inbound adapters) must not import concrete service implementations.
     * They must depend only on inbound port interfaces (port.in).
     */
    @Test
    void controllers_must_not_depend_on_services() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("com.curio..controller..")
                .should().dependOnClassesThat().resideInAPackage("com.curio..service..")
                .as("Controllers must depend on port interfaces, not service implementations");
        rule.check(productionClasses);
    }

    /**
     * JPA adapters must not cross-import other adapter classes.
     * Adapters communicate through ports, never by calling each other directly.
     */
    @Test
    void adapters_must_not_depend_on_adapters() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("com.curio..adapter..")
                .should().dependOnClassesThat().resideInAPackage("com.curio..adapter..")
                .as("Adapters must not depend on other adapter classes directly");
        rule.check(productionClasses);
    }
}
