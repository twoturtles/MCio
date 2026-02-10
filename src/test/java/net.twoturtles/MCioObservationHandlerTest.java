package net.twoturtles;

import static org.junit.jupiter.api.Assertions.*;

import net.minecraft.SharedConstants;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

public class MCioObservationHandlerTest {
  @Test
  void testGetYaw() {
    MCioObservationHandler handler = new MCioObservationHandler(null, MCioConfig.getInstance());

    float[] inputs = {0f, 180f, -180f, 360f, 540f, -540f};
    float[] expected = {0f, 180f, -180f, 0f, 180f, -180f};

    SharedConstants.tryDetectVersion();
    Bootstrap.bootStrap();

    for (int i = 0; i < inputs.length; i++) {
      LocalPlayer mockPlayer = Mockito.mock(LocalPlayer.class);
      Mockito.when(mockPlayer.getYRot()).thenReturn(inputs[i]);

      float result = handler.getYaw(mockPlayer);
      assertEquals(expected[i], result, 0.001f);
    }
  }
}
