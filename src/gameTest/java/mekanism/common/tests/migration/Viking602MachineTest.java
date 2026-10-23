package mekanism.common.tests.migration;

import mekanism.api.AutomationType;
import mekanism.api.RelativeSide;
import mekanism.common.block.attribute.Attribute;
import mekanism.common.lib.transmitter.TransmissionType;
import mekanism.common.registries.MekanismBlocks;
import mekanism.common.registries.MekanismChemicals;
import mekanism.common.registries.MekanismItems;
import mekanism.common.tests.helpers.MekGameTestHelper;
import mekanism.common.tile.TileEntityEnergyCube;
import mekanism.common.tile.component.config.DataType;
import mekanism.common.tile.machine.TileEntityElectrolyticSeparator;
import mekanism.common.tile.machine.TileEntityEnergizedSmelter;
import mekanism.common.tile.machine.TileEntityMetallurgicInfuser;
import mekanism.common.tile.prefab.TileEntityConfigurableMachine;
import mekanism.generators.common.registries.GeneratorsBlocks;
import mekanism.generators.common.tile.TileEntityBioGenerator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.testframework.DynamicTest;
import net.neoforged.testframework.annotation.ForEachTest;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.GameTest;
import net.neoforged.testframework.gametest.StructureTemplateBuilder;

@ForEachTest(groups = "migration.viking602")
public class Viking602MachineTest {

    private static StructureTemplateBuilder poweredTemplate(BlockState machine) {
        return StructureTemplateBuilder.withSize(3, 1, 1)
              .set(0, 0, 0, machine)
              .set(1, 0, 0, MekanismBlocks.BASIC_UNIVERSAL_CABLE.defaultState())
              .set(2, 0, 0, Attribute.setFacing(MekanismBlocks.BASIC_ENERGY_CUBE.defaultState(), Direction.WEST));
    }

    private static void chargePowerSource(MekGameTestHelper helper, TileEntityConfigurableMachine machine) {
        //Structure placement bypasses the item components that supply normal placement's default side configuration.
        for (RelativeSide side : RelativeSide.values()) {
            machine.getConfig().getConfig(TransmissionType.ENERGY).setDataType(DataType.INPUT, side);
            machine.getConfig().sideChanged(TransmissionType.ENERGY, side);
        }
        TileEntityEnergyCube cube = helper.getBlockEntity(2, 0, 0, TileEntityEnergyCube.class);
        cube.getConfig().getConfig(TransmissionType.ENERGY).setDataType(DataType.OUTPUT, RelativeSide.FRONT);
        cube.getConfig().sideChanged(TransmissionType.ENERGY, RelativeSide.FRONT);
        cube.getConfig().getConfig(TransmissionType.ENERGY).setEjecting(true);
        try (Transaction transaction = Transaction.openRoot()) {
            cube.energyContainer().setEnergy(cube.energyContainer().getCapacityAsLong(), transaction);
            transaction.commit();
        }
    }

    @GameTest(timeoutTicks = 300)
    @TestHolder(description = "Run a real energized smelter recipe and check input, output and energy consumption.")
    public static void energizedSmelterProcessesOre(DynamicTest test) {
        test.registerGameTestTemplate(() -> poweredTemplate(MekanismBlocks.ENERGIZED_SMELTER.defaultState()));
        test.onGameTest(MekGameTestHelper.class, helper -> helper.startSequence()
              .thenExecuteAfter(2, () -> {
                  TileEntityEnergizedSmelter machine = helper.getBlockEntity(0, 0, 0, TileEntityEnergizedSmelter.class);
                  chargePowerSource(helper, machine);
                  try (Transaction transaction = Transaction.openRoot()) {
                      helper.assertTrue(machine.getInventorySlots().getFirst().insert(ItemResource.of(Items.IRON_ORE), 1,
                            transaction, AutomationType.MANUAL) == 1, "The input must accept iron ore");
                      transaction.commit();
                  }
              })
              .thenExecuteAfter(220, () -> {
                  TileEntityEnergizedSmelter machine = helper.getBlockEntity(0, 0, 0, TileEntityEnergizedSmelter.class);
                  helper.assertContainerContains(BlockPos.ZERO, Items.IRON_INGOT, 1);
                  helper.assertTrue(machine.getInventorySlots().getFirst().isEmpty(), "The input must be consumed");
                  TileEntityEnergyCube cube = helper.getBlockEntity(2, 0, 0, TileEntityEnergyCube.class);
                  helper.assertTrue(cube.energyContainer().getAmountAsLong() < cube.energyContainer().getCapacityAsLong(),
                        "The cable must transfer power from the energy cube while the machine processes");
              }).thenSucceed());
    }

