package io.provenance.p8e.plugin

import org.gradle.api.Project

const val CONTRACT_HASH_PACKAGE = "contractHashPackage"
const val PROTO_HASH_PACKAGE = "protoHashPackage"

fun getProject(project: Project, name: String): Project {
    require (project.hasProperty(name)) {
        "Project does not have a \"$name\" property set"
    }

    return project.subprojects.firstOrNull { it.name == project.property(name) }
        ?: throw IllegalStateException("Subproject ${project.property(name)} could not be found")
}
