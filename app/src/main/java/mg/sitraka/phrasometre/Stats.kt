package mg.sitraka.phrasometre

import android.content.Context
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import java.io.OutputStream
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.Month
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as JTextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale

val FR: Locale = Locale.FRENCH

enum class Periode(val libelle: String) { JOUR("Jour"), SEMAINE("Semaine"), MOIS("Mois"), ANNEE("Année") }
enum class ModeStats(val libelle: String) { PHRASES("Phrases"), GROUPES("Groupes") }

data class Plage(val debut: LocalDate, val fin: LocalDate)
data class LigneStat(val cle: String, val libelle: String, val valeur: Int, val detail: String)
data class Barre(val etiquette: String, val valeur: Int)

data class Resultat(
    val total: Int,
    val precedent: Int,
    val joursActifs: Int,
    val moyenneJourActif: Double,
    val meilleurJour: String?,
    val meilleurJourValeur: Int,
    val lignes: List<LigneStat>,
    val barres: List<Barre>,
    val titreBarres: String,
    val serieActuelle: Int,
    val meilleureSerie: Int
)

fun plage(periode: Periode, ancre: LocalDate): Plage = when (periode) {
    Periode.JOUR -> Plage(ancre, ancre)
    Periode.SEMAINE -> {
        val d = ancre.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        Plage(d, d.plusDays(6))
    }
    Periode.MOIS -> Plage(ancre.withDayOfMonth(1), ancre.withDayOfMonth(ancre.lengthOfMonth()))
    Periode.ANNEE -> Plage(LocalDate.of(ancre.year, 1, 1), LocalDate.of(ancre.year, 12, 31))
}

fun decaler(periode: Periode, ancre: LocalDate, n: Int): LocalDate = when (periode) {
    Periode.JOUR -> ancre.plusDays(n.toLong())
    Periode.SEMAINE -> ancre.plusWeeks(n.toLong())
    Periode.MOIS -> ancre.plusMonths(n.toLong())
    Periode.ANNEE -> ancre.plusYears(n.toLong())
}

fun moisCourant(): String = YearMonth.now().toString()

fun moisLibelle(m: String): String =
    YearMonth.parse(m).format(DateTimeFormatter.ofPattern("MMMM yyyy", FR)).replaceFirstChar { it.uppercase() }

fun nomMoisCourt(n: Int): String = Month.of(n).getDisplayName(JTextStyle.SHORT, FR)

