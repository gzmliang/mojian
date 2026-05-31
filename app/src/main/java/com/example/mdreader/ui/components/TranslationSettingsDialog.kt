package com.example.mdreader.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.mdreader.DebugLog
import com.example.mdreader.translate.TranslationService

/**
 * AI 翻译设置对话框。
 */
@Composable
fun TranslationSettingsDialog(
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val currentConfig = remember { TranslationService.getConfig(context) }

    var endpoint by remember { mutableStateOf(currentConfig.endpoint) }
    var apiKey by remember { mutableStateOf(currentConfig.apiKey) }
    var model by remember { mutableStateOf(currentConfig.model) }
    var showKey by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("AI 翻译设置") },
        text = {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("配置 OpenAI 兼容的 API 端点（默认 DeepSeek）",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))

                OutlinedTextField(
                    value = endpoint,
                    onValueChange = { endpoint = it },
                    label = { Text("API 端点") },
                    placeholder = { Text("https://api.deepseek.com/v1/chat/completions") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("API Key") },
                    placeholder = { Text("sk-...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = if (showKey) VisualTransformation.None
                        else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showKey = !showKey }) {
                            Icon(
                                if (showKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (showKey) "隐藏" else "显示",
                            )
                        }
                    },
                )

                OutlinedTextField(
                    value = model,
                    onValueChange = { model = it },
                    label = { Text("模型") },
                    placeholder = { Text("deepseek-chat") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                if (saved) {
                    Text("✅ 已保存", color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                TranslationService.saveConfig(context,
                    TranslationService.Config(endpoint, apiKey, model))
                saved = true
                DebugLog.i("Translation", "配置已保存")
                onDismiss()
            }) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}
