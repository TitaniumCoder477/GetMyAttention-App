
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
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsText
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.UtcOffset
import kotlinx.datetime.format
import kotlinx.datetime.format.DateTimeComponents
import kotlinx.datetime.format.Padding
import kotlinx.datetime.format.char
import kotlinx.datetime.format.format
import kotlinx.datetime.offsetAt
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.Json

//const val server = "wil-gma-srv"
//const val port = "5000"

const val server = "127.0.0.1"
const val port = "5000"
const val url = "http://$server:$port"

val commonLogger = KotlinLogging.logger {}


//val logger = KotlinLogging.logger {}

@Composable
fun App() {

    MaterialTheme {
        var statusMessage by remember { mutableStateOf("") }
        val scheduledAlerts = remember { mutableStateMapOf<String, Boolean>() }

        val client = HttpClient(CIO) {
            install(ContentNegotiation) {
                json()
            }
        }

        val scope = rememberCoroutineScope()
        scope.launch {
            withContext(Dispatchers.IO) {
                while (true) {
                    try {
                        val jsonString = client.get("$url/schedules").bodyAsText()
                        val jsonMap = Json.decodeFromString<Map<String, List<String>>>(jsonString)
                        jsonMap["schedules"]?.forEach { timestamp ->
                            if (!scheduledAlerts.contains(timestamp)) {
                                scheduledAlerts[timestamp] = false
                            }
                        }
                        scheduledAlerts.keys.forEach { timestamp ->
                            if (jsonMap["schedules"]?.contains(timestamp) == false) {
                                scheduledAlerts.remove(timestamp)
                            }
                        }
                        delay(1000)
                    } catch(e: Exception) {
                        commonLogger.error { e.message }
                    }
                }
            }
        }

        Surface(modifier = Modifier) {
            Column(modifier = Modifier) {
                ScheduledAlerts(
                    modifier = Modifier
                        .weight(1f, fill = true)
                        .padding(5.dp)
                        .fillMaxHeight(0.70f)
                        .border(BorderStroke(2.dp, SolidColor(Color.Blue)))
                        .verticalScroll(rememberScrollState()),
                    scheduledAlerts = scheduledAlerts,
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
                    nudgeButtonClick = { minutes ->
                        val scheduledAlertsThatAreChecked = scheduledAlerts.filterValues { it }
                        if (scheduledAlertsThatAreChecked.isEmpty()) {
                            val nudgedTimestamp = Clock.System.now()
                                .plus(minutes, DateTimeUnit.MINUTE)
                                .toLocalDateTime(TimeZone.currentSystemDefault())
                                .format(LocalDateTime.Formats.ISO)
                            //TODO: This needs to get moved into the Add button where it makes more sense
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    try {
                                        val message = client
                                            .post("$url/schedule?timestamp=$nudgedTimestamp")
                                            .bodyAsText()
                                        commonLogger.info { message }
                                    } catch (e: Exception) {
                                        commonLogger.error { e.message }
                                    }
                                }
                            }
                        } else {
                            scheduledAlertsThatAreChecked.keys.forEach { timestamp ->
                                val outputFormat = DateTimeComponents.Format {
                                    date(LocalDate.Formats.ISO)
                                    char('T')
                                    hour(); char(':'); minute(); char(':'); second()
                                    offset(UtcOffset.Formats.ISO)
                                }
                                try {
                                    scope.launch {
                                        withContext(Dispatchers.IO) {
                                            try {
                                                val message = client
                                                    .post("$url/delete?timestamp=$timestamp")
                                                    .bodyAsText()
                                                commonLogger.info { message }
                                            } catch (e: Exception) {
                                                commonLogger.error { e.message }
                                            }
                                        }
                                    }
                                    val nudgedTimestamp = Instant.parse(timestamp)
                                        .plus(minutes, DateTimeUnit.MINUTE)
                                        .toLocalDateTime(TimeZone.currentSystemDefault())
                                    val outputFormatted = outputFormat.format {
                                        with(TimeZone.currentSystemDefault()) {
                                            setDateTime(nudgedTimestamp)
                                            setOffset(
                                                offsetAt(
                                                    nudgedTimestamp.toInstant(
                                                        TimeZone.currentSystemDefault()
                                                    )
                                                )
                                            )
                                        }
                                    }
                                    scope.launch {
                                        withContext(Dispatchers.IO) {
                                            try {
                                                val message = client
                                                    .post("$url/schedule?timestamp=$outputFormatted")
                                                    .bodyAsText()
                                                scheduledAlerts["$outputFormatted"] = true
                                                commonLogger.info { message }
                                            } catch (e: Exception) {
                                                commonLogger.error { e.message }
                                            }
                                        }
                                    }
                                } catch (e: Exception) {
                                    commonLogger.error { e.message }
                                }
                            }
                        }
                    },
                    deleteButtonClick = {
                        scheduledAlerts
                            .filterValues { it }
                            .keys.forEach { timestamp ->
                                scope.launch {
                                    withContext(Dispatchers.IO) {
                                        try {
                                            val message = client
                                                .post("$url/delete?timestamp=$timestamp")
                                                .bodyAsText()
                                            commonLogger.info { message }
                                        } catch (e: Exception) {
                                            commonLogger.error { e.message }
                                        }
                                    }
                                }
                            }
                    }
                )
            }
        }
    }
}

