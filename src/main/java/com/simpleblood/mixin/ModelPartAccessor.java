package com.simpleblood.mixin;

import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;
import java.util.Map;

/** A model part's own cubes and its children, for laying blood over a mob's model. */
@Mixin(ModelPart.class)
public interface ModelPartAccessor {
    @Accessor("cubes")
    List<ModelPart.Cube> simpleblood$cubes();

    @Accessor("children")
    Map<String, ModelPart> simpleblood$children();
}
