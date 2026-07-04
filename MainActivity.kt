package com.lpu.personalnotesapplication

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Note
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.lpu.personalnotesapplication.data.PreferencesManager
import com.lpu.personalnotesapplication.ui.theme.PersonalNotesApplicationTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val prefs = PreferencesManager(applicationContext)

        setContent {
            val isDark by prefs.isDarkThemeFlow.collectAsState(initial = false)
            PersonalNotesApplicationTheme(darkTheme = isDark) {
                AppContent(prefs = prefs)
            }
        }
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppContent(prefs: PreferencesManager) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isDark by prefs.isDarkThemeFlow.collectAsState(initial = false)
    val username by prefs.usernameFlow.collectAsState(initial = "")
    var currentUsername by remember { mutableStateOf(username) }
    var noteText by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("General") }
    var showClearDialog by remember { mutableStateOf(false) }
    var lastEditTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(username) {
        currentUsername = username
    }
    val snackbarHostState = remember { SnackbarHostState() }
    val createDocument = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                writeTextToUri(context.contentResolver, uri, noteText)
                snackbarHostState.showSnackbar("Note Saved Successfully ✓")
            }
        }
    }
    val storagePermissionLauncher = rememberLauncherForActivityResult(RequestPermission()) { granted ->
        if (granted) {
            scope.launch {
                snackbarHostState.showSnackbar("Storage permission granted. You can now save notes.")
            }
        }
    }
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = { TopBar() },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    val suggestedName = currentUsername.ifBlank { "note" } + "_" + System.currentTimeMillis() + ".txt"
                    val needsLegacyPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
                    val hasPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
                    if (needsLegacyPermission && !hasPermission) {
                        storagePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    } else {
                        createDocument.launch(suggestedName)
                    }
                },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Filled.Save, contentDescription = "Save Note")
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                UsernameCard(
                    username = currentUsername,
                    onUsernameChange = {
                        currentUsername = it
                        scope.launch { prefs.setUsername(it) }
                    }
                )
            }
            item {
                ThemeCard(isDark = isDark, onToggle = { scope.launch { prefs.setDarkTheme(it) } })
            }
            item {
                CategoryCard(
                    selectedCategory = selectedCategory,
                    onCategorySelected = { selectedCategory = it }
                )
            }
            item {
                NoteCard(
                    note = noteText,
                    onNoteChange = {
                        noteText = it
                        lastEditTime = System.currentTimeMillis()
                    },
                    onCopyClick = {
                        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("Note", noteText)
                        clipboard.setPrimaryClip(clip)
                        scope.launch { snackbarHostState.showSnackbar("Copied to clipboard! 📋") }
                    },
                    onClearClick = { showClearDialog = true }
                )
            }
            item {
                NoteStatsCard(
                    noteText = noteText,
                    category = selectedCategory,
                    lastEditTime = lastEditTime
                )
            }
            item {
                Spacer(modifier = Modifier.height(20.dp))
            }
        }
        if (showClearDialog) {
            AlertDialog(
                onDismissRequest = { showClearDialog = false },
                title = { Text("Clear Note?") },
                text = { Text("Are you sure you want to clear this note? This action cannot be undone.") },
                confirmButton = {
                    TextButton(onClick = {
                        noteText = ""
                        showClearDialog = false
                        scope.launch { snackbarHostState.showSnackbar("Note cleared") }
                    }) {
                        Text("Clear")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showClearDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopBar() {
    TopAppBar(
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.Note, contentDescription = "Notes", tint = MaterialTheme.colorScheme.primary)
                Text(text = "Personal Notes", style = MaterialTheme.typography.headlineSmall)
            }
        },
        actions = {
            IconButton(onClick = { /* open settings */ }) {
                Icon(Icons.Filled.Settings, contentDescription = "Settings")
            }
        }
    )
}
@Composable
fun UsernameCard(username: String, onUsernameChange: (String) -> Unit) {
    Card(
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Username", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = username,
                onValueChange = onUsernameChange,
                leadingIcon = { Icon(Icons.Filled.Person, contentDescription = "User") },
                placeholder = { Text("Enter username") },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
@Composable
fun ThemeCard(isDark: Boolean, onToggle: (Boolean) -> Unit) {
    Card(
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Theme", style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Brightness4, contentDescription = "Dark Mode")
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Dark Mode")
                }
                Switch(checked = isDark, onCheckedChange = onToggle)
            }
        }
    }
}
@Composable
fun NoteCard(
    note: String,
    onNoteChange: (String) -> Unit,
    onCopyClick: () -> Unit = {},
    onClearClick: () -> Unit = {}
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Your Note", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(onClick = onCopyClick, modifier = Modifier.width(36.dp)) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = "Copy", modifier = Modifier.width(20.dp))
                    }
                    IconButton(onClick = onClearClick, modifier = Modifier.width(36.dp)) {
                        Icon(Icons.Filled.Clear, contentDescription = "Clear", modifier = Modifier.width(20.dp))
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = note,
                onValueChange = onNoteChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Write your note here...") },
                maxLines = 12
            )
        }
    }
}
@Composable
fun NoteStatsCard(noteText: String, category: String, lastEditTime: Long) {
    val wordCount = if (noteText.isBlank()) 0 else noteText.trim().split(Regex("\\s+")).size
    val charCount = noteText.length
    val formatter = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
    val editTimeString = formatter.format(Date(lastEditTime))
    Card(
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Note Statistics", style = MaterialTheme.typography.titleSmall)
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatItem("Words", wordCount.toString())
                StatItem("Characters", charCount.toString())
                StatItem("Category", category)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text("Last edited: $editTimeString", style = MaterialTheme.typography.labelSmall)
        }
    }
}
@Composable
fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}
@Composable
fun CategoryCard(selectedCategory: String, onCategorySelected: (String) -> Unit) {
    val categories = listOf("General", "Work", "Personal", "Ideas", "Todo")
    Card(
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Category", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                categories.forEach { category ->
                    FilterChip(
                        selected = selectedCategory == category,
                        onClick = { onCategorySelected(category) },
                        label = { Text(category) }
                    )
                }
            }
        }
    }
}
suspend fun writeTextToUri(resolver: android.content.ContentResolver, uri: Uri, text: String) {
    withContext(Dispatchers.IO) {
        resolver.openOutputStream(uri)?.use { out ->
            out.write(text.toByteArray())
            out.flush()
        }
    }
}
@Preview(showBackground = true)
@Composable
fun AppPreview() {
    val dummyPrefs = PreferencesManager(LocalContext.current.applicationContext)
    PersonalNotesApplicationTheme {
        AppContent(prefs = dummyPrefs)
    }
}
