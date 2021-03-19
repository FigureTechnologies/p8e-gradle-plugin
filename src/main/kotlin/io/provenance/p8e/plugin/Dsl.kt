package io.provenance.p8e.plugin

open class P8ePartyExtension {
    var publicKey: String = ""
}

open class P8eLocationExtension {
    var privateKey: String? = ""
    var url: String? = ""
    var audience: Map<String, P8ePartyExtension> = emptyMap()
}

open class P8eExtension {
    var contractProject: String = "contract"
    var protoProject: String = "proto"
    // TODO what is a good default package path that is somehow derived from the current project?
    var contractHashPackage: String = ""
    var protoHashPackage: String = ""
    var locations: Map<String, P8eLocationExtension> = emptyMap()
}
