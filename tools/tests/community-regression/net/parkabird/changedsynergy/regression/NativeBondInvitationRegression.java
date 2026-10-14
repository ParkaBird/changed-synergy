package net.parkabird.changedsynergy.regression;

import com.mojang.authlib.GameProfile;
import java.util.Map;
import java.util.UUID;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.ltxprogrammer.changed.entity.LivingEntityDataExtension;
import net.ltxprogrammer.changed.init.ChangedEntities;
import net.ltxprogrammer.changed.init.ChangedTransfurVariants;
import net.ltxprogrammer.changed.process.ProcessTransfur;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.BlockPos;
import net.minecraft.network.Connection;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.fml.LogicalSide;
import net.parkabird.changedsynergy.ChangedSynergyConfig;
import net.parkabird.changedsynergy.ai.CreaturePersonality;
import net.parkabird.changedsynergy.ai.BondedSuitService;
import net.parkabird.changedsynergy.ai.LatexSocialMemory;
import net.parkabird.changedsynergy.ai.NativeBondInvitationService;
import net.parkabird.changedsynergy.ai.VoluntaryBondTransfurService;
import net.parkabird.changedsynergy.init.ChangedSynergyGameRules;
import net.parkabird.changedsynergy.origin.NativeOriginData;
import net.parkabird.changedsynergy.world.inventory.SocialInteractionMenu;

/** Real server-side relationship state; no form conversion is used to establish the bond. */
public final class NativeBondInvitationRegression {
    private static int checks;

