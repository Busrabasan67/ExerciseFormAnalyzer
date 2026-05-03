package com.example.exerciseformanalyzer.data.remote

import com.example.exerciseformanalyzer.model.firestore.FsGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CommunityFirestoreServiceTest {

    @Test
    fun resolveFirestoreGroup_usesIsPrivateFieldWhenPresent() {
        val group = resolveFirestoreGroup(
            group = FsGroup(groupId = "group-1", isPrivate = false),
            documentId = "doc-1",
            isPrivateField = true,
            legacyPrivateField = false
        )

        assertTrue(group.isPrivate)
        assertEquals("group-1", group.groupId)
    }

    @Test
    fun resolveFirestoreGroup_supportsLegacyPrivateField() {
        val group = resolveFirestoreGroup(
            group = FsGroup(groupId = "", isPrivate = false),
            documentId = "doc-2",
            isPrivateField = null,
            legacyPrivateField = true
        )

        assertTrue(group.isPrivate)
        assertEquals("doc-2", group.groupId)
    }

    @Test
    fun resolveFirestoreGroup_defaultsToPublicWhenPrivacyFieldsAreMissing() {
        val group = resolveFirestoreGroup(
            group = FsGroup(groupId = "", isPrivate = false),
            documentId = "doc-3",
            isPrivateField = null,
            legacyPrivateField = null
        )

        assertFalse(group.isPrivate)
        assertEquals("doc-3", group.groupId)
    }
}
