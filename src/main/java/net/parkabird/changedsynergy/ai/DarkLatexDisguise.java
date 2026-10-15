package net.parkabird.changedsynergy.ai;

import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.ltxprogrammer.changed.entity.TamableLatexEntity;
import net.ltxprogrammer.changed.entity.variant.TransfurVariantInstance;
import net.ltxprogrammer.changed.init.ChangedLatexTypes;
import net.ltxprogrammer.changed.process.ProcessTransfur;
import net.parkabird.changedsynergy.compat.ChangedAddonCompat;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TridentItem;

/** Per-observer memory for Changed Addon's dark-latex coat disguise. */
public final class DarkLatexDisguise {
    private static final String ROOT = "ChangedSynergyDarkDisguise";
    private static final String ACCEPTED = "Accepted";
    private static final String FIRST_SEEN = "FirstSeen";
    private static final String INSPECTED = "Inspected";
    private static final String REVEALED = "Revealed";
    private static final String WHITE_NOTICE = "WhiteNotice";
    private static final double INSPECTION_DISTANCE_SQR = 3.25D * 3.25D;
    private static final long INSPECTION_DELAY = 60L;

    private DarkLatexDisguise() {
    }

    public static boolean isWearingFullSet(Player player) {
        return ChangedAddonCompat.is(
                        player.getItemBySlot(EquipmentSlot.HEAD),
                        ChangedAddonCompat.DARK_LATEX_COAT_CAP)
                && ChangedAddonCompat.is(
                        player.getItemBySlot(EquipmentSlot.CHEST),
                        ChangedAddonCompat.DARK_LATEX_COAT);
    }

    public static boolean isImpersonatingDarkLatex(ServerPlayer player) {
        return isWearingFullSet(player) && !isActuallyDarkLatex(player);
    }

    public static boolean isActuallyDarkLatex(ServerPlayer player) {
        TransfurVariantInstance<?> variant = ProcessTransfur.getPlayerTransfurVariant(player);
        return variant != null && isDarkLatex(variant.getChangedEntity());
    }

    public static boolean isDarkLatex(ChangedEntity mob) {
        return mob.getLatexType() == ChangedLatexTypes.DARK_LATEX.get()
                || HunterFaction.of(mob) == HunterFaction.DARK;
    }

    public static boolean isWhiteLatex(ChangedEntity mob) {
        return mob.getLatexType() == ChangedLatexTypes.WHITE_LATEX.get()
                || HunterFaction.of(mob) == HunterFaction.WHITE;
    }

    /** True while this particular dark-latex observer still believes the outfit. */
    public static boolean foolsDarkObserver(ChangedEntity mob, ServerPlayer player) {
        return isDarkLatex(mob)
                && isImpersonatingDarkLatex(player)
                && !isRevealed(mob, player)
                && !LatexSocialMemory.isProvoked(mob, player)
                && !LatexSocialMemory.isBonded(mob, player)
                && !LatexSocialMemory.isPetOwner(mob, player)
                && !isNativeOwner(mob, player);
    }

    /** White latex reacts to the identity being presented by the coat. */
    public static boolean appearsAsDarkRivalToWhite(
            ChangedEntity mob,
            ServerPlayer player) {
        return isWhiteLatex(mob)
                && isImpersonatingDarkLatex(player)
                && !LatexSocialMemory.isBonded(mob, player)
                && !LatexSocialMemory.isPetOwner(mob, player)
                && !isNativeOwner(mob, player);
    }

    public static Observation observeDark(ChangedEntity mob, ServerPlayer player) {
        if (!foolsDarkObserver(mob, player)) {
            return Observation.NONE;
        }

        CompoundTag observation = observation(mob, player);
        long now = mob.level().getGameTime();
        if (!observation.getBoolean(ACCEPTED)) {
            observation.putBoolean(ACCEPTED, true);
            observation.putLong(FIRST_SEEN, now);
            return Observation.ACCEPTED;
        }
        if (!observation.getBoolean(INSPECTED)
                && mob.distanceToSqr(player) <= INSPECTION_DISTANCE_SQR
                && now - observation.getLong(FIRST_SEEN) >= INSPECTION_DELAY) {
            return inspect(mob, player, false);
        }
        return Observation.NONE;
    }

