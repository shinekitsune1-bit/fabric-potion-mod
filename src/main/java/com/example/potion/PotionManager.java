package com.example.potion;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.PotionItem;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.potion.PotionUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

public class PotionManager implements ClientModInitializer {

    private static final String KEYBIND_NAME = "key.potion_mod.toggle";
    private static KeyBinding toggleKeyBinding;
    private static boolean modEnabled = false;
    private static MinecraftClient client;
    private static final int POTION_SLOT = 7; // Hotbar slot 8 (0-indexed)
    private static final int POTION_EFFECT_THRESHOLD = 2 * 20; // 2 seconds in ticks
    private static long lastPotionThrowTime = 0;
    private static final long POTION_THROW_COOLDOWN = 500; // 500ms cooldown

    @Override
    public void onInitializeClient() {
        client = MinecraftClient.getInstance();

        // Register the key binding
        toggleKeyBinding = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                KEYBIND_NAME,
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_RIGHT_CONTROL,
                "category.potion_mod"
        ));

        // Register the tick event
        ClientTickEvents.END_CLIENT_TICK.register(this::onClientTick);
    }

    private void onClientTick(MinecraftClient client) {
        if (client.player == null) return;

        // Check for toggle key press
        while (toggleKeyBinding.wasPressed()) {
            modEnabled = !modEnabled;
            if (client.player != null) {
                String status = modEnabled ? "§aENABLED" : "§cDISABLED";
                client.player.sendMessage(
                        Text.literal("§6Potion Mod §f" + status),
                        true
                );
            }
        }

        if (!modEnabled || client.player == null) return;

        // Main potion management logic
        managePotions(client.player);
    }

    private void managePotions(net.minecraft.entity.player.PlayerEntity player) {
        Inventory inventory = player.getInventory();
        var strength = player.getStatusEffect(StatusEffects.STRENGTH);
        var speed = player.getStatusEffect(StatusEffects.SPEED);
        var fireRes = player.getStatusEffect(StatusEffects.FIRE_RESISTANCE);

        // Priority: Strength -> Speed -> Fire Resistance
        if (shouldApplyPotion(strength)) {
            applyPotionEffect(player, inventory, StatusEffects.STRENGTH);
            return;
        }

        if (shouldApplyPotion(speed)) {
            applyPotionEffect(player, inventory, StatusEffects.SPEED);
            return;
        }

        if (shouldApplyPotion(fireRes)) {
            applyPotionEffect(player, inventory, StatusEffects.FIRE_RESISTANCE);
            return;
        }
    }

    private boolean shouldApplyPotion(net.minecraft.entity.effect.StatusEffectInstance effectInstance) {
        if (effectInstance == null) {
            return true; // Effect not active, should apply
        }
        // Apply again if 2 seconds or less remaining
        return effectInstance.getDuration() <= POTION_EFFECT_THRESHOLD;
    }

    private void applyPotionEffect(net.minecraft.entity.player.PlayerEntity player, Inventory inventory, StatusEffect targetEffect) {
        // Check cooldown to avoid spam
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastPotionThrowTime < POTION_THROW_COOLDOWN) {
            return;
        }

        // Find the splash potion in inventory
        int potionSlot = findSplashPotionSlot(inventory, targetEffect);
        if (potionSlot == -1) {
            return; // Potion not found
        }

        // Move potion to slot 8 if not already there
        if (potionSlot != POTION_SLOT) {
            swapToSlot(inventory, potionSlot, POTION_SLOT);
        }

        // Throw the potion
        throwPotion(player, inventory);
        lastPotionThrowTime = currentTime;
    }

    private int findSplashPotionSlot(Inventory inventory, StatusEffect targetEffect) {
        for (int i = 0; i < inventory.size(); i++) {
            ItemStack stack = inventory.getStack(i);
            if (isSplashPotionWithEffect(stack, targetEffect)) {
                return i;
            }
        }
        return -1;
    }

    private boolean isSplashPotionWithEffect(ItemStack stack, StatusEffect targetEffect) {
        if (stack.isEmpty() || !(stack.getItem() instanceof PotionItem)) {
            return false;
        }

        // Check custom potion effects from NBT
        var potionEffects = PotionUtil.getCustomPotionEffects(stack);
        for (var effect : potionEffects) {
            if (effect.getEffectType().value() == targetEffect) {
                return true;
            }
        }

        return false;
    }

    private void swapToSlot(Inventory inventory, int fromSlot, int toSlot) {
        ItemStack fromStack = inventory.getStack(fromSlot).copy();
        ItemStack toStack = inventory.getStack(toSlot).copy();

        inventory.setStack(toSlot, fromStack);
        inventory.setStack(fromSlot, toStack);
    }

    private void throwPotion(net.minecraft.entity.player.PlayerEntity player, Inventory inventory) {
        if (!(player instanceof net.minecraft.client.network.ClientPlayerEntity clientPlayer)) {
            return;
        }

        // Select slot 8
        clientPlayer.getInventory().selectedSlot = POTION_SLOT;

        // Store original rotation
        float originalPitch = player.getPitch();
        float originalYaw = player.getYaw();

        // Rotate to throw upward for splash effect
        player.setPitch(45f);

        // Interact with the potion (throw it)
        if (client.interactionManager != null) {
            client.interactionManager.interactItem(clientPlayer, clientPlayer.getWorld(), net.minecraft.util.Hand.MAIN_HAND);
        }

        // Restore original rotation
        player.setPitch(originalPitch);
    }
}
