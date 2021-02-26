package io.provenance.p8e.plugin

import org.gradle.api.Project

fun getProject(project: Project, name: String): Project {
    return project.subprojects.firstOrNull { it.name == name }
        ?: throw IllegalStateException("Subproject $name could not be found")
}
