package com.heihun.easytiermd3.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Article
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.heihun.easytiermd3.feature.home.HomeScreen
import com.heihun.easytiermd3.feature.logs.LogsScreen
import com.heihun.easytiermd3.feature.network.NetworkEditorScreen
import com.heihun.easytiermd3.feature.network.NetworkListScreen
import com.heihun.easytiermd3.feature.peer.PeerDetailScreen
import com.heihun.easytiermd3.feature.peer.PeerListScreen
import com.heihun.easytiermd3.feature.settings.SettingsScreen

private data class NavItem(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
)

private val navItems = listOf(
    NavItem(Routes.HOME, "主页", Icons.Filled.Home, Icons.Outlined.Home),
    NavItem(Routes.NETWORKS, "网络", Icons.Filled.Dns, Icons.Outlined.Dns),
    NavItem(Routes.PEERS, "节点", Icons.Filled.Hub, Icons.Outlined.Hub),
    NavItem(Routes.LOGS, "日志", Icons.Filled.Article, Icons.Outlined.Article),
    NavItem(Routes.SETTINGS, "设置", Icons.Filled.Settings, Icons.Outlined.Settings),
)

@Composable
fun AppNavHost(
    navController: NavHostController = rememberNavController(),
    modifier: Modifier = Modifier,
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val bottomBarVisible = currentRoute in Routes.TOP_LEVEL_ROUTES

    Scaffold(
        modifier = modifier,
        bottomBar = {
            if (bottomBarVisible) {
                NavigationBar {
                    navItems.forEach { item ->
                        NavigationBarItem(
                            selected = currentRoute == item.route,
                            onClick = { navController.navigateSingleTopTo(item.route) },
                            icon = {
                                Icon(
                                    imageVector = if (currentRoute == item.route) {
                                        item.selectedIcon
                                    } else {
                                        item.unselectedIcon
                                    },
                                    contentDescription = item.label,
                                )
                            },
                            label = { Text(text = item.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier.padding(padding),
        ) {
            composable(Routes.HOME) {
                HomeScreen(
                    onNavigateToNetworks = { navController.navigateSingleTopTo(Routes.NETWORKS) },
                    onNavigateToCreateNetwork = {
                        navController.navigate(Routes.networkEditor(null))
                    },
                )
            }
            composable(Routes.NETWORKS) {
                NetworkListScreen(
                    onNavigateToCreate = { navController.navigate(Routes.networkEditor(null)) },
                    onNavigateToEdit = { id ->
                        navController.navigate(Routes.networkEditor(id))
                    },
                )
            }
            composable(
                route = Routes.NETWORK_EDITOR_ARG,
                arguments = listOf(
                    navArgument("networkId") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    }
                ),
            ) {
                NetworkEditorScreen(
                    onDone = { navController.popBackStack() },
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Routes.PEERS) {
                PeerListScreen(
                    onPeerClick = { id -> navController.navigate(Routes.peerDetail(id)) },
                )
            }
            composable(Routes.PEER_DETAIL) {
                PeerDetailScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.LOGS) {
                LogsScreen()
            }
            composable(Routes.SETTINGS) {
                SettingsScreen()
            }
        }
    }
}

private fun NavHostController.navigateSingleTopTo(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}