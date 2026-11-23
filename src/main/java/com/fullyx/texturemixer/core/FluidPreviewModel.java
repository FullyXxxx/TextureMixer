package com.fullyx.texturemixer.core;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/** Fluid blockstate models have particle sprites but no geometry to render in the editor. */
public final class FluidPreviewModel {
    private FluidPreviewModel() {}

    public static boolean isFluid(AssetId block) {
        return block.namespace().equals("minecraft")
                && (block.path().equals("water") || block.path().equals("lava"));
    }

    public static boolean isFluidSprite(AssetId sprite) {
        if (!sprite.namespace().equals("minecraft")) return false;
        return switch (sprite.path()) {
            case "block/water_still", "block/water_flow", "block/water_overlay",
                 "block/lava_still", "block/lava_flow" -> true;
            default -> false;
        };
    }

    public static byte[] create(GraphCompiler compiler, AssetId fluid, boolean tintWater) {
        var model = JsonParser.parseString("""
                {"display":{"gui":{"rotation":[30,225,0],"scale":[0.625,0.625,0.625]}},
                 "elements":[{"from":[0,0,0],"to":[16,16,16],"faces":{
                   "north":{"texture":"#flow"},"south":{"texture":"#flow"},
                   "east":{"texture":"#flow"},"west":{"texture":"#flow"},
                   "up":{"texture":"#still"},"down":{"texture":"#still"}}}]}
                """).getAsJsonObject();
        var textures = new JsonObject();
        textures.addProperty("still", compiler.privateId(AssetId.of("block/" + fluid.path() + "_still")).toString());
        textures.addProperty("flow", compiler.privateId(AssetId.of("block/" + fluid.path() + "_flow")).toString());
        textures.addProperty("particle", "#still");
        model.add("textures", textures);
        if (tintWater && fluid.path().equals("water")) {
            var faces = model.getAsJsonArray("elements").get(0).getAsJsonObject().getAsJsonObject("faces");
            for (var face : faces.entrySet()) face.getValue().getAsJsonObject().addProperty("tintindex", 0);
        }
        return Assets.jsonBytes(model);
    }
}
