
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.Checkbox
import androidx.compose.material.LocalTextStyle
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import kotlinx.datetime.format.Padding
import kotlinx.datetime.format.char
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

const val server = "wil-gma-srv"
const val port = "5000"

val commonLogger = KotlinLogging.logger {}


//val logger = KotlinLogging.logger {}

@Composable
fun App() {

    MaterialTheme {
        var statusMessage by remember { mutableStateOf("") }
        val scheduledAlerts = remember { mutableStateMapOf<String, Boolean>() }

        val client = HttpClient(CIO)
        val scope = rememberCoroutineScope()

        scope.launch {
            withContext(Dispatchers.IO) {
                while (true) {
                    try {
                        val onResponse = client.get("http://$server:$port/state")
                        statusMessage = onResponse.bodyAsText()
                        println(statusMessage)
                        delay(5000)
                    } catch(e: Exception) {
                        statusMessage = "Error: ".plus(e.message.toString())
                    }
                }
            }
        }

        Surface(modifier = Modifier) {
            Column {
                ScheduledAlerts(
                    modifier = Modifier
                        .weight(1f, fill = true)
                        .padding(5.dp)
                        .fillMaxHeight(0.70f)
                        .border(BorderStroke(2.dp, SolidColor(Color.Blue)))
                        .verticalScroll(rememberScrollState()),
                    timestamps = scheduledAlerts,
                    onCheckedChange = { timestamp, isChecked ->
                        scheduledAlerts[timestamp] = isChecked
                    }
                )
                Dashboard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.30f)
                        .padding(start = 5.dp, top = 5.dp, end = 5.dp),
                    scope = scope,
                    client = client,
                    addButtonClick = { timestamp ->
                        scheduledAlerts[timestamp] = false
                    },
                    nudgeButtonClick = { minutes ->
                        scheduledAlerts
                            .filterValues { it }
                            .forEach { checkedItem ->
                                val newTimestamp = LocalDateTime.parse(checkedItem.key)
                                    .toInstant(TimeZone.currentSystemDefault())
                                    .plus(minutes, DateTimeUnit.MINUTE)
                                    .toLocalDateTime(TimeZone.currentSystemDefault())
                                    .format(LocalDateTime.Formats.ISO)
                                scheduledAlerts[newTimestamp] = true
                                scheduledAlerts.remove(checkedItem.key)
                            }
                    },
                    deleteButtonClick = {
                        scheduledAlerts
                            .filterValues { it }
                            .forEach { checkedItem -> scheduledAlerts.remove(checkedItem.key) }
                    }
                )
            }
        }
    }
}

@Composable
private fun TimestampTextBox(
    modifier: Modifier = Modifier
) {

}

