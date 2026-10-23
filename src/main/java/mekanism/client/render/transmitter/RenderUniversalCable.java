package mekanism.client.render.transmitter;

import com.mojang.blaze3d.vertex.PoseStack;
import mekanism.client.render.transmitter.TransmitterRenderState.CableRenderState;
import mekanism.client.render.MekanismRenderer;
import mekanism.common.base.ProfilerConstants;
import mekanism.common.content.network.EnergyNetwork;
import mekanism.common.content.network.transmitter.UniversalCable;
import mekanism.common.tile.transmitter.TileEntityUniversalCable;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.util.ARGB;
import net.minecraft.util.LightCoordsUtil;
import org.jspecify.annotations.Nullable;

public class RenderUniversalCable extends RenderTransmitterBase<TileEntityUniversalCable, CableRenderState> {

    public RenderUniversalCable(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public CableRenderState createRenderState() {
        return new CableRenderState();
    }

    @Override
    public void extractRenderState(TileEntityUniversalCable cable, CableRenderState state, float partialTick, Vec3 cameraPosition,
          ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        super.extractRenderState(cable, state, partialTick, cameraPosition, breakProgress);
        state.currentScale = 0;
        EnergyNetwork network = cable.getTransmitter().getTransmitterNetwork();
        if (network == null) {//TODO - 26.1: Does this race condition still exist?
            return;//race conditions, yay
        }
        state.currentScale = network.currentScale;
    }

    @Override
    public void submit(CableRenderState state, PoseStack poseStack, SubmitNodeCollector nodeCollector, CameraRenderState camera) {
        TransmitterContentsModel.submit(state, poseStack, nodeCollector, MekanismRenderer.getSinglePicker(MekanismRenderer.energyIcon),
              ARGB.color(ARGB.as8BitChannel(state.currentScale), 255, 255, 255), LightCoordsUtil.FULL_BRIGHT);
    }

    @Override
    protected String getProfilerSection() {
        return ProfilerConstants.UNIVERSAL_CABLE;
    }

    @Override
    protected boolean shouldRenderTransmitter(TileEntityUniversalCable tile, Vec3 camera) {
        if (super.shouldRenderTransmitter(tile, camera)) {
            UniversalCable cable = tile.getTransmitter();
            if (cable.hasTransmitterNetwork()) {
                EnergyNetwork network = cable.getTransmitterNetworkNN();
                //Note: We don't check if the network is empty as we don't actually ever sync the energy value to the client
                return network.currentScale > 0;
            }
        }
        return false;
    }
}
