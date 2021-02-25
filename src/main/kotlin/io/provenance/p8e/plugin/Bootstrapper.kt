package io.provenance.p8e.plugin

import arrow.core.valid
import io.p8e.ContractManager
import io.p8e.proto.Common.ProvenanceReference
import io.p8e.proto.PK
import io.p8e.spec.ContractSpecMapper
import io.provenance.core.encryption.ecies.ECUtils
import io.provenance.core.encryption.util.ByteUtil
import org.bouncycastle.jcajce.provider.asymmetric.ec.BCECPrivateKey
import org.bouncycastle.jcajce.provider.asymmetric.ec.BCECPublicKey
import org.bouncycastle.jce.ECNamedCurveTable
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.jce.spec.ECPrivateKeySpec
import org.bouncycastle.jce.spec.ECPublicKeySpec
import org.bouncycastle.util.encoders.Hex
import org.gradle.api.Project
import org.gradle.jvm.tasks.Jar
import org.reflections.Reflections
import org.reflections.scanners.SubTypesScanner
import java.io.File
import java.io.FileInputStream
import java.net.URLClassLoader
import java.security.KeyFactory
import java.security.KeyPair
import java.security.PublicKey
import java.security.Security
import java.util.jar.JarFile
import kotlin.system.exitProcess

internal class Bootstrapper(
    private val project: Project,
    val config: P8eConfiguration
) {

    init {
        Security.addProvider(BouncyCastleProvider())
    }

    private fun validate() {
        // TODO validate configuration is good enough to publish
        require (project.hasProperty(CONTRACT_HASH_PACKAGE)) {
            "Project does not have a \"$CONTRACT_HASH_PACKAGE\" property set"
        }

        require (project.hasProperty(PROTO_HASH_PACKAGE)) {
            "Project does not have a \"$PROTO_HASH_PACKAGE\" property set"
        }
    }

    @Synchronized
    fun execute() {
        validate()

        val hashes = mutableMapOf<String, String>()
        val contractKey = "contractKey"
        val protoKey = "protoKey"
        val contractProject = getProject(project, "contractProject")
        val protoProject = getProject(project, "protoProject")
        val contractJar = getJar(contractProject)
        val protoJar = getJar(protoProject)
        val contractClassLoader = URLClassLoader(arrayOf(contractJar.toURI().toURL()), javaClass.classLoader)
        val contracts = findContracts(contractClassLoader)
        val protos = findProtos(contractClassLoader)

        config.locations.forEach { location ->
            project.logger.info("Publishing contracts - location: ${location.name} url: ${location.url}")

            val manager = ContractManager.create(getKeyPair(location.privateKey), location.url)
            val contractJarLocation = storeObject(manager, contractJar, location)
                .also {
                    require (it.hash == hashes.getOrDefault(contractKey, it.hash)) {
                        "Received different hash for the same contract jar ${it.hash} ${hashes.getValue(contractKey)}"
                    }

                    hashes.put(contractKey, it.hash)
                }
            val protoJarLocation = storeObject(manager, protoJar, location)
                .also {
                    require (it.hash == hashes.getOrDefault(protoKey, it.hash)) {
                        "Received different hash for the same proto jar ${it.hash} ${hashes.getValue(protoKey)}"
                    }

                    hashes.put(protoKey, it.hash)
                }

            contracts
                .map { clazz -> ContractSpecMapper.dehydrateSpec(clazz.kotlin, contractJarLocation, protoJarLocation) }
                .takeUnless { it.isNullOrEmpty() }
                .also { specs -> project.logger.info("Saving ${specs?.size ?: 0} contract specifications") }
                ?.let { specs -> manager.client.addSpec(specs) }
                ?: throw IllegalStateException("Could not find any subclasses of io.p8e.spec.P8eContract in ${contractJar.path}")
        }

        project.logger.info("Writing services providers")

        val currentTimeMillis = System.currentTimeMillis().toString()
        ServiceProvider.writeContractHash(contractProject, currentTimeMillis, contracts, hashes.getValue(contractKey))
        ServiceProvider.writeProtoHash(protoProject, currentTimeMillis, protos, hashes.getValue(protoKey))
    }

    fun getJar(project: Project): File {
        return (project.tasks.getByName("jar") as Jar)
            .archiveFile
            .orNull
            ?.asFile
            ?.also(::verifyJar)
            ?: throw IllegalStateException("task :jar in ${project.name} could not be found")
    }

    fun storeObject(manager: ContractManager, jar: File, location: P8eLocation): ProvenanceReference {
        return manager.client.storeObject(FileInputStream(jar), location.audience.map { it.toPublicKey() }.toSet())
            .ref
            .also { project.logger.info("Saved jar ${jar.path} with hash ${it.hash}") }
    }

    fun findContracts(classLoader: ClassLoader): Set<Class<out io.p8e.spec.P8eContract>> =
        findClasses(io.p8e.spec.P8eContract::class.java, classLoader)

    fun findProtos(classLoader: ClassLoader): Set<Class<out com.google.protobuf.Message>> =
        findClasses(com.google.protobuf.Message::class.java, classLoader)

    fun<T> findClasses(clazz: Class<T>, classLoader: ClassLoader): Set<Class<out T>> =
        Reflections("io", "com", SubTypesScanner(false), classLoader)
            .getSubTypesOf(clazz)

    fun verifyJar(jar: File) {
        try {
            JarFile(jar)
        } catch (e: Exception) {
            project.logger.error("Could not verify ${jar.absolutePath} as a valid jar file.", e)

            exitProcess(0)
        }
    }

    fun getKeyPair(privateKey: String): KeyPair {
        // compute private key from string
        val protoPrivateKey = PK.PrivateKey.parseFrom(Hex.decode(privateKey))
        val keyFactory = KeyFactory.getInstance("ECDH", "BC")
        val ecSpec = ECNamedCurveTable.getParameterSpec(ECUtils.LEGACY_DIME_CURVE)
        val privateKeySpec = ECPrivateKeySpec(
            ByteUtil.unsignedBytesToBigInt(protoPrivateKey.keyBytes.toByteArray()),
            ecSpec
        )
        val typedPrivateKey = BCECPrivateKey(keyFactory.algorithm, privateKeySpec, BouncyCastleProvider.CONFIGURATION)

        // compute public key from private key
        val point = ecSpec.g.multiply(typedPrivateKey.d)
        val publicKeySpec = ECPublicKeySpec(point, ecSpec)
        val publicKey = BCECPublicKey(keyFactory.algorithm, publicKeySpec, BouncyCastleProvider.CONFIGURATION)

        return KeyPair(publicKey, typedPrivateKey)
    }

    fun PartyConfiguration.toPublicKey(): PublicKey {
        val protoPublicKey = PK.PublicKey.parseFrom(Hex.decode(this.publicKey))
        val keyFactory = KeyFactory.getInstance("ECDH", "BC")
        val ecSpec = ECNamedCurveTable.getParameterSpec(ECUtils.LEGACY_DIME_CURVE)
        val point = ecSpec.curve.decodePoint(protoPublicKey.publicKeyBytes.toByteArray())
        val publicKeySpec = ECPublicKeySpec(point, ecSpec)

        return BCECPublicKey(keyFactory.algorithm, publicKeySpec, BouncyCastleProvider.CONFIGURATION)
    }
}
