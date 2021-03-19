package io.provenance.p8e.plugin

open class P8ePartyExtension {
    val publicKey: String = ""
}

open class P8eLocationExtension {
    val privateKey: String? = ""
    val url: String? = ""
    val audience: Map<String, P8ePartyExtension> = emptyMap()
}

open class P8eExtension {
    val contractProject: String = "contract"
    val protoProject: String = "proto"
    // TODO what is a good default package path that is somehow derived from the current project?
    val contractHashPackage: String = ""
    val protoHashPackage: String = ""
    val locations: Map<String, P8eLocationExtension> = emptyMap()
}
