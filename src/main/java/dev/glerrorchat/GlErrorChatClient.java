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

import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GlErrorChatClient implements ClientModInitializer {
	public static final String MOD_ID = "gl_error_chat";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitializeClient() {
		LOGGER.info(
				"[{}] loaded, OpenGL errors polled by GlStateManager#_getError will now be printed to the chat",
				MOD_ID
		);
	}
}