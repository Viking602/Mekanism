package mekanism.common.item.block;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Consumer;
import mekanism.api.RelativeSide;
import mekanism.api.energy.IEnergyContainer;
import mekanism.api.text.EnumColor;
import mekanism.common.MekanismLang;
import mekanism.common.block.BlockEnergyCube;
import mekanism.common.block.attribute.Attribute;
import mekanism.common.component.component.AttachedEjector;
import mekanism.common.component.component.AttachedSideConfig;
import mekanism.common.component.component.AttachedSideConfig.LightConfigInfo;
import mekanism.common.component.containers.creator.IContainerCreator;
import mekanism.common.component.containers.energy.EnergyContainerBuilder;
import mekanism.common.component.containers.type.ContainerType;
import mekanism.common.config.MekanismConfig;
import mekanism.common.lib.transmitter.TransmissionType;
import mekanism.common.registration.impl.CreativeTabDeferredRegister.ICustomCreativeTabContents;
import mekanism.common.registries.MekanismBlocks;
import mekanism.common.registries.MekanismDataComponents;
import mekanism.common.tier.EnergyCubeTier;
import mekanism.common.tile.component.config.DataType;
import mekanism.common.util.EnumUtils;
import mekanism.common.util.ItemAccessUtils;
import mekanism.common.util.StorageUtils;
import mekanism.common.util.text.EnergyDisplay;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.item.ItemResource;

public class ItemBlockEnergyCube extends ItemBlockTooltip<BlockEnergyCube> implements ICustomCreativeTabContents {

    public static final AttachedSideConfig SIDE_CONFIG = sideConfig(LightConfigInfo.FRONT_OUT_EJECT);
    public static final AttachedSideConfig ALL_INPUT = Util.make(() -> {
        Map<RelativeSide, DataType> sideData = new EnumMap<>(RelativeSide.class);
        for (RelativeSide side : EnumUtils.SIDES) {
            sideData.put(side, DataType.INPUT);
        }
        return sideConfig(new LightConfigInfo(sideData, false));
    });
    public static final AttachedSideConfig ALL_OUTPUT = Util.make(() -> {
        Map<RelativeSide, DataType> sideData = new EnumMap<>(RelativeSide.class);
        for (RelativeSide side : EnumUtils.SIDES) {
            sideData.put(side, DataType.OUTPUT);
        }
        return sideConfig(new LightConfigInfo(sideData, true));
    });

    private static AttachedSideConfig sideConfig(LightConfigInfo energyConfig) {
        Map<TransmissionType, LightConfigInfo> configInfo = new EnumMap<>(TransmissionType.class);
        configInfo.put(TransmissionType.ITEM, LightConfigInfo.FRONT_OUT_NO_EJECT);
        configInfo.put(TransmissionType.ENERGY, energyConfig);
        return new AttachedSideConfig(configInfo);
    }

    private final EnergyCubeTier tier;

    public ItemBlockEnergyCube(BlockEnergyCube block, Item.Properties properties) {
        EnergyCubeTier cubeTier = Attribute.getTierNN(block, EnergyCubeTier.class);
        tier = cubeTier;
        super(block, true, defaultProperties(properties, cubeTier));
    }

    private static Item.Properties defaultProperties(Item.Properties properties, EnergyCubeTier tier) {
        properties.component(MekanismDataComponents.EJECTOR, AttachedEjector.DEFAULT)
              .component(MekanismDataComponents.SIDE_CONFIG, tier == EnergyCubeTier.CREATIVE ? ALL_OUTPUT : SIDE_CONFIG);
        if (tier == EnergyCubeTier.CREATIVE) {
            properties.component(MekanismDataComponents.ATTACHED_ENERGY, tier.getCapacity());
        }
        return properties;
    }

    @Override
    public Component getName(ItemStack stack) {
        Component name = super.getName(stack);
        if (tier == EnergyCubeTier.CREATIVE) {
            return name.copy().append(Component.translatable(stack.getOrDefault(MekanismDataComponents.ATTACHED_ENERGY, 0L) > 0
                  ? "block.mekanism.creative_energy_cube.source" : "block.mekanism.creative_energy_cube.sink"));
        }
        return name;
    }

    @Override
    public EnergyCubeTier getTier() {
        return tier;
    }

    @Override
    @Deprecated
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay tooltipDisplay,
          Consumer<Component> tooltipAdder, TooltipFlag flag) {
        StorageUtils.addStoredEnergy(ItemAccessUtils.sideEffectFreeAccess(stack), tooltipAdder, true);
        tooltipAdder.accept(MekanismLang.CAPACITY.translateColored(EnumColor.INDIGO, EnumColor.GRAY, EnergyDisplay.of(tier.getCapacity())));
        super.appendHoverText(stack, context, tooltipDisplay, tooltipAdder, flag);
    }

    @Override
    protected void addTypeDetails(ItemStack stack, ItemAccess itemAccess, Item.TooltipContext context, TooltipDisplay tooltipDisplay,
          Consumer<Component> tooltipAdder, TooltipFlag flag) {
        //Don't call super so that we can exclude the stored energy from being shown as we show it in hover text
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return StorageUtils.isEnergyBarVisible(stack);
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return StorageUtils.getEnergyBarWidth(stack);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return MekanismConfig.client.energyColor.get();
    }

    @Override
    public void addItems(Holder<Item> item, Consumer<ItemStack> tabOutput) {
        if (tier == EnergyCubeTier.CREATIVE) {
            //The ordinary creative pick is a power source; expose the explicit void variant separately.
            tabOutput.accept(ContainerType.ENERGY.getFilledVariant(withCreativeSideConfig(ALL_OUTPUT), null));
            tabOutput.accept(withCreativeSideConfig(ALL_INPUT).toStack());
        } else {
            tabOutput.accept(ContainerType.ENERGY.getFilledVariant(item, null));
        }
    }

    @Override
    public boolean addDefault() {
        return tier != EnergyCubeTier.CREATIVE;
    }

    public static ItemResource withCreativeSideConfig(AttachedSideConfig config) {
        ItemResource resource = ItemResource.of(MekanismBlocks.CREATIVE_ENERGY_CUBE)
              .with(MekanismDataComponents.SIDE_CONFIG, config);
        return ALL_INPUT.equals(config) ? resource.with(MekanismDataComponents.ATTACHED_ENERGY, 0L) : resource;
    }

    @Override
    protected IContainerCreator<IEnergyContainer, Long> getDefaultEnergyContainer() {
        return EnergyContainerBuilder.ENERGY_CUBE;
    }
}
