package mekanism.client.model.data;

import java.util.Arrays;
import mekanism.common.lib.transmitter.ConnectionType;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TransmitterModelDataTest {
    @Test
    void terminalPlatesOnlyCloseNormalDevicePorts() {
        for (Direction side : Direction.values()) {
            for (ConnectionType type : ConnectionType.values()) {
                var connections = new ConnectionType[6];
                Arrays.fill(connections, ConnectionType.NONE);
                connections[side.ordinal()] = type;
                var data = new TransmitterModelData();
                data.setConnectionData(connections);
                data.setTerminalConnections((byte) (1 << side.ordinal()));
                for (Direction queried : Direction.values()) {
                    assertEquals(queried == side && type == ConnectionType.NORMAL, data.hasTerminal(queried));
                }
            }
        }
    }

    @Test
    void changingBetweenCableAndDeviceInvalidatesTheModelKeyEvenWhenBothAreNormal() {
        var connections = new ConnectionType[6];
        Arrays.fill(connections, ConnectionType.NORMAL);
        var cable = new TransmitterModelData();
        cable.setConnectionData(connections);
        var device = new TransmitterModelData();
        device.setConnectionData(connections);
        assertEquals(cable, device);
        device.setTerminalConnections((byte) (1 << Direction.UP.ordinal()));
        assertNotEquals(cable, device);
        assertNotEquals(cable.hashCode(), device.hashCode());
        assertFalse(cable.hasTerminal(Direction.UP));
        assertTrue(device.hasTerminal(Direction.UP));
        device.setTerminalConnections((byte) 0);
        assertEquals(cable, device);
    }
}
