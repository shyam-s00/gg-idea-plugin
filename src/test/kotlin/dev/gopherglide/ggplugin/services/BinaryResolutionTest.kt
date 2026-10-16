package dev.gopherglide.ggplugin.services

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File
import java.nio.file.Files

class BinaryResolutionTest {
    private val root: File = Files.createTempDirectory("gg-resolve").toFile()

    @After
    fun cleanUp() {
        root.deleteRecursively()
    }

    private fun executable(vararg parts: String): File =
        File(root, parts.joinToString("/")).apply {
            parentFile.mkdirs()
            writeText("#!/bin/sh\n")
            setExecutable(true)
        }

    private fun resolve(custom: String = "", managed: File, vararg pathDirs: File) =
        BinaryManager.resolveBinary(custom, managed, pathDirs.map { it.absolutePath }, "gg")

    @Test
    fun pluginManagedCopyOutranksOneOnPath() {
        val managed = executable("managed", "gg")
        val onPath = executable("bin", "gg")
        assertEquals(managed.absolutePath, resolve(managed = managed, pathDirs = arrayOf(onPath.parentFile)))
    }

    @Test
    fun pathIsUsedWhenThereIsNoManagedCopy() {
        val onPath = executable("bin", "gg")
        assertEquals(onPath.absolutePath, resolve(managed = File(root, "managed/gg"), pathDirs = arrayOf(onPath.parentFile)))
    }

    @Test
    fun aUsableCustomPathOutranksEverything() {
        val custom = executable("mine", "gg")
        val managed = executable("managed", "gg")
        assertEquals(custom.absolutePath, resolve(custom.absolutePath, managed))
    }

    @Test
    fun anUnusableCustomPathFallsBackToTheManagedCopy() {
        val managed = executable("managed", "gg")
        assertEquals(managed.absolutePath, resolve(File(root, "missing/gg").absolutePath, managed))
    }

    @Test
    fun nothingFoundMeansNull() {
        assertNull(resolve(managed = File(root, "managed/gg"), pathDirs = arrayOf(File(root, "empty"))))
    }
}
