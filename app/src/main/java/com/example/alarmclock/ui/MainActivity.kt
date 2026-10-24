package com.example.alarmclock.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val context = LocalContext.current
            val colors = if (android.os.Build.VERSION.SDK_INT >= 31)
                dynamicDarkColorScheme(context) else darkColorScheme()

            MaterialTheme(colorScheme = colors) {
                Surface {
                    val nav = rememberNavController()
                    NavHost(nav, startDestination = "list") {
                        composable("list") {
                            AlarmListScreen(
                                onAddAlarm = { nav.navigate("edit/0") },
                                onEditAlarm = { id -> nav.navigate("edit/$id") },
                                onOpenGroups = { nav.navigate("groups") },
                                onOpenPermissions = { nav.navigate("permissions") }
                            )
                        }
                        composable("edit/{id}") { entry ->
                            val id = entry.arguments?.getString("id")?.toLongOrNull() ?: 0L
                            AlarmEditScreen(alarmId = id, onDone = { nav.popBackStack() })
                        }
                        composable("groups") {
                            GroupsScreen(onBack = { nav.popBackStack() })
                        }
                        composable("permissions") {
                            PermissionsScreen(onBack = { nav.popBackStack() })
                        }
                    }
                }
            }
        }
    }
}
