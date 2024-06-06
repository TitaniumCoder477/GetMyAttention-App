
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
import androidx.compose.material.TextFieldDefaults
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

    MaterialTheme(
        colors = MaterialTheme.colors.copy(
            primary = Color(0xFFBF5700)
        )
    ) {
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

        scope.launch {
            withContext(Dispatchers.IO) {
                while (true) {
                    try {
                        val jsonString = client.get("$url/schedules").bodyAsText()
                        val jsonMap = Json.decodeFromString<Map<String, List<String>>>(jsonString)
                        jsonMap["schedules"]?.forEach { timestamp ->
                            if (Instant.parse(timestamp) < Clock.System.now()) {
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
                        delay(5000)
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
                        //.border(BorderStroke(2.dp, SolidColor(Color.Blue)))
                        .verticalScroll(rememberScrollState()),
                    scheduledAlerts = scheduledAlerts,
                    onCheckedChange = { timestamp, isChecked ->
                        scheduledAlerts[timestamp] = isChecked
                    }
                )

                var nudgeValue by rememberSaveable { mutableStateOf(0) }
                var timestampFormattedForOutput by rememberSaveable { mutableStateOf("") }
                var txtFldValue by rememberSaveable { mutableStateOf("") }
                var txtFldColorState by rememberSaveable { mutableStateOf(true) }
                val nudgeMinutesRegex = Regex("""([-+]?\d+) ?[mM]""")
                val nudgeHoursRegex = Regex("""([-+]?\d+\.?\d*+) ?[hH]""")
                val nudgeDaysRegex = Regex("""([-+]?\d+) ?[dD]""")
                val inputFormatRequired = LocalDateTime.Format {
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

                val outputFormatDesired = DateTimeComponents.Format {
                    date(LocalDate.Formats.ISO)
                    char('T')
                    hour(); char(':'); minute(); char(':'); second()
                    offset(UtcOffset.Formats.ISO)
                }

                Dashboard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.30f)
                        .padding(start = 5.dp, top = 5.dp, end = 5.dp),
                    scope = scope,
                    client = client,
                    txtFldValue = txtFldValue,
                    txtFldColorState = txtFldColorState,
                    onValueChange = { changedTxtFldValue ->
                        txtFldValue = changedTxtFldValue
                        timestampFormattedForOutput = ""
                        nudgeValue = 0
                        try {
                            val minutesInstances = nudgeMinutesRegex.matchEntire(txtFldValue)
                            val hoursInstances = nudgeHoursRegex.matchEntire(txtFldValue)
                            val daysInstances = nudgeDaysRegex.matchEntire(txtFldValue)
                            if (minutesInstances != null) {
                                nudgeValue = minutesInstances.groupValues[1].toInt()
                                commonLogger.info { "Input nudge value parsed as $nudgeValue minutes..." }
                            } else if (hoursInstances != null) {
                                nudgeValue = (hoursInstances.groupValues[1].toDouble() * 60).toInt()
                                commonLogger.info { "Input nudge value parsed as $nudgeValue minutes (converted from hours)..." }
                            } else if (daysInstances != null) {
                                nudgeValue = daysInstances.groupValues[1].toInt() * 24 * 60
                                commonLogger.info { "Input nudge value parsed as $nudgeValue minutes (converted from days)..." }
                            } else {
                                val parsedTimestamp = LocalDateTime.parse(txtFldValue, inputFormatRequired)
                                timestampFormattedForOutput = outputFormatDesired.format {
                                    with(TimeZone.currentSystemDefault()) {
                                        setDateTime(
                                            parsedTimestamp
                                                .toInstant(TimeZone.currentSystemDefault())
                                                .toLocalDateTime()
                                        )
                                        setOffset(offsetAt(parsedTimestamp.toInstant(TimeZone.currentSystemDefault())))
                                    }
                                }
                                if (Instant.parse(timestampFormattedForOutput) >= Clock.System.now())
                                    commonLogger.info { "Input timestamp parsed as $timestampFormattedForOutput..." }
                                else
                                    throw Exception("Time provided is in the past.")
                            }
                            txtFldColorState = true
                        } catch(e: Exception) {
                            txtFldColorState = false
                            timestampFormattedForOutput = ""
                            commonLogger.error { e.message }
                        }
                    },
                    addButtonClick = {
                        if (timestampFormattedForOutput.isNotBlank()) {
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    try {
                                        val message = client
                                            .post("$url/schedule?timestamp=$timestampFormattedForOutput")
                                            .bodyAsText()
                                        commonLogger.info { message }
                                    } catch (e: Exception) {
                                        commonLogger.error { e.message }
                                    }
                                }
                            }
                        } else if (nudgeValue != 0) {
                            val nudgedTimestamp = Clock.System.now()
                                .plus(nudgeValue, DateTimeUnit.MINUTE)
                                .toLocalDateTime(TimeZone.currentSystemDefault())
                            val newTimestamp = outputFormatDesired.format {
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
                                            .post("$url/schedule?timestamp=$newTimestamp")
                                            .bodyAsText()
                                        commonLogger.info { message }
                                    } catch (e: Exception) {
                                        commonLogger.error { e.message }
                                    }
                                }
                            }
                        } else {
                            commonLogger.warn { "Format requested is not supported." }
                        }
                    },
                    nudgeButtonClick = {
                        if(nudgeValue != 0) {
                            val selectedScheduledAlerts = scheduledAlerts.filterValues { it }
                            if (selectedScheduledAlerts.isNotEmpty()) {
                                selectedScheduledAlerts.keys.forEach { oldTimestamp ->
                                    try {
                                        scope.launch {
                                            withContext(Dispatchers.IO) {
                                                try {
                                                    val message = client
                                                        .post("$url/delete?timestamp=$oldTimestamp")
                                                        .bodyAsText()
                                                    commonLogger.info { message }
                                                } catch (e: Exception) {
                                                    commonLogger.error { e.message }
                                                }
                                            }
                                        }
                                        val nudgedTimestamp = Instant.parse(oldTimestamp)
                                            .plus(nudgeValue, DateTimeUnit.MINUTE)
                                            .toLocalDateTime(TimeZone.currentSystemDefault())
                                        val newTimestamp = outputFormatDesired.format {
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
                                                        .post("$url/schedule?timestamp=$newTimestamp")
                                                        .bodyAsText()
                                                    scheduledAlerts[newTimestamp] =
                                                        true
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
                            } else {
                                commonLogger.warn { "No schedules are selected for nudging." }
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
                    },
                    snoozeButtonClick = {
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
                        txtFldValue = "5m"
                        txtFldColorState = true
                        val minutesInstances = nudgeMinutesRegex.matchEntire(txtFldValue)
                        nudgeValue = minutesInstances!!.groupValues[1].toInt()
                        commonLogger.info { "Input nudge value parsed as $nudgeValue minutes..." }
                        val nudgedTimestamp = Clock.System.now()
                            .plus(nudgeValue, DateTimeUnit.MINUTE)
                            .toLocalDateTime(TimeZone.currentSystemDefault())
                        val newTimestamp = outputFormatDesired.format {
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
                                        .post("$url/schedule?timestamp=$newTimestamp")
                                        .bodyAsText()
                                    commonLogger.info { message }
                                } catch (e: Exception) {
                                    commonLogger.error { e.message }
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
    txtFldValue: String,
    txtFldColorState: Boolean,
    onValueChange: (String) -> Unit,
    addButtonClick: () -> Unit,
    nudgeButtonClick: () -> Unit,
    deleteButtonClick: () -> Unit,
    snoozeButtonClick: () -> Unit
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .weight(1f)
                .padding(bottom = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            TextField(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(5.dp),
                singleLine = true,
                textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.End),
                value = txtFldValue,
                colors = TextFieldDefaults.textFieldColors(
                    if (txtFldColorState) MaterialTheme.colors.primary else Color.Red
                ),
                onValueChange = { onValueChange(it) }
            )
            Button(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(5.dp),
                onClick = addButtonClick
            ) { Text("Add") }
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
                onClick = nudgeButtonClick
            ) { Text("Nudge") }
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
            ) { Text("Stop") }
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
            ) { Text("Delete") }
            Button(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(5.dp),
                onClick = snoozeButtonClick
            ) { Text("Snooze") }
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
        scheduledAlerts.keys.sorted().forEach { timestamp ->
            scheduledAlerts[timestamp]?.let {
                ScheduledAlert(
                    timestamp = timestamp,
                    checked = it,
                    onCheckedChange = onCheckedChange
                )
            }
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
            text = timestamp,
            color = if (Instant.parse(timestamp) < Clock.System.now()) {
                MaterialTheme.colors.secondary
            } else {
                MaterialTheme.colors.primary
            }
        )
        Checkbox(
            checked = checked,
            onCheckedChange = { onCheckedChange(timestamp, !checked) }
        )
    }
}
