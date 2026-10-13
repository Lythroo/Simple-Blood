package com.simpleblood.compat;

import com.simpleblood.Platform;
import com.simpleblood.SimpleBlood;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * Wet, reflective blood under Iris shader packs. Iris finds the labPBR maps of whatever
 * texture is bound through its {@code PBRTextureLoaderRegistry}, keyed by the texture's exact
 * class; the surface atlas registers a loader there that hands over its specular twin, so a
 * pack reading labPBR maps renders fresh pools smooth and reflective and dried blood matte.
 *
 * <p>Iris has no stable API for this, so it is touched only by reflection, once, on Iris's own
 * names (no Minecraft names, so it works mapped or not). Without Iris, or if its internals
 * moved, nothing is registered and the atlas skips its specular twin altogether.
 */
public final class IrisPbr {
    private IrisPbr() {}

    private static final String MOD_ID = "iris";
    /** Where the loader registry has lived across Iris versions, newest first. */
    private static final String[] PACKAGES = {
            "net.irisshaders.iris.pbr.loader.",          // Iris for 1.21.1 and later
            "net.irisshaders.iris.texture.pbr.loader.",  // Iris 1.7 for 1.20.1, same API
    };

    /** {@code -Dsimpleblood.irisPbr=false} keeps the blood matte under shaders, to rule this out. */
    public static boolean present() {
        return !"false".equalsIgnoreCase(System.getProperty("simpleblood.irisPbr"))
                && Platform.modIds().contains(MOD_ID);
    }

    /**
     * Makes Iris use {@code specular} as the labPBR specular map whenever a texture of exactly
     * {@code textureClass} is bound. Iris closes the textures its loaders hand over on every
     * shader reload, so {@code specular} must survive {@code close()}.
     *
     * @return whether Iris took the loader
     */
    public static boolean registerSpecular(Class<?> textureClass, Object specular) {
        if (!present()) return false;
        ClassLoader cl = IrisPbr.class.getClassLoader();
        for (String pkg : PACKAGES) {
            try {
                Class<?> registryClass = Class.forName(pkg + "PBRTextureLoaderRegistry", false, cl);
                Class<?> loaderClass = Class.forName(pkg + "PBRTextureLoader", false, cl);
                Class<?> consumerClass = Class.forName(pkg + "PBRTextureLoader$PBRTextureConsumer", false, cl);
                Method accept = null;
                for (Method m : consumerClass.getMethods()) {
                    if (m.getName().equals("acceptSpecularTexture")) accept = m;
                }
                if (accept == null) throw new NoSuchMethodException("acceptSpecularTexture");
                Method acceptSpecular = accept;
                Object loader = Proxy.newProxyInstance(loaderClass.getClassLoader(), new Class<?>[]{loaderClass},
                        (proxy, method, args) -> switch (method.getName()) {
                            case "load" -> {
                                acceptSpecular.invoke(args[2], specular);
                                yield null;
                            }
                            case "hashCode" -> System.identityHashCode(proxy);
                            case "equals" -> proxy == args[0];
                            case "toString" -> "Simple Blood surface PBR loader";
                            default -> null;
                        });
                Object registry = registryClass.getField("INSTANCE").get(null);
                registryClass.getMethod("register", Class.class, loaderClass).invoke(registry, textureClass, loader);
                SimpleBlood.LOGGER.info("Iris found: surface blood gets a labPBR specular map (wet pools)");
                return true;
            } catch (ClassNotFoundException e) {
                // not this Iris layout; try the next one
            } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
                SimpleBlood.LOGGER.warn("Iris found but its PBR loader registry could not be used; blood stays matte under shaders", e);
                return false;
            }
        }
        SimpleBlood.LOGGER.info("Iris found but no known PBR loader registry; blood stays matte under shaders");
        return false;
    }
}
