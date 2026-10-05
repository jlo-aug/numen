package it.palsoftware.pastiera.data.layout

import it.palsoftware.pastiera.data.layout.LayoutFileStore.LayoutImportError
import it.palsoftware.pastiera.data.layout.LayoutFileStore.LayoutImportResult
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.json.JSONObject
import java.io.File
import java.nio.file.Files

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class LayoutFileStoreImportTest {
    private val context get() = RuntimeEnvironment.getApplication()

    @After
    fun tearDown() {
        LayoutFileStore.getLayoutsDirectory(context).deleteRecursively()
    }

    @Test
    fun validSupportedMapping_isSaved() {
        val result = import(
            """{"name":"Русский","mappings":{"KEYCODE_Q":{"lowercase":"й","uppercase":"Й"}}}"""
        )

        assertEquals(LayoutImportResult.Success("Русский"), result)
        assertTrue(LayoutFileStore.getLayoutFile(context, "Русский").exists())
    }

    @Test
    fun malformedJson_isRejectedWithoutMutation() {
        assertRejectedWithoutMutation("{", LayoutImportError.MALFORMED_JSON)
    }

    @Test
    fun missingMappings_isRejectedWithoutMutation() {
        assertRejectedWithoutMutation("""{"name":"Missing"}""", LayoutImportError.MISSING_MAPPINGS)
    }

    @Test
    fun nonObjectMappings_isRejectedWithoutMutation() {
        assertRejectedWithoutMutation("""{"mappings":[]}""", LayoutImportError.MAPPINGS_NOT_OBJECT)
    }

    @Test
    fun emptyMappings_isRejectedWithoutMutation() {
        assertRejectedWithoutMutation("""{"mappings":{}}""", LayoutImportError.EMPTY_MAPPINGS)
    }

    @Test
    fun onlyUnknownKeyCodes_areRejectedWithoutMutation() {
        assertRejectedWithoutMutation(
            """{"mappings":{"KEYCODE_FUTURE":{"lowercase":"x","uppercase":"X"}}}""",
            LayoutImportError.NO_SUPPORTED_MAPPINGS
        )
    }

    @Test
    fun incompleteSupportedMapping_isRejectedWithoutMutation() {
        assertRejectedWithoutMutation(
            """{"mappings":{"KEYCODE_Q":{"lowercase":"й"}}}""",
            LayoutImportError.INVALID_MAPPING
        )
    }

    @Test
    fun invalidSupportedMapping_rejectsWholeFileEvenAlongsideValidMapping() {
        assertRejectedWithoutMutation(
            """{"mappings":{"KEYCODE_Q":{"lowercase":"й","uppercase":"Й"},"KEYCODE_W":{"lowercase":"ц","uppercase":3}}}""",
            LayoutImportError.INVALID_MAPPING
        )
    }

    @Test
    fun enabledMultiTap_requiresTwoUsableTaps() {
        assertRejectedWithoutMutation(
            """{"mappings":{"KEYCODE_Q":{"lowercase":"й","uppercase":"Й","multiTapEnabled":true,"taps":[{"lowercase":"й","uppercase":"Й"}]}}}""",
            LayoutImportError.INVALID_MAPPING
        )
    }

    @Test
    fun bundledEmptyTapPlaceholders_areIgnored() {
        val result = import(
            """{"mappings":{"KEYCODE_Q":{"lowercase":"я","uppercase":"Я","multiTapEnabled":true,"taps":[{"lowercase":"я","uppercase":"Я"},{"lowercase":"э","uppercase":"Э"},{"lowercase":"","uppercase":""}]}}}"""
        )

        assertTrue(result is LayoutImportResult.Success)
        val loaded = LayoutFileStore.loadLayoutFromFile(LayoutFileStore.getLayoutFile(context, "Русский"))
        assertEquals(2, loaded?.get(android.view.KeyEvent.KEYCODE_Q)?.taps?.size)
    }

    @Test
    fun displayNameWithPathSegments_isStoredUnderContainedOpaqueId() {
        val displayName = "../../Русский/абсолютный"

        val result = LayoutFileStore.saveLayoutFromJson(context, displayName, validJson("ф", "Ф"))

        assertEquals(LayoutImportResult.Success(displayName), result)
        val root = LayoutFileStore.getLayoutsDirectory(context).canonicalFile
        val stored = LayoutFileStore.getLayoutFile(context, displayName).canonicalFile
        assertEquals(root, stored.parentFile)
        assertTrue(stored.name.matches(Regex("custom-[0-9a-f]{64}\\.json")))
        assertFalse(stored.name.contains("Русский"))
        assertEquals(displayName, JSONObject(stored.readText()).getString("layout_id"))
        assertTrue(LayoutFileStore.getCustomLayoutNames(context).contains(displayName))
    }

    @Test
    fun absoluteDisplayName_neverBecomesAbsoluteStoragePath() {
        val displayName = "/tmp/issue273-layout"

        val result = LayoutFileStore.saveLayoutFromJson(context, displayName, validJson("а", "А"))

        assertTrue(result is LayoutImportResult.Success)
        val stored = LayoutFileStore.getLayoutFile(context, displayName).canonicalFile
        assertEquals(LayoutFileStore.getLayoutsDirectory(context).canonicalFile, stored.parentFile)
        assertNotEquals(File("$displayName.json").absoluteFile, stored)
    }

    @Test
    fun controlCharacterInName_isRejectedWithoutCreatingAFile() {
        val result = LayoutFileStore.saveLayoutFromJson(context, "unsafe\u0000name", validJson("x", "X"))

        assertEquals(LayoutImportError.INVALID_NAME, (result as LayoutImportResult.Failure).error)
        assertTrue(LayoutFileStore.getLayoutsDirectory(context).listFiles().orEmpty().isEmpty())
    }

    @Test
    fun duplicateName_isRejectedWithoutOverwritingExistingLayout() {
        val first = LayoutFileStore.saveLayoutFromJson(context, "Duplicate", validJson("a", "A"))
        val stored = LayoutFileStore.getLayoutFile(context, "Duplicate")
        val before = stored.readBytes()

        val second = LayoutFileStore.saveLayoutFromJson(context, "Duplicate", validJson("b", "B"))

        assertTrue(first is LayoutImportResult.Success)
        assertEquals(LayoutImportError.NAME_CONFLICT, (second as LayoutImportResult.Failure).error)
        assertArrayEquals(before, stored.readBytes())
    }

    @Test
    fun explicitReplacement_updatesOneContainedFile() {
        LayoutFileStore.saveLayoutFromJson(context, "Cloud layout", validJson("a", "A"))

        val replacement = LayoutFileStore.saveLayoutFromJson(
            context = context,
            layoutName = "Cloud layout",
            jsonString = validJson("b", "B"),
            conflictPolicy = LayoutFileStore.LayoutConflictPolicy.REPLACE
        )

        assertTrue(replacement is LayoutImportResult.Success)
        assertEquals(1, LayoutFileStore.getLayoutsDirectory(context).listFiles().orEmpty().size)
        val loaded = LayoutFileStore.loadLayoutFromFile(LayoutFileStore.getLayoutFile(context, "Cloud layout"))
        assertEquals("b", loaded?.get(android.view.KeyEvent.KEYCODE_Q)?.lowercase)
    }

    @Test
    fun canonicallyEquivalentUnicodeNames_conflictOnTheSameStorageId() {
        assertTrue(
            LayoutFileStore.saveLayoutFromJson(context, "Café", validJson("a", "A"))
                is LayoutImportResult.Success
        )

        val duplicate = LayoutFileStore.saveLayoutFromJson(context, "Cafe\u0301", validJson("b", "B"))

        assertEquals(LayoutImportError.NAME_CONFLICT, (duplicate as LayoutImportResult.Failure).error)
        assertEquals(1, LayoutFileStore.getLayoutsDirectory(context).listFiles().orEmpty().size)
    }

    @Test
    fun legacyNameCollidingWithMigratedCanonicalSafeId_remainsIndependentlyReplaceable() {
        val root = LayoutFileStore.getLayoutsDirectory(context)
        val composedName = "Café"
        val decomposedName = "Cafe\u0301"
        assertTrue(
            LayoutFileStore.saveLayoutFromJson(context, composedName, validJson("a", "A"))
                is LayoutImportResult.Success
        )
        val decomposedLegacy = File(root, "legacy-collision-source.json").apply {
            writeText(validJson("b", "B"))
        }

        val migrated = LayoutFileStore.migrateLegacyLayoutFile(
            context = context,
            layoutName = decomposedName,
            legacyFile = decomposedLegacy
        )

        assertTrue(migrated.exists())
        assertFalse(decomposedLegacy.exists())
        assertEquals(2, root.listFiles().orEmpty().count { it.name.matches(Regex("custom-[0-9a-f]{64}\\.json")) })
        assertTrue(LayoutFileStore.getCustomLayoutNames(context).containsAll(listOf(composedName, decomposedName)))
        assertEquals(
            "a",
            LayoutFileStore.loadLayoutFromFile(
                LayoutFileStore.getLayoutFile(context, composedName)
            )?.get(android.view.KeyEvent.KEYCODE_Q)?.lowercase
        )
        assertEquals(
            "b",
            LayoutFileStore.loadLayoutFromFile(
                LayoutFileStore.getLayoutFile(context, decomposedName)
            )?.get(android.view.KeyEvent.KEYCODE_Q)?.lowercase
        )

        val replacement = LayoutFileStore.saveLayoutFromJson(
            context = context,
            layoutName = decomposedName,
            jsonString = validJson("c", "C"),
            conflictPolicy = LayoutFileStore.LayoutConflictPolicy.REPLACE
        )

        assertTrue(replacement is LayoutImportResult.Success)
        assertEquals(
            "a",
            LayoutFileStore.loadLayoutFromFile(
                LayoutFileStore.getLayoutFile(context, composedName)
            )?.get(android.view.KeyEvent.KEYCODE_Q)?.lowercase
        )
        assertEquals(
            "c",
            LayoutFileStore.loadLayoutFromFile(
                LayoutFileStore.getLayoutFile(context, decomposedName)
            )?.get(android.view.KeyEvent.KEYCODE_Q)?.lowercase
        )
    }

    @Test
    fun legacyNameBasedFile_isMigratedWithoutBreakingLogicalName() {
        val root = LayoutFileStore.getLayoutsDirectory(context)
        val legacy = File(root, "Русский legacy.json")
        legacy.writeText("""{"name":"Русский legacy","mappings":{"KEYCODE_Q":{"lowercase":"й","uppercase":"Й"}}}""")

        val resolved = LayoutFileStore.getLayoutFile(context, "Русский legacy")

        assertTrue(resolved.exists())
        assertTrue(resolved.name.matches(Regex("custom-[0-9a-f]{64}\\.json")))
        assertFalse(legacy.exists())
        assertTrue(LayoutFileStore.getCustomLayoutNames(context).contains("Русский legacy"))
        assertEquals(
            "й",
            LayoutFileStore.loadLayoutFromFile(resolved)?.get(android.view.KeyEvent.KEYCODE_Q)?.lowercase
        )
    }

    @Test
    fun malformedLegacyFile_isPreservedWhenMigrationCannotBeCompleted() {
        val legacy = File(LayoutFileStore.getLayoutsDirectory(context), "Do not lose.json")
        legacy.writeText("not-json")

        val resolved = LayoutFileStore.getLayoutFile(context, "Do not lose")

        assertEquals(legacy.canonicalFile, resolved.canonicalFile)
        assertTrue(legacy.exists())
        assertEquals("not-json", legacy.readText())
    }

    @Test
    fun failedAtomicMove_preservesExistingFileAndRemovesTemporaryFile() {
        val root = LayoutFileStore.getLayoutsDirectory(context)
        val target = File(root, "custom-${"a".repeat(64)}.json")
        target.writeText("old")

        val failure = runCatching {
            LayoutFileStore.writeAtomically(target, "new".toByteArray()) { _, _ ->
                throw IllegalStateException("synthetic move failure")
            }
        }.exceptionOrNull()

        assertTrue(failure is IllegalStateException)
        assertEquals("old", target.readText())
        assertTrue(root.listFiles().orEmpty().none { it.name.endsWith(".tmp") })
    }

    @Test
    fun symlinkOutsideLayoutRoot_isNeitherResolvedNorListed() {
        val root = LayoutFileStore.getLayoutsDirectory(context)
        val outside = File(context.filesDir, "outside-layout.json").apply { writeText(validJson("x", "X")) }
        val link = File(root, "legacy-link.json")
        Files.createSymbolicLink(link.toPath(), outside.toPath())

        val resolved = LayoutFileStore.getLayoutFile(context, "legacy-link")

        assertEquals(root.canonicalFile, resolved.parentFile)
        assertNotEquals(outside.canonicalFile, resolved.canonicalFile)
        assertFalse(LayoutFileStore.getCustomLayoutNames(context).contains("legacy-link"))
        outside.delete()
    }

    private fun assertRejectedWithoutMutation(json: String, expected: LayoutImportError) {
        val existing = LayoutFileStore.getLayoutFile(context, "Русский")
        existing.writeText("existing-layout")
        val before = existing.readBytes()

        val result = import(json)

        assertTrue(result is LayoutImportResult.Failure)
        assertEquals(expected, (result as LayoutImportResult.Failure).error)
        assertArrayEquals(before, existing.readBytes())
        assertFalse(LayoutFileStore.getLayoutFile(context, "Unexpected").exists())
    }

    private fun import(json: String): LayoutImportResult =
        LayoutFileStore.saveLayoutFromJson(context, "Русский", json)

    private fun validJson(lowercase: String, uppercase: String): String =
        """{"name":"Fixture","mappings":{"KEYCODE_Q":{"lowercase":"$lowercase","uppercase":"$uppercase"}}}"""
}
