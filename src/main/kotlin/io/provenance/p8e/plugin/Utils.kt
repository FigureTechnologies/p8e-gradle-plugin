package io.provenance.p8e.plugin

import org.gradle.api.Project
import org.gradle.jvm.tasks.Jar
import java.io.File
import java.util.jar.JarFile

fun getProject(project: Project, name: String): Project {
    return project.subprojects.firstOrNull { it.name == name }
        ?: throw IllegalStateException("Subproject $name could not be found")
}

fun getJar(project: Project, taskName: String = "jar"): File {
    return (project.tasks.getByName(taskName) as Jar)
        .archiveFile
        .orNull
        ?.asFile
        ?.also { JarFile(it) }
        ?: throw IllegalStateException("task :$taskName in ${project.name} could not be found")
}
