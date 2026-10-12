package com.franzkafkayu.vcserver.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowLeft
import androidx.compose.material.icons.filled.ArrowRight
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardTab
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.franzkafkayu.vcserver.R
import com.franzkafkayu.vcserver.ui.viewmodels.TerminalViewModel
import com.franzkafkayu.vcserver.utils.AnsiParser
import com.franzkafkayu.vcserver.utils.CharCell

/**
 * 终端界面
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class, ExperimentalLayoutApi::class)
@Composable
fun TerminalScreen(
	viewModel: TerminalViewModel,
	onBackClick: () -> Unit
) {
	val uiState = viewModel.uiState.collectAsStateWithLifecycle().value
	var commandInput by remember { mutableStateOf("") }
	var realtimeInputMode by remember { mutableStateOf(false) } // 实时输入模式
	var ctrlArmed by remember { mutableStateOf(false) }
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
					// 重连按钮（当未连接时显示�?
					if (!uiState.isConnected && !uiState.isConnecting) {
						IconButton(
							onClick = { viewModel.reconnect() }
						) {
							Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.reconnect))
						}
					}
					// 清空输出按钮（当已连接时显示�?
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
				.consumeWindowInsets(paddingValues)
				.imePadding()
				.background(Color(0xFF1E1E1E)) // 深色背景
		) {
			// 连接状态提�?
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
				LaunchedEffect(uiState.frame, uiState.alternateScreen) {
					withFrameNanos { }
					if (uiState.alternateScreen) {
						scrollState.scrollTo(0)
					} else {
						scrollState.scrollTo(scrollState.maxValue)
					}
				}

				BoxWithConstraints(
					modifier = Modifier
						.fillMaxSize()
						.padding(8.dp)
				) {
					val density = LocalDensity.current
					val cols = with(density) {
						((maxWidth.toPx()) / (12.sp.toPx() * 0.6f)).toInt().coerceAtLeast(40)
					}
					val rows = with(density) {
						((maxHeight.toPx()) / (12.sp.toPx() * 1.2f)).toInt().coerceAtLeast(10)
					}
					LaunchedEffect(cols, rows) {
						viewModel.updateTerminalSize(cols, rows)
					}
				Column(
					modifier = Modifier
						.fillMaxSize()
						.verticalScroll(scrollState)
				) {
					if (uiState.terminalBuffer != null) {
						TerminalTextContent(uiState.terminalBuffer)
					} else {
						Text(
							text = uiState.output,
							color = AnsiParser.DefaultForeground,
							fontFamily = FontFamily.Monospace,
							fontSize = 12.sp,
							softWrap = false,
							modifier = Modifier.fillMaxWidth()
						)
					}
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
					Row(
						modifier = Modifier
							.fillMaxWidth()
							.horizontalScroll(rememberScrollState()),
						horizontalArrangement = Arrangement.spacedBy(4.dp),
						verticalAlignment = Alignment.CenterVertically
					) {
						IconButton(
							onClick = { viewModel.sendInterrupt() },
							enabled = uiState.isConnected,
							colors = IconButtonDefaults.iconButtonColors(
								containerColor = MaterialTheme.colorScheme.errorContainer
							)
						) {
							Icon(
								Icons.Default.Close,
								contentDescription = stringResource(R.string.terminal_interrupt),
								tint = MaterialTheme.colorScheme.onErrorContainer
							)
						}
						IconButton(
							onClick = { viewModel.sendSuspend() },
							enabled = uiState.isConnected
						) {
							Icon(
								Icons.Default.Pause,
								contentDescription = stringResource(R.string.terminal_suspend)
							)
						}
						IconButton(
							onClick = { viewModel.sendEOF() },
							enabled = uiState.isConnected
						) {
							Icon(
								Icons.Default.Logout,
								contentDescription = stringResource(R.string.terminal_eof)
							)
						}
						IconButton(
							onClick = { viewModel.sendClearScreen() },
							enabled = uiState.isConnected
						) {
							Icon(
								Icons.Default.CleaningServices,
								contentDescription = stringResource(R.string.terminal_clear_screen)
							)
						}
						IconButton(
							onClick = { viewModel.sendTab() },
							enabled = uiState.isConnected
						) {
							Icon(
								Icons.Default.KeyboardTab,
								contentDescription = stringResource(R.string.terminal_tab)
							)
						}
						TextButton(
							onClick = { viewModel.sendEscape() },
							enabled = uiState.isConnected
						) {
							Text(stringResource(R.string.terminal_esc))
						}
						if (uiState.alternateScreen) {
							TextButton(
								onClick = { ctrlArmed = !ctrlArmed },
								enabled = uiState.isConnected,
								colors = ButtonDefaults.textButtonColors(
									containerColor = if (ctrlArmed) {
										MaterialTheme.colorScheme.primaryContainer
									} else {
										Color.Transparent
									}
								)
							) {
								Text(stringResource(R.string.terminal_ctrl))
							}
						}
						IconButton(
							onClick = {
								if (uiState.alternateScreen) {
									viewModel.sendArrow('A')
								} else if (realtimeInputMode) {
									viewModel.sendAnsiSequence("\u001B[A")
								} else {
									val prevCommand = viewModel.getPreviousCommand()
									if (prevCommand != null) {
										commandInput = prevCommand
									}
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
								if (uiState.alternateScreen) {
									viewModel.sendArrow('B')
								} else if (realtimeInputMode) {
									viewModel.sendAnsiSequence("\u001B[B")
								} else {
									commandInput = viewModel.getNextCommand()
								}
							},
							enabled = uiState.isConnected
						) {
							Icon(
								Icons.Default.ArrowDownward,
								contentDescription = stringResource(R.string.next_command)
							)
						}
						if (uiState.alternateScreen || realtimeInputMode) {
							IconButton(
								onClick = {
									if (uiState.alternateScreen) {
										viewModel.sendArrow('D')
									} else {
										viewModel.sendAnsiSequence("\u001B[D")
									}
								},
								enabled = uiState.isConnected
							) {
								Icon(
									Icons.Default.ArrowLeft,
									contentDescription = stringResource(R.string.cursor_left)
								)
							}
							IconButton(
								onClick = {
									if (uiState.alternateScreen) {
										viewModel.sendArrow('C')
									} else {
										viewModel.sendAnsiSequence("\u001B[C")
									}
								},
								enabled = uiState.isConnected
							) {
								Icon(
									Icons.Default.ArrowRight,
									contentDescription = stringResource(R.string.cursor_right)
								)
							}
						}
						TextButton(
							onClick = {
								val completed = viewModel.triggerAutoComplete(commandInput)
								if (completed != commandInput) {
									commandInput = completed
								}
							},
							enabled = uiState.isConnected && !uiState.alternateScreen && !realtimeInputMode && commandInput.isNotEmpty()
						) {
							Text(stringResource(R.string.auto_complete))
						}
					}

					// 实时输入模式切换
					if (!uiState.alternateScreen) {
						Row(
							modifier = Modifier.fillMaxWidth(),
							horizontalArrangement = Arrangement.SpaceBetween,
							verticalAlignment = Alignment.CenterVertically
						) {
							Text(
								text = if (realtimeInputMode) "实时输入模式" else "命令模式",
								style = MaterialTheme.typography.bodySmall,
								color = MaterialTheme.colorScheme.onSurfaceVariant
							)
							Switch(
								checked = realtimeInputMode,
								onCheckedChange = { realtimeInputMode = it },
								enabled = uiState.isConnected
							)
						}
					}

					// 输入�?
					Row(
						modifier = Modifier.fillMaxWidth(),
						horizontalArrangement = Arrangement.spacedBy(8.dp),
						verticalAlignment = Alignment.CenterVertically
					) {
						TextField(
							value = commandInput,
							onValueChange = { newValue ->
								val direct = uiState.alternateScreen || realtimeInputMode
								if (direct && uiState.isConnected) {
									val old = commandInput
									var next = newValue
									if (newValue.length > old.length) {
										var added = newValue.substring(old.length)
										if (uiState.alternateScreen && ctrlArmed && added.isNotEmpty()) {
											val code = controlCode(added[0])
											if (code != null) {
												viewModel.sendRawInput(byteArrayOf(code.toByte()))
												ctrlArmed = false
												added = added.drop(1)
												next = old + added
											}
										}
										if (added.isNotEmpty()) {
											viewModel.sendRawInput(added.toByteArray(Charsets.UTF_8))
										}
									} else if (newValue.length < old.length) {
										val backspace = if (uiState.alternateScreen) 0x7F.toByte() else 0x08.toByte()
										val count = old.length - newValue.length
										viewModel.sendRawInput(ByteArray(count) { backspace })
									}
									commandInput = next
								} else {
									commandInput = newValue
								}
							},
							modifier = Modifier.weight(1f),
							enabled = uiState.isConnected,
							placeholder = { 
								Text(
									when {
										uiState.alternateScreen -> "按键发送到全屏程序"
										realtimeInputMode -> "实时输入模式（字符将实时发送）"
										else -> stringResource(R.string.command_input)
									}
								)
							},
							keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
							keyboardActions = KeyboardActions(
								onSend = {
									if (uiState.isConnected) {
										when {
											uiState.alternateScreen -> {
												viewModel.sendRawInput(byteArrayOf(0x0D))
												commandInput = ""
											}
											realtimeInputMode -> {
												viewModel.sendRawInput(byteArrayOf(0x0A))
												commandInput = ""
												keyboardController?.hide()
											}
											commandInput.isNotEmpty() -> {
												viewModel.sendCommand(commandInput)
												commandInput = ""
												keyboardController?.hide()
											}
										}
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
								if (uiState.isConnected) {
									when {
										uiState.alternateScreen -> {
											viewModel.sendRawInput(byteArrayOf(0x0D))
											commandInput = ""
										}
										realtimeInputMode -> {
											viewModel.sendRawInput(byteArrayOf(0x0A))
											commandInput = ""
											keyboardController?.hide()
										}
										commandInput.isNotEmpty() -> {
											viewModel.sendCommand(commandInput)
											commandInput = ""
											keyboardController?.hide()
										}
									}
								}
							},
							enabled = uiState.isConnected && (uiState.alternateScreen || realtimeInputMode || commandInput.isNotEmpty())
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
 * 渲染终端文本内容（支�?ANSI 颜色和格式）
 */
