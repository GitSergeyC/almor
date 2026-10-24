package com.example.alarmclock.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupsScreen(
    onBack: () -> Unit,
    viewModel: GroupsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showDialog by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Группы будильников") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showDialog = true }) {
                Icon(Icons.Default.Add, "Создать группу")
            }
        }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    "Выключение группы глушит все будильники внутри, но сохраняет их личные настройки: при обратном включении вернётся прежнее состояние.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            items(state.groups, key = { it.group.id }) { row ->
                Card(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.padding(16.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Spacer(
                            Modifier.size(16.dp).clip(CircleShape)
                                .background(Color(row.group.colorArgb))
                        )
                        Spacer(Modifier.size(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(row.group.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "${row.alarmCount} будильников",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        IconButton(onClick = { viewModel.deleteGroup(row.group.id) }) {
                            Icon(Icons.Default.Delete, "Удалить")
                        }
                        Switch(
                            checked = row.group.enabled,
                            onCheckedChange = { viewModel.toggle(row.group.id, it) }
                        )
                    }
                }
            }
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Новая группа") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("Название") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.createGroup(newName)
                        newName = ""
                        showDialog = false
                    },
                    enabled = newName.isNotBlank()
                ) { Text("Создать") }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) { Text("Отмена") }
            }
        )
    }
}
