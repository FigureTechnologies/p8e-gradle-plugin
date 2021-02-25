package io.provenance.p8e.plugin

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.testfixtures.ProjectBuilder

class ContractPluginTest : WordSpec({

    fun project(): ProjectInternal {
        val project = ProjectBuilder.builder()
            .withName("test")
            .build()
            .also {
                it.extensions.add("contractProject", "contracts")
                it.extensions.add("protoProject", "protos")

                it.extensions.add("contractHashPackage", "io.p8e.contracts.test")
                it.extensions.add("protoHashPackage", "io.p8e.proto.test")

                it.pluginManager.apply(ContractPlugin::class.java)
            } as ProjectInternal
        ProjectBuilder.builder()
            .withName("contracts")
            .withParent(project)
            .build()
            .also {
                it.pluginManager.apply("java")
                it.tasks.create("uberJar")
            }
        ProjectBuilder.builder()
            .withName("protos")
            .withParent(project)
            .build()
            .also { it.pluginManager.apply("java") }

        project.evaluate()

        return project
    }

    "Using the plugin ID" should {
        "Apply the plugin" {
            val project = ProjectBuilder.builder().build()
            project.pluginManager.apply("io.provenance.p8e.p8e-gradle-plugin")

            project.plugins.getPlugin(ContractPlugin::class.java) shouldNotBe null
        }
    }

    "Applying the plugin" should {
        "Register the 'contract' extension" {
            val project = ProjectBuilder.builder().build()
            project.pluginManager.apply(ContractPlugin::class.java)

            project.p8eConfiguration() shouldNotBe null
        }
    }

    "Clean task" should {
        "Exist" {
            val project = project()

            project.tasks.withType(CleanTask::class.java).size shouldBe 1
        }
    }

    "Bootstrap task" should {
        "Exist" {
            val project = project()

            project.tasks.withType(BootstrapTask::class.java).size shouldBe 1
        }
    }

    "Jar task" should {
        "Exist" {
            val project = project()

            project.tasks.getByName("p8eJar").enabled shouldBe true
        }
    }

    "Maven plugin" should {
        "Exist" {
            val project = project()

            project.plugins.hasPlugin("maven-publish") shouldBe true
        }

        "Depends on p8e jar" {
            val project = project()
            val jarTask = project.tasks.getByName("p8eJar")

            project.tasks.getByName("publish").dependsOn.contains(jarTask) shouldBe true
            project.tasks.getByName("publishToMavenLocal").dependsOn.contains(jarTask) shouldBe true
        }
    }
})
