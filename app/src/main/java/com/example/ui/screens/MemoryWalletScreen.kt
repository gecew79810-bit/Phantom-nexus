package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ai.ConsoleMessage
import com.example.ai.MaxOrchestrator
import com.example.data.local.MemoryWalletItemEntity
import com.example.data.local.UserPreferenceEntity
import com.example.ui.theme.CrimsonNeon
import com.example.ui.theme.CyanNeon
import com.example.ui.theme.DeepSpaceBlack
import com.example.ui.theme.EmeraldNeon
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.GlassSurface
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VioletNeon
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MemoryWalletScreen(
    orchestrator: MaxOrchestrator,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val walletItems by orchestrator.memoryRepository.walletItems.collectAsState(initial = emptyList())
    val preferences by orchestrator.memoryRepository.allPreferences.collectAsState(initial = emptyList())
    val messages by orchestrator.memoryRepository.allMessages.collectAsState(initial = emptyList())
    val messageCount by orchestrator.memoryRepository.messageCount.collectAsState(initial = 0)
    val voiceMessageCount by orchestrator.memoryRepository.voiceMessageCount.collectAsState(initial = 0)

    // Selected Tab: 0 = User Preferences, 1 = Chat History, 2 = Vault & Secrets
    var selectedTab by remember { mutableIntStateOf(0) }

    // Vault Dialog state
    var newMemoryTitle by remember { mutableStateOf("") }
    var newMemoryContent by remember { mutableStateOf("") }
    var showAddVaultDialog by remember { mutableStateOf(false) }

    // Preference Dialog state
    var prefKeyInput by remember { mutableStateOf("") }
    var prefValueInput by remember { mutableStateOf("") }
    var prefCategoryInput by remember { mutableStateOf("GENERAL") }
    var showAddPrefDialog by remember { mutableStateOf(false) }

    // Search query for Chat History
    var chatSearchQuery by remember { mutableStateOf("") }

    // Category filter for Preferences
    var selectedPrefCategory by remember { mutableStateOf("ALL") }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = DeepSpaceBlack,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .background(DeepSpaceBlack)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("memory_wallet_back_btn")) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TextPrimary
                        )
                    }
                    Column {
                        Text(
                            text = "ROOM DATA STORAGE",
                            color = TextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "Encrypted SQLite / SQLCipher • On-Device AI OS",
                            color = CyanNeon,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0x3300E5FF))
                        .border(1.dp, CyanNeon, RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = EmeraldNeon,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = "ACTIVE",
                            color = EmeraldNeon,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Stats Summary Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = GlassSurface),
            border = androidx.compose.foundation.BorderStroke(1.dp, GlassBorder)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "Preferences", color = TextMuted, fontSize = 10.sp)
                    Text(
                        text = "${preferences.size}",
                        color = CyanNeon,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Box(modifier = Modifier.width(1.dp).height(24.dp).background(GlassBorder))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "Stored Chats", color = TextMuted, fontSize = 10.sp)
                    Text(
                        text = "$messageCount",
                        color = EmeraldNeon,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Box(modifier = Modifier.width(1.dp).height(24.dp).background(GlassBorder))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "Voice Turns", color = TextMuted, fontSize = 10.sp)
                    Text(
                        text = "$voiceMessageCount",
                        color = VioletNeon,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Box(modifier = Modifier.width(1.dp).height(24.dp).background(GlassBorder))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "Vault Notes", color = TextMuted, fontSize = 10.sp)
                    Text(
                        text = "${walletItems.size}",
                        color = TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        // Navigation Tabs Segmented Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0x22112233))
                .border(1.dp, GlassBorder, RoundedCornerShape(10.dp))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            val tabs = listOf(
                Triple(0, "Preferences", Icons.Default.Settings),
                Triple(1, "Chat History", Icons.AutoMirrored.Filled.Chat),
                Triple(2, "Vault Items", Icons.Default.Key)
            )

            tabs.forEach { (index, title, icon) ->
                val isSelected = selectedTab == index
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSelected) CyanNeon else Color.Transparent)
                        .clickable { selectedTab = index }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = if (isSelected) DeepSpaceBlack else TextSecondary,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = title,
                            color = if (isSelected) DeepSpaceBlack else TextSecondary,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }
        }

        // Tab Content
        when (selectedTab) {
            0 -> PreferencesTabContent(
                preferences = preferences,
                selectedCategory = selectedPrefCategory,
                onSelectCategory = { selectedPrefCategory = it },
                showAddDialog = showAddPrefDialog,
                onToggleAddDialog = { showAddPrefDialog = it },
                keyInput = prefKeyInput,
                onKeyChange = { prefKeyInput = it },
                valueInput = prefValueInput,
                onValueChange = { prefValueInput = it },
                categoryInput = prefCategoryInput,
                onCategoryChange = { prefCategoryInput = it },
                onSavePreference = { k, v, c ->
                    coroutineScope.launch {
                        orchestrator.memoryRepository.savePreference(k, v, c)
                        prefKeyInput = ""
                        prefValueInput = ""
                        showAddPrefDialog = false
                    }
                },
                onDeletePreference = { k ->
                    coroutineScope.launch {
                        orchestrator.memoryRepository.deletePreference(k)
                    }
                }
            )

            1 -> ChatHistoryTabContent(
                messages = messages,
                searchQuery = chatSearchQuery,
                onSearchChange = { chatSearchQuery = it },
                onDeleteMessage = { id ->
                    coroutineScope.launch {
                        orchestrator.memoryRepository.deleteMessage(id)
                    }
                },
                onClearAll = {
                    coroutineScope.launch {
                        orchestrator.clearConversationHistory()
                    }
                }
            )

            2 -> VaultTabContent(
                walletItems = walletItems,
                showAddDialog = showAddVaultDialog,
                onToggleAddDialog = { showAddVaultDialog = it },
                titleInput = newMemoryTitle,
                onTitleChange = { newMemoryTitle = it },
                contentInput = newMemoryContent,
                onContentChange = { newMemoryContent = it },
                onSaveItem = { title, content ->
                    coroutineScope.launch {
                        orchestrator.memoryRepository.saveMemoryWalletItem(title, content)
                        orchestrator.nlpBrain.storeMemory("$title: $content", "WALLET")
                        newMemoryTitle = ""
                        newMemoryContent = ""
                        showAddVaultDialog = false
                    }
                },
                onDeleteItem = { id ->
                    coroutineScope.launch {
                        orchestrator.memoryRepository.deleteMemoryWalletItem(id)
                    }
                }
            )
        }
    }
}
}

