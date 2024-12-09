package net.twoturtles;

import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.SharedConstants;
import net.minecraft.Bootstrap;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import static org.junit.jupiter.api.Assertions.*;

public class MCioObservationHandlerTest {
    @Test
    void testGetYaw() {
        MCioObservationHandler handler = new MCioObservationHandler(null, MCioConfig.getInstance());

        float[] inputs = {0f, 180f, -180f, 360f, 540f, -540f};
        float[] expected = {0f, 180f, -180f, 0f, 180f, -180f};

        SharedConstants.createGameVersion();
        Bootstrap.initialize();

        for (int i = 0; i < inputs.length; i++) {
            ClientPlayerEntity mockPlayer = Mockito.mock(ClientPlayerEntity.class);
            Mockito.when(mockPlayer.getYaw()).thenReturn(inputs[i]);

            float result = handler.getYaw(mockPlayer);
            assertEquals(expected[i], result, 0.001f);
        }
    }
}
