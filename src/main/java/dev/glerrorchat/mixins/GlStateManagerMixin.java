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

import com.mojang.blaze3d.opengl.GlStateManager;
import dev.glerrorchat.GlErrorReporter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = GlStateManager.class)
public class GlStateManagerMixin {
	@Inject(method = "_getError", at = @At("RETURN"))
	private static void glErrorChat$onGetError(CallbackInfoReturnable<Integer> cir) {
		int error = cir.getReturnValue();

		if (error != 0) {
			GlErrorReporter.report(error);
		}
	}
}