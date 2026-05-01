package `in`.sakhi.feature.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import `in`.sakhi.core.domain.model.ChatMessage
import `in`.sakhi.core.domain.model.ChatRole
import `in`.sakhi.core.ui.component.DisclaimerText
import `in`.sakhi.core.ui.component.LoadingDots
import `in`.sakhi.core.ui.theme.SakhiColors

private val QUICK_QUESTIONS = listOf(
    "What are the danger signs in pregnancy?",
    "How often should I check BP?",
    "When should I refer to PHC?",
    "What IFA tablets should she take?",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AskSakhiScreen(
    patientId: String? = null,
    patientType: String? = null,
    viewModel: ChatViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()

    // Load patient context if provided
    LaunchedEffect(patientId) {
        if (patientId != null && patientType != null) {
            viewModel.loadPatient(patientId, patientType)
        }
    }

    // Auto-scroll to bottom when new messages arrive, loading starts, or streaming content grows
    LaunchedEffect(state.messages.size, state.isLoading, state.streamingContent) {
        if (state.messages.isNotEmpty() || state.isLoading) {
            listState.animateScrollToItem(
                index = state.messages.size - 1 + if (state.isLoading) 1 else 0
            )
        }
    }

    Scaffold(
        containerColor = SakhiColors.PageBackground,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Ask Sakhi",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp
                        )
                        if (state.patientName != null) {
                            Text(
                                text = state.patientName!!,
                                fontSize = 12.sp,
                                color = SakhiColors.TextSecondary
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .imePadding()
            ) {
                // Model not ready warning
                if (!state.modelReady) {
                    Surface(
                        color = SakhiColors.YellowBackground,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "AI model not yet downloaded — answers may be limited",
                            color = SakhiColors.YellowText,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                        )
                    }
                }
                // Input bar
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    OutlinedTextField(
                        value = state.inputText,
                        onValueChange = viewModel::onInputChange,
                        placeholder = {
                            Text(
                                "Ask a clinical question…",
                                color = SakhiColors.TextSecondary
                            )
                        },
                        minLines = 1,
                        maxLines = 5,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Sentences,
                            imeAction = ImeAction.Send
                        ),
                        keyboardActions = KeyboardActions(onSend = { viewModel.send() }),
                        shape = RoundedCornerShape(20.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = SakhiColors.Divider,
                            focusedBorderColor = SakhiColors.Primary,
                            unfocusedContainerColor = SakhiColors.PageBackground,
                            focusedContainerColor = SakhiColors.PageBackground
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = viewModel::send,
                        enabled = state.inputText.isNotBlank() && !state.isLoading,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(
                                if (state.inputText.isNotBlank() && !state.isLoading)
                                    SakhiColors.Primary
                                else
                                    SakhiColors.Divider
                            )
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding() + 8.dp,
                bottom = 8.dp,
                start = 12.dp,
                end = 12.dp
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            // Empty state
            if (state.messages.isEmpty() && !state.isLoading) {
                item {
                    EmptyState(
                        patientName = state.patientName,
                        onQuickQuestion = { viewModel.sendQuickQuestion(it) }
                    )
                }
            }

            // Messages
            items(state.messages, key = { it.id }) { message ->
                ChatBubble(message = message)
            }

            // Loading / streaming indicator
            if (state.isLoading) {
                item {
                    val streaming = state.streamingContent
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.Bottom,
                        modifier = Modifier.padding(start = 4.dp, top = 4.dp)
                    ) {
                        SakhiAvatar()
                        if (!streaming.isNullOrEmpty()) {
                            // Tokens are arriving — show growing bubble
                            Surface(
                                shape = RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp),
                                color = Color.White,
                                shadowElevation = 1.dp,
                                modifier = Modifier.widthIn(max = 300.dp)
                            ) {
                                Text(
                                    text = streaming,
                                    color = SakhiColors.TextPrimary,
                                    fontSize = 15.sp,
                                    lineHeight = 22.sp,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                                )
                            }
                        } else {
                            // Waiting for first token
                            Surface(
                                shape = RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp),
                                color = Color.White,
                                shadowElevation = 1.dp
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
                                ) {
                                    LoadingDots()
                                    Text(
                                        text = "Sakhi is thinking…",
                                        color = SakhiColors.TextSecondary,
                                        fontSize = 14.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Error
            if (state.error != null) {
                item {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = SakhiColors.RedBackground,
                        border = androidx.compose.foundation.BorderStroke(1.dp, SakhiColors.RedBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = state.error!!,
                            color = SakhiColors.RedText,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }

            // Disclaimer (non-negotiable)
            item {
                DisclaimerText(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                )
            }
        }
    }
}

// ── Chat bubble ────────────────────────────────────────────────────────────────

@Composable
private fun ChatBubble(message: ChatMessage) {
    val isUser = message.role == ChatRole.USER

    Column(
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
            verticalAlignment = Alignment.Bottom,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (!isUser) {
                SakhiAvatar()
                Spacer(modifier = Modifier.width(8.dp))
            }
            Surface(
                shape = if (isUser)
                    RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp)
                else
                    RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp),
                color = if (isUser) SakhiColors.Primary else Color.White,
                shadowElevation = if (isUser) 0.dp else 1.dp,
                modifier = Modifier.widthIn(max = 300.dp)
            ) {
                Text(
                    text = message.content,
                    color = if (isUser) Color.White else SakhiColors.TextPrimary,
                    fontSize = 15.sp,
                    lineHeight = 22.sp,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                )
            }
        }

        // PHC referral flag — inline below assistant bubble
        if (!isUser && message.refer) {
            Spacer(modifier = Modifier.height(4.dp))
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = SakhiColors.RedBackground,
                border = androidx.compose.foundation.BorderStroke(1.dp, SakhiColors.RedBorder),
                modifier = Modifier
                    .padding(start = 40.dp)
                    .fillMaxWidth(0.9f)
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text(
                        text = "PHC referral recommended",
                        color = SakhiColors.RedText,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                    Text(
                        text = "Please refer this patient to the PHC — do not delay.",
                        color = SakhiColors.RedText,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        }
    }
}

// ── Sakhi avatar ───────────────────────────────────────────────────────────────

@Composable
private fun SakhiAvatar() {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(30.dp)
            .clip(CircleShape)
            .background(SakhiColors.Primary)
    ) {
        Icon(
            imageVector = Icons.Default.Favorite,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(14.dp)
        )
    }
}

// ── Empty state ────────────────────────────────────────────────────────────────

@Composable
private fun EmptyState(patientName: String?, onQuickQuestion: (String) -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 40.dp, bottom = 24.dp)
    ) {
        // Large heart avatar
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(SakhiColors.Primary)
        ) {
            Icon(
                imageVector = Icons.Default.Favorite,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(32.dp)
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Ask me anything",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = SakhiColors.TextPrimary
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = if (patientName != null)
                "Advising on $patientName"
            else
                "About pregnancy, ANC protocols, or patient care.",
            fontSize = 14.sp,
            color = SakhiColors.TextSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp)
        )

        // Quick questions — only shown without patient context
        if (patientName == null) {
            Spacer(Modifier.height(24.dp))
            Text(
                text = "COMMON QUESTIONS",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = SakhiColors.TextSecondary,
                letterSpacing = 1.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, bottom = 8.dp)
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                QUICK_QUESTIONS.forEach { q ->
                    Surface(
                        onClick = { onQuickQuestion(q) },
                        shape = RoundedCornerShape(12.dp),
                        color = Color.White,
                        border = androidx.compose.foundation.BorderStroke(1.dp, SakhiColors.Divider),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = q,
                            color = SakhiColors.TextPrimary,
                            fontSize = 15.sp,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
                        )
                    }
                }
            }
        }
    }
}
