package app.spur

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpurBackupStorageTest {
    @Test
    fun driveBackupWithoutWifiWaits() {
        assertTrue(
            backupNeedsWifi(
                isGoogleDriveDestination = true,
                wifiConnected = false,
            ),
        )
    }

    @Test
    fun driveBackupWithWifiContinues() {
        assertFalse(
            backupNeedsWifi(
                isGoogleDriveDestination = true,
                wifiConnected = true,
            ),
        )
    }

    @Test
    fun localBackupDoesNotRequireWifi() {
        assertFalse(
            backupNeedsWifi(
                isGoogleDriveDestination = false,
                wifiConnected = false,
            ),
        )
    }
}
