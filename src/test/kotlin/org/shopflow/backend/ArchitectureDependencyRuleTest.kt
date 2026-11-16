package org.shopflow.backend

import com.tngtech.archunit.core.domain.JavaClasses
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import jakarta.persistence.Entity
import jakarta.persistence.MappedSuperclass
import jakarta.persistence.Table
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.shopflow.backend.infrastructure.persistence.entity.analytics.UserEventJpaEntity
import org.shopflow.backend.infrastructure.persistence.entity.auth.UserJpaEntity
import org.shopflow.backend.infrastructure.persistence.entity.common.BaseJpaEntity
import org.shopflow.backend.infrastructure.persistence.entity.order.OrderItemJpaEntity
import org.shopflow.backend.infrastructure.persistence.entity.order.OrderJpaEntity
import org.shopflow.backend.infrastructure.persistence.entity.product.CategoryJpaEntity
import org.shopflow.backend.infrastructure.persistence.entity.product.ProductJpaEntity
import org.shopflow.backend.infrastructure.persistence.entity.review.ReviewJpaEntity
import org.springframework.stereotype.Component
import org.springframework.stereotype.Repository
import org.springframework.stereotype.Service
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.readLines

class ArchitectureDependencyRuleTest {
    private val sourceRoot: Path = Path.of("src/main/kotlin/org/shopflow/backend")

    private val importedClasses: JavaClasses =
        ClassFileImporter()
            .withImportOption(ImportOption.DoNotIncludeTests())
            .importPackages("org.shopflow.backend")

    @Test
    fun `domain layer must stay pure Kotlin without framework or outer-layer imports`() {
        val domainDir = sourceRoot.resolve("domain")
        val kotlinFiles = listKotlinFiles(domainDir)
        assertTrue(kotlinFiles.isNotEmpty(), "Expected Kotlin files under $domainDir")

        val forbiddenPrefixes =
            listOf(
                "import org.springframework.",
                "import jakarta.",
                "import io.jsonwebtoken.",
                "import com.fasterxml.jackson.",
                "import tools.jackson.",
                "import org.bson.",
                "import com.cloudinary.",
                "import org.shopflow.backend.application.",
                "import org.shopflow.backend.infrastructure.",
                "import org.shopflow.backend.presentation.",
            )

        val violations = findImportViolations(kotlinFiles, forbiddenPrefixes)
        assertTrue(
            violations.isEmpty(),
            "Domain layer dependency violations found:\n${violations.joinToString("\n")}",
        )
    }

    @Test
    fun `application layer must not depend on infrastructure, presentation, JPA, or JWT`() {
        val applicationDir = sourceRoot.resolve("application")
        val kotlinFiles = listKotlinFiles(applicationDir)
        assertTrue(kotlinFiles.isNotEmpty(), "Expected Kotlin files under $applicationDir")

        val forbiddenPrefixes =
            listOf(
                "import org.springframework.data.",
                "import org.shopflow.backend.infrastructure.",
                "import org.shopflow.backend.presentation.",
                "import jakarta.persistence.",
                "import io.jsonwebtoken.",
                "import com.cloudinary.",
            )

        val violations = findImportViolations(kotlinFiles, forbiddenPrefixes)
        assertTrue(
            violations.isEmpty(),
            "Application layer dependency violations found:\n${violations.joinToString("\n")}",
        )
    }

    @Test
    fun `presentation layer must not depend on infrastructure or domain repositories`() {
        val presentationDir = sourceRoot.resolve("presentation")
        val kotlinFiles = listKotlinFiles(presentationDir)
        assertTrue(kotlinFiles.isNotEmpty(), "Expected Kotlin files under $presentationDir")

        val forbiddenPrefixes =
            listOf(
                "import org.shopflow.backend.infrastructure.",
                "import org.shopflow.backend.domain.repository.",
            )

        val violations = findImportViolations(kotlinFiles, forbiddenPrefixes)
        assertTrue(
            violations.isEmpty(),
            "Presentation layer dependency violations found:\n${violations.joinToString("\n")}",
        )
    }

