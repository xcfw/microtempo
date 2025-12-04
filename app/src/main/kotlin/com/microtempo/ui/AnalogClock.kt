package com.microtempo.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import com.microtempo.PreciseTime
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

// Eastern Arabic numerals for hours subdial (12 at top, then 1-11 clockwise)
private val HOURS_EASTERN = listOf("١٢", "١", "٢", "٣", "٤", "٥", "٦", "٧", "٨", "٩", "١٠", "١١")
private val MONTHS = listOf("JAN", "FEB", "MAR", "APR", "MAY", "JUN", "JUL", "AUG", "SEP", "OCT", "NOV", "DEC")
private val DAYS = listOf("SUN", "MON", "TUE", "WED", "THU", "FRI", "SAT")

@Composable
fun AnalogClock(
    time: PreciseTime,
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()

    val calendar = remember { java.util.GregorianCalendar() }
    val calData = remember(time.millis / 1000) {
        calendar.timeInMillis = time.millis
        CalData(
            day = calendar.get(java.util.Calendar.DAY_OF_MONTH),
            dow = calendar.get(java.util.Calendar.DAY_OF_WEEK) - 1,
            month = calendar.get(java.util.Calendar.MONTH),
            year = calendar.get(java.util.Calendar.YEAR)
        )
    }

    Canvas(modifier = modifier) {
        val cx = size.width / 2
        val cy = size.height / 2
        val R = min(size.width, size.height) / 2 * 0.9f

        // Colors
        val dialBackground = Color(0xFFF0F0F0)  // Silver-white / Matte Pearl
        val bluedSteel = Color(0xFF1E3A5F)
        val black = Color(0xFF1A1A1A)
        val red = Color(0xFFCC0000)
        val gold = Color(0xFFD4AF37)

        // Subdial positions
        val subdialOffset = 0.35f * R
        val subdialRadius = 0.45f * R

        // Bottom-Right subdial (Hours) - at 135° (4:30 position)
        val hrCx = cx + subdialOffset
        val hrCy = cy + subdialOffset

        // Bottom-Left subdial (Minutes) - at 225° (7:30 position)
        val minCx = cx - subdialOffset
        val minCy = cy + subdialOffset

        // Large seconds circle
        val secRadius = 0.85f * R

        // =============================================
        // Z-INDEX 0: BACKGROUND
        // =============================================
        drawCircle(dialBackground, R, Offset(cx, cy))

        // =============================================
        // Z-INDEX 1: TRACKS
        // =============================================

        // --- Large Top Circle (Seconds) - Railroad Track ---
        val secTrackOuter = secRadius * 0.98f
        val secTrackInner = secRadius * 0.88f

        // Outer and inner track rings
        drawCircle(black, secTrackOuter, Offset(cx, cy), style = Stroke(1.5f))
        drawCircle(black, secTrackInner, Offset(cx, cy), style = Stroke(1.5f))

        // Tick marks for seconds (60 divisions)
        for (i in 0 until 60) {
            val angle = (i * 6 - 90) * PI / 180
            val tickOuter = secTrackOuter
            val tickInner = if (i % 5 == 0) secTrackInner else secTrackInner + (secTrackOuter - secTrackInner) * 0.4f
            drawLine(
                color = black,
                start = Offset(cx + tickInner * cos(angle).toFloat(), cy + tickInner * sin(angle).toFloat()),
                end = Offset(cx + tickOuter * cos(angle).toFloat(), cy + tickOuter * sin(angle).toFloat()),
                strokeWidth = if (i % 5 == 0) 2f else 1f
            )
        }

        // Seconds numerals: 60 (top), 15 (right), 30 (bottom), 45 (left) - RED
        val secNumStyle = TextStyle(
            fontSize = (R * 0.07f).toSp(),
            fontWeight = FontWeight.Bold,
            color = red,
            fontFamily = FontFamily.Serif
        )
        val secNumDist = secRadius * 0.78f
        listOf(
            Triple("60", 0, -90),   // Top
            Triple("15", 1, 0),     // Right
            Triple("30", 2, 90),    // Bottom
            Triple("45", 3, 180)    // Left
        ).forEach { (num, _, angleDeg) ->
            val angle = angleDeg * PI / 180
            val txt = textMeasurer.measure(num, secNumStyle)
            drawText(txt, topLeft = Offset(
                cx + secNumDist * cos(angle).toFloat() - txt.size.width / 2,
                cy + secNumDist * sin(angle).toFloat() - txt.size.height / 2
            ))
        }

        // --- Bottom-Left Subdial (Minutes) - Drawn ON TOP ---
        drawCircle(dialBackground, subdialRadius, Offset(minCx, minCy))

        // Minutes track (railroad style)
        val minTrackOuter = subdialRadius * 0.95f
        val minTrackInner = subdialRadius * 0.80f
        drawCircle(black, minTrackOuter, Offset(minCx, minCy), style = Stroke(1.2f))
        drawCircle(black, minTrackInner, Offset(minCx, minCy), style = Stroke(1.2f))

        // Minute tick marks (60 divisions)
        for (i in 0 until 60) {
            val angle = (i * 6 - 90) * PI / 180
            val tickOuter = minTrackOuter
            val tickInner = if (i % 5 == 0) minTrackInner else minTrackInner + (minTrackOuter - minTrackInner) * 0.5f
            drawLine(
                color = black,
                start = Offset(minCx + tickInner * cos(angle).toFloat(), minCy + tickInner * sin(angle).toFloat()),
                end = Offset(minCx + tickOuter * cos(angle).toFloat(), minCy + tickOuter * sin(angle).toFloat()),
                strokeWidth = if (i % 5 == 0) 1.5f else 0.8f
            )
        }

        // Minutes numerals: 10, 20, 30, 40, 50, 60 - BLACK
        val minNumStyle = TextStyle(
            fontSize = (subdialRadius * 0.15f).toSp(),
            fontWeight = FontWeight.Bold,
            color = black,
            fontFamily = FontFamily.Serif
        )
        val minNumDist = subdialRadius * 0.62f
        listOf("60", "10", "20", "30", "40", "50").forEachIndexed { idx, num ->
            val angle = (idx * 60 - 90) * PI / 180
            val txt = textMeasurer.measure(num, minNumStyle)
            drawText(txt, topLeft = Offset(
                minCx + minNumDist * cos(angle).toFloat() - txt.size.width / 2,
                minCy + minNumDist * sin(angle).toFloat() - txt.size.height / 2
            ))
        }

        // --- Bottom-Right Subdial (Hours) - Drawn ON TOP ---
        drawCircle(dialBackground, subdialRadius, Offset(hrCx, hrCy))

        // Hours track (railroad style)
        val hrTrackOuter = subdialRadius * 0.95f
        val hrTrackInner = subdialRadius * 0.80f
        drawCircle(black, hrTrackOuter, Offset(hrCx, hrCy), style = Stroke(1.2f))
        drawCircle(black, hrTrackInner, Offset(hrCx, hrCy), style = Stroke(1.2f))

        // Hour tick marks (12 major, 60 minor)
        for (i in 0 until 60) {
            val angle = (i * 6 - 90) * PI / 180
            val tickOuter = hrTrackOuter
            val tickInner = if (i % 5 == 0) hrTrackInner else hrTrackInner + (hrTrackOuter - hrTrackInner) * 0.5f
            drawLine(
                color = black,
                start = Offset(hrCx + tickInner * cos(angle).toFloat(), hrCy + tickInner * sin(angle).toFloat()),
                end = Offset(hrCx + tickOuter * cos(angle).toFloat(), hrCy + tickOuter * sin(angle).toFloat()),
                strokeWidth = if (i % 5 == 0) 1.5f else 0.8f
            )
        }

        // Hours numerals: Eastern Arabic ١٢, ١, ٢, ٣, ٤, ٥, ٦, ٧, ٨, ٩, ١٠, ١١
        val hrNumStyle = TextStyle(
            fontSize = (subdialRadius * 0.16f).toSp(),
            fontWeight = FontWeight.Bold,
            color = black,
            fontFamily = FontFamily.Serif
        )
        val hrNumDist = subdialRadius * 0.60f
        HOURS_EASTERN.forEachIndexed { idx, num ->
            val angle = (idx * 30 - 90) * PI / 180
            val txt = textMeasurer.measure(num, hrNumStyle)
            drawText(txt, topLeft = Offset(
                hrCx + hrNumDist * cos(angle).toFloat() - txt.size.width / 2,
                hrCy + hrNumDist * sin(angle).toFloat() - txt.size.height / 2
            ))
        }

        // =============================================
        // Z-INDEX 2: WINDOWS & TEXT
        // =============================================

        // --- Big Date Window (Top Center) ---
        val dateY = cy - secRadius * 0.55f
        val dateW = R * 0.22f
        val dateH = R * 0.12f

        // Double window frame
        drawRoundRect(
            color = Color.White,
            topLeft = Offset(cx - dateW / 2, dateY - dateH / 2),
            size = Size(dateW, dateH),
            cornerRadius = CornerRadius(3f)
        )
        // Divider line in middle
        drawLine(
            color = black.copy(alpha = 0.3f),
            start = Offset(cx, dateY - dateH / 2 + 3f),
            end = Offset(cx, dateY + dateH / 2 - 3f),
            strokeWidth = 1f
        )
        // Grey border
        drawRoundRect(
            color = Color(0xFF888888),
            topLeft = Offset(cx - dateW / 2, dateY - dateH / 2),
            size = Size(dateW, dateH),
            cornerRadius = CornerRadius(3f),
            style = Stroke(2f)
        )
        // Date digits
        val dateStr = calData.day.toString().padStart(2, '0')
        val dateStyle = TextStyle(
            fontSize = (dateH * 0.70f).toSp(),
            fontWeight = FontWeight.Bold,
            color = black,
            fontFamily = FontFamily.Serif
        )
        val digit1 = textMeasurer.measure(dateStr[0].toString(), dateStyle)
        val digit2 = textMeasurer.measure(dateStr[1].toString(), dateStyle)
        drawText(digit1, topLeft = Offset(cx - dateW / 4 - digit1.size.width / 2, dateY - digit1.size.height / 2))
        drawText(digit2, topLeft = Offset(cx + dateW / 4 - digit2.size.width / 2, dateY - digit2.size.height / 2))

        // --- Brand Text ---
        val brandStyle = TextStyle(
            fontSize = (R * 0.045f).toSp(),
            fontWeight = FontWeight.Normal,
            color = black,
            fontFamily = FontFamily.Serif
        )
        val brandTxt = textMeasurer.measure("EAST TURKISTAN", brandStyle)
        drawText(brandTxt, topLeft = Offset(cx - brandTxt.size.width / 2, cy - secRadius * 0.32f))

        val subBrandStyle = TextStyle(
            fontSize = (R * 0.032f).toSp(),
            fontWeight = FontWeight.Normal,
            color = black,
            fontFamily = FontFamily.Serif
        )
        val subBrandTxt = textMeasurer.measure("SAGHAT 1988", subBrandStyle)
        drawText(subBrandTxt, topLeft = Offset(cx - subBrandTxt.size.width / 2, cy - secRadius * 0.22f))

        // --- Day Window (Inside Bottom-Left subdial, 9 o'clock position) ---
        val dayWinW = subdialRadius * 0.28f
        val dayWinH = subdialRadius * 0.14f
        val dayWinX = minCx - subdialRadius * 0.38f
        val dayWinY = minCy

        drawRoundRect(Color.White, Offset(dayWinX - dayWinW/2, dayWinY - dayWinH/2), Size(dayWinW, dayWinH), CornerRadius(2f))
        drawRoundRect(black.copy(alpha = 0.4f), Offset(dayWinX - dayWinW/2, dayWinY - dayWinH/2), Size(dayWinW, dayWinH), CornerRadius(2f), style = Stroke(1f))
        val dayStyle = TextStyle(fontSize = (dayWinH * 0.60f).toSp(), color = black, fontWeight = FontWeight.Medium)
        val dayTxt = textMeasurer.measure(DAYS[calData.dow], dayStyle)
        drawText(dayTxt, topLeft = Offset(dayWinX - dayTxt.size.width/2, dayWinY - dayTxt.size.height/2))

        // --- Month Window (Inside Bottom-Right subdial, 3 o'clock position) ---
        val monWinX = hrCx + subdialRadius * 0.38f
        val monWinY = hrCy

        drawRoundRect(Color.White, Offset(monWinX - dayWinW/2, monWinY - dayWinH/2), Size(dayWinW, dayWinH), CornerRadius(2f))
        drawRoundRect(black.copy(alpha = 0.4f), Offset(monWinX - dayWinW/2, monWinY - dayWinH/2), Size(dayWinW, dayWinH), CornerRadius(2f), style = Stroke(1f))
        val monTxt = textMeasurer.measure(MONTHS[calData.month], dayStyle)
        drawText(monTxt, topLeft = Offset(monWinX - monTxt.size.width/2, monWinY - monTxt.size.height/2))

        // --- Leap Year Indicator (outside Bottom-Right subdial, inside seconds dial, well above Month and "15") ---
        val lastDigitOfYear = calData.year % 10
        val leapX = hrCx + subdialRadius * 0.48f
        val leapY = hrCy - subdialRadius * 1.18f
        val leapR = subdialRadius * 0.08f

        drawCircle(Color.White, leapR, Offset(leapX, leapY))
        drawCircle(black.copy(alpha = 0.4f), leapR, Offset(leapX, leapY), style = Stroke(1f))
        val leapStyle = TextStyle(fontSize = (leapR * 1.2f).toSp(), color = red, fontWeight = FontWeight.Bold)
        val leapTxt = textMeasurer.measure(lastDigitOfYear.toString(), leapStyle)
        drawText(leapTxt, topLeft = Offset(leapX - leapTxt.size.width/2, leapY - leapTxt.size.height/2))

        // --- Curved Text: "14 DAYS REMONTOIR" (left bezel) ---
        drawCurvedText(
            textMeasurer = textMeasurer,
            text = "WITH LOVE",
            cx = cx, cy = cy,
            radius = R * 0.92f,
            startAngle = 200f,
            sweepAngle = 70f,
            fontSize = (R * 0.028f).toSp(),
            color = black
        )

        // --- Curved Text: "PERPETUAL CALENDAR" (right bezel) ---
        drawCurvedText(
            textMeasurer = textMeasurer,
            text = "FROM UYGHUR",
            cx = cx, cy = cy,
            radius = R * 0.92f,
            startAngle = -70f,
            sweepAngle = 70f,
            fontSize = (R * 0.028f).toSp(),
            color = black
        )

        // =============================================
        // Z-INDEX 3: HANDS
        // =============================================

        val totalMs = time.millis
        val microFrac = time.micros / 1_000_000.0

        // Smooth time calculations
        val smoothSecs = ((totalMs / 1000) % 60) + ((totalMs % 1000) / 1000.0) + microFrac
        val smoothMins = ((totalMs / 60000) % 60) + (smoothSecs / 60.0)
        val smoothHrs = ((totalMs / 3600000) % 12) + (smoothMins / 60.0)

        // --- Large Seconds Hand (pivot at main center) ---
        val secAngle = (smoothSecs / 60.0 * 360.0 - 90.0).toFloat()
        rotate(secAngle, pivot = Offset(cx, cy)) {
            // Counterweight
            drawLine(
                color = bluedSteel,
                start = Offset(cx - secRadius * 0.18f, cy),
                end = Offset(cx, cy),
                strokeWidth = R * 0.012f,
                cap = StrokeCap.Round
            )
            // Main hand - thin, reaches to track
            drawLine(
                color = bluedSteel,
                start = Offset(cx, cy),
                end = Offset(cx + secRadius * 0.92f, cy),
                strokeWidth = R * 0.006f,
                cap = StrokeCap.Round
            )
        }

        // --- Minutes Hand (pivot at bottom-left subdial) ---
        val minAngle = (smoothMins / 60.0 * 360.0 - 90.0).toFloat()
        rotate(minAngle, pivot = Offset(minCx, minCy)) {
            // Counterweight with hoop
            val hoopCx = minCx - subdialRadius * 0.25f
            drawCircle(bluedSteel, R * 0.018f, Offset(hoopCx, minCy), style = Stroke(R * 0.008f))
            drawLine(
                color = bluedSteel,
                start = Offset(hoopCx + R * 0.018f, minCy),
                end = Offset(minCx, minCy),
                strokeWidth = R * 0.008f,
                cap = StrokeCap.Butt
            )
            // Main hand
            drawLine(
                color = bluedSteel,
                start = Offset(minCx, minCy),
                end = Offset(minCx + subdialRadius * 0.72f, minCy),
                strokeWidth = R * 0.010f,
                cap = StrokeCap.Round
            )
        }

        // --- Hours Hand (pivot at bottom-right subdial) - Alpha/Lancet shape ---
        val hrAngle = (smoothHrs / 12.0 * 360.0 - 90.0).toFloat()
        rotate(hrAngle, pivot = Offset(hrCx, hrCy)) {
            // Lancet-shaped hour hand (thicker, tapered)
            val path = Path().apply {
                moveTo(hrCx - subdialRadius * 0.12f, hrCy)
                lineTo(hrCx, hrCy - R * 0.012f)
                lineTo(hrCx + subdialRadius * 0.55f, hrCy)
                lineTo(hrCx, hrCy + R * 0.012f)
                close()
            }
            drawPath(path, bluedSteel)
        }

        // =============================================
        // Z-INDEX 4: CENTER CAPS
        // =============================================

        // Main center (seconds)
        drawCircle(bluedSteel, R * 0.025f, Offset(cx, cy))
        drawCircle(gold, R * 0.012f, Offset(cx, cy))
        drawCircle(black, R * 0.004f, Offset(cx, cy))

        // Minutes subdial center
        drawCircle(bluedSteel, R * 0.018f, Offset(minCx, minCy))
        drawCircle(gold, R * 0.008f, Offset(minCx, minCy))

        // Hours subdial center
        drawCircle(bluedSteel, R * 0.018f, Offset(hrCx, hrCy))
        drawCircle(gold, R * 0.008f, Offset(hrCx, hrCy))
    }
}

/**
 * Draw curved text along an arc.
 */
private fun DrawScope.drawCurvedText(
    textMeasurer: androidx.compose.ui.text.TextMeasurer,
    text: String,
    cx: Float,
    cy: Float,
    radius: Float,
    startAngle: Float,
    sweepAngle: Float,
    fontSize: androidx.compose.ui.unit.TextUnit,
    color: Color
) {
    val style = TextStyle(
        fontSize = fontSize,
        color = color,
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Normal
    )

    val anglePerChar = sweepAngle / (text.length - 1)

    text.forEachIndexed { idx, char ->
        val angle = (startAngle + idx * anglePerChar) * PI / 180
        val x = cx + radius * cos(angle).toFloat()
        val y = cy + radius * sin(angle).toFloat()

        val charTxt = textMeasurer.measure(char.toString(), style)

        rotate((startAngle + idx * anglePerChar + 90).toFloat(), pivot = Offset(x, y)) {
            drawText(charTxt, topLeft = Offset(x - charTxt.size.width / 2, y - charTxt.size.height / 2))
        }
    }
}

private data class CalData(val day: Int, val dow: Int, val month: Int, val year: Int)

@Composable
private fun Float.toSp() = (this / androidx.compose.ui.platform.LocalDensity.current.density).sp
