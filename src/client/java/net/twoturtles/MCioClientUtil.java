package net.twoturtles;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.resources.PlayerSkin;
import net.twoturtles.mixin.client.DefaultPlayerSkinMixin;
import org.slf4j.Logger;

public class MCioClientUtil {
  private static final Logger LOGGER = LogUtils.getLogger();

  public static List<String> getDefaultSkins() {
    List<String> result = new ArrayList<>();
    for (PlayerSkin skin : DefaultPlayerSkinMixin.SkinAccessor.getSkins()) {
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
