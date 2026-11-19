/*
 * Ported from Android Auto Maker (https://github.com/malebuffy/AndroidAutoMaker, commit 9b83522),
 * engine/ManifestPatcher.kt, "parked game" mode only (removeInternalStamps, markStandalone,
 * ensureAutomotiveFeature, removeDistractionAttributes), GPL-3.0.
 * Used in AA Installer under the same licence.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package org.carbridge.patch

import com.reandroid.app.AndroidManifest
import com.reandroid.arsc.chunk.xml.AndroidManifestBlock
import com.reandroid.arsc.chunk.xml.ResXmlAttribute
import com.reandroid.arsc.chunk.xml.ResXmlElement
import com.reandroid.arsc.value.ValueType

/** Manifest clean-up so a re-signed, parked-only car app installs and lists reliably. */
object ParkedExtras {
    private const val AUTOMOTIVE_FEATURE = "android.hardware.type.automotive"
    private const val ID_REQUIRED = 0x0101028e
    private val INTERNAL_META = listOf(
        "com.android.vending.derived.apk.id",
        "com.android.vending.splits.required",
        "com.android.vending.splits",
        "SPDE.build.signature",
    )

    fun apply(manifest: AndroidManifestBlock, log: (String) -> Unit) {
        if (removeInternalStamps(manifest)) log("Removed Play Store build stamps so the re-signed APK installs")
        if (removeDistractionAttributes(manifest)) log("Removed distraction-optimised attributes: parked use only")
        if (ensureOptionalAutomotiveFeature(manifest)) log("Declared optional automotive hardware")
    }

    fun removeMeta(manifest: AndroidManifestBlock, name: String): Boolean {
        val found = ArrayList<ResXmlElement>()
        val iterator = manifest.recursiveElements()
        while (iterator.hasNext()) {
            val element = iterator.next() as? ResXmlElement ?: continue
            if (element.equalsName(AndroidManifest.TAG_meta_data) && AndroidManifestBlock.getAndroidNameValue(element) == name) {
                found.add(element)
            }
        }
        for (element in found) element.parentElement?.removeElementsIf { it === element }
        return found.isNotEmpty()
    }

    private fun removeInternalStamps(manifest: AndroidManifestBlock): Boolean {
        var removed = false
        for (name in INTERNAL_META) if (removeMeta(manifest, name)) removed = true
        for (element in listOfNotNull(manifest.manifestElement, manifest.applicationElement)) {
            removed = removeAttribute(element, AndroidManifest.ID_requiredSplitTypes, AndroidManifest.NAME_requiredSplitTypes) || removed
            removed = removeAttribute(element, AndroidManifest.ID_isSplitRequired, AndroidManifest.NAME_isSplitRequired) || removed
        }
        return removed
    }

    /** Marks a merged APK as a standalone APK (what bundletool writes for universal APKs). */
    fun markStandalone(application: ResXmlElement): Boolean {
        var changed = setMetaValue(application, "com.android.stamp.type", "STAMP_TYPE_STANDALONE_APK")
        val fused = findMeta(application, "com.android.dynamic.apk.fused.modules")
        if (fused != null) {
            val value = (fused.searchAttributeByResourceId(AndroidManifest.ID_value)?.valueAsString).orEmpty()
            val parts = value.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toMutableList()
            if ("base" !in parts) {
                parts.add(0, "base")
                setMetaValue(application, "com.android.dynamic.apk.fused.modules", parts.joinToString(","))
                changed = true
            }
        }
        return changed
    }

    private fun removeDistractionAttributes(manifest: AndroidManifestBlock): Boolean {
        val elements = ArrayList<ResXmlElement>()
        val iterator = manifest.recursiveElements()
        while (iterator.hasNext()) (iterator.next() as? ResXmlElement)?.let(elements::add)
        var removed = false
        for (element in elements) {
            val attribute = element.searchAttributeByName("distractionOptimized") ?: continue
            if (!isTrue(attribute)) continue
            element.removeAttribute(attribute)
            removed = true
        }
        return removed
    }

    private fun ensureOptionalAutomotiveFeature(manifest: AndroidManifestBlock): Boolean {
        val root = manifest.manifestElement ?: return false
        val features = root.getElements(AndroidManifest.TAG_uses_feature)
        while (features.hasNext()) {
            val f = features.next() as? ResXmlElement ?: continue
            if (AndroidManifestBlock.getAndroidNameValue(f) != AUTOMOTIVE_FEATURE) continue
            val required = f.searchAttributeByResourceId(ID_REQUIRED)
            if (required != null && !isTrue(required)) return false
            f.getOrCreateAndroidAttribute("required", ID_REQUIRED).setValueAsBoolean(false)
            return true
        }
        val application = root.getElement(AndroidManifest.TAG_application)
        val feature = if (application != null) root.newElementAt(application.index, AndroidManifest.TAG_uses_feature)
        else root.newElement(AndroidManifest.TAG_uses_feature)
        feature.getOrCreateAndroidAttribute(AndroidManifest.NAME_name, AndroidManifest.ID_name).setValueAsString(AUTOMOTIVE_FEATURE)
        feature.getOrCreateAndroidAttribute("required", ID_REQUIRED).setValueAsBoolean(false)
        return true
    }

    private fun findMeta(application: ResXmlElement, name: String): ResXmlElement? {
        val iterator = application.recursiveElements()
        while (iterator.hasNext()) {
            val element = iterator.next() as? ResXmlElement ?: continue
            if (element.equalsName(AndroidManifest.TAG_meta_data) && AndroidManifestBlock.getAndroidNameValue(element) == name) return element
        }
        return null
    }

    private fun setMetaValue(application: ResXmlElement, name: String, value: String): Boolean {
        val existing = findMeta(application, name)
        val meta = existing ?: application.newElement(AndroidManifest.TAG_meta_data).also {
            it.getOrCreateAndroidAttribute(AndroidManifest.NAME_name, AndroidManifest.ID_name).setValueAsString(name)
        }
        val attribute = meta.getOrCreateAndroidAttribute("value", AndroidManifest.ID_value)
        val changed = existing == null || attribute.valueAsString != value
        attribute.setValueAsString(value)
        return changed
    }

    private fun removeAttribute(element: ResXmlElement, id: Int, name: String): Boolean {
        val attribute = element.searchAttributeByResourceId(id) ?: element.searchAttributeByName(name) ?: return false
        element.removeAttribute(attribute)
        return true
    }

    private fun isTrue(attribute: ResXmlAttribute): Boolean = when (attribute.valueType) {
        ValueType.BOOLEAN -> attribute.valueAsBoolean
        ValueType.DEC, ValueType.HEX -> attribute.data != 0
        else -> attribute.valueAsString == "true"
    }
}
