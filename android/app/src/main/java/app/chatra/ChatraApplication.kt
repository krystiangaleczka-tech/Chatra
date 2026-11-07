package app.chatra

import android.app.Application
import app.chatra.di.AppContainer
import app.chatra.di.createContainer

class ChatraApplication : Application() {
    private var instance: AppContainer? = null

    /** Kontener powstaje przy pierwszym ekranie, bo tryb referencyjny przychodzi w intencji. */
    fun container(referenceFrame: Boolean, roleScenario: String? = null): AppContainer =
        instance ?: createContainer(this, referenceFrame, roleScenario).also { instance = it }
}
