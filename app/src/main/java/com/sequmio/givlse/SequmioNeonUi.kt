package com.sequmio.givlse

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal enum class SeqHeroKind { CATALOG, DEVELOPER, HISTORY }

/** Compact outlined glyphs, traced as vector paths instead of scaling raster mockups. */
@Composable
private fun SeqNavGlyph(index: Int, tint: Color, glow: Boolean, modifier: Modifier) {
    Canvas(modifier) {
        val u = size.minDimension / 24f
        fun p(x: Float, y: Float) = Offset(x*u, y*u)
        fun line(vararg xy: Float, closed: Boolean = false) {
            val path = Path().apply {
                moveTo(xy[0]*u, xy[1]*u)
                for (i in 2 until xy.size step 2) lineTo(xy[i]*u, xy[i+1]*u)
                if (closed) close()
            }
            if (glow) {
                drawPath(path, tint.copy(alpha = .09f),
                    style = Stroke(6*u, cap = StrokeCap.Round, join = StrokeJoin.Round))
                drawPath(path, tint.copy(alpha = .22f),
                    style = Stroke(3.5f*u, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            drawPath(path, tint, style = Stroke(1.65f*u,
                cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        when (index) {
            0 -> line(7f,4f, 20f,12f, 7f,20f, closed = true)
            1 -> {
                line(2.5f,8f, 12f,3f, 21.5f,8f, 12f,13f, closed = true)
                line(3f,12f,12f,17f,21f,12f)
                line(3f,16f,12f,21f,21f,16f)
            }
            2 -> {
                line(8f,6f,2.5f,12f,8f,18f)
                line(16f,6f,21.5f,12f,16f,18f)
                line(14.5f,3f,9.5f,21f)
            }
            else -> {
                if (glow) drawArc(tint.copy(alpha=.18f), -158f, 315f, false,
                    topLeft=p(3f,3f), size=Size(18*u,18*u),
                    style=Stroke(5*u, cap=StrokeCap.Round))
                drawArc(tint, -158f, 315f, false, topLeft=p(3f,3f),
                    size=Size(18*u,18*u), style=Stroke(1.65f*u, cap=StrokeCap.Round))
                line(2.5f,5f,2.5f,10f,7f,10f)
                line(12f,7f,12f,12f,16f,14.5f)
            }
        }
    }
}

@Composable
internal fun SeqNeonNavigation(selected: Int, onSelect: (Int) -> Unit) {
    val labels = listOf("Runner", "App List", "My App", "History")
    val accents = listOf(SeqColors.Primary, SeqColors.Teal, SeqColors.Primary, SeqColors.Primary)
    Row(
        Modifier.fillMaxWidth().background(Color(0xFF0B1224))
            .padding(horizontal = 12.dp, vertical = 6.dp).selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        labels.forEachIndexed { index, label ->
            val active = index == selected
            val shape = RoundedCornerShape(17.dp)
            val accent = accents[index]
            val tile = if (active) Modifier
                .background(Brush.linearGradient(listOf(
                    Color(0xFF28435D), Color(0xFF202F49), Color(0xFF18283D))), shape)
                .border(1.dp, Brush.linearGradient(listOf(
                    SeqColors.Sky.copy(alpha=.68f), Color(0xFF42617E),
                    SeqColors.Primary.copy(alpha=.24f))), shape) else Modifier
            Box(Modifier.weight(1f).heightIn(min = 64.dp).clip(shape)
                .then(tile).selectable(selected = active, role = Role.Tab,
                    onClick = { onSelect(index) })) {
                if (active) Canvas(Modifier.matchParentSize()) {
                    drawRoundRect(Brush.radialGradient(listOf(
                        accent.copy(alpha=.19f), Color.Transparent),
                        center=Offset(size.width*.49f,size.height*.18f),
                        radius=size.width*.58f), cornerRadius=CornerRadius(17.dp.toPx()))
                }
                Column(Modifier.fillMaxWidth().padding(vertical=8.dp),
                    horizontalAlignment=Alignment.CenterHorizontally,
                    verticalArrangement=Arrangement.spacedBy(5.dp)) {
                    SeqNavGlyph(index, if (active) accent else Color(0xFFD2DBED),
                        glow=active, modifier=Modifier.size(24.dp))
                    Text(label, color=if(active) SeqColors.Text else Color(0xFFCCD5E6),
                        fontSize=11.sp, fontWeight=if(active) FontWeight.SemiBold else FontWeight.Medium,
                        maxLines=1, softWrap=false)
                }
            }
        }
    }
}

@Composable
internal fun SeqRunnerAtmosphere(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        drawRect(Brush.radialGradient(listOf(Color(0xFF124655).copy(alpha=.40f), Color.Transparent),
            center=Offset(size.width*.03f,size.height*.12f), radius=size.width*.78f))
        drawRect(Brush.radialGradient(listOf(Color(0xFF42246B).copy(alpha=.36f), Color.Transparent),
            center=Offset(size.width*.91f,size.height*.22f), radius=size.width*.72f))
    }
}

@Composable
internal fun SeqRunnerHero() {
    Column(Modifier.fillMaxWidth().padding(top=1.dp,bottom=4.dp),
        horizontalAlignment=Alignment.CenterHorizontally,
        verticalArrangement=Arrangement.spacedBy(3.dp)) {
        Box(Modifier.size(106.dp), contentAlignment=Alignment.Center) {
            Canvas(Modifier.matchParentSize()) {
                val center=Offset(size.width*.5f,size.height*.5f)
                drawCircle(Brush.radialGradient(listOf(
                    SeqColors.Sky.copy(alpha=.20f), SeqColors.Teal.copy(alpha=.12f),
                    SeqColors.Primary.copy(alpha=.07f),Color.Transparent),
                    center=center,radius=size.width*.50f),radius=size.width*.50f,center=center)
            }
            Box(Modifier.size(82.dp)) {
                SequmioMark(82.dp)
                Canvas(Modifier.matchParentSize()) {
                    val inset=1.dp.toPx()
                    val tl=Offset(inset,inset)
                    val box=Size(size.width-2*inset,size.height-2*inset)
                    val corners=CornerRadius(18.dp.toPx())
                    val rim=Brush.linearGradient(listOf(SeqColors.Sky,Color(0xFF346B9C),
                        SeqColors.Primary.copy(alpha=.94f)), start=Offset.Zero,
                        end=Offset(size.width,size.height))
                    drawRoundRect(rim, topLeft=tl,size=box,cornerRadius=corners,
                        style=Stroke(3.dp.toPx()),alpha=.14f)
                    drawRoundRect(rim,topLeft=tl,size=box,cornerRadius=corners,
                        style=Stroke(1.dp.toPx()),alpha=.92f)
                    drawRoundRect(Brush.radialGradient(listOf(SeqColors.Sky.copy(alpha=.30f),Color.Transparent),
                        center=Offset(size.width*.09f,size.height*.04f),radius=size.width*.65f),
                        cornerRadius=corners)
                    drawRoundRect(Brush.radialGradient(listOf(SeqColors.Primary.copy(alpha=.27f),Color.Transparent),
                        center=Offset(size.width*.95f,size.height*.96f),radius=size.width*.60f),
                        cornerRadius=corners)
                }
            }
        }
        Text("Sequmio", fontSize=31.sp, fontWeight=FontWeight.Bold)
        Text("Mobile Test Runner", color=SeqColors.Muted, fontSize=14.sp)
    }
}

/** Scalable decorative header artwork. Text and actions remain native UI. */
@Composable
internal fun SeqNeonHeader(kind: SeqHeroKind, title: String, subtitle: String,
    trailing: (@Composable () -> Unit)? = null) {
    val index = when(kind) { SeqHeroKind.CATALOG -> 1; SeqHeroKind.DEVELOPER -> 2; SeqHeroKind.HISTORY -> 3 }
    val accent = if(kind==SeqHeroKind.CATALOG) SeqColors.Teal else SeqColors.Primary
    Box(Modifier.fillMaxWidth().heightIn(min=96.dp)) {
        Canvas(Modifier.matchParentSize()) { heroArt(kind) }
        Row(Modifier.fillMaxWidth().padding(top=16.dp,bottom=18.dp),
            verticalAlignment=Alignment.CenterVertically) {
            Box(Modifier.size(43.dp).clip(RoundedCornerShape(12.dp))
                .background(Brush.linearGradient(listOf(
                    accent.copy(alpha=.24f), Color(0xFF1B2540), Color(0xFF141C32))))
                .border(1.dp,Brush.linearGradient(listOf(
                    accent.copy(alpha=.52f),SeqColors.Primary.copy(alpha=.15f))),
                    RoundedCornerShape(12.dp)), contentAlignment=Alignment.Center) {
                SeqNavGlyph(index,accent,true,Modifier.size(27.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontSize=22.sp,fontWeight=FontWeight.Bold,color=SeqColors.Text)
                Spacer(Modifier.height(3.dp))
                Text(subtitle,fontSize=12.sp,color=Color(0xFFD0D9EC))
            }
            Spacer(Modifier.width(if(trailing==null) 70.dp else 24.dp))
            trailing?.invoke()
        }
    }
}

private fun DrawScope.heroArt(kind: SeqHeroKind) {
    val w=size.width; val h=size.height
    if(w<=0f || h<=0f) return
    val cyan=Color(0xFF41DCE8); val violet=Color(0xFF9767F2)
    drawRect(Brush.radialGradient(listOf(cyan.copy(alpha=.18f),Color.Transparent),
        center=Offset(w*.86f,h*.53f),radius=h*.95f))
    drawRect(Brush.radialGradient(listOf(violet.copy(alpha=.16f),Color.Transparent),
        center=Offset(w*.98f,h*.73f),radius=h*.82f))
    val crest=Path().apply {
        moveTo(w*.52f,h*1.01f)
        cubicTo(w*.68f,h*.94f,w*.76f,h*.28f,w*.88f,h*.43f)
        cubicTo(w*.97f,h*.51f,w*.98f,h*.79f,w*1.08f,h*.28f)
    }
    val waveFill=Path().apply {
        addPath(crest); lineTo(w*1.08f,h); lineTo(w*.52f,h); close()
    }
    drawPath(waveFill,Brush.verticalGradient(listOf(cyan.copy(alpha=.17f),Color.Transparent),
        startY=h*.35f,endY=h))
    val edge=Brush.horizontalGradient(listOf(Color.Transparent,cyan.copy(alpha=.83f),
        violet.copy(alpha=.50f)),startX=w*.52f,endX=w)
    drawPath(crest,edge,style=Stroke(10.dp.toPx()),alpha=.06f)
    drawPath(crest,edge,style=Stroke(4.dp.toPx()),alpha=.17f)
    drawPath(crest,edge,style=Stroke(.8.dp.toPx()),alpha=.75f)
    if(kind==SeqHeroKind.HISTORY) return
    val cardW=h*.73f; val cardH=if(kind==SeqHeroKind.CATALOG) h*.94f else h*.74f
    val x=w-cardW-h*.12f; val y=h*.04f
    val center=Offset(x+cardW*.5f,y+cardH*.5f)
    val tl=Offset(x,y); val sz=Size(cardW,cardH); val rad=CornerRadius(h*.10f)
    rotate(if(kind==SeqHeroKind.CATALOG) 9f else 15f,pivot=center) {
        val rim=Brush.linearGradient(listOf(cyan,Color(0xFF3683B7),violet),
            start=tl,end=Offset(x+cardW,y+cardH*.8f))
        drawRoundRect(rim,topLeft=tl,size=sz,cornerRadius=rad,style=Stroke(8.dp.toPx()),alpha=.07f)
        drawRoundRect(rim,topLeft=tl,size=sz,cornerRadius=rad,style=Stroke(3.dp.toPx()),alpha=.20f)
        drawRoundRect(Brush.linearGradient(listOf(Color(0xFF204B62),Color(0xFF142841),
            Color(0xFF191833)),start=tl,end=Offset(x+cardW,y+cardH)),
            topLeft=tl,size=sz,cornerRadius=rad)
        drawRoundRect(rim,topLeft=tl,size=sz,cornerRadius=rad,style=Stroke(1.dp.toPx()),alpha=.85f)
        drawCircle(cyan.copy(alpha=.85f),radius=h*.018f,center=Offset(x+cardW*.13f,y+cardH*.12f))
        drawLine(cyan.copy(alpha=.55f),Offset(x+cardW*.25f,y+cardH*.12f),
            Offset(x+cardW*.64f,y+cardH*.12f),strokeWidth=h*.029f,cap=StrokeCap.Round)
        if(kind==SeqHeroKind.CATALOG) {
            val cell=cardW*.28f
            for(row in 0..2) for(col in 0..1) {
                val cellTop=Offset(x+cardW*.17f+col*cardW*.38f,y+cardH*.24f+row*cardH*.25f)
                val alpha=if(row==2) .33f else .85f
                val cellBrush=Brush.linearGradient(listOf(
                    (if(col==0) cyan else Color(0xFF658ADB)).copy(alpha=alpha),
                    Color(0xFF203A65)),start=cellTop,end=Offset(cellTop.x+cell,cellTop.y+cell))
                drawRoundRect(cellBrush,topLeft=cellTop,size=Size(cell,cell*.94f),
                    cornerRadius=CornerRadius(cell*.24f))
            }
        } else {
            fun pt(a:Float,b:Float)=Offset(x+cardW*a,y+cardH*b)
            fun glyph(color:Color,vararg points:Offset) {
                for(k in 0 until points.size-1) {
                    drawLine(color.copy(alpha=.20f),points[k],points[k+1],
                        strokeWidth=h*.060f,cap=StrokeCap.Round)
                    drawLine(color,points[k],points[k+1],strokeWidth=h*.023f,cap=StrokeCap.Round)
                }
            }
            glyph(cyan,pt(.37f,.39f),pt(.17f,.57f),pt(.37f,.75f))
            glyph(violet,pt(.66f,.39f),pt(.86f,.57f),pt(.66f,.75f))
            glyph(Color(0xFF94D7FF),pt(.57f,.33f),pt(.46f,.80f))
        }
    }
    // Keep foreground text dominant and fade the illustration into the page.
    drawRect(Brush.horizontalGradient(listOf(Color(0xFF0E1629),Color.Transparent),
        startX=w*.49f,endX=w*.70f),topLeft=Offset(w*.49f,0f),size=Size(w*.21f,h),alpha=.55f)
}
