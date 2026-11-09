package app.chatra

import android.app.Application
import app.chatra.di.AppContainer
import app.chatra.di.createContainer

class ChatraApplication : Application() {
    private var instance: AppContainer? = null

    /** Kontener powstaje przy pierwszym ekranie, bo tryb referencyjny przychodzi w intencji. */
    fun container(referenceFrame: Boolean, roleScenario: String? = null, exactAlarms: Boolean = true): AppContainer =
        instance ?: createContainer(this, referenceFrame, roleScenario, exactAlarms).also { instance = it }
}
