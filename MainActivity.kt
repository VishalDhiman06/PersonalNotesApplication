package com.lpu.personalnotesapplication

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.*
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.lpu.personalnotesapplication.data.NoteItem
import com.lpu.personalnotesapplication.data.PreferencesManager
import com.lpu.personalnotesapplication.data.searchNotes
import com.lpu.personalnotesapplication.ui.theme.PersonalNotesApplicationTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class PermissionItem(
    val title: String, val permission: String, val icon: ImageVector,
    val actionLabel: String = "Allow", val isCameraAction: Boolean = false
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val prefs = PreferencesManager(applicationContext)
        setContent {
            val isDark by prefs.isDarkThemeFlow.collectAsState(initial = false)
            PersonalNotesApplicationTheme(darkTheme = isDark) { AppContent(prefs) }
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
    var showSearchBar by remember { mutableStateOf(false) }
    var showPermissionDialog by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    val savedNotes = remember { mutableStateListOf<NoteItem>() }
    val searchResults = remember(savedNotes, searchQuery) { searchNotes(savedNotes.toList(), searchQuery) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(username) { currentUsername = username }

    val createDocument = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri: Uri? ->
        uri?.let {
            scope.launch {
                writeTextToUri(context.contentResolver, it, noteText)
                snackbarHostState.showSnackbar("Note Saved Successfully ✓")
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        val granted = permissions.filterValues { it }.keys
        val message = if (granted.isNotEmpty()) "Granted: ${granted.size} permission(s)." else "Permission request was denied."
        scope.launch { snackbarHostState.showSnackbar(message) }
    }

    val openCamera = {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            if (intent.resolveActivity(context.packageManager) != null) context.startActivity(intent)
            else Toast.makeText(context, "No camera app found", Toast.LENGTH_SHORT).show()
        } else {
            permissionLauncher.launch(arrayOf(Manifest.permission.CAMERA))
        }
    }

    val openAppSettings = {
        context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
        })
    }

    val permissionItems = listOfNotNull(
        PermissionItem("Camera", Manifest.permission.CAMERA, Icons.Filled.CameraAlt, isCameraAction = true),
        PermissionItem("Location", Manifest.permission.ACCESS_FINE_LOCATION, Icons.Filled.LocationOn),
        PermissionItem("Microphone", Manifest.permission.RECORD_AUDIO, Icons.Filled.Mic),
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q)
            PermissionItem("Storage", Manifest.permission.WRITE_EXTERNAL_STORAGE, Icons.Filled.Folder) else null
    )

    val missingPermissions = permissionItems.map { it.permission }
        .filter { ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED }
        .distinct()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopBar(showSearchBar, searchQuery, { showSearchBar = !showSearchBar },
                { searchQuery = it }, { searchQuery = "" }, { showPermissionDialog = true })
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                val suggestedName = currentUsername.ifBlank { "note" } + "_" + System.currentTimeMillis() + ".txt"
                val needsLegacyPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
                val hasPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
                if (noteText.isNotBlank()) {
                    savedNotes.add(0, NoteItem(title = currentUsername.ifBlank { "Note" }, content = noteText, createdAt = System.currentTimeMillis()))
                }
                if (needsLegacyPermission && !hasPermission) permissionLauncher.launch(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE))
                else createDocument.launch(suggestedName)
            }, containerColor = MaterialTheme.colorScheme.primary) {
                Icon(Icons.Filled.Save, contentDescription = "Save Note")
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.padding(innerPadding).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { UsernameCard(currentUsername) { currentUsername = it; scope.launch { prefs.setUsername(it) } } }
            item { ThemeCard(isDark) { scope.launch { prefs.setDarkTheme(it) } } }
            item { CategoryCard(selectedCategory) { selectedCategory = it } }
            item {
                NoteCard(
                    note = noteText,
                    onNoteChange = { noteText = it },
                    onCopyClick = {
                        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Note", noteText))
                        scope.launch { snackbarHostState.showSnackbar("Copied to clipboard! 📋") }
                    },
                    onClearClick = { showClearDialog = true }
                )
            }
            if (showSearchBar || searchQuery.isNotBlank()) {
                item { SearchNotesCard(searchQuery, searchResults) { searchQuery = "" } }
            }
            item { Spacer(Modifier.height(20.dp)) }
        }

        if (showClearDialog) {
            AlertDialog(
                onDismissRequest = { showClearDialog = false },
                title = { Text("Clear Note?") },
                text = { Text("Are you sure you want to clear this note? This action cannot be undone.") },
                confirmButton = {
                    TextButton(onClick = {
                        noteText = ""; showClearDialog = false
                        scope.launch { snackbarHostState.showSnackbar("Note cleared") }
                    }) { Text("Clear") }
                },
                dismissButton = { TextButton(onClick = { showClearDialog = false }) { Text("Cancel") } }
            )
        }

        if (showPermissionDialog) {
            AlertDialog(
                onDismissRequest = { showPermissionDialog = false },
                title = { Text("App Permissions") },
                text = {
                    Column {
                        Text("Allow camera, microphone, location, and storage from here. If Android blocks a request, use App Settings.")
                        Spacer(Modifier.height(8.dp))
                        permissionItems.forEach { item ->
                            val isGranted = ContextCompat.checkSelfPermission(context, item.permission) == PackageManager.PERMISSION_GRANTED
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(item.icon, contentDescription = item.title)
                                    Spacer(Modifier.width(8.dp))
                                    Text(item.title)
                                }
                                TextButton(onClick = { if (item.isCameraAction) openCamera() else permissionLauncher.launch(arrayOf(item.permission)) }) {
                                    Text(if (isGranted) "Granted" else "Allow")
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            TextButton(onClick = {
                                if (missingPermissions.isEmpty()) scope.launch { snackbarHostState.showSnackbar("All permissions are already granted.") }
                                else permissionLauncher.launch(missingPermissions.toTypedArray())
                            }) { Text("Allow All") }
                            TextButton(onClick = openAppSettings) { Text("App Settings") }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { showPermissionDialog = false }) { Text("Close") } },
                dismissButton = { TextButton(onClick = { showPermissionDialog = false }) { Text("Cancel") } }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopBar(
    showSearchBar: Boolean, searchQuery: String, onSearchToggle: () -> Unit,
    onSearchQueryChange: (String) -> Unit, onSearchQueryClear: () -> Unit, onSettingsClick: () -> Unit
) {
    Column {
        TopAppBar(
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.AutoMirrored.Filled.Note, contentDescription = "Notes", tint = MaterialTheme.colorScheme.primary)
                    Text("Personal Notes", style = MaterialTheme.typography.headlineSmall)
                }
            },
            actions = {
                IconButton(onClick = onSearchToggle) { Icon(Icons.Filled.Search, contentDescription = "Search notes") }
                IconButton(onClick = onSettingsClick) { Icon(Icons.Filled.Settings, contentDescription = "Settings") }
            }
        )
        if (showSearchBar) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery, onValueChange = onSearchQueryChange, modifier = Modifier.weight(1f), singleLine = true,
                    placeholder = { Text("Search your notes") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = "Search") },
                    trailingIcon = {
                        if (searchQuery.isNotBlank()) {
                            IconButton(onClick = onSearchQueryClear) { Icon(Icons.Filled.Clear, contentDescription = "Clear search") }
                        }
                    }
                )
                TextButton(onClick = onSearchToggle) { Text("Close") }
            }
        }
    }
}