    public static Observation inspectFromPat(ChangedEntity mob, ServerPlayer player) {
        if (!foolsDarkObserver(mob, player)) {
            return isRevealed(mob, player) ? Observation.REVEALED : Observation.NONE;
        }
        CompoundTag observation = observation(mob, player);
        if (!observation.getBoolean(ACCEPTED)) {
            observation.putBoolean(ACCEPTED, true);
            observation.putLong(FIRST_SEEN, mob.level().getGameTime());
        }
        return inspect(mob, player, true);
    }

    public static boolean isRevealed(ChangedEntity mob, ServerPlayer player) {
        return observation(mob, player).getBoolean(REVEALED);
    }

    public static boolean wasAccepted(ChangedEntity mob, ServerPlayer player) {
        return observation(mob, player).getBoolean(ACCEPTED);
    }

    public static boolean wasInspectedAndAccepted(
            ChangedEntity mob,
            ServerPlayer player) {
        CompoundTag observation = observation(mob, player);
        return observation.getBoolean(INSPECTED) && !observation.getBoolean(REVEALED);
    }

    /** @return true only when this observer has just learned the player's identity. */
    public static boolean reveal(ChangedEntity mob, ServerPlayer player) {
        CompoundTag observation = observation(mob, player);
        boolean first = !observation.getBoolean(REVEALED);
        observation.putBoolean(ACCEPTED, true);
        observation.putBoolean(INSPECTED, true);
        observation.putBoolean(REVEALED, true);
        return first;
    }

    public static boolean shouldNoticeWhiteRival(
            ChangedEntity mob,
            ServerPlayer player) {
        if (!appearsAsDarkRivalToWhite(mob, player)) {
            return false;
        }
        CompoundTag observation = observation(mob, player);
        if (observation.getBoolean(WHITE_NOTICE)) {
            return false;
        }
        observation.putBoolean(WHITE_NOTICE, true);
        return true;
    }

    private static Observation inspect(
            ChangedEntity mob,
            ServerPlayer player,
            boolean duringPat) {
        CompoundTag observation = observation(mob, player);
        if (observation.getBoolean(REVEALED)) {
            return Observation.REVEALED;
        }
        if (observation.getBoolean(INSPECTED)) {
            return Observation.ALREADY_ACCEPTED;
        }

        observation.putBoolean(INSPECTED, true);
        double revealChance = switch (HunterArchetype.of(mob)) {
            case SOLDIER, ROYAL -> 0.25D;
            case CANINE, DRACONIC -> 0.19D;
            case FELINE, AVIAN -> 0.15D;
            case AQUATIC, INSECT -> 0.12D;
            case CRITTER, GENERAL -> 0.09D;
        };
        if (player.isSprinting()) {
            revealChance += 0.08D;
        }
        if (isHoldingObviousWeapon(player)) {
            revealChance += 0.08D;
        }
        if (duringPat) {
            revealChance += 0.10D;
        }

        if (mob.getRandom().nextDouble() < Math.min(0.45D, revealChance)) {
            observation.putBoolean(REVEALED, true);
            return Observation.REVEALED;
        }
        return Observation.INSPECTED_AND_ACCEPTED;
    }

    private static boolean isHoldingObviousWeapon(ServerPlayer player) {
        ItemStack stack = player.getMainHandItem();
        return stack.getItem() instanceof SwordItem
                || stack.getItem() instanceof AxeItem
                || stack.getItem() instanceof ProjectileWeaponItem
                || stack.getItem() instanceof TridentItem;
    }

    private static boolean isNativeOwner(ChangedEntity mob, ServerPlayer player) {
        return mob instanceof TamableLatexEntity tamable
                && tamable.isTame()
                && player.getUUID().equals(tamable.getOwnerUUID());
    }

    private static CompoundTag observation(ChangedEntity mob, ServerPlayer player) {
        CompoundTag entityData = mob.getPersistentData();
        if (!entityData.contains(ROOT, Tag.TAG_COMPOUND)) {
            entityData.put(ROOT, new CompoundTag());
        }
        CompoundTag root = entityData.getCompound(ROOT);
        String key = player.getUUID().toString();
        if (!root.contains(key, Tag.TAG_COMPOUND)) {
            root.put(key, new CompoundTag());
        }
        return root.getCompound(key);
    }

    public enum Observation {
        NONE,
        ACCEPTED,
        INSPECTED_AND_ACCEPTED,
        ALREADY_ACCEPTED,
        REVEALED
    }
}
