package mekanism.common.integration.lookingat.jade;

import java.util.List;
import java.util.Optional;
import mekanism.api.SerializationConstants;
import mekanism.client.render.IFancyFontRenderer.TextAlignment;
import mekanism.common.integration.lookingat.ILookingAtElement;
import mekanism.common.integration.lookingat.EnergyElement;
import mekanism.common.integration.lookingat.LookingAtElement;
import mekanism.common.integration.lookingat.LookingAtElementType;
import mekanism.common.integration.lookingat.TextElement;
import mekanism.common.tile.base.TileEntityUpdateable;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.CommonColors;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;
import snownee.jade.api.Accessor;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.JadeIds;
import snownee.jade.api.config.IPluginConfig;
import snownee.jade.api.ui.Element;
import snownee.jade.api.ui.BoxStyle;
import snownee.jade.api.ui.JadeUI;
import snownee.jade.api.view.ProgressView;

public class JadeTooltipRenderer<ACCESSOR extends Accessor<?>> implements IComponentProvider<ACCESSOR> {

    static final JadeTooltipRenderer<?> INSTANCE = new JadeTooltipRenderer<>();
    private static final Element STANDARD_ENERGY_OVERLAY = JadeUI.horizontalTiledSprite(
          RenderPipelines.GUI_TEXTURED, JadeIds.JADE("energy_progress"), 16, 16);

    @Override
    public Identifier getUid() {
        return JadeConstants.TOOLTIP_RENDERER;
    }

    @Override
    public void appendTooltip(ITooltip tooltip, ACCESSOR accessor, IPluginConfig config) {
        Tag tag = accessor.getServerData().get(SerializationConstants.MEK_DATA);
        if (tag != null) {
            Optional<List<ILookingAtElement>> optionalElements = accessor.decodeFromNbt(LookingAtElementType.ELEMENT_LIST_STREAM_CODEC, tag);
            //noinspection OptionalIsPresent - Capturing lambda
            if (optionalElements.isPresent()) {
                boolean mekanismEnergyStyle = !(accessor instanceof BlockAccessor block)
                      || block.getBlockEntity() instanceof TileEntityUpdateable;
                appendElements(tooltip, config, optionalElements.get(), mekanismEnergyStyle);
            }
        }
    }

    private void appendElements(ITooltip tooltip, IPluginConfig config, List<ILookingAtElement> elements, boolean mekanismEnergyStyle) {
        Component lastText = null;
        //Copy the data we need and have from the server and pass it on to the tooltip rendering
        for (ILookingAtElement element : elements) {
            if (element instanceof TextElement(Component text)) {
                if (lastText != null) {//Fallback to printing the last text
                    tooltip.add(lastText);
                }
                lastText = text;
            } else {
                Identifier name = element.getID();
                if (config.get(name)) {
                    if (!mekanismEnergyStyle && element instanceof EnergyElement energy) {
                        // Foreign FE devices use Jade's native red striped bar, while keeping the
                        // same long-valued snapshot, text (including infinity) and energy toggle.
                        if (lastText != null) {
                            tooltip.add(lastText);
                        }
                        tooltip.add(standardEnergy(energy).tag(name));
                    } else {
                        tooltip.add(MekElement.create(lastText, (LookingAtElement) element).tag(name));
                    }
                }
                lastText = null;
            }
        }
        if (lastText != null) {
            tooltip.add(lastText);
        }
    }

    private static Element standardEnergy(EnergyElement energy) {
        float ratio = energy.getEnergy() == Long.MAX_VALUE ? 1 : energy.getMaxEnergy() > 0
              ? Mth.clamp((float) (energy.getEnergy() / (double) energy.getMaxEnergy()), 0, 1) : 0;
        return JadeUI.progress(new ProgressView(ProgressView.Part.of(ratio, STANDARD_ENERGY_OVERLAY),
              energy.getText().copy().withStyle(ChatFormatting.WHITE), JadeUI.progressStyle(), BoxStyle.nestedBox()));
    }

    private static class MekElement extends Element {

        public static Element create(@Nullable Component text, LookingAtElement element) {
            MekElement mekElement = new MekElement(text, element);
            int width = element.getWidth();
            int height = element.getHeight() + 2;
            if (text != null) {
                width = Math.max(width, 96);
                height += 14;
            }
            return mekElement.size(width, height);
        }

        @Nullable
        private final Component text;
        private final LookingAtElement element;

        private MekElement(@Nullable Component text, LookingAtElement element) {
            this.element = element;
            this.text = text;
        }

        @Override
        @Nullable
        public Component getNarration() {
            return text;
        }

        @Override
        public void extractRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTicks) {
            int x = getX();
            int y = getY();
            if (text != null) {
                element.drawScrollingString(guiGraphics, text, x, y + 3, TextAlignment.LEFT, CommonColors.WHITE, 4, false);
                y += 13;
            }
            element.render(guiGraphics, x, y + 1);
        }
    }
}
