package com.vcserver.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vcserver.R
import com.vcserver.ui.viewmodels.TerminalViewModel
import com.vcserver.utils.CharCell

/**
 * 终端界面
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun TerminalScreen(
	viewModel: TerminalViewModel,
	onBackClick: () -> Unit
) {
	val uiState = viewModel.uiState.collectAsStateWithLifecycle().value
	var commandInput by remember { mutableStateOf("") }
	val keyboardController = LocalSoftwareKeyboardController.current

	Scaffold(
		topBar = {
			TopAppBar(
				title = { Text(stringResource(R.string.terminal) + " - " + viewModel.server.name) },
				navigationIcon = {
					IconButton(onClick = onBackClick) {
						Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.cancel))
					}
				},
				actions = {
					// 重连按钮（当未连接时显示）
					if (!uiState.isConnected && !uiState.isConnecting) {
						IconButton(
							onClick = { viewModel.reconnect() }
						) {
							Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.reconnect))
						}
					}
					// 清空输出按钮（当已连接时显示）
					if (uiState.isConnected) {
						IconButton(
							onClick = { viewModel.clearOutput() }
						) {
							Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.clear_output))
						}
					}
				}
			)
		}
	) { paddingValues ->
		Column(
			modifier = Modifier
				.fillMaxSize()
				.padding(paddingValues)
				.background(Color(0xFF1E1E1E)) // 深色背景
		) {
			// 连接状态提示
			if (uiState.isConnecting) {
				LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
				Text(
					text = stringResource(R.string.shell_connecting),
					modifier = Modifier.padding(8.dp),
					color = Color.White
				)
			}

			// 错误提示
			uiState.error?.let { error ->
				Card(
					modifier = Modifier
						.fillMaxWidth()
						.padding(8.dp),
					colors = CardDefaults.cardColors(
						containerColor = MaterialTheme.colorScheme.errorContainer
					)
				) {
					Row(
						modifier = Modifier
							.fillMaxWidth()
							.padding(16.dp),
						horizontalArrangement = Arrangement.SpaceBetween,
						verticalAlignment = Alignment.CenterVertically
					) {
						Column(modifier = Modifier.weight(1f)) {
							Text(
								text = error.message,
								color = MaterialTheme.colorScheme.onErrorContainer
							)
							// 如果是连接错误，显示重连提示
							if (!uiState.isConnected && !uiState.isConnecting) {
								TextButton(
									onClick = { viewModel.reconnect() },
									modifier = Modifier.padding(top = 8.dp)
								) {
									Icon(
										Icons.Default.Refresh,
										contentDescription = null,
										modifier = Modifier.size(18.dp)
									)
									Spacer(modifier = Modifier.width(4.dp))
									Text(stringResource(R.string.reconnect))
								}
							}
						}
						IconButton(onClick = { viewModel.clearError() }) {
							Icon(
								Icons.Default.Clear,
								contentDescription = stringResource(R.string.cancel)
							)
						}
					}
				}
			}

			// 终端输出区域
			Card(
				modifier = Modifier
					.weight(1f)
					.fillMaxWidth()
					.padding(8.dp),
				colors = CardDefaults.cardColors(
					containerColor = Color(0xFF000000)
				)
			) {
				val scrollState = rememberScrollState()
				LaunchedEffect(uiState.terminalBuffer) {
					// 自动滚动到底部
					scrollState.animateScrollTo(scrollState.maxValue)
				}
				
				Column(
					modifier = Modifier
						.fillMaxSize()
						.verticalScroll(scrollState)
						.padding(8.dp)
				) {
					if (uiState.terminalBuffer != null) {
						// 使用终端缓冲区渲染格式化文本
						TerminalTextContent(uiState.terminalBuffer)
					} else {
						// 回退到纯文本显示
						Text(
							text = uiState.output,
							color = Color(0xFF00FF00),
							fontFamily = FontFamily.Monospace,
							fontSize = 12.sp,
							modifier = Modifier.fillMaxWidth()
						)
					}
				}
			}

			// 命令输入区域
			Card(
				modifier = Modifier.fillMaxWidth(),
				shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
			) {
				Column(
					modifier = Modifier.padding(8.dp)
				) {
					// 历史命令导航按钮
					Row(
						modifier = Modifier.fillMaxWidth(),
						horizontalArrangement = Arrangement.spacedBy(8.dp)
					) {
						IconButton(
							onClick = {
								val prevCommand = viewModel.getPreviousCommand()
								if (prevCommand != null) {
									commandInput = prevCommand
								}
							},
							enabled = uiState.isConnected
						) {
							Icon(
								Icons.Default.ArrowUpward,
								contentDescription = stringResource(R.string.previous_command)
							)
						}
						IconButton(
							onClick = {
								val nextCommand = viewModel.getNextCommand()
								commandInput = nextCommand
							},
							enabled = uiState.isConnected
						) {
							Icon(
								Icons.Default.ArrowDownward,
								contentDescription = stringResource(R.string.next_command)
							)
						}
						Spacer(modifier = Modifier.weight(1f))
						TextButton(
							onClick = {
								val completed = viewModel.triggerAutoComplete(commandInput)
								if (completed != commandInput) {
									commandInput = completed
								}
							},
							enabled = uiState.isConnected && commandInput.isNotEmpty()
						) {
							Text(stringResource(R.string.auto_complete))
						}
					}

					// 输入框
					Row(
						modifier = Modifier.fillMaxWidth(),
						horizontalArrangement = Arrangement.spacedBy(8.dp),
						verticalAlignment = Alignment.CenterVertically
					) {
						TextField(
							value = commandInput,
							onValueChange = { commandInput = it },
							modifier = Modifier.weight(1f),
							enabled = uiState.isConnected,
							placeholder = { Text(stringResource(R.string.command_input)) },
							keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
							keyboardActions = KeyboardActions(
								onSend = {
									if (commandInput.isNotEmpty() && uiState.isConnected) {
										viewModel.sendCommand(commandInput)
										commandInput = ""
										keyboardController?.hide()
									}
								}
							),
							singleLine = true,
							colors = TextFieldDefaults.colors(
								focusedContainerColor = MaterialTheme.colorScheme.surface,
								unfocusedContainerColor = MaterialTheme.colorScheme.surface
							)
						)
						IconButton(
							onClick = {
								if (commandInput.isNotEmpty() && uiState.isConnected) {
									viewModel.sendCommand(commandInput)
									commandInput = ""
									keyboardController?.hide()
								}
							},
							enabled = uiState.isConnected && commandInput.isNotEmpty()
						) {
							Icon(
								Icons.Default.Send,
								contentDescription = stringResource(R.string.send_command)
							)
						}
					}
				}
			}
		}
	}
}

/**
 * 渲染终端文本内容（支持 ANSI 颜色和格式）
 */
