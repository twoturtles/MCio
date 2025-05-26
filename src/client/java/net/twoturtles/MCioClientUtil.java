package net.twoturtles;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import com.mojang.logging.LogUtils;
import net.minecraft.client.util.SkinTextures;

import net.twoturtles.mixin.client.DefaultSkinHelperMixin;

public class MCioClientUtil {
    private static final Logger LOGGER = LogUtils.getLogger();

    public static List<String> getDefaultSkins() {
        List<String> result = new ArrayList<>();
        for (SkinTextures skin : DefaultSkinHelperMixin.SkinAccessor.getSkins()) {
            String[] parts = skin.texture().getPath().split("/");
            int len = parts.length;
            if (len >= 2) {
                result.add(parts[len - 2] + "/" + parts[len - 1]);
            } else {
                result.add(skin.texture().getPath()); // fallback
            }
        }
        return result;
    }
}