    @GameTest(timeoutTicks = 300)
    @TestHolder(description = "Run infused-alloy production using actual item and chemical recipe inputs.")
    public static void metallurgicInfuserProducesAlloy(DynamicTest test) {
        test.registerGameTestTemplate(() -> poweredTemplate(MekanismBlocks.METALLURGIC_INFUSER.defaultState()));
        test.onGameTest(MekGameTestHelper.class, helper -> helper.startSequence()
              .thenExecute(() -> {
                  TileEntityMetallurgicInfuser machine = helper.getBlockEntity(0, 0, 0, TileEntityMetallurgicInfuser.class);
                  chargePowerSource(helper, machine);
                  try (Transaction transaction = Transaction.openRoot()) {
                      helper.assertTrue(machine.infusionTank.insert(MekanismChemicals.REDSTONE.asResource(), 10, transaction,
                            AutomationType.MANUAL) == 10, "The tank must accept redstone infusion");
                      helper.assertTrue(machine.getInventorySlots().get(1).insert(ItemResource.of(Items.COPPER_INGOT), 1,
                            transaction, AutomationType.MANUAL) == 1, "The input must accept copper");
                      transaction.commit();
                  }
              })
              .thenExecuteAfter(220, () -> {
                  TileEntityMetallurgicInfuser machine = helper.getBlockEntity(0, 0, 0, TileEntityMetallurgicInfuser.class);
                  helper.assertContainerContains(BlockPos.ZERO, MekanismItems.INFUSED_ALLOY.get(), 1);
                  helper.assertTrue(machine.getInventorySlots().get(1).isEmpty() && machine.infusionTank.isEmpty(),
                        "The item and chemical inputs must both be consumed");
              }).thenSucceed());
    }

    @GameTest
    @TestHolder(description = "Split water in an electrolytic separator and verify the exact hydrogen/oxygen ratio.")
    public static void electrolyticSeparatorSplitsWater(DynamicTest test) {
        test.registerGameTestTemplate(() -> StructureTemplateBuilder.withSize(1, 1, 1)
              .set(0, 0, 0, MekanismBlocks.ELECTROLYTIC_SEPARATOR.defaultState()));
        test.onGameTest(MekGameTestHelper.class, helper -> helper.startSequence()
              .thenExecute(() -> {
                  TileEntityElectrolyticSeparator machine = helper.getBlockEntity(0, 0, 0, TileEntityElectrolyticSeparator.class);
                  try (Transaction transaction = Transaction.openRoot()) {
                      machine.energyContainer().setEnergy(machine.energyContainer().getCapacityAsLong(), transaction);
                      helper.assertTrue(machine.fluidTank.insert(FluidResource.of(Fluids.WATER), 2, transaction,
                            AutomationType.MANUAL) == 2, "The separator must accept water");
                      transaction.commit();
                  }
              })
              .thenExecuteAfter(25, () -> {
                  TileEntityElectrolyticSeparator machine = helper.getBlockEntity(0, 0, 0, TileEntityElectrolyticSeparator.class);
                  helper.assertTrue(machine.fluidTank.isEmpty(), "Water must be consumed");
                  helper.assertTrue(machine.leftTank.resource().equals(MekanismChemicals.HYDROGEN.asResource()) &&
                        machine.leftTank.amountAsLong() == 2, "The hydrogen output must be exactly 2 mB");
                  helper.assertTrue(machine.rightTank.resource().equals(MekanismChemicals.OXYGEN.asResource()) &&
                        machine.rightTank.amountAsLong() == 1, "The oxygen output must be exactly 1 mB");
              }).thenSucceed());
    }

    @GameTest
    @TestHolder(description = "Convert a biofuel item and produce energy using the Generators module.")
    public static void bioGeneratorProducesEnergy(DynamicTest test) {
        test.registerGameTestTemplate(() -> StructureTemplateBuilder.withSize(1, 1, 1)
              .set(0, 0, 0, GeneratorsBlocks.BIO_GENERATOR.defaultState()));
        test.onGameTest(MekGameTestHelper.class, helper -> helper.startSequence()
              .thenExecute(() -> {
                  TileEntityBioGenerator generator = helper.getBlockEntity(0, 0, 0, TileEntityBioGenerator.class);
                  try (Transaction transaction = Transaction.openRoot()) {
                      helper.assertTrue(generator.getInventorySlots().getFirst().insert(ItemResource.of(MekanismItems.BIO_FUEL.get()),
                            1, transaction, AutomationType.MANUAL) == 1, "The generator must accept biofuel");
                      transaction.commit();
                  }
              })
              .thenExecuteAfter(40, () -> {
                  TileEntityBioGenerator generator = helper.getBlockEntity(0, 0, 0, TileEntityBioGenerator.class);
                  helper.assertTrue(generator.getInventorySlots().getFirst().isEmpty(), "The biofuel item must be converted");
                  helper.assertTrue(generator.energyContainer().getAmountAsLong() > 0, "The generator must produce energy");
              }).thenSucceed());
    }
}
