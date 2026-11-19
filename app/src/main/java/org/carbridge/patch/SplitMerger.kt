/*
 * Ported from Android Auto Maker (https://github.com/malebuffy/AndroidAutoMaker, commit 9b83522),
 * engine/PackageConverter.kt (mergeBundle, mergeFeatureManifests, stripSplitMetadata,
 * repairResourceTable), GPL-3.0. Used in AA Installer under the same licence.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package org.carbridge.patch

import com.reandroid.apk.APKLogger
import com.reandroid.apk.ApkBundle
import com.reandroid.apk.ApkModule
import com.reandroid.app.AndroidManifest
import com.reandroid.archive.ZipEntryMap
import com.reandroid.arsc.chunk.PackageBlock
import com.reandroid.arsc.chunk.TypeBlock
import com.reandroid.arsc.chunk.xml.AndroidManifestBlock
import com.reandroid.arsc.chunk.xml.ResXmlAttribute
import com.reandroid.arsc.chunk.xml.ResXmlElement
import com.reandroid.arsc.container.SpecTypePair
import java.io.File
import java.io.IOException

/** Merges a base APK and its split APKs into one standalone, unsigned APK. */
object SplitMerger {

    private val ROOT_TAGS = setOf("uses-permission", "uses-permission-sdk-23", "uses-feature", "permission")
    private val APPLICATION_TAGS = setOf("activity", "activity-alias", "service", "receiver", "provider", "meta-data", "uses-library")
    private val SPLIT_META = listOf(
        "com.android.vending.splits.required",
        "com.android.vending.splits",
        "com.android.vending.derived.apk.id",
    )

    /** Writes the merged APK to [output]. [apks] = base + splits, in any order. */
    fun merge(apks: List<File>, work: File, output: File, log: (String) -> Unit) {
        val dir = File(work, "merge-in").apply { deleteRecursively(); mkdirs() }
        apks.forEachIndexed { i, f -> f.copyTo(File(dir, "${i}_${f.name.replace(Regex("[^A-Za-z0-9._-]"), "_")}"), true) }
        try {
            ApkBundle().use { bundle ->
                bundle.loadApkDirectory(dir)
                mergeBundle(bundle, log).use { module ->
                    mergeFeatureManifests(bundle, module)
                    stripSplitMetadata(module)
                    repairResourceTable(module)
                    module.apkSignatureBlock = null
                    if (output.exists()) output.delete()
                    module.writeApk(output)
                }
            }
        } finally {
            dir.deleteRecursively()
        }
    }

    private fun mergeBundle(bundle: ApkBundle, log: (String) -> Unit): ApkModule {
        val modules = bundle.apkModuleList
        if (modules.isEmpty()) throw IOException("Nothing to merge")
        val base = bundle.baseModule ?: modules.maxByOrNull { it.tableBlock?.headerBlock?.chunkSize ?: 0 } ?: modules.first()
        val ordered = listOf(base) + modules.filter { it !== base }
        val merged = ApkModule("merged", ZipEntryMap())
        merged.setLoadDefaultFramework(false)
        merged.setAPKLogger(object : APKLogger {
            override fun logMessage(message: String?) {}
            override fun logError(message: String?, error: Throwable?) {}
            override fun logVerbose(message: String?) {}
        })
        for ((i, module) in ordered.withIndex()) {
            log("Merging ${module.moduleName?.takeIf { it.isNotBlank() } ?: "split ${i + 1}"}")
            merged.merge(module, false)
        }
        if (merged.hasTableBlock()) {
            merged.tableBlock?.sortPackages()
            merged.tableBlock?.refresh()
        }
        merged.zipEntryMap.autoSortApkFiles()
        return merged
    }

    private fun mergeFeatureManifests(bundle: ApkBundle, merged: ApkModule) {
        val target = merged.androidManifest ?: return
        val root = target.manifestElement ?: return
        val application = target.applicationElement ?: return
        merged.refreshManifest()
        for (module in bundle.modules) {
            module.refreshManifest()
            val source = module.androidManifest ?: continue
            val sourceRoot = source.manifestElement ?: continue
            copyNamedChildren(root, sourceRoot, ROOT_TAGS)
            source.applicationElement?.let { copyNamedChildren(application, it, APPLICATION_TAGS) }
        }
        target.refresh()
    }

    private fun copyNamedChildren(destination: ResXmlElement, source: ResXmlElement, tags: Set<String>) {
        val iterator = source.elements
        while (iterator.hasNext()) {
            val element = iterator.next() as? ResXmlElement ?: continue
            if (element.name !in tags || isDistributionElement(element)) continue
            val name = AndroidManifestBlock.getAndroidNameValue(element) ?: continue
            if (hasNamedChild(destination, element.name, name)) continue
            val copy = destination.newElement(element.name)
            try {
                copyElement(copy, element)
            } catch (error: IOException) {
                copy.parentElement?.removeElementsIf { it === copy }
            }
        }
    }

