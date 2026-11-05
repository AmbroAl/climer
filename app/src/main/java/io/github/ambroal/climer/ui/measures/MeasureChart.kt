package io.github.ambroal.climer.ui.measures

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.compose.cartesian.data.lineModel
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.common.ProvideVicoTheme
import com.patrykandpatrick.vico.compose.m3.common.rememberM3VicoTheme
import io.github.ambroal.climer.data.db.entity.MeasureEntryEntity
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val AXIS_DATE = DateTimeFormatter.ofPattern("dd/MM")

private val dateFormatter = CartesianValueFormatter { _, value, _ -> LocalDate.ofEpochDay(value.toLong()).format(AXIS_DATE) }

/** Una linea per il valore unico, oppure due (destra, sinistra) per i tipi per mano. Asse x = giorni. */
@Composable
fun MeasureChart(entries: List<MeasureEntryEntity>, perSide: Boolean, modifier: Modifier = Modifier) {
    val sorted = entries.sortedBy { it.date }
    val series: List<List<Pair<Long, Double>>> = if (perSide) {
        listOf(
            sorted.mapNotNull { e -> e.right?.let { e.date.toEpochDay() to it } },
            sorted.mapNotNull { e -> e.left?.let { e.date.toEpochDay() to it } }
        )
    } else {
        listOf(sorted.mapNotNull { e -> e.value?.let { e.date.toEpochDay() to it } })
    }.filter { it.isNotEmpty() }
    if (series.isEmpty() || series.all { it.size < 2 }) return

    val producer = remember { CartesianChartModelProducer() }
    LaunchedEffect(series) {
        producer.runTransaction {
            lineModel { series.forEach { s -> series(s.map { it.first }, s.map { it.second }) } }
        }
    }
    ProvideVicoTheme(rememberM3VicoTheme()) {
        CartesianChartHost(
            chart = rememberCartesianChart(
                rememberLineCartesianLayer(),
                startAxis = VerticalAxis.rememberStart(),
                bottomAxis = HorizontalAxis.rememberBottom(valueFormatter = dateFormatter)
            ),
            modelProducer = producer,
            modifier = modifier.fillMaxWidth().height(220.dp)
        )
    }
}