@Composable
private fun TerminalTextContent(buffer: com.vcserver.utils.TerminalBuffer) {
	val content = buffer.getAllContent()
	
	Column {
		content.forEach { row ->
			if (row.isNotEmpty()) {
				Text(
					text = buildAnnotatedString {
						var currentFg: Color? = null
						var currentBg: Color? = null
						var currentBold = false
						
						row.forEach { cell ->
							// 如果格式改变，切换样式
							if (cell.fgColor != currentFg || 
								cell.bgColor != currentBg || 
								cell.isBold != currentBold) {
								// 结束当前样式
								currentFg = cell.fgColor
								currentBg = cell.bgColor
								currentBold = cell.isBold
								
								// 开始新样式
								val style = SpanStyle(
									color = currentFg ?: Color(0xFF00FF00),
									background = currentBg ?: Color.Unspecified,
									fontWeight = if (currentBold) FontWeight.Bold else FontWeight.Normal
								)
								withStyle(style = style) {
									append(cell.char.toString())
								}
							} else {
								// 继续使用当前样式
								val style = SpanStyle(
									color = currentFg ?: Color(0xFF00FF00),
									background = currentBg ?: Color.Unspecified,
									fontWeight = if (currentBold) FontWeight.Bold else FontWeight.Normal
								)
								withStyle(style = style) {
									append(cell.char.toString())
								}
							}
						}
					},
					fontFamily = FontFamily.Monospace,
					fontSize = 12.sp,
					modifier = Modifier.fillMaxWidth()
				)
			}
		}
	}
}

