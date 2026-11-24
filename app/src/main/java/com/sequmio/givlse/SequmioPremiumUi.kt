package com.sequmio.givlse

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Shared section title used by Runner, My App, App List and History. */
@Composable
internal fun SeqSectionTitle(
    title: String, icon: ImageVector,
    modifier: Modifier = Modifier,
    tint: Color = SeqColors.Teal,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = tint.copy(alpha = 0.10f),
            border = BorderStroke(1.dp, tint.copy(alpha = 0.20f))
        ) {
            Icon(icon, contentDescription = null, tint = tint,
                modifier = Modifier.padding(9.dp).size(21.dp))
        }
        Spacer(Modifier.width(10.dp))
        Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f), maxLines = 1,
            overflow = TextOverflow.Ellipsis)
        trailing?.invoke()
    }
}

@Composable
internal fun SeqChip(
    text: String,
    color: Color = SeqColors.Teal,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null
) {
    Surface(
        modifier, shape = RoundedCornerShape(12.dp),
        color = color.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.35f))
    ) {
        Row(Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (icon != null) Icon(icon, contentDescription = null, tint = color,
                modifier = Modifier.size(13.dp))
            Text(text, fontSize = 11.sp, color = color, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
internal fun SeqIconAction(
    icon: ImageVector, label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = SeqColors.Primary
) {
    IconButton(onClick = onClick, modifier = modifier.size(44.dp), enabled = enabled) {
        Icon(icon, contentDescription = label,
            tint = if (enabled) tint else SeqColors.Muted,
            modifier = Modifier.size(22.dp))
    }
}

/** Translucent tinted pill: soft highlight, restrained rim, no opaque action fill. */
@Composable
internal fun SeqGlassButton(
    title: String, tint: Color, onClick: () -> Unit,
    modifier: Modifier = Modifier, enabled: Boolean = true,
    compact: Boolean = false, icon: ImageVector? = null,
    inactiveTint: Color = SeqColors.Muted
) {
    val accent = if (enabled) tint else inactiveTint
    val shape = RoundedCornerShape(50)
    val fill = Brush.verticalGradient(listOf(
        accent.copy(alpha = if (enabled) 0.22f else 0.12f),
        accent.copy(alpha = if (enabled) 0.07f else 0.04f)))
    val rim = Brush.verticalGradient(listOf(
        accent.copy(alpha = 0.58f), accent.copy(alpha = 0.20f)))
    Box(modifier.clip(shape).background(fill)) {
        OutlinedButton(onClick = onClick, enabled = enabled,
            modifier = Modifier.then(if (compact) Modifier.heightIn(min = 40.dp)
                else Modifier.fillMaxWidth().heightIn(min = 48.dp)),
            shape = shape, border = BorderStroke(1.dp, rim),
            contentPadding = if (compact) PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                else PaddingValues(horizontal = 12.dp, vertical = 10.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = Color.Transparent, contentColor = accent,
                disabledContainerColor = Color.Transparent, disabledContentColor = accent)) {
            if (icon != null) {
                Icon(icon, null, Modifier.size(18.dp))
                Spacer(Modifier.width(7.dp))
            }
            Text(title, fontSize = if (compact) 11.sp else 14.sp,
                fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
        }
    }
}
