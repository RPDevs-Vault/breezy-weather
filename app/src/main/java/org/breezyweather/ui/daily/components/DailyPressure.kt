/*
 * This file is part of Breezy Weather.
 *
 * Breezy Weather is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by the
 * Free Software Foundation, version 3 of the License.
 *
 * Breezy Weather is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Breezy Weather. If not, see <https://www.gnu.org/licenses/>.
 */

package org.breezyweather.ui.daily.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import breezyweather.domain.location.model.Location
import breezyweather.domain.weather.model.Hourly
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries
import com.patrykandpatrick.vico.core.cartesian.marker.CartesianMarker
import com.patrykandpatrick.vico.core.cartesian.marker.CartesianMarkerVisibilityListener
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableMap
import org.breezyweather.R
import org.breezyweather.common.basic.models.options.appearance.ChartDisplay
import org.breezyweather.common.basic.models.options.unit.PressureUnit
import org.breezyweather.common.extensions.getFormattedTime
import org.breezyweather.common.extensions.is12Hour
import org.breezyweather.common.extensions.toDate
import org.breezyweather.domain.settings.SettingsManager
import org.breezyweather.ui.common.charts.BreezyLineChart
import org.breezyweather.ui.common.charts.SpecificVerticalAxisItemPlacer
import org.breezyweather.ui.settings.preference.bottomInsetItem
import java.util.Date
import kotlin.math.max
import kotlin.math.min

@Composable
fun DailyPressure(
    location: Location,
    hourlyList: ImmutableList<Hourly>,
    theDay: Date,
    modifier: Modifier = Modifier,
) {
    val mappedValues = remember(hourlyList) {
        hourlyList
            .filter { it.pressure != null }
            .associate { it.date.time to it.pressure!! }
            .toImmutableMap()
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            horizontal = dimensionResource(R.dimen.normal_margin),
            vertical = dimensionResource(R.dimen.little_margin)
        )
    ) {
        if (mappedValues.size >= ChartDisplay.CHART_MIN_COUNT) {
            item {
                PressureChart(location, mappedValues, theDay)
            }
        } else {
            item {
                UnavailableChart(mappedValues.size)
            }
        }
        item {
            Spacer(modifier = Modifier.height(dimensionResource(R.dimen.normal_margin)))
        }
        // TODO: Daily summary
        item {
            DailySectionHeader(stringResource(R.string.pressure_about))
        }
        item {
            DailyCardText(
                stringResource(R.string.pressure_about_description1),
                stringResource(R.string.pressure_about_description2)
            )
        }
        bottomInsetItem()
    }
}

@Composable
private fun PressureSummary(
    pressure: Double,
) {
    PressureItem(
        header = {
            Text(
                text = stringResource(R.string.pressure_average),
                style = MaterialTheme.typography.labelMedium
            )
        },
        pressure = pressure
    )
}

@Composable
private fun PressureItem(
    header: @Composable () -> Unit,
    pressure: Double?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    Column(
        modifier = modifier.fillMaxWidth()
    ) {
        header()
        Text(
            text = pressure?.let {
                SettingsManager.getInstance(context).pressureUnit.getValueText(context, it)
            } ?: "",
            style = MaterialTheme.typography.displaySmall
        )
    }
}

@Composable
private fun PressureChart(
    location: Location,
    mappedValues: ImmutableMap<Long, Double>,
    theDay: Date,
) {
    val context = LocalContext.current
    val maxY = remember(mappedValues) {
        max(
            PressureUnit.NORMAL + 30.0, // TODO: Make this a const
            mappedValues.values.max()
        )
    }
    val minY = remember(mappedValues) {
        min(
            PressureUnit.NORMAL - 30.0, // TODO: Make this a const
            mappedValues.values.min()
        )
    }
    val averagePressure = remember(mappedValues) {
        mappedValues.values.average()
    }

    val endAxisValueFormatter = CartesianValueFormatter { _, value, _ ->
        SettingsManager.getInstance(context).pressureUnit.getValueText(context, value)
    }

    val modelProducer = remember { CartesianChartModelProducer() }

    LaunchedEffect(location) {
        modelProducer.runTransaction {
            lineSeries {
                series(
                    x = mappedValues.keys,
                    y = mappedValues.values
                )
            }
        }
    }

    var activeMarkerTarget: CartesianMarker.Target? by remember { mutableStateOf(null) }
    val markerVisibilityListener = object : CartesianMarkerVisibilityListener {
        override fun onShown(marker: CartesianMarker, targets: List<CartesianMarker.Target>) {
            activeMarkerTarget = targets.firstOrNull()
        }

        override fun onUpdated(marker: CartesianMarker, targets: List<CartesianMarker.Target>) {
            activeMarkerTarget = targets.firstOrNull()
        }

        override fun onHidden(marker: CartesianMarker) {
            activeMarkerTarget = null
        }
    }

    activeMarkerTarget?.let {
        mappedValues.getOrElse(it.x.toLong()) { null }?.let { pressure ->
            PressureItem(
                header = {
                    Text(
                        text = it.x.toLong().toDate().getFormattedTime(location, context, context.is12Hour),
                        style = MaterialTheme.typography.labelMedium
                    )
                },
                pressure = pressure
            )
        }
    } ?: PressureSummary(averagePressure)

    Spacer(modifier = Modifier.height(dimensionResource(R.dimen.normal_margin)))

    BreezyLineChart(
        location,
        modelProducer,
        theDay,
        maxY,
        endAxisValueFormatter,
        persistentListOf(
            persistentMapOf(
                1080f to Color(48, 8, 24),
                1046f to Color(111, 24, 64),
                1038f to Color(142, 47, 57),
                1030f to Color(159, 81, 44),
                1024f to Color(163, 116, 67),
                1019f to Color(167, 147, 107),
                1015.25f to Color(176, 174, 152),
                1013.25f to Color(182, 182, 182),
                1011.25f to Color(155, 183, 172),
                1007f to Color(103, 162, 155),
                1002f to Color(26, 140, 147),
                995f to Color(0, 117, 146),
                986f to Color(0, 90, 148),
                976f to Color(0, 52, 146),
                950f to Color(0, 32, 96),
                900f to Color(8, 16, 48)
            )
        ),
        trendHorizontalLines = persistentMapOf(
            PressureUnit.NORMAL to stringResource(R.string.temperature_normal_short)
        ),
        minY = minY,
        endAxisItemPlacer = remember {
            SpecificVerticalAxisItemPlacer(
                listOf(minY, PressureUnit.NORMAL, maxY)
            )
        },
        markerVisibilityListener = markerVisibilityListener
    )
}
