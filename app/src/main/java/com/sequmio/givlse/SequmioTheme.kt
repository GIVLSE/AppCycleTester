package com.sequmio.givlse

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal object SeqColors {
    val Background = Color(0xFF0A1022)
    val BackgroundEnd = Color(0xFF15102D)
    val Card = Color(0xFF18233A)
    val CardRaised = Color(0xFF27304B)
    val Border = Color(0xFF465575)
    // Accessible lavender text links and distinctly darker filled controls.
    val Primary = Color(0xFFC39BFF)
    val PrimaryAction = Color(0xFF8E54E9)
    val PrimaryDark = Color(0xFF59379B)
    val Teal = Color(0xFF40E8DC)
    val Sky = Color(0xFF80D8FF)
    val Text = Color(0xFFF4F6FC)
    val Muted = Color(0xFFADBAD0)
    val Error = Color(0xFFFF879C)
    val Success = Color(0xFF58D9C3)
}
internal val SeqBackground = Brush.verticalGradient(
    listOf(SeqColors.Background, Color(0xFF111A31), SeqColors.BackgroundEnd)
)

@Composable
internal fun SequmioTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = SeqColors.Primary,
            onPrimary = SeqColors.Background,
            primaryContainer = SeqColors.PrimaryDark,
            onPrimaryContainer = Color.White,
            secondary = SeqColors.Teal,
            onSecondary = SeqColors.Background,
            secondaryContainer = SeqColors.CardRaised,
            onSecondaryContainer = SeqColors.Text,
            tertiary = SeqColors.Sky,
            onTertiary = SeqColors.Background,
            background = SeqColors.Background,
            onBackground = SeqColors.Text,
            surface = SeqColors.Card,
            onSurface = SeqColors.Text,
            surfaceContainerLowest = SeqColors.Background,
            surfaceContainerLow = SeqColors.Card,
            surfaceContainer = SeqColors.Card,
            surfaceContainerHigh = SeqColors.CardRaised,
            surfaceContainerHighest = SeqColors.CardRaised,
            surfaceVariant = SeqColors.CardRaised,
            onSurfaceVariant = SeqColors.Muted,
            outline = SeqColors.Border,
            outlineVariant = SeqColors.Border,
            error = SeqColors.Error,
            onError = SeqColors.Background
        ), content = content
    )
}

@Composable
internal fun SeqCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = SeqColors.Card,
            contentColor = SeqColors.Text,
            disabledContainerColor = SeqColors.CardRaised,
            disabledContentColor = SeqColors.Muted
        ),
        border = BorderStroke(1.dp, Brush.linearGradient(listOf(
            SeqColors.Teal.copy(alpha = 0.29f),
            SeqColors.Border.copy(alpha = 0.88f),
            SeqColors.Primary.copy(alpha = 0.28f)))),
        shape = RoundedCornerShape(22.dp)
    ) { Column(content = content) }
}

@Composable
internal fun SeqPrimaryButton(title: String, onClick: () -> Unit,
    modifier: Modifier = Modifier, enabled: Boolean = true,
    icon: ImageVector? = null) {
    val shape = RoundedCornerShape(16.dp)
    Box(modifier = modifier.clip(shape).background(
        if (enabled) Brush.horizontalGradient(listOf(
            SeqColors.PrimaryDark, SeqColors.PrimaryAction, Color(0xFFA364DE)))
        else Brush.horizontalGradient(listOf(SeqColors.CardRaised, SeqColors.CardRaised))
    )) {
        Button(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth(),
            shape = shape,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Transparent, contentColor = Color.White,
                disabledContainerColor = Color.Transparent,
                disabledContentColor = SeqColors.Muted),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp)
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(19.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(title, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** Keep text actions legible on every dark screen, including disabled states. */
@Composable
internal fun SeqTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    TextButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        colors = ButtonDefaults.textButtonColors(
            contentColor = SeqColors.Primary,
            disabledContentColor = SeqColors.Muted
        ),
        content = content
    )
}

/** Native vector rendering based on the approved Sequmio logo concept. */
@Composable
internal fun SequmioMark(size: Dp = 76.dp, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(size)) {
        val u = this.size.width / 100f
        drawRoundRect(
            brush = Brush.linearGradient(listOf(Color(0xFF213858), Color(0xFF14162C)),
                start = Offset.Zero, end = Offset(this.size.width, this.size.height)),
            cornerRadius = CornerRadius(23*u)
        )
        drawRoundRect(SeqColors.Teal, Offset(18*u, 42*u), Size(22*u, 22*u),
            cornerRadius = CornerRadius(6*u))
        drawRoundRect(SeqColors.Sky.copy(alpha = 0.87f), Offset(41*u, 64*u),
            Size(22*u, 20*u), cornerRadius = CornerRadius(5*u))
        drawRoundRect(SeqColors.Primary, Offset(65*u, 42*u), Size(22*u, 22*u),
            cornerRadius = CornerRadius(6*u))
        drawArc(color = SeqColors.Sky, startAngle = 196f, sweepAngle = 173f,
            useCenter = false, topLeft = Offset(24*u, 16*u), size = Size(53*u, 52*u),
            style = Stroke(width=5*u, cap=StrokeCap.Round))
        val arrow = Path().apply {
            moveTo(78*u,36*u)
            lineTo(77*u,22*u)
            lineTo(65*u,30*u)
            close()
        }
        drawPath(arrow, SeqColors.Sky)
    }
}
