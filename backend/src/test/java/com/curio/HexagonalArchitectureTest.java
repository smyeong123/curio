package com.curio;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

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
 *   ..repository.. (Spring Data)
 *     ✗ reachable ONLY from ..adapter.. — no service, job, batch or controller
 *
 *   shared.* sub-packages
 *     ✗ must NOT form dependency cycles with each other
 *
 * <p>Written as plain JUnit 5 tests (not {@code @ArchTest} fields): the ArchUnit
 * JUnit engine is not discovered by Surefire on this Spring Boot / JUnit
 * Platform combination — the class would silently report "Tests run: 0", which
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
     * Spring Data repositories are reachable only through the persistence adapters.
     * Everything else — services, scheduled jobs, batches, controllers — goes through
     * outbound port interfaces (port.out).
     */
    @Test
    void only_adapters_may_touch_repositories() {
        ArchRule rule = noClasses()
                .that().resideOutsideOfPackages("com.curio..adapter..", "com.curio..repository..")
                .should().dependOnClassesThat().resideInAPackage("com.curio..repository..")
                .as("Only persistence adapters may use JPA repositories; everyone else uses outbound ports");
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

    /**
     * The shared sub-packages (batch, config, digest, email, jobs, scheduler, ...)
     * form a DAG: a cycle between two of them means neither can be understood, or
     * tested, without the other.
     */
    @Test
    void shared_sub_packages_must_be_free_of_cycles() {
        ArchRule rule = slices()
                .matching("com.curio.shared.(*)..")
                .should().beFreeOfCycles()
                .as("shared.* sub-packages must not depend on each other in a cycle");
        rule.check(productionClasses);
    }
}
