package com.omarchy.auto

import androidx.car.app.navigation.model.NavigationTemplate
import androidx.car.app.testing.TestCarContext
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OmarchyScreenTest {

    @Test
    fun onGetTemplate_buildsNavigationTemplateWithActionStrip() {
        val carContext = TestCarContext.createCarContext(ApplicationProvider.getApplicationContext())
        val template = OmarchyScreen(carContext).onGetTemplate() as NavigationTemplate
        assertNotNull(template.actionStrip)
    }
}
