package app.gamenative.utils.launchdependencies

import android.content.Context
import app.gamenative.data.GameSource
import app.gamenative.service.gog.GOGService
import app.gamenative.utils.LaunchSteps
import com.winlator.container.Container

/** Runs the GOG scriptinterpreter.exe when required by the game manifest. (Currently only compatible with GLIBC) */
object GogScriptInterpreterLaunchStep : LaunchStep {
    override val runOnce: Boolean = true

    override fun appliesTo(container: Container, appId: String, gameSource: GameSource): Boolean =
        gameSource == GameSource.GOG && container.containerVariant.equals(Container.GLIBC)

    override fun run(
        context: Context,
        appId: String,
        container: Container,
        stepRunner: StepRunner,
        gameSource: GameSource,
    ): Boolean {
        val parts = GOGService.getInstance()?.gogManager?.getScriptInterpreterPartsForLaunch(appId) ?: return false
        val content = if (parts.isEmpty()) null else parts.joinToString(" & ")
        if (content.isNullOrBlank()) return false
        val wrapped = LaunchSteps.wrapInWinHandler(content)
        stepRunner.runStepContent(wrapped)
        return true
    }
}