fun libellePeriode(periode: Periode, ancre: LocalDate): String {
    val r = plage(periode, ancre)
    return when (periode) {
        Periode.JOUR -> ancre.format(DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", FR)).replaceFirstChar { it.uppercase() }
        Periode.SEMAINE -> r.debut.format(DateTimeFormatter.ofPattern("d MMM", FR)) + " – " +
            r.fin.format(DateTimeFormatter.ofPattern("d MMM yyyy", FR))
        Periode.MOIS -> moisLibelle(YearMonth.from(ancre).toString())
        Periode.ANNEE -> ancre.year.toString()
    }
}

private fun moisDuGroupe(c: Clic): Int = c.mois.substring(5, 7).toIntOrNull() ?: 0

fun clicsDans(clics: List<Clic>, periode: Periode, ancre: LocalDate, moisSel: Set<Int>): List<Clic> {
    val r = plage(periode, ancre)
    return clics.filter { c ->
        val j = try { LocalDate.parse(c.jour) } catch (e: Exception) { return@filter false }
        !j.isBefore(r.debut) && !j.isAfter(r.fin) && (periode != Periode.ANNEE || moisDuGroupe(c) in moisSel)
    }
}

fun serieActuelle(clics: List<Clic>): Int {
    val jours = clics.mapTo(HashSet()) { it.jour }
    var j = LocalDate.now()
    if (j.toString() !in jours) j = j.minusDays(1)
    var n = 0
    while (j.toString() in jours) {
        n++
        j = j.minusDays(1)
    }
    return n
}

fun meilleureSerie(clics: List<Clic>): Int {
    val jours = clics.mapNotNull { try { LocalDate.parse(it.jour) } catch (e: Exception) { null } }.distinct().sorted()
    var best = 0
    var cur = 0
    var prev: LocalDate? = null
    for (j in jours) {
        cur = if (prev != null && prev.plusDays(1) == j) cur + 1 else 1
        if (cur > best) best = cur
        prev = j
    }
    return best
}

private fun heure(ts: Long): Int = Instant.ofEpochMilli(ts).atZone(ZoneId.systemDefault()).hour

fun calculer(
    d: Donnees,
    periode: Periode,
    mode: ModeStats,
    ancre: LocalDate,
    moisSel: Set<Int>,
    filtre: String?
): Resultat {
    val r = plage(periode, ancre)
    val tous = clicsDans(d.clics, periode, ancre, moisSel)

    fun garde(c: Clic): Boolean =
        filtre == null || (if (mode == ModeStats.PHRASES) c.phraseId == filtre else c.mois == filtre)

    val sel = tous.filter { garde(it) }

    val moisPlage: List<String> = when (periode) {
        Periode.ANNEE -> moisSel.sorted().map { YearMonth.of(r.debut.year, it).toString() }
        else -> {
            val fin = YearMonth.from(r.fin)
            generateSequence(YearMonth.from(r.debut)) { if (it < fin) it.plusMonths(1) else null }
                .map { it.toString() }.toList()
        }
    }

    // Classement
    val comptes = tous.groupingBy { it.phraseId }.eachCount()
    val lignes: List<LigneStat> = if (mode == ModeStats.PHRASES) {
        d.phrases
            .filter { it.mois in moisPlage || (comptes[it.id] ?: 0) > 0 }
            .map { LigneStat(it.id, it.texte, comptes[it.id] ?: 0, moisLibelle(it.mois)) }
            .filter { periode != Periode.ANNEE || it.valeur > 0 }
            .sortedByDescending { it.valeur }
    } else {
        val groupes = (moisPlage.filter { m -> d.groupes.any { it.mois == m } } + tous.map { it.mois })
            .distinct().sorted()
        groupes.map { m ->
            val nb = d.phrases.count { it.mois == m }
            LigneStat(m, moisLibelle(m), tous.count { it.mois == m }, if (nb > 1) "$nb phrases" else "$nb phrase")
        }
    }

    // Graphique
    val barres: List<Barre>
    val titre: String
    when (periode) {
        Periode.JOUR -> {
            val m = sel.groupingBy { heure(it.ts) }.eachCount()
            barres = (0..23).map { Barre("${it}h", m[it] ?: 0) }
            titre = "Clics par heure"
        }
        Periode.SEMAINE -> {
            val m = sel.groupingBy { it.jour }.eachCount()
            barres = (0..6).map { i ->
                val j = r.debut.plusDays(i.toLong())
                Barre(j.dayOfWeek.getDisplayName(JTextStyle.SHORT, FR).take(3), m[j.toString()] ?: 0)
            }
            titre = "Clics par jour"
        }
        Periode.MOIS -> {
            val m = sel.groupingBy { it.jour }.eachCount()
            barres = (1..r.debut.lengthOfMonth()).map { dd ->
                Barre("$dd", m[r.debut.withDayOfMonth(dd).toString()] ?: 0)
            }
            titre = "Clics par jour"
        }
        Periode.ANNEE -> {
            val m = sel.groupingBy { LocalDate.parse(it.jour).monthValue }.eachCount()
            barres = moisSel.sorted().map { Barre(nomMoisCourt(it).removeSuffix("."), m[it] ?: 0) }
            titre = "Clics par mois"
        }
    }

    val parJour = sel.groupingBy { it.jour }.eachCount()
    val meilleur = parJour.maxByOrNull { it.value }
    val precedent = clicsDans(d.clics, periode, decaler(periode, ancre, -1), moisSel).count { garde(it) }

    return Resultat(
        total = sel.size,
        precedent = precedent,
        joursActifs = parJour.size,
        moyenneJourActif = if (parJour.isEmpty()) 0.0 else sel.size.toDouble() / parJour.size,
        meilleurJour = meilleur?.key?.let {
            LocalDate.parse(it).format(DateTimeFormatter.ofPattern("d MMM yyyy", FR))
        },
        meilleurJourValeur = meilleur?.value ?: 0,
        lignes = lignes,
        barres = barres,
        titreBarres = titre,
        serieActuelle = serieActuelle(d.clics),
        meilleureSerie = meilleureSerie(d.clics)
    )
}

// ───────────── Export ─────────────

private fun csv(s: String) = "\"" + s.replace("\"", "\"\"") + "\""

fun genererCsv(d: Donnees): String {
    val phrases = d.phrases.associateBy { it.id }
    val sb = StringBuilder("\uFEFF")
    sb.append("Date;Heure;Groupe;Phrase\n")
    val fmt = DateTimeFormatter.ofPattern("HH:mm")
    d.clics.sortedBy { it.ts }.forEach { c ->
        val heure = Instant.ofEpochMilli(c.ts).atZone(ZoneId.systemDefault()).format(fmt)
        sb.append(c.jour).append(';').append(heure).append(';')
            .append(csv(moisLibelle(c.mois))).append(';')
            .append(csv(phrases[c.phraseId]?.texte ?: "")).append('\n')
    }
    return sb.toString()
}

fun genererPdf(out: OutputStream, titre: String, sousTitre: String, kpis: List<String>, lignes: List<Pair<String, Int>>) {
    val doc = PdfDocument()
    val w = 595
    val h = 842
    val marge = 44f
    val pTitre = Paint().apply { textSize = 24f; isFakeBoldText = true; color = 0xFF0B5C59.toInt(); isAntiAlias = true }
    val pSous = Paint().apply { textSize = 13f; color = 0xFF55615F.toInt(); isAntiAlias = true }
    val pKpi = Paint().apply { textSize = 13f; isFakeBoldText = true; color = 0xFF171D1C.toInt(); isAntiAlias = true }
    val pTexte = Paint().apply { textSize = 12f; color = 0xFF171D1C.toInt(); isAntiAlias = true }
    val pNombre = Paint().apply { textSize = 12f; isFakeBoldText = true; color = 0xFF0B5C59.toInt(); isAntiAlias = true; textAlign = Paint.Align.RIGHT }
    val pTrait = Paint().apply { color = 0xFFDDE5E3.toInt(); strokeWidth = 1f }

    var num = 1
    var page = doc.startPage(PdfDocument.PageInfo.Builder(w, h, num).create())
    var c = page.canvas
    var y = marge + 18

    fun nouvellePage() {
        doc.finishPage(page)
        num++
        page = doc.startPage(PdfDocument.PageInfo.Builder(w, h, num).create())
        c = page.canvas
        y = marge + 10
    }

    c.drawText(titre, marge, y, pTitre); y += 24
    c.drawText(sousTitre, marge, y, pSous); y += 30
    kpis.forEach { c.drawText(it, marge, y, pKpi); y += 20 }
    y += 14
    c.drawLine(marge, y, w - marge, y, pTrait); y += 22

    val largeurTexte = w - 2 * marge - 60f
    lignes.forEach { (t, v) ->
        if (y > h - marge) nouvellePage()
        val n = pTexte.breakText(t, true, largeurTexte, null)
        val texte = if (n < t.length) t.substring(0, n).trimEnd() + "…" else t
        c.drawText(texte, marge, y, pTexte)
        c.drawText("$v", w - marge, y, pNombre)
        y += 8
        c.drawLine(marge, y, w - marge, y, pTrait)
        y += 16
    }
    doc.finishPage(page)
    doc.writeTo(out)
    doc.close()
}

fun ecrireUri(ctx: Context, uri: Uri, f: (OutputStream) -> Unit): Boolean = try {
    ctx.contentResolver.openOutputStream(uri)?.use(f)
    true
} catch (e: Exception) {
    false
}
