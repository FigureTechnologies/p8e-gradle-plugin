package io.provenance.p8e.plugin

import com.github.jengelman.gradle.plugins.shadow.ShadowBasePlugin
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.publish.maven.plugins.MavenPublishPlugin

private const val EXTENSION_NAME = "p8e"

private const val BOOTSTRAP_TASK = "p8eBootstrap"
private const val CLEAN_TASK = "p8eClean"
private const val JAR_TASK = "p8eJar"

class ContractPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        val extension = project.extensions.create<P8eExtension>(EXTENSION_NAME, P8eExtension::class.java)

        project.plugins.apply("maven-publish")
        project.subprojects.forEach {
            it.plugins.apply("com.github.johnrengelman.shadow")
        }

        project.evaluationDependsOnChildren()

        project.afterEvaluate {
            val contractProject = getProject(it, extension.contractProject)
            val contractJarTask = contractProject.tasks.getByName("jar")
            val contractUberJarTask = contractProject.tasks.getByName("shadowJar")
            val protoProject = getProject(it, extension.protoProject)
            val protoJarTask = protoProject.tasks.getByName("jar")

            it.tasks.register(CLEAN_TASK, CleanTask::class.java)

            it.tasks.register(JAR_TASK, CheckTask::class.java) { task ->
                task.dependsOn(protoJarTask)
                task.dependsOn(contractJarTask)

                contractJarTask.dependsOn(protoJarTask)
            }

            it.tasks.register(BOOTSTRAP_TASK, BootstrapTask::class.java) { task ->
                val cleanTask = it.tasks.getByName(CLEAN_TASK)

                task.dependsOn(cleanTask)
                task.dependsOn(protoJarTask)
                task.dependsOn(contractUberJarTask)

                protoJarTask.dependsOn(cleanTask)
                contractUberJarTask.dependsOn(protoJarTask)
            }

            it.tasks.getByName("publish").dependsOn(project.tasks.getByName(JAR_TASK))
            it.tasks.getByName("publishToMavenLocal").dependsOn(project.tasks.getByName(JAR_TASK))
        }
    }
}

internal fun Project.p8eConfiguration(): P8eExtension =
    extensions.getByName(EXTENSION_NAME) as? P8eExtension
        ?: throw IllegalStateException("$EXTENSION_NAME is not of the correct schema")
