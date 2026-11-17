package com.krvpn.app.presentation.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.core.view.isVisible
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.krvpn.app.R
import com.krvpn.app.data.model.KrConfig
import com.krvpn.app.domain.tunnel.TunnelController
import com.krvpn.app.presentation.viewmodel.*
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val homeViewModel: HomeViewModel by viewModels()
    private val navController: NavController by lazy { rememberNavController() }
    private val startDestination = "home"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface {
                    KrVpnApp(navController, startDestination)
                }
            }
        }
        
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        val action = intent.action
        val data = intent.data
        
        when {
            action == Intent.ACTION_VIEW && data != null -> {
                handleDeepLink(data)
            }
            Intent.ACTION_SEND.equals(action) -> {
                handleSharedContent(intent)
            }
        }
    }

    private fun handleDeepLink(uri: Uri) {
        val link = uri.toString()
        lifecycleScope.launch {
            when {
                link.startsWith("krvpn://") -> {
                    homeViewModel.importFromLink(link)
                    navController.navigate("configs")
                }
                link.startsWith("vmess://") || link.startsWith("vless://") || 
                link.startsWith("trojan://") || link.startsWith("ssh://") -> {
                    homeViewModel.importFromLink(link)
                    navController.navigate("configs")
                }
                else -> {
                    Log.w("MainActivity", "Unknown deep link: $link")
                }
            }
        }
    }

    private fun handleSharedContent(intent: Intent) {
        val text = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return
        if (text.startsWith("krvpn://") || text.startsWith("vmess://") || 
            text.startsWith("vless://") || text.startsWith("trojan://") || text.startsWith("ssh://")) {
            lifecycleScope.launch {
                homeViewModel.importFromLink(text)
                navController.navigate("configs")
            }
        }
    }
}

@Composable
fun KrVpnApp(
    navController: NavController,
    startDestination: String
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: startDestination

    NavHost(navController, startDestination) {
        composable("home") {
            HomeScreen(
                onConfigClick = { config -> navController.navigate("config_detail/$config.id") },
                onAddConfig = { navController.navigate("config_create") },
                onSettingsClick = { navController.navigate("settings") }
            )
        }
        
        composable("configs") {
            ConfigListScreen(
                onConfigClick = { config -> navController.navigate("config_detail/$config.id") },
                onAddConfig = { navController.navigate("config_create") },
                onImportClick = { navController.navigate("config_import") }
            )
        }
        
        composable(
            route = "config_detail/${configId}",
            arguments = listOf(navArgument("configId") { type = NavType.StringType })
        ) { backStackEntry ->
            val configId = backStackEntry.getString()!!
            ConfigDetailScreen(
                configId = configId,
                onEdit = { navController.navigate("config_edit/$configId") },
                onDelete = { navController.popBackStack() }
            )
        }
        
        composable("config_create") {
            ConfigCreateScreen(onSave = { navController.popBackStack() })
        }
        
        composable(
            route = "config_edit/${configId}",
            arguments = listOf(navArgument("configId") { type = NavType.StringType })
        ) { backStackEntry ->
            val configId = backStackEntry.getString()!!
            ConfigEditScreen(configId = configId, onSave = { navController.popBackStack() })
        }
        
        composable("config_import") {
            ConfigImportScreen(onImport = { navController.popBackStack() })
        }
        
        composable("settings") {
            SettingsScreen()
        }
        
        composable("statistics") {
            StatisticsScreen()
        }
        
        composable("diagnostics") {
            DiagnosticsScreen()
        }
        
        composable("session_history") {
            SessionHistoryScreen()
        }
        
        composable("load_balance") {
            LoadBalanceScreen()
        }
        
        composable("payload_templates") {
            PayloadTemplatesScreen()
        }
        
        composable("network_tools") {
            NetworkToolsScreen()
        }
        
        composable("about") {
            AboutScreen()
        }
    }
}

// Placeholder screen composables - to be implemented
@Composable
fun HomeScreen(
    onConfigClick: (KrConfig) -> Unit,
    onAddConfig: () -> Unit,
    onSettingsClick: () -> Unit
) {
    androidx.compose.material3.Text(text = "Home Screen - TODO")
}

@Composable
fun ConfigListScreen(
    onConfigClick: (KrConfig) -> Unit,
    onAddConfig: () -> Unit,
    onImportClick: () -> Unit
) {
    androidx.compose.material3.Text(text = "Config List - TODO")
}

@Composable
fun ConfigDetailScreen(
    configId: String,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    androidx.compose.material3.Text(text = "Config Detail: $configId - TODO")
}

@Composable
fun ConfigCreateScreen(onSave: () -> Unit) {
    androidx.compose.material3.Text(text = "Create Config - TODO")
}

@Composable
fun ConfigEditScreen(configId: String, onSave: () -> Unit) {
    androidx.compose.material3.Text(text = "Edit Config: $configId - TODO")
}

@Composable
fun ConfigImportScreen(onImport: () -> Unit) {
    androidx.compose.material3.Text(text = "Import Config - TODO")
}

@Composable
fun SettingsScreen() {
    androidx.compose.material3.Text(text = "Settings - TODO")
}

@Composable
fun StatisticsScreen() {
    androidx.compose.material3.Text(text = "Statistics - TODO")
}

@Composable
fun DiagnosticsScreen() {
    androidx.compose.material3.Text(text = "Diagnostics - TODO")
}

@Composable
fun SessionHistoryScreen() {
    androidx.compose.material3.Text(text = "Session History - TODO")
}

@Composable
fun LoadBalanceScreen() {
    androidx.compose.material3.Text(text = "Load Balance - TODO")
}

@Composable
fun PayloadTemplatesScreen() {
    androidx.compose.material3.Text(text = "Payload Templates - TODO")
}

@Composable
fun NetworkToolsScreen() {
    androidx.compose.material3.Text(text = "Network Tools - TODO")
}

@Composable
fun AboutScreen() {
    androidx.compose.material3.Text(text = "About KRVPN - TODO")
}
