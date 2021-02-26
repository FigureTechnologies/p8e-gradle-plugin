package io.provenance.p8e.plugin

import org.gradle.api.Project

internal class Checker(
    private val project: Project,
    val extension: P8eExtension
) {

    @Synchronized
    fun execute() {
        project.logger.info("Checking P8eContract's against ruleset")

        val contractProject = getProject(project, extension.contractProject)
        // TODO is this needed?
        val protoProject = getProject(project, extension.protoProject)
        val contractJar = getJar(contractProject)
        // TODO is this needed?
        val protoJar = getJar(protoProject)
    }
}
