@file:OptIn(ExperimentalMaterial3Api::class)

package mg.sitraka.phrasometre

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch

enum class Ecran(val titre: String) {
    ACCUEIL("Accueil"), SAISIE("Saisie des phrases"), STATS("Statistiques"), REGLAGES("Paramètres")
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val vm: AppViewModel = viewModel()
            AppRoot(vm)
        }
    }
}

@Composable
fun AppRoot(vm: AppViewModel) {
    val reglages by vm.reglages.collectAsState()
    PhrasometreTheme(reglages.theme, reglages.echelle) {
        val drawerState = rememberDrawerState(DrawerValue.Closed)
        val scope = rememberCoroutineScope()
        val snack = remember { SnackbarHostState() }
        val user by vm.utilisateur.collectAsState()
        var ecranNom by rememberSaveable { mutableStateOf(Ecran.ACCUEIL.name) }
        var apropos by remember { mutableStateOf(false) }
        val ecran = Ecran.valueOf(ecranNom)

        LaunchedEffect(Unit) { vm.messages.collect { snack.showSnackbar(it) } }

        BackHandler(enabled = drawerState.isOpen) { scope.launch { drawerState.close() } }
        BackHandler(enabled = drawerState.isClosed && ecran != Ecran.ACCUEIL) { ecranNom = Ecran.ACCUEIL.name }

        fun ouvrirMenu() { scope.launch { drawerState.open() } }

        ModalNavigationDrawer(
            drawerState = drawerState,
            gesturesEnabled = true,
            drawerContent = {
                ModalDrawerSheet {
                    EnteteDrawer(user)
                    Spacer(Modifier.height(8.dp))
                    val icones = mapOf(
                        Ecran.ACCUEIL to Icons.Default.Home,
                        Ecran.SAISIE to Icons.Default.Edit,
                        Ecran.STATS to Icons.Default.BarChart,
                        Ecran.REGLAGES to Icons.Default.Settings
                    )
                    Ecran.entries.forEach { e ->
                        NavigationDrawerItem(
                            label = { Text(e.titre) },
                            icon = { Icon(icones.getValue(e), contentDescription = null) },
                            selected = e == ecran,
                            onClick = {
                                ecranNom = e.name
                                scope.launch { drawerState.close() }
                            },
                            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                        )
                    }
                    HorizontalDivider(Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
                    NavigationDrawerItem(
                        label = { Text("À propos") },
                        icon = { Icon(Icons.Default.Info, contentDescription = null) },
                        selected = false,
                        onClick = {
                            apropos = true
                            scope.launch { drawerState.close() }
                        },
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                    )
                }
            }
        ) {
            when (ecran) {
                Ecran.ACCUEIL -> EcranAccueil(vm, snack, ::ouvrirMenu) { ecranNom = Ecran.SAISIE.name }
                Ecran.SAISIE -> EcranSaisie(vm, snack, ::ouvrirMenu)
                Ecran.STATS -> EcranStats(vm, snack, ::ouvrirMenu)
                Ecran.REGLAGES -> EcranReglages(vm, snack, ::ouvrirMenu)
            }
        }

        if (apropos) {
            AlertDialog(
                onDismissRequest = { apropos = false },
                title = { Text("Phrasomètre") },
                text = { Text("Version 1.0\n\nCompteur de phrases mensuelles : comptez, analysez et gardez vos données synchronisées avec votre compte Google.") },
                confirmButton = { TextButton(onClick = { apropos = false }) { Text("Fermer") } }
            )
        }
    }
}

@Composable
private fun EnteteDrawer(user: Utilisateur?) {
    val cs = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxWidth()
            .background(Brush.linearGradient(listOf(Color(0xFF12857F), Color(0xFF083F3D))))
            .statusBarsPadding()
            .padding(horizontal = 24.dp, vertical = 24.dp)
    ) {
        Image(
            painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = null,
            modifier = Modifier.size(64.dp).clip(CircleShape).background(Color(0x33FFFFFF))
        )
        Spacer(Modifier.height(14.dp))
        Text("Phrasomètre", style = MaterialTheme.typography.headlineSmall, color = Color.White)
        Text(
            user?.email?.ifBlank { null } ?: "Mode local, non synchronisé",
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFFCFEDEA)
        )
    }
}
