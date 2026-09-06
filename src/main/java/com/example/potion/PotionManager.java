package com.example.potion;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.PotionItem;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import org.lwjgl.glfw.GLFW;

public class PotionManager implements ClientModInitializer {

    private static final String KEYBIND_NAME = "key.potion_mod.toggle";

    private static final int POTION_SLOT = 7; // Hotbar slot 8
    private static final int EFFECT_THRESHOLD = 2 * 20;
    private static final long POTION_COOLDOWN = 500L;

    private static boolean modEnabled = false;
    private static long lastPotionThrowTime = 0L;

    private KeyBinding toggleKeyBinding;

    @Override
    public void onInitializeClient() {

        toggleKeyBinding = KeyBindingHelper.registerKeyBinding(
                new KeyBinding(
                        KEYBIND_NAME,
                        InputUtil.Type.KEYSYM,
                        GLFW.GLFW_KEY_RIGHT_CONTROL,
                        "category.potion_mod"
                )
        );

        ClientTickEvents.END_CLIENT_TICK.register(this::onClientTick);
    }

    private void onClientTick(MinecraftClient client) {

        if (client.player == null) {
            return;
        }

        // Right Ctrl = Toggle ON/OFF
        while (toggleKeyBinding.wasPressed()) {

            modEnabled = !modEnabled;

            String status = modEnabled
                    ? "§aENABLED"
                    : "§cDISABLED";

            client.player.sendMessage(
                    Text.literal("§6Potion Mod §f" + status),
                    true
            );
        }

        if (!modEnabled) {
            return;
        }

        managePotions(client.player);
    }

    private void managePotions(ClientPlayerEntity player) {

        Inventory inventory = player.getInventory();

        StatusEffectInstance strength =
                player.getStatusEffect(StatusEffects.STRENGTH);

        StatusEffectInstance speed =
                player.getStatusEffect(StatusEffects.SPEED);

        StatusEffectInstance fireResistance =
                player.getStatusEffect(StatusEffects.FIRE_RESISTANCE);

        // Priority:
        // 1. Strength
        // 2. Speed
        // 3. Fire Resistance

        if (shouldApplyPotion(strength)) {

            if (applyPotionEffect(
                    player,
                    inventory,
                    StatusEffects.STRENGTH
            )) {
                return;
            }
        }

        if (shouldApplyPotion(speed)) {

            if (applyPotionEffect(
                    player,
                    inventory,
                    StatusEffects.SPEED
            )) {
                return;
            }
        }

        if (shouldApplyPotion(fireResistance)) {

            applyPotionEffect(
                    player,
                    inventory,
                    StatusEffects.FIRE_RESISTANCE
            );
        }
    }

    private boolean shouldApplyPotion(StatusEffectInstance effect) {

        if (effect == null) {
            return true;
        }

        return effect.getDuration() <= EFFECT_THRESHOLD;
    }

    private boolean applyPotionEffect(
            ClientPlayerEntity player,
            Inventory inventory,
            RegistryEntry<StatusEffect> targetEffect
    ) {

        long currentTime = System.currentTimeMillis();

        if (currentTime - lastPotionThrowTime < POTION_COOLDOWN) {
            return false;
        }

        int potionSlot =
                findPotionSlot(inventory, targetEffect);

        if (potionSlot == -1) {
            return false;
        }

        // Save currently selected hotbar slot
        int originalSlot = inventory.selectedSlot;

        // Move potion to slot 8 if necessary
        if (potionSlot != POTION_SLOT) {

            ItemStack potion =
                    inventory.getStack(potionSlot).copy();

            ItemStack oldSlot =
                    inventory.getStack(POTION_SLOT).copy();

            inventory.setStack(POTION_SLOT, potion);
            inventory.setStack(potionSlot, oldSlot);
        }

        // Select slot 8
        inventory.selectedSlot = POTION_SLOT;

        // Throw potion
        float originalPitch = player.getPitch();

        player.setPitch(45.0F);

        if (player.getWorld() != null) {

            MinecraftClient client =
                    MinecraftClient.getInstance();

            if (client.interactionManager != null) {

                client.interactionManager.interactItem(
                        player,
                        Hand.MAIN_HAND
                );
            }
        }

        // Restore rotation
        player.setPitch(originalPitch);

        // Restore original selected slot
        inventory.selectedSlot = originalSlot;

        lastPotionThrowTime = currentTime;

        return true;
    }

    private int findPotionSlot(
            Inventory inventory,
            RegistryEntry<StatusEffect> targetEffect
    ) {

        for (int i = 0; i < inventory.size(); i++) {

            ItemStack stack = inventory.getStack(i);

            if (!(stack.getItem() instanceof PotionItem)) {
                continue;
            }

            PotionContentsComponent contents =
                    stack.get(DataComponentTypes.POTION_CONTENTS);

            if (contents == null) {
                continue;
            }

            boolean hasEffect = false;

            for (StatusEffectInstance effect : contents.getEffects()) {
                if (effect.getEffectType().equals(targetEffect)) {
                    hasEffect = true;
                    break;
                }
            }

            if (hasEffect) {
                return i;
            }
        }

        return -1;
    }
                                       }
