package com.sublite.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

/**
 * "shared" (billing / retention / loyalty modules land here in later days)
 * must never depend on a business module - dependencies only flow the
 * other way. modules_should_be_free_of_cycles catches any two modules
 * quietly depending on each other, which package structure alone doesn't
 * prevent (unlike separate Gradle/Maven modules, this is a convention we
 * check with a test, not something the compiler enforces).
 */
@AnalyzeClasses(packages = "com.sublite", importOptions = ImportOption.DoNotIncludeTests.class)
class ModuleBoundaryTest {

    @ArchTest
    static final ArchRule modules_should_be_free_of_cycles =
            slices().matching("com.sublite.(*)..").should().beFreeOfCycles();

    @ArchTest
    static final ArchRule shared_should_not_depend_on_business_modules =
            noClasses().that().resideInAPackage("com.sublite.shared..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "com.sublite.subscription..",
                            "com.sublite.billing..",
                            "com.sublite.retention..",
                            "com.sublite.loyalty..");
}
