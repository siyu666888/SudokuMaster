package com.example.sudokumaster.nav

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState

/** 底部常驻导航的四个入口。 */
private data class Tab(
    val route: String,
    val label: String,
    val icon: ImageVector,        // 未选中（描边）
    val iconFilled: ImageVector,  // 选中（填充）
    val color: Color              // 专属彩色（未选中态）
)

private val TABS = listOf(
    Tab(Routes.HOME, "首页", Icons.Outlined.Home, Icons.Filled.Home, Color(0xFF2F9E6E)),
    Tab(Routes.HISTORY, "历史", Icons.Outlined.History, Icons.Filled.History, Color(0xFFF59E0B)),
    Tab(Routes.ACHIEVEMENTS, "成就", Icons.Outlined.EmojiEvents, Icons.Filled.EmojiEvents, Color(0xFF8B5CF6)),
    Tab(Routes.SETTINGS, "设置", Icons.Outlined.Settings, Icons.Filled.Settings, Color(0xFF3B82F6))
)

/**
 * 底部常驻导航栏：
 *  - 选中态（图标 + 文字 + 椭圆指示块）统一绑定全局主题色，换肤时全局一致；
 *  - 未选中保留各 Tab 专属淡彩描边图标；
 *  - 导航采用 popUpTo(start) + launchSingleTop + restoreState，保证返回栈干净。
 */
@Composable
fun AppBottomBar(navController: NavController) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    NavigationBar {
        TABS.forEach { tab ->
            val selected = currentRoute == tab.route
            NavigationBarItem(
                selected = selected,
                onClick = {
                    if (!selected) {
                        navController.navigate(tab.route) {
                            popUpTo(Routes.HOME) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                },
                icon = {
                    Icon(
                        imageVector = if (selected) tab.iconFilled else tab.icon,
                        contentDescription = tab.label,
                        tint = if (selected) MaterialTheme.colorScheme.primary
                        else tab.color.copy(alpha = 0.5f)
                    )
                },
                label = {
                    Text(
                        tab.label,
                        color = if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    // 选中态椭圆指示块绑定主题主色（避免默认淡紫与主题割裂）
                    indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                ),
                alwaysShowLabel = true
            )
        }
    }
}
