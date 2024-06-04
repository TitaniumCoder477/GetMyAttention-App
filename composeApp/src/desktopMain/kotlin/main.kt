
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import getmyattention_app.composeapp.generated.resources.Res
import getmyattention_app.composeapp.generated.resources.icon2
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.painterResource

@OptIn(ExperimentalResourceApi::class)
fun main() = application {
    val logger = KotlinLogging.logger {}
    val state = rememberWindowState(
        size = DpSize(412.dp, 892.dp),
        position = WindowPosition(300.dp, 300.dp)
    )
    Window(
        onCloseRequest = ::exitApplication,
        title = "GetMyAttention App",
        state = state,
        icon = painterResource(Res.drawable.icon2)
    ) {
        logger.info { "Test" }
        App()
    }
}