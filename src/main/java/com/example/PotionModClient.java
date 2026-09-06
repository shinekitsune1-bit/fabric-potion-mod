package com.example;

import com.example.potion.PotionManager;
import net.fabricmc.api.ClientModInitializer;

public class PotionModClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        new PotionManager().onInitializeClient();
    }
}
