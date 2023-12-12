package net.coderbot.iris.compat.sodium.mixin.shader_overrides;

import com.mojang.blaze3d.systems.RenderSystem;
import me.jellysquid.mods.sodium.client.gl.device.RenderDevice;
import me.jellysquid.mods.sodium.client.gl.shader.GlProgram;
import me.jellysquid.mods.sodium.client.gl.shader.GlShader;
import me.jellysquid.mods.sodium.client.gl.shader.ShaderConstants;
import me.jellysquid.mods.sodium.client.gl.shader.ShaderLoader;
import me.jellysquid.mods.sodium.client.gl.shader.ShaderParser;
import me.jellysquid.mods.sodium.client.gl.shader.ShaderType;
import me.jellysquid.mods.sodium.client.render.chunk.ShaderChunkRenderer;
import me.jellysquid.mods.sodium.client.render.chunk.shader.ChunkShaderInterface;
import me.jellysquid.mods.sodium.client.render.chunk.terrain.TerrainRenderPass;
import me.jellysquid.mods.sodium.client.render.chunk.vertex.format.ModelQuadFormat;
import net.coderbot.iris.Iris;
import net.coderbot.iris.compat.sodium.impl.shader_overrides.IrisChunkShaderInterface;
import net.coderbot.iris.compat.sodium.impl.shader_overrides.ShaderChunkRendererExt;
import net.coderbot.iris.compat.sodium.impl.vertex_format.IrisModelVertexFormats;
import net.coderbot.iris.gl.program.ProgramSamplers;
import net.coderbot.iris.gl.program.ProgramUniforms;
import net.coderbot.iris.shaderpack.transform.StringTransformations;
import net.coderbot.iris.shaderpack.transform.Transformations;
import net.coderbot.iris.shadows.ShadowRenderingState;
import net.coderbot.iris.compat.sodium.impl.shader_overrides.IrisChunkProgramOverrides;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Overrides shaders in {@link ShaderChunkRenderer} with our own as needed.
 */
@Mixin(ShaderChunkRenderer.class)
public class MixinShaderChunkRenderer implements ShaderChunkRendererExt {
    @Unique
    private IrisChunkProgramOverrides irisChunkProgramOverrides;

    @Unique
    private GlProgram<IrisChunkShaderInterface> override;

    @Shadow(remap = false)
    private GlProgram<ChunkShaderInterface> activeProgram;

    @Shadow(remap = false)
	@Final
	protected ModelQuadFormat vertexType;

    @Inject(method = "<init>", at = @At("RETURN"), remap = false)
    private void iris$onInit(RenderDevice device, ModelQuadFormat vertexType, CallbackInfo ci) {
        irisChunkProgramOverrides = new IrisChunkProgramOverrides();
    }

	@Inject(method = "begin", at = @At("HEAD"), cancellable = true, remap = false)
	private void iris$begin(TerrainRenderPass pass, CallbackInfo ci) {
		this.override = irisChunkProgramOverrides.getProgramOverride(pass, this.vertexType);

		irisChunkProgramOverrides.bindFramebuffer(pass);

		if (this.override == null) {
			return;
		}

		// Override with our own behavior
		ci.cancel();

		// Set a sentinel value here, so we can catch it in RegionChunkRenderer and handle it appropriately.
		activeProgram = null;

		if (ShadowRenderingState.areShadowsCurrentlyBeingRendered()) {
			// No back face culling during the shadow pass
			// TODO: Hopefully this won't be necessary in the future...
			RenderSystem.disableCull();
		}

		pass.startDrawing();

		override.bind();
		override.getInterface().setupState();
	}

    @Inject(method = "end", at = @At("HEAD"), remap = false, cancellable = true)
    private void iris$onEnd(TerrainRenderPass pass, CallbackInfo ci) {
        ProgramUniforms.clearActiveUniforms();
		ProgramSamplers.clearActiveSamplers();
		irisChunkProgramOverrides.unbindFramebuffer();

        if (override != null) {
			override.getInterface().restore();
			override.unbind();
			pass.endDrawing();

			override = null;
			ci.cancel();
		}
    }

    @Inject(method = "delete", at = @At("HEAD"), remap = false)
    private void iris$onDelete(CallbackInfo ci) {
        irisChunkProgramOverrides.deleteShaders();
    }

	@Override
	public GlProgram<IrisChunkShaderInterface> iris$getOverride() {
		return override;
	}
}
