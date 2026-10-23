package mekanism.common.integration;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Set;
import mekanism.common.Mekanism;
import net.neoforged.fml.ModList;

/** Startup compatibility for Iris 1.11.4's pre-26.1 Mekanism renderer patches. */
public final class LegacyIrisCompatibility {

    private static final Set<String> OBSOLETE_PATCHES = Set.of(
          "net.irisshaders.iris.mixin.forge.MixinRenderMekasuit",
          "net.irisshaders.iris.mixin.forge.MixinRenderFlame",
          "net.irisshaders.iris.mixin.forge.MixinRenderSPS"
    );

    private LegacyIrisCompatibility() {
    }

    public static void register() {
        var iris = ModList.get().getModContainerById("iris");
        if (iris.isEmpty() || !iris.get().getModInfo().getVersion().toString().equals("1.11.4+mc26.1.2")) {
            return;
        }
        try {
            //Use the existing optional MixinSquared API without making it a dependency for standalone Mekanism.
            ClassLoader loader = LegacyIrisCompatibility.class.getClassLoader();
            Class<?> api = Class.forName("com.bawnorton.mixinsquared.api.MixinCanceller", false, loader);
            Class<?> registrar = Class.forName("com.bawnorton.mixinsquared.canceller.MixinCancellerRegistrar", false, loader);
            Object canceller = Proxy.newProxyInstance(loader, new Class<?>[]{api}, (proxy, method, args) -> switch (method.getName()) {
                case "shouldCancel" -> OBSOLETE_PATCHES.contains(args[1]) && ((List<?>) args[0]).stream()
                      .anyMatch(target -> target.toString().replace('/', '.').startsWith("mekanism.client.render."));
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                case "toString" -> "Mekanism 26.1 legacy Iris renderer compatibility";
                default -> throw new UnsupportedOperationException(method.toString());
            });
            registrar.getMethod("register", api).invoke(null, canceller);
            Mekanism.logger.info("Registered compatibility for Iris 1.11.4's three obsolete Mekanism renderer patches");
        } catch (ReflectiveOperationException exception) {
            Mekanism.logger.error("Could not register legacy Iris renderer compatibility; MixinSquared is required with Iris 1.11.4", exception);
        }
    }
}
