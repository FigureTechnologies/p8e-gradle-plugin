package io.provenance.p8e.plugin

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.WordSpec
import io.kotest.core.test.TestCaseOrder
import io.kotest.matchers.should
import io.kotest.matchers.string.shouldContain
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.gradle.testkit.runner.UnexpectedBuildFailure
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

class ContractPluginIntegrationTest : WordSpec() {

    override fun testCaseOrder() = TestCaseOrder.Sequential
    override fun isolationMode() = IsolationMode.SingleInstance

    fun run(projectDir: File, task: String) = try {
        GradleRunner.create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withArguments(task, "--info", "--stacktrace")
            .build()
    } catch (e: UnexpectedBuildFailure) {
        e.buildResult
    }

    fun p8eClean(projectDir: File) = run(projectDir, "p8eClean")
    fun p8eJar(projectDir: File) = run(projectDir, "p8eJar")
    fun p8eBootstrap(projectDir: File) = run(projectDir, "p8eBootstrap")

    init {
        "A configured DSL buildscript" should {

            "Lead to a successful bootstrap with saved specs" {
                val projectDir = File("example")
                val result = p8eBootstrap(projectDir)

                result.task("p8eBootstrap") should {
                    it != null && it.outcome == TaskOutcome.SUCCESS
                }

                // TODO test contracthash and protohash sources exists
                // TODO check service meta-inf exists c + p
                // TODO check uber jar was built

                result.output.shouldContain("Saved jar")
                result.output.shouldContain("Saving 2 contract specifications")
            }

            "Lead to a successful publish" {
                val projectDir = File("example")
                val result = p8eJar(projectDir)

                result.task("p8eJar") should {
                    it != null && it.outcome == TaskOutcome.SUCCESS
                }

                // TODO verify contract and proto jar exists

            }

            "Lead to a successful clean" {
                val projectDir = File("example")

                // TODO verify contracthash and protohash source exist
                // TODO verify service meta-inf exists c + p

                val result = p8eClean(projectDir)

                result.task("p8eClean") should {
                    it != null && it.outcome == TaskOutcome.SUCCESS
                }

                // TODO verify contracthash and protohash do not exist
                // TODO verify  service meta-inf doesn't exist c + p

            }
        }
    }
}
