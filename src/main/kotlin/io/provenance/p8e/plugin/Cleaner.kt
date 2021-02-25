package io.provenance.p8e.plugin

import org.gradle.api.Project

internal class Cleaner(
    private val project: Project
) {

    @Synchronized
    fun execute() {
        project.logger.info("Cleaning generated hash files")

        val contractProject = getProject(project, "contractProject")
        val protoProject = getProject(project, "protoProject")

        ServiceProvider.cleanContracts(contractProject)
        ServiceProvider.cleanProtos(protoProject)
    }
}
