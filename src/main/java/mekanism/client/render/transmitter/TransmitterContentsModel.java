package mekanism.client.render.transmitter;

import com.mojang.blaze3d.vertex.PoseStack;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import mekanism.client.render.RenderResizableCuboid.TexturePicker;
import mekanism.common.Mekanism;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;

/** The original transmitter's contents mesh, including its stepped push/pull connectors. */
public final class TransmitterContentsModel {
    private static Map<String, List<Face>> parts;

    private TransmitterContentsModel() {
    }

    public static void clear() {
        parts = null;
    }

    public static void submit(TransmitterRenderState state, PoseStack poses, SubmitNodeCollector nodes,
          TexturePicker texture, int tint, int light) {
        if (texture == null || (tint >>> 24) == 0) {
            return;
        }
        var mesh = parts();
        var connections = state.connectionContents == null ? List.<String>of() : state.connectionContents;
        // Snapshot the visible parts before deferred submission; never capture mutable tile/network data.
        var faces = new ArrayList<Face>();
        for (Direction side : Direction.values()) {
            String prefix = side.getSerializedName();
            String group = prefix + "NONE";
            for (String connection : connections) {
                if (connection.startsWith(prefix)) {
                    group = connection;
                    break;
                }
            }
            faces.addAll(mesh.getOrDefault(group, List.of()));
        }
        nodes.submitCustomGeometry(poses, Sheets.translucentBlockSheet(), (pose, buffer) -> {
            for (Face face : faces) {
                var sprite = texture.apply(face.side);
                if (sprite == null) {
                    continue;
                }
                for (Vertex vertex : face.vertices) {
                    buffer.addVertex(pose, vertex.x + .5F, vertex.y + .5F, vertex.z + .5F)
                          .setColor(tint).setUv(sprite.getU(vertex.u), sprite.getV(1 - vertex.v))
                          .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light)
                          .setNormal(pose, .57735026F, .57735026F, .57735026F);
                }
            }
        });
    }

    private static Map<String, List<Face>> parts() {
        if (parts == null) {
            try (var reader = Minecraft.getInstance().getResourceManager()
                  .getResourceOrThrow(RenderTransmitterBase.MODEL_LOCATION).openAsReader()) {
                parts = read(reader);
            } catch (IOException | IllegalArgumentException error) {
                Mekanism.logger.error("Unable to load transmitter contents mesh", error);
                parts = Map.of();
            }
        }
        return parts;
    }

    static Map<String, List<Face>> read(BufferedReader reader) throws IOException {
        var positions = new ArrayList<float[]>();
        var uvs = new ArrayList<float[]>();
        var mesh = new HashMap<String, List<Face>>();
        String group = "";
        for (String line; (line = reader.readLine()) != null;) {
            String[] values = line.trim().split("\\s+");
            switch (values[0]) {
                case "v" -> positions.add(new float[]{Float.parseFloat(values[1]), Float.parseFloat(values[2]), Float.parseFloat(values[3])});
                case "vt" -> uvs.add(new float[]{Float.parseFloat(values[1]), Float.parseFloat(values[2])});
                case "g", "o" -> group = values[1];
                case "f" -> {
                    if (values.length != 5) {
                        throw new IllegalArgumentException("Transmitter contents faces must be quads");
                    }
                    var vertices = new ArrayList<Vertex>(4);
                    for (int i = 1; i <= 4; i++) {
                        String[] indices = values[i].split("/");
                        float[] pos = positions.get(Integer.parseInt(indices[0]) - 1);
                        float[] uv = uvs.get(Integer.parseInt(indices[1]) - 1);
                        vertices.add(new Vertex(pos[0], pos[1], pos[2], uv[0], uv[1]));
                    }
                    var a = vertices.get(0);
                    var b = vertices.get(1);
                    var c = vertices.get(2);
                    float ax = b.x - a.x, ay = b.y - a.y, az = b.z - a.z;
                    float bx = c.x - a.x, by = c.y - a.y, bz = c.z - a.z;
                    var side = Direction.getApproximateNearest(ay * bz - az * by, az * bx - ax * bz, ax * by - ay * bx);
                    mesh.computeIfAbsent(group, _ -> new ArrayList<>()).add(new Face(side, List.copyOf(vertices)));
                }
                default -> { }
            }
        }
        mesh.replaceAll((_, faces) -> List.copyOf(faces));
        return Map.copyOf(mesh);
    }

    record Vertex(float x, float y, float z, float u, float v) {
    }

    record Face(Direction side, List<Vertex> vertices) {
    }
}
