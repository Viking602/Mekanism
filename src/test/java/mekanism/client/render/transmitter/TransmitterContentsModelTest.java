package mekanism.client.render.transmitter;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import mekanism.common.lib.transmitter.ConnectionType;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TransmitterContentsModelTest {
    @Test
    void nativeContentsFitTheSmallTransmitterAndIncludeEveryConnector() throws Exception {
        try (var reader = new BufferedReader(new InputStreamReader(getClass().getResourceAsStream(
              "/assets/mekanism/models/transmitter_contents.obj"), StandardCharsets.UTF_8))) {
            var mesh = TransmitterContentsModel.read(reader);
            assertEquals(24, mesh.size());
            for (Direction side : Direction.values()) {
                for (ConnectionType type : ConnectionType.values()) {
                    var faces = mesh.get(side.getSerializedName() + type.name());
                    assertNotNull(faces);
                    assertFalse(faces.isEmpty());
                    for (var face : faces) {
                        assertEquals(4, face.vertices().size());
                        for (var vertex : face.vertices()) {
                            assertTrue(Math.abs(vertex.x()) <= .5F && Math.abs(vertex.y()) <= .5F && Math.abs(vertex.z()) <= .5F);
                            assertTrue(vertex.u() >= 0 && vertex.u() <= 1 && vertex.v() >= 0 && vertex.v() <= 1);
                            if (type == ConnectionType.NORMAL || type == ConnectionType.NONE) {
                                for (Direction.Axis axis : Direction.Axis.values()) {
                                    if (axis != side.getAxis()) {
                                        assertTrue(Math.abs(axis.choose(vertex.x(), vertex.y(), vertex.z())) <= .168751F,
                                              "Energy must stay inside the 0.375-wide casing");
                                    }
                                }
                            }
                        }
                    }
                    if (type == ConnectionType.NONE) assertEquals(1, faces.size(), "Disconnected ends need their cap");
                    if (type == ConnectionType.PULL || type == ConnectionType.PUSH) assertTrue(faces.size() > 4, "Ports need their stepped necks and shoulders");
                }
            }
        }
    }
}