    private fun copyElement(destination: ResXmlElement, source: ResXmlElement) {
        destination.setName(source.name)
        if (source.uri != null || source.prefix != null) destination.setNamespace(source.uri, source.prefix)
        val attributes = source.attributes
        while (attributes.hasNext()) copyAttribute(destination, attributes.next())
        val children = source.elements
        while (children.hasNext()) {
            val child = children.next() as? ResXmlElement ?: continue
            if (isDistributionElement(child)) continue
            copyElement(destination.newElement(child.name), child)
        }
    }

    private fun copyAttribute(destination: ResXmlElement, attribute: ResXmlAttribute) {
        val rawName = attribute.name
        val name = if (rawName.isNullOrEmpty() || rawName.startsWith("r0x")) attribute.decodeName(false) else rawName
        if (name.isNullOrEmpty()) return
        val type = attribute.valueType
        val stringValue = type == null || type.name == "STRING"
        val value = if (stringValue) safeValue(attribute) else null
        if (stringValue && value.isNullOrEmpty()) return
        val created = destination.newAttribute()
        created.setName(name, attribute.nameId)
        if (attribute.uri != null) created.setNamespace(attribute.uri, attribute.prefix)
        try {
            if (type?.name == "STRING") {
                created.setValueAsString(attribute.valueAsString ?: throw IOException("Split manifest attribute has no value"))
            } else if (type != null) {
                created.valueType = type
                created.data = attribute.data
            } else {
                created.setValueAsString(value)
            }
        } catch (error: RuntimeException) {
            destination.removeAttribute(created)
            if (name == "name") throw IOException("Split manifest attribute could not be copied", error)
        }
    }

    private fun safeValue(attribute: ResXmlAttribute): String? {
        try {
            val decoded = attribute.decodeValue(true)
            if (!decoded.isNullOrEmpty()) return decoded
        } catch (_: RuntimeException) {
        }
        return try {
            attribute.valueAsString
        } catch (_: RuntimeException) {
            null
        }
    }

    private fun isDistributionElement(element: ResXmlElement) =
        element.uri?.contains("schemas.android.com/apk/distribution") == true

    private fun hasNamedChild(parent: ResXmlElement, tag: String, name: String): Boolean {
        val iterator = parent.getElements(tag)
        while (iterator.hasNext()) {
            val element = iterator.next() as? ResXmlElement ?: continue
            if (AndroidManifestBlock.getAndroidNameValue(element) == name) return true
        }
        return false
    }

    private fun stripSplitMetadata(apk: ApkModule) {
        val manifest = apk.androidManifest ?: return
        val root = manifest.manifestElement ?: return
        removeSplitAttributes(root)
        manifest.applicationElement?.let(::removeSplitAttributes)
        root.removeElementsIf { it is ResXmlElement && it.name == "uses-split" }
        for (name in SPLIT_META) ParkedExtras.removeMeta(manifest, name)
        manifest.applicationElement?.let(ParkedExtras::markStandalone)
        manifest.refresh()
    }

    private fun removeSplitAttributes(element: ResXmlElement) {
        removeAttribute(element, AndroidManifest.ID_requiredSplitTypes, AndroidManifest.NAME_requiredSplitTypes)
        removeAttribute(element, AndroidManifest.ID_splitTypes, AndroidManifest.NAME_splitTypes)
        removeAttribute(element, AndroidManifest.ID_isSplitRequired, AndroidManifest.NAME_isSplitRequired)
        removeAttribute(element, AndroidManifest.ID_extractNativeLibs, AndroidManifest.NAME_extractNativeLibs)
        element.searchAttributeByName(AndroidManifest.NAME_split)?.let { element.removeAttribute(it) }
        element.searchAttributeByName("isolatedSplits")?.let { element.removeAttribute(it) }
    }

    private fun repairResourceTable(apk: ApkModule) {
        val table = apk.tableBlock ?: return
        val packages = table.iterator()
        while (packages.hasNext()) {
            val pkg = packages.next() as? PackageBlock ?: continue
            val pairs = pkg.specTypePairs
            while (pairs.hasNext()) {
                val pair = pairs.next() as? SpecTypePair ?: continue
                val needed = maxOf(pair.highestEntryCount, pair.highestEntryId + 1)
                if (needed < 1) continue
                val spec = pair.specBlock
                if (spec != null && spec.entryCount < needed) spec.entryCount = needed
                val types = pair.typeBlocks
                while (types.hasNext()) {
                    val type = types.next() as? TypeBlock ?: continue
                    if (type.entryArray.size() < needed) type.ensureEntriesCount(needed)
                }
            }
        }
        table.refreshFull()
    }

    private fun removeAttribute(element: ResXmlElement, id: Int, name: String) {
        val attribute = element.searchAttributeByResourceId(id) ?: element.searchAttributeByName(name)
        if (attribute != null) element.removeAttribute(attribute)
    }
}
