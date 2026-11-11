package com.englishos;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;

/** Fronteras del monolito modular (ADR-0001) y reglas transversales verificadas automáticamente. */
class ArchitectureTest {

    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.englishos");
    }

    @Test
    void domainDoesNotDependOnWebOrInfrastructure() {
        noClasses().that().resideInAPackage("..domain..")
                .should().dependOnClassesThat().resideInAnyPackage("..web..", "..infrastructure..", "..application..",
                        "org.springframework.web..", "jakarta.servlet..")
                .check(classes);
    }

    @Test
    void controllersDoNotUseRepositoriesDirectly() {
        noClasses().that().resideInAPackage("..web..")
                .should().dependOnClassesThat().resideInAPackage("..infrastructure..")
                .check(classes);
    }

    @Test
    void sharedKernelDoesNotDependOnBusinessModules() {
        noClasses().that().resideInAPackage("com.englishos.shared..")
                .should().dependOnClassesThat().resideInAnyPackage("com.englishos.identity..", "com.englishos.audit..")
                .check(classes);
    }

    @Test
    void auditDoesNotDependOnOtherModules() {
        noClasses().that().resideInAPackage("com.englishos.audit..")
                .should().dependOnClassesThat().resideInAnyPackage("com.englishos.identity..")
                .check(classes);
    }

    @Test
    void otherModulesOnlyUseIdentityThroughItsApi() {
        noClasses().that().resideOutsideOfPackages("com.englishos.identity..", "com.englishos.config..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "com.englishos.identity.domain..",
                        "com.englishos.identity.application..",
                        "com.englishos.identity.infrastructure..",
                        "com.englishos.identity.web..")
                .check(classes);
    }

    /** Cada módulo usa a los demás solo a través de su paquete {@code api} (ADR-0001). */
    @Test
    void modulesTalkToEachOtherOnlyThroughTheirApi() {
        java.util.List<String> modules = java.util.List.of("identity", "learning", "assessment", "tutor", "voice",
                "metering", "wallet", "ai", "vocabulary", "recommendation", "payments", "gamification");
        for (String m : modules) {
            String[] internals = modules.stream().filter(o -> !o.equals(m))
                    .flatMap(o -> java.util.stream.Stream.of("application", "domain", "infrastructure", "web")
                            .map(layer -> "com.englishos." + o + "." + layer + ".."))
                    .toArray(String[]::new);
            noClasses().that().resideInAPackage("com.englishos." + m + "..")
                    .should().dependOnClassesThat().resideInAnyPackage(internals)
                    .because("el módulo " + m + " debe usar a los demás solo por su API")
                    .check(classes);
        }
    }

    /**
     * El contrato OpenAPI nombra los esquemas por el nombre simple de la clase: dos tipos expuestos con el mismo nombre
     * se sobrescriben en silencio (pasó con {@code StartRequest} del tutor y de la voz).
     */
    @Test
    void typesExposedByTheApiHaveUniqueSimpleNames() {
        java.util.Map<String, java.util.List<String>> byName = new java.util.TreeMap<>();
        classes.stream()
                .filter(c -> c.isRecord() || c.isEnum())
                .filter(c -> c.getPackageName().matches("com\\.englishos\\.[a-z]+\\.(web|application)"))
                .forEach(c -> byName.computeIfAbsent(c.getSimpleName(), k -> new java.util.ArrayList<>())
                        .add(c.getName()));
        var duplicated = byName.entrySet().stream().filter(e -> e.getValue().size() > 1).toList();
        org.assertj.core.api.Assertions.assertThat(duplicated).as("nombres repetidos en tipos de la API").isEmpty();
    }

    /** RNF-1: el dinero nunca se representa con coma flotante. */
    @Test
    void noFloatingPointFieldsInBusinessCode() {
        noFields().that().areDeclaredInClassesThat().resideInAPackage("com.englishos..")
                .should().haveRawType(double.class).orShould().haveRawType(float.class)
                .orShould().haveRawType(Double.class).orShould().haveRawType(Float.class)
                .allowEmptyShould(true)
                .check(classes);
    }
}
