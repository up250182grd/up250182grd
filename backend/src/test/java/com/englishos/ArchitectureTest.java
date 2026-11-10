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