    @Test
    fun `infrastructure layer must not depend on presentation layer`() {
        val infrastructureDir = sourceRoot.resolve("infrastructure")
        val kotlinFiles = listKotlinFiles(infrastructureDir)
        assertTrue(kotlinFiles.isNotEmpty(), "Expected Kotlin files under $infrastructureDir")

        val violations = findImportViolations(kotlinFiles, listOf("import org.shopflow.backend.presentation."))
        assertTrue(
            violations.isEmpty(),
            "Infrastructure layer must not import presentation:\n${violations.joinToString("\n")}",
        )

        noClasses()
            .that()
            .resideInAPackage("..infrastructure..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("..presentation..")
            .check(importedClasses)
    }

    @Test
    fun `domain classes must not be annotated with Spring or Jakarta stereotypes`() {
        noClasses()
            .that()
            .resideInAPackage("..domain..")
            .should()
            .beAnnotatedWith(Entity::class.java)
            .orShould()
            .beAnnotatedWith(Table::class.java)
            .orShould()
            .beAnnotatedWith(MappedSuperclass::class.java)
            .orShould()
            .beAnnotatedWith(Embeddable::class.java)
            .orShould()
            .beAnnotatedWith(Service::class.java)
            .orShould()
            .beAnnotatedWith(Component::class.java)
            .orShould()
            .beAnnotatedWith(Repository::class.java)
            .check(importedClasses)
    }

    @Test
    fun `domain layer bytecode must not depend on frameworks or outer layers`() {
        noClasses()
            .that()
            .resideInAPackage("..domain..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                "org.springframework..",
                "jakarta..",
                "io.jsonwebtoken..",
                "com.fasterxml.jackson..",
                "tools.jackson..",
                "org.bson..",
                "com.cloudinary..",
                "..application..",
                "..infrastructure..",
                "..presentation..",
            ).check(importedClasses)
    }

    @Test
    fun `all persistence entities must reside in entity package and extend BaseJpaEntity`() {
        classes()
            .that()
            .resideInAPackage("..infrastructure.persistence.entity..")
            .and()
            .areTopLevelClasses()
            .and()
            .doNotHaveFullyQualifiedName(BaseJpaEntity::class.java.name)
            .and()
            .areNotAnnotatedWith(Embeddable::class.java)
            .should()
            .beAssignableTo(BaseJpaEntity::class.java)
            .check(importedClasses)

        classes()
            .that()
            .areAnnotatedWith(Entity::class.java)
            .should()
            .resideInAPackage("..infrastructure.persistence.entity..")
            .andShould()
            .beAssignableTo(BaseJpaEntity::class.java)
            .check(importedClasses)
    }

    @Test
    fun `all controllers must reside in presentation controller package and not depend on repositories or JPA entities`() {
        classes()
            .that()
            .haveSimpleNameEndingWith("Controller")
            .should()
            .resideInAPackage("..presentation.controller..")
            .check(importedClasses)

        noClasses()
            .that()
            .haveSimpleNameEndingWith("Controller")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("..domain.repository..", "..infrastructure..")
            .orShould()
            .dependOnClassesThat()
            .haveSimpleNameEndingWith("Repository")
            .orShould()
            .dependOnClassesThat()
            .haveSimpleNameEndingWith("RepositoryImpl")
            .orShould()
            .dependOnClassesThat()
            .haveSimpleNameEndingWith("JpaRepository")
            .orShould()
            .dependOnClassesThat()
            .haveSimpleNameEndingWith("JpaEntity")
            .check(importedClasses)
    }

    @Test
    fun `frozen dataset entities and YAML configurations must obey schema freeze guardrail`() {
        val frozenSchemas: Map<Class<out BaseJpaEntity>, Pair<String, Set<String>>> =
            mapOf(
                UserJpaEntity::class.java to (
                    "users" to setOf("email", "hashed_password", "status", "full_name", "phone_number", "city", "address", "avatar_url")
                ),
                CategoryJpaEntity::class.java to (
                    "categories" to setOf("name", "slug", "parent_id", "description", "display_order", "is_active")
                ),
                ProductJpaEntity::class.java to (
                    "products" to
                        setOf(
                            "category_id",
                            "name",
                            "slug",
                            "sku",
                            "description",
                            "price",
                            "stock_quantity",
                            "average_rating",
                            "review_count",
                            "image_url",
                            "status",
                        )
                ),
                OrderJpaEntity::class.java to (
                    "orders" to
                        setOf(
                            "user_id",
                            "order_date",
                            "order_status",
                            "total_amount",
                            "shipping_address",
                            "shipping_city",
                            "payment_method",
                            "payment_status",
                            "version",
                        )
                ),
                OrderItemJpaEntity::class.java to (
                    "order_items" to setOf("order_id", "product_id", "user_id", "quantity", "item_price", "item_total")
                ),
                ReviewJpaEntity::class.java to (
                    "reviews" to setOf("order_id", "product_id", "user_id", "rating", "review_text", "review_date")
                ),
                UserEventJpaEntity::class.java to (
                    "user_events" to setOf("user_id", "product_id", "event_type", "event_timestamp")
                ),
            )

        frozenSchemas.forEach { (entityClass, expectedSchema) ->
            val (expectedTable, expectedColumns) = expectedSchema
            val tableAnnotation = entityClass.getAnnotation(Table::class.java)
            assertEquals(expectedTable, tableAnnotation?.name, "Frozen table name changed on ${entityClass.simpleName}")
            val actualColumns =
                entityClass.declaredFields
                    .mapNotNull { it.getAnnotation(Column::class.java)?.name }
                    .toSet()
            assertTrue(
                actualColumns.containsAll(expectedColumns),
                "Missing frozen columns on ${entityClass.simpleName}: ${expectedColumns - actualColumns}",
            )
        }

        val destructiveDdlRegex = Regex("""ddl-auto:\s*(create|create-drop)\b""")
        listOf(
            Path.of("src/main/resources/application.yaml"),
            Path.of("src/test/resources/application.yaml"),
        ).forEach { yamlPath ->
            val yamlContent = Files.readString(yamlPath)
            assertFalse(
                destructiveDdlRegex.containsMatchIn(yamlContent),
                "Destructive ddl-auto setting detected in $yamlPath",
            )
        }
    }

    private fun listKotlinFiles(directory: Path): List<Path> {
        assertTrue(Files.isDirectory(directory), "Directory does not exist: $directory")
        return Files.walk(directory).use { stream ->
            stream.filter { Files.isRegularFile(it) && it.extension == "kt" }.toList()
        }
    }

    private fun findImportViolations(
        files: List<Path>,
        forbiddenPrefixes: List<String>,
    ): List<String> {
        val violations = mutableListOf<String>()
        for (file in files) {
            file.readLines().forEachIndexed { index, rawLine ->
                val line = rawLine.trim()
                if (forbiddenPrefixes.any { prefix -> line.startsWith(prefix) }) {
                    violations += "$file:${index + 1} -> $line"
                }
            }
        }
        return violations
    }
}