@Composable
private fun TerminalTextContent(buffer: com.franzkafkayu.vcserver.utils.TerminalBuffer) {
	val content = buffer.getAllContent()
	
	Column {
		content.forEach { row ->
			val cells = if (row.isEmpty()) listOf(CharCell(' ', null, null, false)) else row
			run {
				Text(
					text = buildAnnotatedString {
						var currentFg: Color? = null
						var currentBg: Color? = null
						var currentBold = false
						
						cells.forEach { cell ->
							// 如果格式改变，切换样�?
							if (cell.fgColor != currentFg || 
								cell.bgColor != currentBg || 
								cell.isBold != currentBold) {
								// 结束当前样式
								currentFg = cell.fgColor
								currentBg = cell.bgColor
								currentBold = cell.isBold
								
								// 开始新样式
								val style = SpanStyle(
									color = currentFg ?: AnsiParser.DefaultForeground,
									background = currentBg ?: Color.Unspecified,
									fontWeight = if (currentBold) FontWeight.Bold else FontWeight.Normal
								)
								withStyle(style = style) {
									append(cell.char.toString())
								}
							} else {
								val style = SpanStyle(
									color = currentFg ?: AnsiParser.DefaultForeground,
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
					softWrap = false,
					overflow = TextOverflow.Visible,
					modifier = Modifier.fillMaxWidth()
				)
			}
		}
	}
}

private fun controlCode(ch: Char): Int? = when (ch) {
	in 'a'..'z' -> ch.code - 'a'.code + 1
	in 'A'..'Z' -> ch.code - 'A'.code + 1
	else -> null
}

