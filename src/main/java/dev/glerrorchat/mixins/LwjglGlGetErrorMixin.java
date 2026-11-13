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

package dev.glerrorchat.mixins;

import dev.glerrorchat.GlErrorReporter;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Hooks the LWJGL entry point {@link GL11#glGetError} directly, in addition to the vanilla
 * {@code GlStateManager#_getError} mixin.
 *
 * <p>Some mods (e.g. Xaero's Minimap and Xaero's World Map) poll OpenGL errors with raw LWJGL calls
 * instead of going through {@code GlStateManager}, and even drain the whole error queue periodically
 * ({@code xaero.*.graphics.OpenGlHelper#clearErrors}) before anybody else can poll it. Without this
 * mixin those polls and drains are completely invisible to the mod.
 *
 * <p>Polls that went through {@code GlStateManager#_getError} are skipped here, because that call
 * site is mixed in separately and reports them with better context; this avoids duplicated chat
 * messages for the same poll.
 */
@Mixin(value = GL11.class)
public class LwjglGlGetErrorMixin {
	@Inject(method = "glGetError", at = @At("RETURN"))
	private static void glErrorChat$onRawGetError(CallbackInfoReturnable<Integer> cir) {
		int error = cir.getReturnValue();

		if (error != 0) {
			GlErrorReporter.reportRawPolled(error);
		}
	}
}
