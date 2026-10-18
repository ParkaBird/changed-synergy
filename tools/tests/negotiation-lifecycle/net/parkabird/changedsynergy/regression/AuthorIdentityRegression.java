package net.parkabird.changedsynergy.regression;

import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.ltxprogrammer.changed.entity.TransfurCause;
import net.ltxprogrammer.changed.entity.ai.ImmediateTransfurDecision;
import net.ltxprogrammer.changed.init.ChangedEntities;
import net.ltxprogrammer.changed.init.ChangedGameRules;
import net.ltxprogrammer.changed.init.ChangedTransfurVariants;
import net.ltxprogrammer.changed.process.ProcessTransfur;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.parkabird.changedsynergy.ai.*;

final class AuthorIdentityRegression {
    static void run(MinecraftServer server) throws Exception {
        var level = server.overworld(); var rules = level.getGameRules();
        boolean keep = rules.getBoolean(ChangedGameRules.RULE_KEEP_BRAIN);
        boolean animation = rules.getBoolean(ChangedGameRules.RULE_DO_TRANSFUR_ANIMATION);
        try {
            rules.getRule(ChangedGameRules.RULE_KEEP_BRAIN).set(false, server);
            for (boolean animate : new boolean[]{false, true}) {
                rules.getRule(ChangedGameRules.RULE_DO_TRANSFUR_ANIMATION).set(animate, server);
                for (var variant : java.util.List.of(ChangedTransfurVariants.DARK_LATEX_WOLF_MALE.get(),
                        ChangedTransfurVariants.WHITE_LATEX_WOLF_MALE.get(), ChangedTransfurVariants.GAS_WOLF_MALE.get())) {
                    var player = new ServerPlayer(server, level, new GameProfile(UUID.randomUUID(), "SuccessorCheck"));
                    player.connection = new ServerGamePacketListenerImpl(server, new Connection(PacketFlow.SERVERBOUND), player);
                    net.minecraftforge.fml.util.ObfuscationReflectionHelper.setPrivateValue(ServerPlayer.class, player, 0, "f_8921_");
                    player.setPos(192.5, 160, 192.5); level.getChunkAt(player.blockPosition());
                    var origin = new net.parkabird.changedsynergy.origin.NativeOriginData.Identity(); origin.state = "human";
                    net.parkabird.changedsynergy.origin.NativeOriginData.get(server).players.put(player.getUUID(), origin);
                    var box = player.getBoundingBox().inflate(4);
                    var before = level.getEntitiesOfClass(ChangedEntity.class, box).stream().map(ChangedEntity::getUUID).toList();
                    try {
                        ProcessTransfur.transfur(player, ImmediateTransfurDecision.unsafe(variant, TransfurCause.CRYSTAL));
                        var form = ProcessTransfur.getPlayerTransfurVariant(player);
                        if (form != null) {
                            var tick = form.getClass().getDeclaredMethod("tickTransfurProgress"); tick.setAccessible(true);
                            for (int i=0; i<800 && player.isAlive() && form.transfurProgression<1; i++) tick.invoke(form);
                        }
                        check(!player.isAlive(), "Involuntary death completes: " + variant.getFormId() + "/animation="+animate);
                        var successors = level.getEntitiesOfClass(ChangedEntity.class, box, e -> !before.contains(e.getUUID()));
                        check(successors.size()==1, "Exactly one independent successor: " + variant.getFormId()+"; count="+successors.size());
                        var npc = successors.get(0);
                        check(npc.getType()==variant.getEntityType() && npc.getUnderlyingPlayer()==null && !npc.isNoAi()
                                && !npc.goalSelector.getAvailableGoals().isEmpty(), "Successor retains native species and active NPC AI");
                        check(!npc.getName().getString().equals(player.getGameProfile().getName()) && !npc.hasCustomName(),
                                "Death NPC is not automatically named after the player");
                        TransfurConsciousness.onStaleProjection(new LivingEvent.LivingTickEvent(npc));
                        ProcessTransfur.removePlayerTransfurVariant(player); player.discard();
                        TransfurConsciousness.onStaleProjection(new LivingEvent.LivingTickEvent(npc));
                        check(!npc.isRemoved(), "Respawn/body cleanup cannot remove the independent NPC");
                        var saved = new CompoundTag(); npc.saveWithoutId(saved);
                        var restored = (ChangedEntity)npc.getType().create(level); restored.load(saved);
                        CreatureIdentity.ensure(restored);
                        check(restored.getUnderlyingPlayer()==null && !restored.hasCustomName(),
                                "Independence and normal naming survive NPC save/load");
                        restored.discard();
                    } finally {
                        level.getEntitiesOfClass(ChangedEntity.class, box, e -> !before.contains(e.getUUID())).forEach(ChangedEntity::discard);
                        player.discard();
                    }
                }
            }
        } finally {
            rules.getRule(ChangedGameRules.RULE_KEEP_BRAIN).set(keep, server);
            rules.getRule(ChangedGameRules.RULE_DO_TRANSFUR_ANIMATION).set(animation, server);
        }
        var namedSource=ChangedEntities.DARK_LATEX_WOLF_MALE.get().create(level);
        var namedCopy=ChangedEntities.DARK_LATEX_WOLF_MALE.get().create(level);
        namedSource.setPos(192.5,160,192.5);namedCopy.setPos(193.5,160,192.5);
        namedSource.setCustomName(net.minecraft.network.chat.Component.literal("NamedCompanion"));
        level.addFreshEntity(namedSource);level.addFreshEntity(namedCopy);
        try {
            namedCopy.copyTraitsFrom(net.ltxprogrammer.changed.ability.IAbstractChangedEntity.forEntity(namedSource));
            check(namedCopy.getName().getString().equals("NamedCompanion") && namedCopy.getUnderlyingPlayer()==null,
                    "Ordinary named Dark NPC trait copies retain their name and independence");
        } finally {namedSource.discard();namedCopy.discard();}
        for (var trait : CreaturePersonality.Trait.values()) {
            var command = server.createCommandSourceStack().withPosition(new net.minecraft.world.phys.Vec3(192.5,160,192.5));
            level.getChunkAt(new net.minecraft.core.BlockPos(192,160,194));
            var box = new net.minecraft.world.phys.AABB(188,156,188,198,165,198);
            var before = level.getEntitiesOfClass(ChangedEntity.class, box).stream().map(ChangedEntity::getUUID).toList();
            check(server.getCommands().performPrefixedCommand(command,
                    "changedsynergy spawn changed:pooltoy_wolf " + trait.dialogueKey())==1, "Actual spawn command accepts " + trait);
            var spawned = level.getEntitiesOfClass(ChangedEntity.class, box, e -> !before.contains(e.getUUID())).stream().findFirst().orElseThrow();
            try {
                for(int i=0;i<5;i++) CreaturePersonality.ensure(spawned);
                check(CreaturePersonality.dominantTrait(spawned)==trait, "Spawned primary personality is " + trait);
                var saved = new CompoundTag(); spawned.saveWithoutId(saved);
                var reloaded = (ChangedEntity)spawned.getType().create(level); reloaded.load(saved);
                try {
                    check(CreaturePersonality.dominantTrait(reloaded)==trait, "Explicit personality survives save/load: " + trait);
                    check(!(CreaturePersonality.has(reloaded, CreaturePersonality.Trait.PLAYFUL)
                            && CreaturePersonality.has(reloaded, CreaturePersonality.Trait.POLITE)), "Playful and polite remain exclusive");
                } finally { reloaded.discard(); }
            } finally { spawned.discard(); }
        }
        if(net.parkabird.changedsynergy.compat.TrueTransfurCompat.supportsSeparateInstinct()) instinctEyes(server);
        System.out.println("AUTHOR_IDENTITY_REGRESSION_PASS: independent normally named death NPCs, active Dark AI and persistent command-selected personalities");
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void instinctEyes(MinecraftServer server) throws Exception {
        var level=server.overworld();
        var player=new ServerPlayer(server,level,new GameProfile(UUID.randomUUID(),"WhiteInstinct"));
        player.connection=new ServerGamePacketListenerImpl(server,new Connection(PacketFlow.SERVERBOUND),player);
        java.util.Map<UUID,ServerPlayer> map=net.minecraftforge.fml.util.ObfuscationReflectionHelper.getPrivateValue(
                net.minecraft.server.players.PlayerList.class,server.getPlayerList(),"f_11197_");
        java.util.List<ServerPlayer> online=net.minecraftforge.fml.util.ObfuscationReflectionHelper.getPrivateValue(
                net.minecraft.server.players.PlayerList.class,server.getPlayerList(),"f_11196_");
        player.setPos(192.5,160,192.5);level.getChunkAt(player.blockPosition());
        map.put(player.getUUID(),player);online.add(player);level.addNewPlayer(player);
        var identity=new net.parkabird.changedsynergy.origin.NativeOriginData.Identity();identity.state="native";identity.faction="white";
        net.parkabird.changedsynergy.origin.NativeOriginData.get(server).players.put(player.getUUID(),identity);
        Class mode=Class.forName("com.lugert.true_transfur.transfur.ControlMode");
        var switchMode=Class.forName("com.lugert.true_transfur.transfur.TransfurController").getMethod("switchControlMode",ServerPlayer.class,mode);
        var cap=(net.minecraftforge.common.capabilities.Capability)Class.forName("com.lugert.true_transfur.transfur.TransfurCapabilities").getField("TRANSFUR_DATA").get(null);
        Object state=java.util.Objects.requireNonNull(player.getCapability(cap).orElse(null),"Missing TT capability");
        try {
            for(var variant:java.util.List.of(ChangedTransfurVariants.PURE_WHITE_LATEX_WOLF.get(),ChangedTransfurVariants.PURE_WHITE_LATEX_WOLF_PUP.get(),
                    ChangedTransfurVariants.PURE_WHITE_LATEX_CERBERUS.get(),ChangedTransfurVariants.LATEX_MUTANT_BLOODCELL_WOLF.get())) {
                identity.form=identity.current=variant.getFormId().toString();
                ProcessTransfur.setPlayerTransfurVariant(player,variant,null,1F,false);
                state.getClass().getMethod("setTransfurred",boolean.class).invoke(state,true);
                check((Boolean)switchMode.invoke(null,player,Enum.valueOf(mode,"INSTINCT")),"Native White form enters actual TT Instinct: "+variant.getFormId());
                var body=(ChangedEntity)net.parkabird.changedsynergy.compat.TrueTransfurCompat.interactionBody(player);
                UUID bodyId=body.getUUID();body.setPos(194.5,160,194.5);level.getChunkAt(body.blockPosition());
                var holder=(PureWhiteWolfAdaptation.PlayerState)ProcessTransfur.getPlayerTransfurVariant(player);
                var away=new CompoundTag();away.putLong("AwaySince",level.getGameTime()-300);holder.changedSynergy$setPureWhiteAdaptation(away);
                player.setPos(192.5,160,192.5);
                var home=player.blockPosition().below();level.setBlock(home,net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(),2);
                net.ltxprogrammer.changed.world.LatexCoverState.setAt(level,home,net.ltxprogrammer.changed.init.ChangedLatexTypes.WHITE_LATEX.get().defaultCoverState(),2);
                player.tickCount=Math.floorMod(player.getId(),100);
                PureWhiteWolfAdaptation.onPlayerTick(new net.minecraftforge.event.TickEvent.PlayerTickEvent(net.minecraftforge.event.TickEvent.Phase.END,player));
                body.tickCount=Math.floorMod(body.getId(),100);PureWhiteWolfAdaptation.onLivingTick(new LivingEvent.LivingTickEvent(body));
                check(PureWhiteWolfAdaptation.isAdaptedForm(body) && body.getUUID().equals(bodyId) && body.getType()==variant.getEntityType()
                        && ProcessTransfur.getPlayerTransfurVariant(player).getParent()==variant,
                        "Visible Instinct body, not hidden shell position, controls eyes without replacing the species"
                        + "; adapted="+PureWhiteWolfAdaptation.isAdaptedForm(body)+"; state="+holder.changedSynergy$getPureWhiteAdaptation()
                        + "; owner="+(net.parkabird.changedsynergy.compat.TrueTransfurCompat.bodyOwner(body)==player)
                        + "; bodyPos="+body.position()+"; visible="+net.parkabird.changedsynergy.compat.TrueTransfurCompat.interactionBody(player).position()
                        + "; home="+PureWhiteWolfAdaptation.inHomeTerritory(level,body.blockPosition())
                        + "; temporary="+ProcessTransfur.getPlayerTransfurVariant(player).isTemporaryFromSuit());
                body.setPos(player.position());
                PureWhiteWolfAdaptation.onPlayerTick(new net.minecraftforge.event.TickEvent.PlayerTickEvent(net.minecraftforge.event.TickEvent.Phase.END,player));
                check(!PureWhiteWolfAdaptation.isAdaptedForm(body),"Instinct body hides eyes when returning to White covering");
                net.ltxprogrammer.changed.world.LatexCoverState.setAt(level,home,net.ltxprogrammer.changed.init.ChangedLatexTypes.NONE.get().defaultCoverState(),2);
                net.parkabird.changedsynergy.compat.TrueTransfurCompat.ensurePlayerControl(player);
                check(body.isRemoved() && ProcessTransfur.getPlayerTransfurVariant(player).getParent()==variant,
                        "Returning to PLAYER retains the permanent species and removes only the controlled body");
            }
            System.out.println("WHITE_EYES_INSTINCT_REGRESSION_PASS: four real TT Instinct bodies retain species, UUID and permanent identity");
        } finally {
            net.parkabird.changedsynergy.compat.TrueTransfurCompat.ensurePlayerControl(player);
            identity.state="human";state.getClass().getMethod("setTransfurred",boolean.class).invoke(state,false);
            map.remove(player.getUUID());online.remove(player);player.discard();
        }
    }
    private static void check(boolean pass, String message) {
        if(!pass) throw new AssertionError(message);
        System.out.println("AUTHOR_IDENTITY_CHECK: " + message);
    }
}
