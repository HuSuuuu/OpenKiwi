package com.orizon.openkiwi.core.tool.builtin

import com.orizon.openkiwi.core.tool.*
import com.orizon.openkiwi.network.FeishuApiClient
import com.orizon.openkiwi.network.FeishuConfig

class FeishuTool(private val feishuClient: FeishuApiClient) : Tool {
    override val definition = ToolDefinition(
        name = "feishu",
        description = """Interact with Feishu/Lark messaging platform.
Actions:
- auth: Authenticate with App ID and Secret
- status: Check authentication status
- send_message: Send a text/interactive message
- reply: Reply to a specific message
- list_chats: List joined chats/groups
- read_messages: Read recent messages from a chat
- chat_info: Get chat/group details
- create_group: Create a new group chat
- user_info: Get user information""",
        category = ToolCategory.COMMUNICATION.name,
        permissionLevel = PermissionLevel.DANGEROUS.name,
        parameters = mapOf(
            "action" to ToolParamDef("string", "Action to perform", true,
                enumValues = listOf("auth", "status", "send_message", "reply", "list_chats", "read_messages", "chat_info", "create_group", "user_info")),
            "app_id" to ToolParamDef("string", "Feishu App ID (for auth)"),
            "app_secret" to ToolParamDef("string", "Feishu App Secret (for auth)"),
            "receive_id" to ToolParamDef("string", "Recipient ID (chat_id, user_id, open_id, etc.)"),
            "receive_id_type" to ToolParamDef("string", "ID type: chat_id, user_id, open_id, union_id, email", false, "chat_id"),
            "message" to ToolParamDef("string", "Message text to send"),
            "message_id" to ToolParamDef("string", "Message ID (for reply)"),
            "chat_id" to ToolParamDef("string", "Chat/group ID"),
            "group_name" to ToolParamDef("string", "Group name (for create_group)"),
            "user_id" to ToolParamDef("string", "User ID (for user_info)"),
            "page_size" to ToolParamDef("string", "Results per page (default 20)")
        ),
        requiredParams = listOf("action")
    )

    override suspend fun execute(params: Map<String, Any?>): ToolResult {
        val action = params["action"]?.toString() ?: return err("Missing action")
        return runCatching {
            when (action) {
                "auth" -> {
                    val appId = params["app_id"]?.toString() ?: return@runCatching err("Missing app_id")
                    val appSecret = params["app_secret"]?.toString() ?: return@runCatching err("Missing app_secret")
                    feishuClient.authenticate(FeishuConfig(appId = appId, appSecret = appSecret)).fold(
                        onSuccess = { ok("Feishu authenticated successfully") },
                        onFailure = { err("Auth failed: ${it.message}") }
                    )
                }
                "status" -> ok("Authenticated: ${feishuClient.isAuthenticated()}")
                "send_message" -> {
                    val receiveId = params["receive_id"]?.toString() ?: return@runCatching err("Missing receive_id")
                    val idType = params["receive_id_type"]?.toString() ?: "chat_id"
                    val message = params["message"]?.toString() ?: return@runCatching err("Missing message")
                    val content = """{"text":"$message"}"""
                    feishuClient.sendMessage(idType, receiveId, "text", content).fold(
                        onSuccess = { ok("Message sent to $receiveId\n$it") },
                        onFailure = { err("Send failed: ${it.message}") }
                    )
                }
                "reply" -> {
                    val messageId = params["message_id"]?.toString() ?: return@runCatching err("Missing message_id")
                    val message = params["message"]?.toString() ?: return@runCatching err("Missing message")
                    val content = """{"text":"$message"}"""
                    feishuClient.replyMessage(messageId, "text", content).fold(
                        onSuccess = { ok("Reply sent\n$it") },
                        onFailure = { err("Reply failed: ${it.message}") }
                    )
                }
                "list_chats" -> {
                    val pageSize = params["page_size"]?.toString()?.toIntOrNull() ?: 20
                    feishuClient.getChats(pageSize).fold(
                        onSuccess = { ok(it) },
                        onFailure = { err("Failed to list chats: ${it.message}") }
                    )
                }
                "read_messages" -> {
                    val chatId = params["chat_id"]?.toString() ?: return@runCatching err("Missing chat_id")
                    val pageSize = params["page_size"]?.toString()?.toIntOrNull() ?: 20
                    feishuClient.getChatMessages(chatId, pageSize).fold(
                        onSuccess = { ok(it) },
                        onFailure = { err("Failed to read messages: ${it.message}") }
                    )
                }
                "chat_info" -> {
                    val chatId = params["chat_id"]?.toString() ?: return@runCatching err("Missing chat_id")
                    feishuClient.getChatInfo(chatId).fold(
                        onSuccess = { ok(it) },
                        onFailure = { err("Failed to get chat info: ${it.message}") }
                    )
                }
                "create_group" -> {
                    val name = params["group_name"]?.toString() ?: return@runCatching err("Missing group_name")
                    feishuClient.createGroup(name).fold(
                        onSuccess = { ok("Group created\n$it") },
                        onFailure = { err("Failed to create group: ${it.message}") }
                    )
                }
                "user_info" -> {
                    val userId = params["user_id"]?.toString() ?: return@runCatching err("Missing user_id")
                    val idType = params["receive_id_type"]?.toString() ?: "open_id"
                    feishuClient.getUserInfo(idType, userId).fold(
                        onSuccess = { ok(it) },
                        onFailure = { err("Failed to get user info: ${it.message}") }
                    )
                }
                else -> err("Unknown action: $action")
            }
        }.getOrElse { err(it.message ?: "Unknown error") }
    }

    private fun ok(msg: String) = ToolResult(definition.name, true, msg)
    private fun err(msg: String) = ToolResult(definition.name, false, "", msg)
}
