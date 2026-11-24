package dev.fishclient.feature;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.fishclient.FishClient;
import dev.fishclient.config.FishConfig;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;

/** Draws hats and headphones on the local player, attached to the head so they follow every animation. */
public final class CosmeticLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
	private record Piece(ModelPart part, Identifier texture) {
	}

	private final Map<String, Piece> hats;
	private final Piece headphones;

	public CosmeticLayer(RenderLayerParent<AvatarRenderState, PlayerModel> parent) {
		super(parent);
		this.hats = Map.of(
			"top_hat", new Piece(topHat(), texture("top_hat")),
			"fisher_hat", new Piece(fisherHat(), texture("fisher_hat")),
			"crown", new Piece(crown(), texture("crown")),
			"fish_hat", new Piece(fishHat(), texture("fish_hat"))
		);
		this.headphones = new Piece(headphonesModel(), texture("headphones"));
	}

	private static Identifier texture(String name) {
		return FishClient.id("textures/cosmetics/" + name + ".png");
	}

	@Override
	public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, AvatarRenderState state, float yRot, float xRot) {
		Minecraft mc = Minecraft.getInstance();
		if (state.isInvisible || mc.player == null || state.id != mc.player.getId()) {
			return;
		}
		FishConfig config = FishConfig.get();
		Piece hat = this.hats.get(config.hat);
		boolean phones = Cosmetics.headphonesVisible();
		if (hat == null && !phones) {
			return;
		}
		poseStack.pushPose();
		getParentModel().root().translateAndRotate(poseStack);
		getParentModel().translateToHead(poseStack);
		if (hat != null) {
			draw(hat, poseStack, collector, lightCoords, config.hat.equals("fish_hat") ? 0xFFFFFFFF : config.accentColor);
		}
		if (phones) {
			draw(this.headphones, poseStack, collector, lightCoords, config.accentColor);
		}
		poseStack.popPose();
	}

	private static void draw(Piece piece, PoseStack poseStack, SubmitNodeCollector collector, int light, int tint) {
		collector.submitModelPart(piece.part(), poseStack, RenderTypes.entitySolid(piece.texture()), light, OverlayTexture.NO_OVERLAY, null, tint, null);
	}

	private static ModelPart bake(java.util.function.Consumer<PartDefinition> build) {
		MeshDefinition mesh = new MeshDefinition();
		build.accept(mesh.getRoot());
		return LayerDefinition.create(mesh, 64, 64).bakeRoot();
	}

	private static ModelPart topHat() {
		return bake(root -> {
			root.addOrReplaceChild("brim", CubeListBuilder.create().texOffs(0, 32).addBox(-5.5f, -9f, -5.5f, 11f, 1f, 11f), PartPose.ZERO);
			root.addOrReplaceChild("crown", CubeListBuilder.create().texOffs(0, 0).addBox(-3.5f, -15f, -3.5f, 7f, 6f, 7f), PartPose.ZERO);
			root.addOrReplaceChild("band", CubeListBuilder.create().texOffs(0, 22).addBox(-3.5f, -10f, -3.5f, 7f, 1f, 7f, new CubeDeformation(0.05f)), PartPose.ZERO);
		});
	}

	private static ModelPart fisherHat() {
		return bake(root -> {
			root.addOrReplaceChild("brim", CubeListBuilder.create().texOffs(0, 32).addBox(-6.5f, -8.8f, -6.5f, 13f, 1f, 13f), PartPose.ZERO);
			root.addOrReplaceChild("crown", CubeListBuilder.create().texOffs(0, 0).addBox(-4.5f, -11.5f, -4.5f, 9f, 3f, 9f), PartPose.ZERO);
			root.addOrReplaceChild("band", CubeListBuilder.create().texOffs(0, 18).addBox(-4.5f, -9.5f, -4.5f, 9f, 1f, 9f, new CubeDeformation(0.05f)), PartPose.ZERO);
		});
	}

	private static ModelPart crown() {
		return bake(root -> {
			root.addOrReplaceChild("front", CubeListBuilder.create().texOffs(0, 0).addBox(-4.5f, -9.5f, -4.5f, 9f, 1.5f, 1f), PartPose.ZERO);
			root.addOrReplaceChild("back", CubeListBuilder.create().texOffs(0, 0).addBox(-4.5f, -9.5f, 3.5f, 9f, 1.5f, 1f), PartPose.ZERO);
			root.addOrReplaceChild("left", CubeListBuilder.create().texOffs(0, 0).addBox(-4.5f, -9.5f, -3.5f, 1f, 1.5f, 7f), PartPose.ZERO);
			root.addOrReplaceChild("right", CubeListBuilder.create().texOffs(0, 0).addBox(3.5f, -9.5f, -3.5f, 1f, 1.5f, 7f), PartPose.ZERO);
			int i = 0;
			for (float[] tip : new float[][]{{-4.5f, -4.5f}, {-0.5f, -4.5f}, {3.5f, -4.5f}, {-4.5f, -0.5f}, {3.5f, -0.5f}, {-4.5f, 3.5f}, {-0.5f, 3.5f}, {3.5f, 3.5f}}) {
				root.addOrReplaceChild("tip" + i++, CubeListBuilder.create().texOffs(0, 0).addBox(tip[0], -11f, tip[1], 1f, 1.5f, 1f), PartPose.ZERO);
			}
		});
	}

	private static ModelPart fishHat() {
		return bake(root -> {
			root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 0).addBox(-2f, -12f, -5f, 4f, 4f, 9f), PartPose.ZERO);
			root.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 16).addBox(-1.5f, -11.5f, -7f, 3f, 3f, 2f), PartPose.ZERO);
			root.addOrReplaceChild("tail", CubeListBuilder.create().texOffs(0, 24).addBox(-0.5f, -12.5f, 4f, 1f, 5f, 3f), PartPose.ZERO);
			root.addOrReplaceChild("fin", CubeListBuilder.create().texOffs(0, 34).addBox(-0.5f, -14f, -2f, 1f, 2f, 4f), PartPose.ZERO);
		});
	}

	private static ModelPart headphonesModel() {
		return bake(root -> {
			root.addOrReplaceChild("band_top", CubeListBuilder.create().texOffs(0, 0).addBox(-4.5f, -9f, -1f, 9f, 1f, 2f), PartPose.ZERO);
			root.addOrReplaceChild("band_left", CubeListBuilder.create().texOffs(0, 4).addBox(-5f, -8.5f, -1f, 1f, 4f, 2f), PartPose.ZERO);
			root.addOrReplaceChild("band_right", CubeListBuilder.create().texOffs(0, 4).addBox(4f, -8.5f, -1f, 1f, 4f, 2f), PartPose.ZERO);
			root.addOrReplaceChild("cup_left", CubeListBuilder.create().texOffs(0, 12).addBox(-5.6f, -5.5f, -2f, 1.5f, 4f, 4f), PartPose.ZERO);
			root.addOrReplaceChild("cup_right", CubeListBuilder.create().texOffs(0, 12).addBox(4.1f, -5.5f, -2f, 1.5f, 4f, 4f), PartPose.ZERO);
		});
	}
}