@Composable
fun UsernameCard(username: String, onUsernameChange: (String) -> Unit) {
    Card(shape = RoundedCornerShape(16.dp), elevation = CardDefaults.cardElevation(6.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Username", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = username, onValueChange = onUsernameChange,
                leadingIcon = { Icon(Icons.Filled.Person, contentDescription = "User") },
                placeholder = { Text("Enter username") }, modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun ThemeCard(isDark: Boolean, onToggle: (Boolean) -> Unit) {
    Card(shape = RoundedCornerShape(16.dp), elevation = CardDefaults.cardElevation(6.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Theme", style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Brightness4, contentDescription = "Dark Mode")
                    Spacer(Modifier.width(12.dp))
                    Text("Dark Mode")
                }
                Switch(checked = isDark, onCheckedChange = onToggle)
            }
        }
    }
}

@Composable
fun NoteCard(note: String, onNoteChange: (String) -> Unit, onCopyClick: () -> Unit = {}, onClearClick: () -> Unit = {}) {
    Card(
        shape = RoundedCornerShape(16.dp), elevation = CardDefaults.cardElevation(8.dp),
        modifier = Modifier.fillMaxWidth().border(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
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
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = note, onValueChange = onNoteChange, modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Write your note here...") }, maxLines = 12
            )
        }
    }
}

@Composable
fun SearchNotesCard(searchQuery: String, searchResults: List<NoteItem>, onClearSearch: () -> Unit) {
    Card(shape = RoundedCornerShape(12.dp), elevation = CardDefaults.cardElevation(4.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Search Results", style = MaterialTheme.typography.titleSmall)
                if (searchQuery.isNotBlank()) TextButton(onClick = onClearSearch) { Text("Clear") }
            }
            when {
                searchQuery.isBlank() -> Text("Use the search button to find saved notes.", style = MaterialTheme.typography.bodyMedium)
                searchResults.isEmpty() -> Text("No notes matched your search.", style = MaterialTheme.typography.bodyMedium)
                else -> {
                    Spacer(Modifier.height(8.dp))
                    searchResults.take(5).forEach { note ->
                        Column(Modifier.padding(vertical = 4.dp)) {
                            Text(note.title, style = MaterialTheme.typography.bodyMedium)
                            Text(note.content.take(80).ifBlank { "Empty note" }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CategoryCard(selectedCategory: String, onCategorySelected: (String) -> Unit) {
    val categories = listOf("General", "Work", "Personal", "Ideas", "Todo")
    Card(shape = RoundedCornerShape(16.dp), elevation = CardDefaults.cardElevation(6.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Category", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                categories.forEach { category ->
                    FilterChip(selected = selectedCategory == category, onClick = { onCategorySelected(category) }, label = { Text(category) })
                }
            }
        }
    }
}

suspend fun writeTextToUri(resolver: android.content.ContentResolver, uri: Uri, text: String) {
    withContext(Dispatchers.IO) {
        resolver.openOutputStream(uri)?.use { it.write(text.toByteArray()); it.flush() }
    }
}

@Preview(showBackground = true)
@Composable
fun AppPreview() {
    val dummyPrefs = PreferencesManager(LocalContext.current.applicationContext)
    PersonalNotesApplicationTheme { AppContent(dummyPrefs) }
}
