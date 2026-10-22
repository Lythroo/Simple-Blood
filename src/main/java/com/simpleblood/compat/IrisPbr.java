package com.simpleblood.compat;

import com.simpleblood.Platform;
import com.simpleblood.SimpleBlood;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

public final class IrisPbr {
    private IrisPbr() {}

    private static final String MOD_ID = "iris";
    private static final String[] PACKAGES = {
            "net.irisshaders.iris.pbr.loader.",
            "net.irisshaders.iris.texture.pbr.loader.",
    };

    public static boolean present() {
        return !"false".equalsIgnoreCase(System.getProperty("simpleblood.irisPbr"))
                && Platform.modIds().contains(MOD_ID);
    }

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
            } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
                SimpleBlood.LOGGER.warn("Iris found but its PBR loader registry could not be used; blood stays matte under shaders", e);
                return false;
            }
        }
        SimpleBlood.LOGGER.info("Iris found but no known PBR loader registry; blood stays matte under shaders");
        return false;
    }
}
