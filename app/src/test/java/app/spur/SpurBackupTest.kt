package app.spur

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpurBackupTest {
    @Test
    fun backupAcceptsOnlyOriginalMomentPathsInsideKnownFolders() {
        assertEquals(
            MomentType.PHOTO,
            backupMediaTypeForPath("moments/photos/photo-123.jpg"),
        )
        assertEquals(
            MomentType.VIDEO,
            backupMediaTypeForPath("moments/videos/video-123.mp4"),
        )
        assertEquals(
            MomentType.VOICE,
            backupMediaTypeForPath("moments/voice/voice-123.m4a"),
        )
        assertNull(backupMediaTypeForPath("../databases/spur.db"))
        assertNull(backupMediaTypeForPath("moments/photos/folder/photo.jpg"))
        assertNull(backupMediaTypeForPath("moments/unknown/file.bin"))
    }

    @Test
    fun backupFileNamesCannotTraverseDirectories() {
        assertTrue(isSafeBackupFileName("photo-123.jpg"))
        assertFalse(isSafeBackupFileName(".."))
        assertFalse(isSafeBackupFileName("../spur.db"))
        assertFalse(isSafeBackupFileName("folder\\spur.db"))
    }
}