    public static void run(MinecraftServer server) throws Exception {
        checks = 0;
        var level = server.overworld();
        var owner = player(server, "NativeBondTest");
        var other = player(server, "OtherBondTest");
        var first = ChangedEntities.DARK_LATEX_WOLF_MALE.get().create(level);
        var second = ChangedEntities.DARK_LATEX_WOLF_MALE.get().create(level);
        var juvenile = ChangedEntities.DARK_LATEX_WOLF_PUP.get().create(level);
        var third = ChangedEntities.DARK_LATEX_WOLF_MALE.get().create(level);
        var playerField = net.minecraftforge.fml.util.ObfuscationReflectionHelper.findField(PlayerList.class, "f_11197_");
        playerField.setAccessible(true);
        @SuppressWarnings("unchecked") var players = (Map<UUID, ServerPlayer>) playerField.get(server.getPlayerList());
        var origins = NativeOriginData.get(server);
        boolean multiple = ChangedSynergyConfig.COMMON.allowMultipleBonds.get();
        boolean tokens = ChangedSynergyConfig.COMMON.bondTokensRequired.get();
        boolean bonds = level.getGameRules().getBoolean(ChangedSynergyGameRules.BOND_SYSTEM);
        boolean friendships = level.getGameRules().getBoolean(ChangedSynergyGameRules.FRIENDSHIP_SYSTEM);
        long time = level.getGameTime();
        try {
            players.put(owner.getUUID(), owner); players.put(other.getUUID(), other);
            level.addNewPlayer(owner); level.addNewPlayer(other);
            owner.setPos(0.5D, 100.0D, 0.5D); other.setPos(0.5D, 100.0D, 3.5D);
            for (ChangedEntity creature : new ChangedEntity[]{first, second, juvenile, third}) {
                creature.setPos(0.5D, 100.0D, 2.5D);
                level.addFreshEntity(creature);
            }
            level.getGameRules().getRule(ChangedSynergyGameRules.BOND_SYSTEM).set(true, server);
            level.getGameRules().getRule(ChangedSynergyGameRules.FRIENDSHIP_SYSTEM).set(true, server);
            ChangedSynergyConfig.COMMON.allowMultipleBonds.set(false);
            // Escrow/growth are checked separately; isolate the supported token-free invitation policy here.
            ChangedSynergyConfig.COMMON.bondTokensRequired.set(false);
            closeFriend(first, owner); closeFriend(second, owner); closeFriend(third, owner);
            check(!NativeBondInvitationService.canOffer(first, owner), "Human friendship retains voluntary-transfur route");
            check(VoluntaryBondTransfurService.canOffer(first, owner), "Existing human invitation remains available");
            var form = ProcessTransfur.setPlayerTransfurVariant(owner,
                    ChangedTransfurVariants.PURE_WHITE_LATEX_WOLF.get(), null, 1.0F, false);
            check(!NativeBondInvitationService.canOffer(first, owner), "Ordinary transformed player cannot claim native bond route");
            var identity = new NativeOriginData.Identity();
            identity.state = "native";
            identity.form = identity.current = form.getParent().getFormId().toString();
            identity.faction = "white";
            origins.players.put(owner.getUUID(), identity);
            check(NativeBondInvitationService.canOffer(first, owner), "Adult native creature can invite close friend of another faction");
            check(!VoluntaryBondTransfurService.canOffer(first, owner), "Native invitation does not offer voluntary form replacement");
            check(!NativeBondInvitationService.canOffer(juvenile, owner), "Juvenile target cannot enter adult bond");
            ProcessTransfur.setPlayerTransfurVariant(owner,
                    ChangedTransfurVariants.DARK_LATEX_WOLF_PUP.get(), null, 1.0F, false);
            check(!NativeBondInvitationService.canOffer(first, owner), "Juvenile native player cannot enter adult bond");
            form = ProcessTransfur.setPlayerTransfurVariant(owner,
                    ChangedTransfurVariants.PURE_WHITE_LATEX_WOLF.get(), null, 1.0F, false);
            form.setTemporaryForSuit(true);
            check(!NativeBondInvitationService.canOffer(first, owner), "Temporary rescue wrapping cannot impersonate native form");
            form.setTemporaryForSuit(false);
            CreaturePersonality.setFamiliarity(first, owner, 12);
            check(!NativeBondInvitationService.canOffer(first, owner), "Familiar friendship must reach highest tier first");
            CreaturePersonality.setFamiliarity(first, owner, 60);
            owner.setPos(0.5D, 100.0D, 20.5D);
            check(!NativeBondInvitationService.canOffer(first, owner), "Distant request rejected on server");
            owner.setPos(0.5D, 100.0D, 0.5D);

            if (owner instanceof LivingEntityDataExtension extension) {
                extension.setGrabbedBy(first);
                check(!NativeBondInvitationService.canOffer(first, owner), "An active hostile grab cannot become a bond invitation");
                extension.setGrabbedBy(null);
            }
            BondedSuitService.ensureGrabAbility(first);
            var grab = BondedSuitService.ability(first);
            grab.grabbedEntity = other;
            check(!NativeBondInvitationService.canOffer(first, owner), "A creature busy holding another player cannot accept");
            grab.grabbedEntity = null;

            for (int y = 100; y <= 104; y++) level.setBlock(new BlockPos(0, y, 1), Blocks.STONE.defaultBlockState(), 3);
            check(!NativeBondInvitationService.canOffer(first, owner), "A forged wheel request cannot bond through walls");
            for (int y = 100; y <= 104; y++) level.setBlock(new BlockPos(0, y, 1), Blocks.AIR.defaultBlockState(), 3);

            check(!NativeBondInvitationService.select(first, owner)
                    && !LatexSocialMemory.isBonded(first, owner), "First selection requests confirmation without bonding");
            NativeBondInvitationService.onLogout(new PlayerEvent.PlayerLoggedOutEvent(owner));
            check(!NativeBondInvitationService.select(first, owner), "Reconnect cannot reuse a pending confirmation");
            ((net.minecraft.world.level.storage.ServerLevelData)level.getLevelData()).setGameTime(level.getGameTime() + 201L);
            check(!NativeBondInvitationService.select(first, owner), "Expired confirmation needs a new invitation");
            check(!NativeBondInvitationService.select(second, owner)
                    && !NativeBondInvitationService.select(first, owner), "Switching targets never confirms previous invitation");
            level.getGameRules().getRule(ChangedSynergyGameRules.BOND_SYSTEM).set(false, server);
            check(!NativeBondInvitationService.select(first, owner), "Server can disable bonds between confirmation clicks");
            level.getGameRules().getRule(ChangedSynergyGameRules.BOND_SYSTEM).set(true, server);
            check(!NativeBondInvitationService.select(first, owner), "Rejected confirmation is cleared before re-enabling bonds");

            var buffer = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
            SocialInteractionMenu.writeOpeningData(buffer, owner, first, false);
            var menu = new SocialInteractionMenu(41, owner.getInventory(), buffer);
            check(menu.isNativeBondAvailable() && !menu.isVoluntaryBondAvailable()
                    && buffer.readableBytes() == 0, "Native wheel availability and remainder decode without misalignment");
            buffer.clear();
            SocialInteractionMenu.writeAbsorptionOpeningData(buffer, owner);
            var absorption = new SocialInteractionMenu(42, owner.getInventory(), buffer);
            check(!absorption.isNativeBondAvailable() && buffer.readableBytes() == 0,
                    "Virtual absorption menu retains its complete opening schema");
            buffer.release();
            CompoundTag payload = new CompoundTag(); payload.putString("command", "social_native_bond");
            menu.update(payload, LogicalSide.SERVER, owner);
            check(LatexSocialMemory.isBonded(first, owner) && LatexSocialMemory.isPetOwner(first, owner),
                    "Confirmed server wheel action enters existing companion ownership and bond state");
            check(ProcessTransfur.getPlayerTransfurVariant(owner) == form && identity.faction.equals("white")
                    && identity.current.equals(form.getParent().getFormId().toString()),
                    "Bonding preserves original player body, species and faction");
            check(!NativeBondInvitationService.canOffer(first, owner)
                    && !NativeBondInvitationService.canOffer(second, owner), "Existing bond and single-bond limit cannot be bypassed");
            ChangedSynergyConfig.COMMON.allowMultipleBonds.set(true);
            check(NativeBondInvitationService.canOffer(second, owner), "Multiple-bonds option permits another consenting companion");
            check(!NativeBondInvitationService.select(second, owner) && NativeBondInvitationService.select(second, owner),
                    "Additional bond still requires its own confirmation");
            closeFriend(third, other); LatexSocialMemory.addBond(third, other);
            check(!NativeBondInvitationService.canOffer(third, owner), "Another player's companion cannot be claimed");
            System.out.println("NATIVE_BOND_INVITATION_REGRESSION_PASS: " + checks + " checks");
        } finally {
            for (ChangedEntity creature : new ChangedEntity[]{first, second, juvenile, third}) {
                LatexSocialMemory.unregisterBond(creature, owner);
                LatexSocialMemory.unregisterBond(creature, other);
                creature.discard();
            }
            origins.players.remove(owner.getUUID());
            ProcessTransfur.removePlayerTransfurVariant(owner);
            players.remove(owner.getUUID()); players.remove(other.getUUID());
            owner.discard(); other.discard();
            ChangedSynergyConfig.COMMON.allowMultipleBonds.set(multiple);
            ChangedSynergyConfig.COMMON.bondTokensRequired.set(tokens);
            level.getGameRules().getRule(ChangedSynergyGameRules.BOND_SYSTEM).set(bonds, server);
            level.getGameRules().getRule(ChangedSynergyGameRules.FRIENDSHIP_SYSTEM).set(friendships, server);
            ((net.minecraft.world.level.storage.ServerLevelData)level.getLevelData()).setGameTime(time);
        }
    }

    private static ServerPlayer player(MinecraftServer server, String name) {
        var player = new ServerPlayer(server, server.overworld(), new GameProfile(UUID.randomUUID(), name));
        player.connection = new ServerGamePacketListenerImpl(server, new Connection(PacketFlow.SERVERBOUND), player);
        return player;
    }

    private static void closeFriend(ChangedEntity creature, ServerPlayer player) {
        CreaturePersonality.establishRelationship(creature, player);
        CreaturePersonality.setFamiliarity(creature, player, 60);
    }

    private static void check(boolean value, String description) {
        if (!value) throw new AssertionError(description);
        checks++; System.out.println("NATIVE_BOND_PASS: " + description);
    }
}