@Composable
private fun Dashboard(
    modifier: Modifier = Modifier,
    scope: CoroutineScope,
    client: HttpClient,
    addButtonClick: (String) -> Unit,
    nudgeButtonClick: (Int) -> Unit,
    deleteButtonClick: () -> Unit
) {
    Column(modifier = modifier) {

        var timestampAsNudge by rememberSaveable { mutableStateOf(0) }

        Row(
            modifier = Modifier
                .weight(1f)
                .padding(bottom = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {

            var timestamp by rememberSaveable { mutableStateOf("") }
            var timestampAsISO by rememberSaveable { mutableStateOf("") }
            var color by rememberSaveable { mutableStateOf(Color.White) }
            val nudgeMinutesRegex = Regex("""([-+]?\d+) ?[mM]""")
            val nudgeHoursRegex = Regex("""([-+]?\d+\.?\d*+) ?[hH]""")
            val dateTimeFormat = LocalDateTime.Format {
                monthNumber(padding = Padding.NONE)
                char('/')
                dayOfMonth(padding = Padding.NONE)
                char('/')
                yearTwoDigits(2000)
                char(' ')
                hour();
                char(':');
                minute()
            }

            TextField(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(5.dp)
                    .background(color),
                singleLine = true,
                textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.End),
                value = timestamp,
                onValueChange = {
                    timestampAsISO = ""
                    timestampAsNudge = 0
                    timestamp = it
                    try {
                        val minutesInstances = nudgeMinutesRegex.matchEntire(timestamp)
                        val hoursInstances = nudgeHoursRegex.matchEntire(timestamp)
                        if (minutesInstances != null) {
                            timestampAsNudge = minutesInstances.groupValues[1].toInt()
                            commonLogger.info { "INFO: Parsed $timestampAsNudge minutes..." }
                        } else if (hoursInstances != null) {
                            timestampAsNudge = (hoursInstances.groupValues[1].toDouble() * 60).toInt()
                            commonLogger.info { "INFO: Parsed $timestampAsNudge minutes (converted from hours)..." }
                        } else {
                            val parsed = LocalDateTime.parse(timestamp, dateTimeFormat)
                            timestampAsISO = parsed.format(LocalDateTime.Formats.ISO)
                            commonLogger.info { "INFO: Parsed timestamp $timestampAsISO..." }
                        }
                        color = Color.Green
                    } catch(e: Exception) {
                        color = Color.Red
                        println("ERROR: ${e.message}")
                    }
                }
            )
            Button(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(5.dp),
                onClick = {
                    if (timestampAsISO.isNotBlank()) {
                        commonLogger.info { "INFO: timestampAsISO = $timestampAsISO" }
                        addButtonClick(timestampAsISO)
                    } else {
                        commonLogger.info { "INFO: timestampAsISO is blank!" }
                    }
                }
            ) {
                Text("Add")
            }
        }
        Row(
            modifier = Modifier
                .weight(1f)
                .padding(bottom = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Button(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(5.dp),
                onClick = {
                    if (timestampAsNudge != 0) {
                        commonLogger.info { "INFO: timestampAsNudge = $timestampAsNudge" }
                        nudgeButtonClick(timestampAsNudge)
                    } else {
                        commonLogger.info { "INFO: timestampAsNudge is blank!" }
                    }
                }
            ) {
                Text("Nudge")
            }
            Button(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(5.dp),
                onClick = {
                   /* scope.launch {
                        withContext(Dispatchers.IO) {
                            try {
                                val expResponse =
                                    client.post("http://$server:$port/exp?seconds=60")
                                statusMessage = expResponse.bodyAsText()
                                println(statusMessage)
                                val onResponse = client.post("http://$server:$port/on")
                                statusMessage = onResponse.bodyAsText()
                                println(statusMessage)
                            } catch (e: Exception) {
                                statusMessage = "Error: ".plus(e.message.toString())
                            }
                        }
                    }*/
                }
            ) {
                Text("Stop")
            }
        }
        Row(
            modifier = Modifier
                .weight(1f)
                .padding(bottom = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Button(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(5.dp),
                onClick = deleteButtonClick
            ) {
                Text("Delete")
            }
            Button(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(5.dp),
                onClick = {
                    /*scope.launch {
                        withContext(Dispatchers.IO) {
                            try {
                                val expResponse =
                                    client.post("http://$server:$port/exp?seconds=60")
                                statusMessage = expResponse.bodyAsText()
                                println(statusMessage)
                                val onResponse = client.post("http://$server:$port/on")
                                statusMessage = onResponse.bodyAsText()
                                println(statusMessage)
                            } catch (e: Exception) {
                                statusMessage = "Error: ".plus(e.message.toString())
                            }
                        }
                    }*/
                }
            ) {
                Text("Snooze")
            }
        }
    }
}

@Composable
private fun ScheduledAlerts(
    modifier: Modifier = Modifier,
    timestamps: Map<String, Boolean> = emptyMap(),
    onCheckedChange: (String, Boolean) -> Unit
) {
    Column(modifier = modifier) {
        timestamps.forEach {
            ScheduledAlert(
                timestamp = it.key,
                checked = it.value,
                onCheckedChange = onCheckedChange
            )
        }
    }
}

@Composable
private fun ScheduledAlert(
    modifier: Modifier = Modifier,
    timestamp: String,
    checked: Boolean,
    onCheckedChange: (String, Boolean) -> Unit
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        commonLogger.info { "INFO: text = $timestamp" }
        commonLogger.info { "INFO: checked = $checked" }
        Text(
            modifier = Modifier
                .weight(1f)
                .padding(start = 15.dp),
            text = timestamp
        )
        Checkbox(
            checked = checked,
            onCheckedChange = { onCheckedChange(timestamp, !checked) }
        )
    }
}
