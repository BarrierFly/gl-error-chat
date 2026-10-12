/*
 * This file is part of the GL Error Chat project, licensed under the
 * GNU Lesser General Public License v3.0
 *
 * Copyright (C) 2026  OpenCode and contributors
 *
 * GL Error Chat is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * GL Error Chat is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with GL Error Chat.  If not, see <https://www.gnu.org/licenses/>.
 */

package dev.glerrorchat;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Collects the OpenGL errors polled by {@code GlStateManager#_getError} and prints a detailed report to the chat.
 *
 * <p>The report contains the error code, the name of the GL constant, the call site and stack trace that polled
 * the error, the GL device information and the current game state. The whole report is always written to the log,
 * the chat message carries a shorter version and can be clicked to copy the full text.
 *
 * <p>Since a broken mod might poll {@code glGetError} on every single frame, reports of the same error coming from
 * the same call site within {@link #DEDUPE_WINDOW_MS} are merged into a single one-line chat message.
 */
public final class GlErrorReporter {
	private static final Logger LOGGER = LoggerFactory.getLogger(GlErrorChatClient.MOD_ID);

	private static final long DEDUPE_WINDOW_MS = 5000L;
	private static final int MAX_STACK_FRAMES = 16;
	private static final int MAX_TRACKED_SITES = 64;

	private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

	private static final Map<String, SiteState> SITES = new HashMap<>();
	private static long startTime = System.currentTimeMillis();
	private static int totalReports;

	private GlErrorReporter() {
	}

	/**
	 * Called from {@code GlStateManager#_getError} on the render thread whenever a non-zero GL error is polled.
	 */
	public static void report(int error) {
		long now = System.currentTimeMillis();
		Thread thread = Thread.currentThread();
		StackTraceElement[] stack = thread.getStackTrace();
		StackTraceElement caller = findCaller(stack);
		String site = describeElement(caller);

		int occurrence;
		boolean merged;

		synchronized (SITES) {
			totalReports++;
			SiteState state = SITES.get(site);

			if (state != null && now - state.windowStart < DEDUPE_WINDOW_MS) {
				state.count++;
				occurrence = state.count;
				merged = true;
			} else {
				SITES.put(site, new SiteState(now, 1));
				occurrence = 1;
				merged = false;
			}

			if (SITES.size() > MAX_TRACKED_SITES) {
				for (Iterator<Map.Entry<String, SiteState>> it = SITES.entrySet().iterator(); it.hasNext(); ) {
					Map.Entry<String, SiteState> entry = it.next();

					if (now - entry.getValue().windowStart >= DEDUPE_WINDOW_MS) {
						it.remove();
					}
				}
			}
		}

		String errorName = describeError(error);

		if (merged) {
			LOGGER.warn("[{}] {} at {} repeated {} times within {}ms", GlErrorChatClient.MOD_ID, errorName, site, occurrence, DEDUPE_WINDOW_MS);
			sendToChat(buildMergedMessage(error, errorName, site, occurrence));
		} else {
			String detail = buildDetail(error, errorName, thread, caller, site, stack, now);
			LOGGER.error("[{}] {} at {} (report #{} of {})", GlErrorChatClient.MOD_ID, errorName, site, totalReports, error);
			LOGGER.error(detail);
			sendToChat(buildFullMessage(error, errorName, detail));
		}
	}

	private static Component buildFullMessage(int error, String errorName, String detail) {
		MutableComponent message = Component.empty();
		message.append(Component.literal("[" + GlErrorChatClient.MOD_ID + "] ").withStyle(ChatFormatting.DARK_GRAY));
		message.append(Component.literal(errorName + " " + formatHex(error)).withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
		message.append(Component.literal("\n" + detail + "\n(点击本条消息复制完整报告)"));
		message.withStyle(style -> style
				.withClickEvent(new ClickEvent.CopyToClipboard(detail))
				.withHoverEvent(new HoverEvent.ShowText(Component.literal(detail)))
		);
		return message;
	}

	private static Component buildMergedMessage(int error, String errorName, String site, int occurrence) {
		MutableComponent message = Component.empty();
		message.append(Component.literal("[" + GlErrorChatClient.MOD_ID + "] ").withStyle(ChatFormatting.DARK_GRAY));
		message.append(Component.literal(errorName + " " + formatHex(error)).withStyle(ChatFormatting.RED));
		message.append(Component.literal(" 在 " + site + " 处重复发生，").withStyle(ChatFormatting.GRAY));
		message.append(Component.literal(DEDUPE_WINDOW_MS / 1000 + " 秒内共 " + occurrence + " 次").withStyle(ChatFormatting.YELLOW));
		message.append(Component.literal("（详情见上一条消息 / 日志）").withStyle(ChatFormatting.GRAY));
		return message;
	}

	private static String buildDetail(
			int error,
			String errorName,
			Thread thread,
			StackTraceElement caller,
			String site,
			StackTraceElement[] stack,
			long now
	) {
		StringBuilder sb = new StringBuilder(512);
		sb.append("错误: ").append(errorName).append(' ').append(formatHex(error)).append(" (GL error ").append(error).append(')').append('\n');
		sb.append("调用点: ").append(site).append(caller == null ? "" : " [" + caller.getFileName() + ":" + caller.getLineNumber() + "]").append('\n');
		sb.append("时间: ").append(TIME_FORMAT.format(LocalTime.now()))
				.append(" (启动后 ").append((now - startTime) / 1000L).append('s')
				.append(", 第 ").append(totalReports).append(" 次 GL 错误)").append('\n');
		sb.append("线程: ").append(thread.getName())
				.append(", 渲染线程: ").append(isRenderThreadSafe())
				.append('\n');
		appendGameState(sb);
		appendGpuDevice(sb);
		appendStackTrace(sb, stack);
		return sb.toString();
	}

	private static void appendGameState(StringBuilder sb) {
		sb.append("游戏状态: ");
		try {
			Minecraft client = Minecraft.getInstance();

			if (client == null) {
				sb.append("客户端未初始化");
				return;
			}

			sb.append(client.screen == null
					? "界面=无"
					: "界面=" + client.screen.getClass().getSimpleName());

			if (client.level != null) {
				sb.append(", 维度=").append(client.level.dimension().identifier());

				LocalPlayer player = client.player;

				if (player != null) {
					sb.append(String.format(", 玩家=[%.2f, %.2f, %.2f]", player.getX(), player.getY(), player.getZ()));
				}
			} else {
				sb.append(", 未进入世界");
			}
		} catch (Throwable t) {
			sb.append("<获取失败: ").append(t).append('>');
		}

		sb.append('\n');
	}

	private static void appendGpuDevice(StringBuilder sb) {
		sb.append("GL 设备: ");
		try {
			// deliberately no glGetString / glGetIntegerv here: any extra GL command issued right after
			// glGetError could leave errors behind that would be picked up by the next poll of somebody else
			GpuDevice device = RenderSystem.tryGetDevice();

			if (device == null) {
				sb.append("尚未初始化");
				return;
			}

			sb.append(device.getBackendName())
					.append(", vendor=").append(device.getVendor())
					.append(", renderer=").append(device.getRenderer())
					.append(", version=").append(device.getVersion())
					.append(", maxTextureSize=").append(device.getMaxTextureSize())
					.append('\n');
			sb.append("实现: ").append(device.getImplementationInformation());
		} catch (Throwable t) {
			sb.append("<获取失败: ").append(t).append('>');
		}

		sb.append('\n');
	}

	private static void appendStackTrace(StringBuilder sb, StackTraceElement[] stack) {
		sb.append("调用栈: ");
		int printed = 0;

		for (StackTraceElement element : stack) {
			if (isInternalFrame(element)) {
				continue;
			}

			sb.append('\n').append("    at ").append(describeElement(element));
			printed++;

			if (printed >= MAX_STACK_FRAMES) {
				sb.append("\n    ...");
				break;
			}
		}

		if (printed == 0) {
			sb.append("<无>");
		}
	}

	private static StackTraceElement findCaller(StackTraceElement[] stack) {
		for (StackTraceElement element : stack) {
			if (!isInternalFrame(element)) {
				return element;
			}
		}

		return null;
	}

	private static boolean isInternalFrame(StackTraceElement element) {
		String className = element.getClassName();
		return className.startsWith("dev.glerrorchat.")
				|| className.equals("com.mojang.blaze3d.opengl.GlStateManager")
				|| className.equals("java.lang.Thread");
	}

	private static String describeElement(StackTraceElement element) {
		return element == null ? "<未知调用点>" : element.getClassName() + '#' + element.getMethodName();
	}

	private static boolean isRenderThreadSafe() {
		try {
			return RenderSystem.isOnRenderThread();
		} catch (Throwable t) {
			return false;
		}
	}

	private static void sendToChat(Component message) {
		Minecraft client = Minecraft.getInstance();

		if (client == null) {
			LOGGER.warn("[{}] no Minecraft instance available, chat message dropped", GlErrorChatClient.MOD_ID);
			return;
		}

		try {
			client.execute(() -> {
				try {
					client.gui.getChat().addMessage(message);
				} catch (Throwable t) {
					LOGGER.error("[{}] failed to print the GL error report to the chat", GlErrorChatClient.MOD_ID, t);
				}
			});
		} catch (Throwable t) {
			LOGGER.error("[{}] failed to schedule the GL error report to the chat", GlErrorChatClient.MOD_ID, t);
		}
	}

	private static String formatHex(int error) {
		return String.format("(0x%04X)", error);
	}

	private static String describeError(int error) {
		return switch (error) {
			case 0x0500 -> "GL_INVALID_ENUM";
			case 0x0501 -> "GL_INVALID_VALUE";
			case 0x0502 -> "GL_INVALID_OPERATION";
			case 0x0503 -> "GL_STACK_OVERFLOW";
			case 0x0504 -> "GL_STACK_UNDERFLOW";
			case 0x0505 -> "GL_OUT_OF_MEMORY";
			case 0x0506 -> "GL_INVALID_FRAMEBUFFER_OPERATION";
			case 0x0507 -> "GL_CONTEXT_LOST";
			default -> String.format("UNKNOWN_GL_ERROR(0x%04X)", error);
		};
	}

	private static final class SiteState {
		private final long windowStart;
		private int count;

		private SiteState(long windowStart, int count) {
			this.windowStart = windowStart;
			this.count = count;
		}
	}
}