@Composable
private fun PreferencesTabContent(
    preferences: List<UserPreferenceEntity>,
    selectedCategory: String,
    onSelectCategory: (String) -> Unit,
    showAddDialog: Boolean,
    onToggleAddDialog: (Boolean) -> Unit,
    keyInput: String,
    onKeyChange: (String) -> Unit,
    valueInput: String,
    onValueChange: (String) -> Unit,
    categoryInput: String,
    onCategoryChange: (String) -> Unit,
    onSavePreference: (String, String, String) -> Unit,
    onDeletePreference: (String) -> Unit
) {
    val categories = listOf("ALL", "AI_ENGINE", "VOICE", "GENERAL", "SYSTEM", "PRIVACY")
    val filteredPrefs = if (selectedCategory == "ALL") {
        preferences
    } else {
        preferences.filter { it.category.equals(selectedCategory, ignoreCase = true) }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Category chips row
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(categories) { cat ->
                val isSelected = selectedCategory == cat
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (isSelected) Color(0x3300E5FF) else GlassSurface)
                        .border(1.dp, if (isSelected) CyanNeon else GlassBorder, RoundedCornerShape(16.dp))
                        .clickable { onSelectCategory(cat) }
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = cat,
                        color = if (isSelected) CyanNeon else TextSecondary,
                        fontSize = 10.5.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }

        // Add Preference Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "STORED PREFERENCES (${filteredPrefs.size})",
                color = TextMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )

            Button(
                onClick = { onToggleAddDialog(!showAddDialog) },
                colors = ButtonDefaults.buttonColors(containerColor = CyanNeon, contentColor = DeepSpaceBlack),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.testTag("add_preference_btn")
            ) {
                Icon(imageVector = if (showAddDialog) Icons.Default.Close else Icons.Default.Add, contentDescription = null, modifier = Modifier.size(13.dp))
                Spacer(modifier = Modifier.size(4.dp))
                Text(text = if (showAddDialog) "Close" else "Add Config", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }

        if (showAddDialog) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0x2200E5FF)),
                border = androidx.compose.foundation.BorderStroke(1.dp, CyanNeon)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(text = "NEW PREFERENCE ENTRY", color = CyanNeon, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = keyInput,
                        onValueChange = onKeyChange,
                        placeholder = { Text("Key (e.g. max_temperature, auto_dark_mode)", color = TextMuted, fontSize = 11.sp) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(6.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyanNeon,
                            unfocusedBorderColor = GlassBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        )
                    )
                    OutlinedTextField(
                        value = valueInput,
                        onValueChange = onValueChange,
                        placeholder = { Text("Value (e.g. 0.7, true, swara)", color = TextMuted, fontSize = 11.sp) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(6.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyanNeon,
                            unfocusedBorderColor = GlassBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        )
                    )
                    OutlinedTextField(
                        value = categoryInput,
                        onValueChange = onCategoryChange,
                        placeholder = { Text("Category (AI_ENGINE, VOICE, GENERAL, PRIVACY)", color = TextMuted, fontSize = 11.sp) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(6.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyanNeon,
                            unfocusedBorderColor = GlassBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        )
                    )
                    Button(
                        onClick = {
                            if (keyInput.isNotBlank() && valueInput.isNotBlank()) {
                                onSavePreference(keyInput.trim(), valueInput.trim(), categoryInput.trim().ifEmpty { "GENERAL" })
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldNeon, contentColor = DeepSpaceBlack),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.fillMaxWidth().testTag("save_preference_btn")
                    ) {
                        Text("Persist to Room Database", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                }
            }
        }

        // List of Preferences
        if (filteredPrefs.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "No preferences recorded in this category.", color = TextMuted, fontSize = 12.sp)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredPrefs, key = { it.key }) { pref ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(containerColor = GlassSurface),
                        border = androidx.compose.foundation.BorderStroke(1.dp, GlassBorder)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = pref.key,
                                        color = TextPrimary,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(Color(0x33AA00FF))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(text = pref.category, color = VioletNeon, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = "Value: ${pref.value}",
                                    color = EmeraldNeon,
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Updated: " + SimpleDateFormat("MMM dd, yyyy hh:mm a", Locale.getDefault()).format(Date(pref.updatedAt)),
                                    color = TextMuted,
                                    fontSize = 9.5.sp
                                )
                            }

                            // Quick toggle if boolean
                            if (pref.value.equals("true", ignoreCase = true) || pref.value.equals("false", ignoreCase = true)) {
                                val isChecked = pref.value.toBoolean()
                                Switch(
                                    checked = isChecked,
                                    onCheckedChange = { checked ->
                                        onSavePreference(pref.key, checked.toString(), pref.category)
                                    },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = CyanNeon,
                                        checkedTrackColor = Color(0x3300E5FF),
                                        uncheckedThumbColor = TextMuted,
                                        uncheckedTrackColor = Color.DarkGray
                                    ),
                                    modifier = Modifier.size(36.dp)
                                )
                            }

                            IconButton(
                                onClick = { onDeletePreference(pref.key) },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete Preference",
                                    tint = CrimsonNeon,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatHistoryTabContent(
    messages: List<ConsoleMessage>,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    onDeleteMessage: (String) -> Unit,
    onClearAll: () -> Unit
) {
    val filteredMessages = if (searchQuery.isBlank()) {
        messages
    } else {
        messages.filter { it.text.contains(searchQuery, ignoreCase = true) || it.sender.contains(searchQuery, ignoreCase = true) }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Search bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchChange,
            placeholder = { Text("Search conversation archive...", color = TextMuted, fontSize = 12.sp) },
            leadingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = null, tint = CyanNeon, modifier = Modifier.size(16.dp)) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchChange("") }) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Clear", tint = TextMuted, modifier = Modifier.size(16.dp))
                    }
                }
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = CyanNeon,
                unfocusedBorderColor = GlassBorder,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary
            )
        )

        // Chat count & Clear action row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "PERSISTED MESSAGES (${filteredMessages.size})",
                color = TextMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )

            Button(
                onClick = onClearAll,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0x33FF0055), contentColor = CrimsonNeon),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.testTag("clear_chat_archive_btn")
            ) {
                Icon(imageVector = Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(12.dp))
                Spacer(modifier = Modifier.size(4.dp))
                Text("Clear All", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }

        if (filteredMessages.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (searchQuery.isNotEmpty()) "No messages match '$searchQuery'" else "Chat history is empty.",
                    color = TextMuted,
                    fontSize = 12.sp
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredMessages, key = { it.id }) { msg ->
                    val isUser = msg.sender == "USER"
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(containerColor = if (isUser) Color(0x1800E5FF) else GlassSurface),
                        border = androidx.compose.foundation.BorderStroke(1.dp, if (isUser) Color(0x3300E5FF) else GlassBorder)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(if (isUser) CyanNeon else EmeraldNeon),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (isUser) Icons.Default.Person else Icons.Default.SmartToy,
                                        contentDescription = null,
                                        tint = DeepSpaceBlack,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = if (isUser) "USER" else "MAX AI",
                                            color = if (isUser) CyanNeon else EmeraldNeon,
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        if (msg.isVoice) {
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(Color(0x33AA00FF))
                                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(imageVector = Icons.Default.Mic, contentDescription = null, tint = VioletNeon, modifier = Modifier.size(10.dp))
                                                    Spacer(modifier = Modifier.size(2.dp))
                                                    Text(text = "VOICE", color = VioletNeon, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }
                                        Text(text = "• ${msg.timestamp}", color = TextMuted, fontSize = 10.sp)
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = msg.text,
                                        color = TextPrimary,
                                        fontSize = 12.5.sp,
                                        lineHeight = 17.sp
                                    )
                                }
                            }

                            IconButton(
                                onClick = { onDeleteMessage(msg.id) },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete Message",
                                    tint = TextMuted,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VaultTabContent(
    walletItems: List<MemoryWalletItemEntity>,
    showAddDialog: Boolean,
    onToggleAddDialog: (Boolean) -> Unit,
    titleInput: String,
    onTitleChange: (String) -> Unit,
    contentInput: String,
    onContentChange: (String) -> Unit,
    onSaveItem: (String, String) -> Unit,
    onDeleteItem: (String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "PRIVATE MEMORIES & WALLET KEYS",
                color = TextMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Button(
                onClick = { onToggleAddDialog(!showAddDialog) },
                colors = ButtonDefaults.buttonColors(containerColor = CyanNeon, contentColor = DeepSpaceBlack),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.testTag("add_memory_wallet_button")
            ) {
                Icon(imageVector = if (showAddDialog) Icons.Default.Close else Icons.Default.Add, contentDescription = null, modifier = Modifier.size(13.dp))
                Spacer(modifier = Modifier.size(4.dp))
                Text(text = if (showAddDialog) "Close" else "Add Entry", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }

        if (showAddDialog) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0x2200E5FF)),
                border = androidx.compose.foundation.BorderStroke(1.dp, CyanNeon)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(text = "ADD ENCRYPTED VAULT ENTRY", color = CyanNeon, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = titleInput,
                        onValueChange = onTitleChange,
                        placeholder = { Text("Title or Key (e.g. WiFi Password, Secret Note)", color = TextMuted, fontSize = 11.sp) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(6.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyanNeon,
                            unfocusedBorderColor = GlassBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        )
                    )
                    OutlinedTextField(
                        value = contentInput,
                        onValueChange = onContentChange,
                        placeholder = { Text("Encrypted Content payload...", color = TextMuted, fontSize = 11.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(6.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyanNeon,
                            unfocusedBorderColor = GlassBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        )
                    )
                    Button(
                        onClick = {
                            if (titleInput.isNotBlank() && contentInput.isNotBlank()) {
                                onSaveItem(titleInput.trim(), contentInput.trim())
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldNeon, contentColor = DeepSpaceBlack),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.fillMaxWidth().testTag("save_vault_item_button")
                    ) {
                        Text("Save to Encrypted Wallet", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                }
            }
        }

        // Wallet Items List
        if (walletItems.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(imageVector = Icons.Default.Key, contentDescription = null, tint = TextMuted, modifier = Modifier.size(36.dp))
                    Text(text = "Your Memory Wallet is empty.", color = TextSecondary, fontSize = 13.sp)
                    Text(text = "Encrypted conversation logs and private notes are securely stored here.", color = TextMuted, fontSize = 11.sp)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(walletItems, key = { it.id }) { item ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(containerColor = GlassSurface),
                        border = androidx.compose.foundation.BorderStroke(1.dp, GlassBorder)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.Key, contentDescription = null, tint = CyanNeon, modifier = Modifier.size(13.dp))
                                    Text(
                                        text = item.title,
                                        color = TextPrimary,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = item.content,
                                    color = TextSecondary,
                                    fontSize = 12.sp
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = SimpleDateFormat("MMM dd, yyyy hh:mm a", Locale.getDefault()).format(Date(item.timestamp)),
                                    color = TextMuted,
                                    fontSize = 9.5.sp
                                )
                            }
                            IconButton(onClick = { onDeleteItem(item.id) }) {
                                Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete", tint = CrimsonNeon, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}