@Composable
private fun Dashboard(
    modifier: Modifier = Modifier,
    scope: CoroutineScope,
    client: HttpClient,
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

            var textFieldTimestampValue by rememberSaveable { mutableStateOf("") }
            var outputFormatted by rememberSaveable { mutableStateOf("") }
            var color by rememberSaveable { mutableStateOf(Color.White) }
            val nudgeMinutesRegex = Regex("""([-+]?\d+) ?[mM]""")
            val nudgeHoursRegex = Regex("""([-+]?\d+\.?\d*+) ?[hH]""")
            val inputFormat = LocalDateTime.Format {
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

            val outputFormat = DateTimeComponents.Format {
                date(LocalDate.Formats.ISO)
                char('T')
                hour(); char(':'); minute(); char(':'); second()
                offset(UtcOffset.Formats.ISO)
            }

            TextField(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(5.dp)
                    .background(color),
                singleLine = true,
                textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.End),
                value = textFieldTimestampValue,
                onValueChange = {
                    outputFormatted = ""
                    timestampAsNudge = 0
                    textFieldTimestampValue = it
                    try {
                        val minutesInstances = nudgeMinutesRegex.matchEntire(textFieldTimestampValue)
                        val hoursInstances = nudgeHoursRegex.matchEntire(textFieldTimestampValue)
                        if (minutesInstances != null) {
                            timestampAsNudge = minutesInstances.groupValues[1].toInt()
                            commonLogger.info { "Parsed $timestampAsNudge minutes..." }
                        } else if (hoursInstances != null) {
                            timestampAsNudge = (hoursInstances.groupValues[1].toDouble() * 60).toInt()
                            commonLogger.info { "Parsed $timestampAsNudge minutes (converted from hours)..." }
                        } else {
                            val parsed = LocalDateTime.parse(textFieldTimestampValue, inputFormat)
                            outputFormatted = outputFormat.format {
                                with(TimeZone.currentSystemDefault()) {
                                    setDateTime(parsed
                                        .toInstant(TimeZone.currentSystemDefault())
                                        .toLocalDateTime()
                                    )
                                    setOffset(offsetAt(parsed.toInstant(TimeZone.currentSystemDefault())))
                                }
                            }
                            commonLogger.info { "Parsed outputFormatted as $outputFormatted..." }
                        }
                        color = Color.Green
                    } catch(e: Exception) {
                        color = Color.Red
                        commonLogger.error { e.message }
                    }
                }
            )
            Button(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(5.dp),
                onClick = {
                    if (outputFormatted.isNotBlank()) {
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                try {
                                    val message = client
                                        .post("$url/schedule?timestamp=$outputFormatted")
                                        .bodyAsText()
                                    commonLogger.info { message }
                                } catch (e: Exception) {
                                    commonLogger.error { e.message }
                                }
                            }
                        }
                    } else
                        commonLogger.warn { "outputFormatted is blank!" }
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
                    if (timestampAsNudge != 0)
                        nudgeButtonClick(timestampAsNudge)
                    else
                        commonLogger.warn { "timestampAsNudge is blank!" }
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
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            try {
                                val message = client.post("$url/off").bodyAsText()
                                commonLogger.info { message }
                            } catch (e: Exception) {
                                commonLogger.error { e.message }
                            }
                        }
                    }
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
    scheduledAlerts: Map<String, Boolean> = emptyMap(),
    onCheckedChange: (String, Boolean) -> Unit
) {
    Column(modifier = modifier) {
        scheduledAlerts.forEach {
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
