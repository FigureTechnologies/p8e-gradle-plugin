package io.provenance.p8e.plugin

import groovy.lang.Closure
import org.gradle.api.Project
import kotlin.reflect.KProperty

private const val DEFAULT_KEY = ""
private const val DEFAULT_URL = ""

internal class GradleProperty<T, V>(
    project: Project,
    type: Class<V>,
    default: V? = null
) {
    val property = project.objects.property(type).apply {
        set(default)
    }

    operator fun getValue(thisRef: T, property: KProperty<*>): V =
        this.property.get()

    operator fun setValue(thisRef: T, property: KProperty<*>, value: V) =
        this.property.set(value)
}

class PartyConfiguration(val name: String, project: Project) {

    init {
        if (name.isBlank()) {
            throw IllegalArgumentException("Audience party name must not be blank or empty")
        }
    }

    var publicKey by GradleProperty(project, String::class.java, DEFAULT_KEY)
}

class P8eLocation(val name: String, project: Project) {

    init {
        if (name.isBlank()) {
            throw IllegalArgumentException("P8e location name must not be blank or empty")
        }
    }

    var privateKey by GradleProperty(project, String::class.java, DEFAULT_KEY)
    var url by GradleProperty(project, String::class.java, DEFAULT_URL)
    val audience = project.container(PartyConfiguration::class.java) { name ->
        PartyConfiguration(name, project)
    }

    fun audience(config: PartyConfiguration.() -> Unit) {
        audience.configure(object : Closure<Unit>(this, this) {
            fun doCall() {
                @Suppress("UNCHECKED_CAST")
                (delegate as? PartyConfiguration)?.let {
                    config(it)
                }
            }
        })
    }

    fun audience(config: Closure<Unit>) {
        audience.configure(config)
    }
}

open class P8eConfiguration(project: Project) {

    val locations = project.container(P8eLocation::class.java) { name ->
        P8eLocation(name, project)
    }

    fun locations(config: P8eLocation.() -> Unit) {
        locations.configure(object : Closure<Unit>(this, this) {
            fun doCall() {
                @Suppress("UNCHECKED_CAST")
                (delegate as? P8eLocation)?.let {
                    config(it)
                }
            }
        })
    }

    fun locations(config: Closure<Unit>) {
        locations.configure(config)
    }

    internal var bootstrapper = Bootstrapper(project, this)
}
