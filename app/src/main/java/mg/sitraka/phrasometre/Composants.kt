@file:OptIn(ExperimentalMaterial3Api::class)

package mg.sitraka.phrasometre

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.YearMonth

@Composable
fun BarreTitre(titre: String, onMenu: () -> Unit, actions: @Composable RowScope.() -> Unit = {}) {
    CenterAlignedTopAppBar(
        title = { Text(titre, fontWeight = FontWeight.SemiBold) },
        navigationIcon = {
            IconButton(onClick = onMenu) { Icon(Icons.Default.Menu, contentDescription = "Ouvrir le menu") }
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
    )
}

/** Bouton officiel « Se connecter avec Google » (logo G multicolore, charte Google). */
@Composable
fun BoutonGoogle(onClick: () -> Unit, modifier: Modifier = Modifier, enCours: Boolean = false) {
    val sombre = estSombre()
    val fond = if (sombre) Color(0xFF131314) else Color.White
    val bord = if (sombre) Color(0xFF8E918F) else Color(0xFF747775)
    val texte = if (sombre) Color(0xFFE3E3E3) else Color(0xFF1F1F1F)
    Surface(
        onClick = onClick,
        enabled = !enCours,
        modifier = modifier.height(48.dp),
        shape = RoundedCornerShape(24.dp),
        color = fond,
        border = BorderStroke(1.dp, bord)
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (enCours) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = texte)
            } else {
                Image(painterResource(R.drawable.ic_google_g), contentDescription = null, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Text(
                if (enCours) "Connexion…" else "Se connecter avec Google",
                color = texte,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1
            )
        }
    }
}

@Composable
fun SelecteurMois(mois: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    var dialogue by remember { mutableStateOf(false) }
    val ym = YearMonth.parse(mois)
    Row(
        modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        IconButton(onClick = { onChange(ym.minusMonths(1).toString()) }) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Mois précédent")
        }
        TextButton(onClick = { dialogue = true }) {
            Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(moisLibelle(mois), style = MaterialTheme.typography.titleMedium)
        }
        IconButton(onClick = { onChange(ym.plusMonths(1).toString()) }) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Mois suivant")
        }
    }
    if (dialogue) {
        DialogMois(ym, onDismiss = { dialogue = false }, onChoix = {
            onChange(it.toString())
            dialogue = false
        })
    }
}

@Composable
fun DialogMois(courant: YearMonth, onDismiss: () -> Unit, onChoix: (YearMonth) -> Unit) {
    var annee by remember { mutableIntStateOf(courant.year) }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fermer") } },
        title = {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                IconButton(onClick = { annee-- }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Année précédente") }
                Text("$annee")
                IconButton(onClick = { annee++ }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Année suivante") }
            }
        },
        text = {
            Column {
                for (ligne in 0 until 4) {
                    Row(Modifier.fillMaxWidth()) {
                        for (col in 0 until 3) {
                            val m = ligne * 3 + col + 1
                            val ym = YearMonth.of(annee, m)
                            FilterChip(
                                selected = ym == courant,
                                onClick = { onChoix(ym) },
                                label = { Text(nomMoisCourt(m), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) },
                                modifier = Modifier.weight(1f).padding(2.dp)
                            )
                        }
                    }
                }
            }
        }
    )
}

@Composable
fun BadgeCompteur(n: Int) {
    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.tertiaryContainer) {
        Box(
            Modifier.defaultMinSize(minWidth = 52.dp, minHeight = 44.dp).padding(horizontal = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            AnimatedContent(
                targetState = n,
                label = "compteur",
                transitionSpec = {
                    (slideInVertically { it } + fadeIn()) togetherWith (slideOutVertically { -it } + fadeOut())
                }
            ) { valeur ->
                Text(
                    "$valeur",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
        }
    }
}

@Composable
fun CarteKpi(
    titre: String,
    valeur: String,
    detail: String?,
    icone: ImageVector,
    modifier: Modifier = Modifier,
    detailCouleur: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icone, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(titre, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            Spacer(Modifier.height(6.dp))
            Text(valeur, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
            if (detail != null) {
                Text(detail, style = MaterialTheme.typography.labelMedium, color = detailCouleur, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** Diagramme en bâtons : s'étire pour peu de barres, défile horizontalement sinon. */
@Composable
fun GrapheBarres(barres: List<Barre>, modifier: Modifier = Modifier, hauteur: Dp = 160.dp) {
    val max = (barres.maxOfOrNull { it.valeur } ?: 0).coerceAtLeast(1)
    if (barres.size <= 12) {
        Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            barres.forEach { Colonne(it, max, hauteur, Modifier.weight(1f)) }
        }
    } else {
        Row(
            modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            barres.forEach { Colonne(it, max, hauteur, Modifier.width(28.dp)) }
        }
    }
}

@Composable
private fun Colonne(b: Barre, max: Int, hauteur: Dp, modifier: Modifier) {
    val fraction by animateFloatAsState(targetValue = b.valeur.toFloat() / max, animationSpec = tween(500), label = "barre")
    val plein = b.valeur > 0
    Column(
        modifier.height(hauteur + 44.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Bottom
    ) {
        if (plein) {
            Text(
                "${b.valeur}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
        Spacer(Modifier.height(2.dp))
        Box(
            Modifier
                .fillMaxWidth(0.7f)
                .height((hauteur * fraction).coerceAtLeast(if (plein) 4.dp else 2.dp))
                .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                .background(if (plein) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)
        )
        Spacer(Modifier.height(4.dp))
        Text(
            b.etiquette,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            softWrap = false
        )
    }
}

@Composable
fun LigneClassement(ligne: LigneStat, max: Int, selectionnee: Boolean, onClick: (() -> Unit)?) {
    val fraction by animateFloatAsState(
        targetValue = if (max > 0) ligne.valeur.toFloat() / max else 0f,
        animationSpec = tween(500),
        label = "classement"
    )
    val base = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
    val m = if (onClick != null) base.clickableSansRipple(onClick) else base
    Column(
        m
            .background(if (selectionnee) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(ligne.libelle, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (ligne.detail.isNotEmpty()) {
                    Text(ligne.detail, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.width(10.dp))
            Text("${ligne.valeur}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Box(
                Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(6.dp).clip(RoundedCornerShape(3.dp))
                    .background(MaterialTheme.colorScheme.tertiary)
            )
        }
    }
}

private fun Modifier.clickableSansRipple(onClick: () -> Unit): Modifier =
    this.then(Modifier.clickable(onClick = onClick))
