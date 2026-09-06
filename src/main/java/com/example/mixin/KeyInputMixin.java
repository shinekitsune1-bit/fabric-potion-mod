package com.example.mixin;

import org.spongepowered.asm.mixin.Mixin;
import net.minecraft.client.input.Input;

@Mixin(Input.class)
public class KeyInputMixin {
    // Mixin class for input handling
    // Main logic handled via event listeners in PotionManager
}
