package app.spur

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeAutoStartPermissionTest {
    @Test
    fun enablementRequestsPermissionsInOrder() {
        assertEquals(
            HomeAutoStartEnablementStep.REQUEST_NOTIFICATION_PERMISSION,
            nextHomeAutoStartEnablementStep(
                hasNotificationPermission = false,
                hasActivityPermission = false,
            ),
        )
        assertEquals(
            HomeAutoStartEnablementStep.REQUEST_ACTIVITY_PERMISSION,
            nextHomeAutoStartEnablementStep(
                hasNotificationPermission = true,
                hasActivityPermission = false,
            ),
        )
        assertEquals(
            HomeAutoStartEnablementStep.ENABLE,
            nextHomeAutoStartEnablementStep(
                hasNotificationPermission = true,
                hasActivityPermission = true,
            ),
        )
    }
}
