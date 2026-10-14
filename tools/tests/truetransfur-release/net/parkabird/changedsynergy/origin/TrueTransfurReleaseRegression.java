package net.parkabird.changedsynergy.origin;

import com.mojang.authlib.GameProfile;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.ltxprogrammer.changed.init.ChangedTransfurVariants;
import net.ltxprogrammer.changed.process.ProcessTransfur;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.parkabird.changedsynergy.ChangedSynergyConfig;
import net.parkabird.changedsynergy.ChangedSynergyMod;
import net.parkabird.changedsynergy.ai.LatexSocialMemory;
import net.parkabird.changedsynergy.ai.PlayerSocialService;
import net.parkabird.changedsynergy.compat.TrueTransfurCompat;
import net.parkabird.changedsynergy.compat.TrueTransfurPvpGuard;
import net.parkabird.changedsynergy.init.ChangedSynergyGameRules;
import net.parkabird.changedsynergy.network.NativeOriginChoosePacket;

/** Actual distributable-backend checks, kept out of normal release artifacts. */
@Mod.EventBusSubscriber(modid = ChangedSynergyMod.MOD_ID)
public final class TrueTransfurReleaseRegression {
    private static int checks;
    private static net.minecraft.core.RegistryAccess registries;
    @SubscribeEvent public static void run(ServerStartedEvent event) {
        if (Boolean.getBoolean("truetransfur.command-state")) {
            TrueTransfurCommandStateRegression.start(event.getServer());
            return;
        }
        try {
            // As in the camp fixture, isolate config mutations from Forge's asynchronous file watcher.
            ChangedSynergyConfig.SPEC.setConfig(com.electronwill.nightconfig.core.CommentedConfig.inMemory());
            registries = event.getServer().registryAccess();
            regionalCatalog(event.getServer());
            randomCatalog();
            if (ModList.get().isLoaded("true_transfur")) backend(event.getServer());
            else check(!TrueTransfurCompat.supportsNativeOrigin(), "Optional backend remains optional");
            var server = event.getServer();
            if (ModList.get().isLoaded("changed_survive_protocol")) SurvivalProtocolRegression.run(server);
            var owner = player(server, "CarrierOwner");
            var caster = player(server, "CarrierCaster");
            identity(owner, false); identity(caster, false);
            try {
                ChangedExtrasRegression.run(server, owner);
                if(TrueTransfurCompat.supportsSeparateInstinct()) {
                    identity(caster,true);
                    ProcessTransfur.setPlayerTransfurVariant(caster,ChangedTransfurVariants.DARK_LATEX_WOLF_MALE.get(),null,1,false);
                    Capability cap=(Capability)Class.forName("com.lugert.true_transfur.transfur.TransfurCapabilities").getField("TRANSFUR_DATA").get(null);
                    Object data=caster.getCapability(cap).orElse(null);data.getClass().getMethod("setTransfurred",boolean.class).invoke(data,true);
                    Class mode=Class.forName("com.lugert.true_transfur.transfur.ControlMode");
                    Class.forName("com.lugert.true_transfur.transfur.TransfurController").getMethod("switchControlMode",ServerPlayer.class,mode).invoke(null,caster,Enum.valueOf(mode,"INSTINCT"));
                    ChangedExtrasRegression.actualInstinctBody((ChangedEntity)TrueTransfurCompat.interactionBody(caster));
                    TakeoverNavigationRegression.run(server,owner,caster,(ChangedEntity)TrueTransfurCompat.interactionBody(caster));
                    TrueTransfurCompat.ensurePlayerControl(caster);identity(caster,false);ProcessTransfur.removePlayerTransfurVariant(caster);
                }
                net.parkabird.changedsynergy.regression.NativeCarrierRegression.run(server, owner, caster);
                ExoskeletonNavigationRegression.run(owner);
            } finally {
                for (var player : List.of(owner, caster)) {
                    player.discard(); onlinePlayers(server).remove(player);
                }
            }
            System.out.println("TRUETRANSFUR_RELEASE_REGRESSION_PASS: " + checks + " checks; backend="
                    + ModList.get().getModContainerById("true_transfur").map(mod -> mod.getModInfo().getVersion().toString()).orElse("none"));
        } catch (Throwable failure) {
            System.err.println("TRUETRANSFUR_RELEASE_REGRESSION_FAIL"); failure.printStackTrace();
        } finally { event.getServer().halt(false); }
    }
    private static void check(boolean result, String message) {
        if (!result) throw new AssertionError(message);
        checks++; System.out.println("TT_RELEASE_CHECK: " + message);
    }
    private static void regionalCatalog(MinecraftServer server) throws Exception {
        var expected = Map.ofEntries(
                Map.entry("latex_gnoll_taur", "savanna"), Map.entry("latex_hypno_cat", "savanna"),
                Map.entry("latex_medusa_cat", "savanna"), Map.entry("latex_red_panda", "jungle"),
                Map.entry("latex_watermelon_cat", "jungle"), Map.entry("latex_deer", "forest"),
                Map.entry("latex_bee", "forest"), Map.entry("latex_blue_dragon", "taiga"),
                Map.entry("latex_purple_fox", "taiga"), Map.entry("latex_blue_wolf", "mountain"),
                Map.entry("latex_yuin", "mountain"), Map.entry("latex_red_dragon", "badlands"),
                Map.entry("latex_snake", "desert"), Map.entry("latex_fennec_fox", "desert"),
                Map.entry("latex_golden_dragon", "desert"), Map.entry("latex_mimic_plant", "jungle"),
                Map.entry("latex_kobold", "cave"), Map.entry("latex_stiger", "cave"),
                Map.entry("latex_traffic_cone_dragon", "cave"), Map.entry("latex_translucent_lizard", "cave"),
                Map.entry("white_latex_knight", "plains"), Map.entry("latex_human", "general"));
        var seen = new java.util.HashSet<String>();
        var catalog = NativeOriginCatalog.entries(registries);
        for (var entry : catalog) {
            var type = entry.variant().getEntityType();
            var id = net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getKey(type);
            String region = id == null || !id.getNamespace().equals("changed") ? null : expected.get(id.getPath());
            if (region != null) {
                check(entry.faction().equals("light") && entry.region().equals(region), "Native affiliation " + id + " -> " + region
                        + " (actual=" + entry.faction() + ":" + entry.region() + ")");
                seen.add(id.getPath());
            }
            System.out.println("ARCHIVE_FACTION_AUDIT: " + entry.form() + " -> " + entry.faction() + ":" + entry.region());
        }
        check(seen.equals(expected.keySet()), "Every regional regression species exists in the actual archive");
        var gnoll = catalog.stream().filter(entry -> entry.variant().getEntityType() ==
                net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getValue(
                        net.minecraft.resources.ResourceLocation.parse("changed:latex_gnoll_taur"))).findFirst().orElseThrow();
        check(net.parkabird.changedsynergy.ai.FactionDiplomacy.RIVALRIES.stream().anyMatch(pair ->
                pair.first().equals("faction:light:swamp") && pair.second().equals("faction:light:" + gnoll.region())),
                "Gnoll archive opposition is Swamp, not Snowy");
        var level = server.overworld();
        var search = new OriginSpawnSearch(level, gnoll, false, new net.minecraft.core.BlockPos(0, 64, 0));
        var matches = OriginSpawnSearch.class.getDeclaredMethod("matches", net.minecraft.core.Holder.class);
        matches.setAccessible(true);
        var biomes = registries.registryOrThrow(net.minecraft.core.registries.Registries.BIOME);
        check((Boolean)matches.invoke(search, biomes.getHolderOrThrow(net.minecraft.world.level.biome.Biomes.SAVANNA))
                && !(Boolean)matches.invoke(search, biomes.getHolderOrThrow(net.minecraft.world.level.biome.Biomes.PLAINS)),
                "Gnoll birth search accepts Savanna and rejects the previous Plains fallback");
        var packet = new net.parkabird.changedsynergy.network.NativeOriginStatePacket(0, UUID.randomUUID(), true, false,
                net.minecraft.network.chat.Component.empty(), List.of(gnoll));
        var buffer = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        net.parkabird.changedsynergy.network.NativeOriginStatePacket.encode(packet, buffer);
        check(net.parkabird.changedsynergy.network.NativeOriginStatePacket.decode(buffer).entries().equals(List.of(gnoll)),
                "Server catalog preserves the corrected regional identity in its client packet");
        buffer.release();
        var origins = level.getGameRules().getRule(ChangedSynergyGameRules.NATIVE_ORIGINS);
        boolean previousRule = origins.get();
        var disabled = ChangedSynergyConfig.COMMON.disabledOriginForms;
        var previousDisabled = disabled.get();
        try {
            origins.set(false, server); disabled.set(List.of(gnoll.form().toString()));
            var migrant = player(server, "ArchiveMigration");
            var identity = new NativeOriginData.Identity(); identity.state = "native";
            identity.form = gnoll.form().toString(); identity.current = "changed:form_dark_latex_wolf/male";
            identity.faction = "light"; identity.region = "plains"; identity.home = new net.minecraft.core.BlockPos(31, 78, -12);
            var data = NativeOriginData.get(server); data.players.put(migrant.getUUID(), identity);
            NativeOriginService.login(new net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent(migrant));
            check(identity.region.equals("savanna") && NativeOriginService.account(identity).equals("light:savanna")
                    && identity.current.equals("changed:form_dark_latex_wolf/male") && identity.home.equals(new net.minecraft.core.BlockPos(31, 78, -12)),
                    "Legacy Plains guess is corrected even for a now-disabled birth form; current body and home are retained");
            var saved = NativeOriginData.load(data.save(new CompoundTag())).players.get(migrant.getUUID());
            check(saved.region.equals("savanna") && saved.current.equals(identity.current), "Corrected citizenship survives world-data serialization");
        } finally { origins.set(previousRule, server); disabled.set(previousDisabled); }
    }
    private static void randomCatalog() {
        var catalog = NativeOriginCatalog.entries(registries);
        check(!catalog.isEmpty(), "Actual Changed catalog is populated");
        var seen = new java.util.HashSet<net.minecraft.resources.ResourceLocation>();
        var random = RandomSource.create(4567);
        for (int i = 0; i < 10000; i++) seen.add(NativeOriginCatalog.drawRandom(catalog, 0, random));
        check(seen.contains(null) && seen.size() == catalog.size() + 1, "Full random reaches human and every allowed species");
        for (int category = 0; category <= 5; category++) {
            check(NativeOriginCatalog.randomCategory(NativeOriginCatalog.randomRequest(category)) == category, "Random request category round-trip " + category);
            if (category == 0) continue;
            final int group = category;
            var pool = catalog.stream().filter(entry -> NativeOriginCatalog.categoryOf(entry) == group).toList();
            check(!pool.isEmpty(), "Original archive category populated " + category);
            boolean valid = true;
            for (int i = 0; i < 200; i++) {
                var result = NativeOriginCatalog.find(registries, NativeOriginCatalog.drawRandom(catalog, category, random));
                valid &= result != null && NativeOriginCatalog.categoryOf(result) == category;
            }
            check(valid, "Category draw cannot become human or cross groups " + category);
        }
        check(NativeOriginCatalog.randomCategory(net.minecraft.resources.ResourceLocation.parse("changed_synergy:random_origin/6")) == -1,
                "Unknown random requests cannot impersonate a category");
        var disabled = ChangedSynergyConfig.COMMON.disabledOriginForms;
        var previous = disabled.get();
        try {
            var blocked = catalog.get(0).form(); disabled.set(List.of(blocked.toString()));
            var current = NativeOriginCatalog.entries(registries);
            boolean valid = true;
            for (int i = 0; i < 1000; i++) valid &= !blocked.equals(NativeOriginCatalog.drawRandom(current, 0, random));
            check(valid && current.size() == catalog.size() - 1, "Random choices obey the current server denylist");
            disabled.set(catalog.stream().map(entry -> entry.form().toString()).toList());
            check(NativeOriginCatalog.entries(registries).isEmpty() && NativeOriginCatalog.drawRandom(List.of(), 0, random) == null,
                    "An empty allowed catalog safely leaves human as the sole full-random choice");
            boolean rejected = false;
            try { NativeOriginCatalog.drawRandom(List.of(), 2, random); } catch (IllegalArgumentException expected) { rejected = true; }
            check(rejected, "Empty latex categories never silently select human");
        } finally { disabled.set(previous); }
    }
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void backend(MinecraftServer server) throws Exception {
        check(TrueTransfurCompat.supportsNativeOrigin(), "Distributable TrueTransfur control API loads");
        var level = server.overworld();
        level.getGameRules().getRule(ChangedSynergyGameRules.NATIVE_ORIGINS).set(true, server);
        ServerPlayer human = player(server, "TTReleaseHuman"), nativePlayer = player(server, "TTReleaseNative");
        human.setPos(.5, 180, .5); nativePlayer.setPos(2.5, 180, .5);
        identity(nativePlayer, true); identity(human, false);
        var controller = Class.forName("com.lugert.true_transfur.transfur.TransfurController");
        for (var player : List.of(human, nativePlayer)) {
            ProcessTransfur.setPlayerTransfurVariant(player, ChangedTransfurVariants.DARK_LATEX_WOLF_MALE.get(), null, 1, false);
            Capability cap = (Capability)Class.forName("com.lugert.true_transfur.transfur.TransfurCapabilities").getField("TRANSFUR_DATA").get(null);
            Object data = player.getCapability(cap).orElse(null);
            if (data == null) throw new AssertionError("Missing backend capability");
            data.getClass().getMethod("setTransfurred", boolean.class).invoke(data, true);
        }
        check(!(Boolean)controller.getMethod("isTrueTransfurActive", ServerPlayer.class).invoke(null, human)
                && (Boolean)controller.getMethod("isTrueTransfurActive", ServerPlayer.class).invoke(null, nativePlayer),
                "Origin mode exempts human-origin players and retains native permanent identity");
        Class<?> reborn = null;
        try { reborn = Class.forName("com.lugert.true_transfur.transfur.RebornHandler"); } catch (ClassNotFoundException olderBackend) { /* 1.0.1 has no rebirth. */ }
        if (reborn != null) {
            check(ModList.get().isLoaded("mixin_args"), "New release's mandatory MixinArgs dependency loads");
            var intercept = reborn.getMethod("interceptDeath", LivingDeathEvent.class, ServerPlayer.class);
            var humanDeath = new LivingDeathEvent(human, human.damageSources().generic());
            check(!(Boolean)intercept.invoke(null, humanDeath, human) && !humanDeath.isCanceled(), "Human-origin dark wolves do not enter TrueTransfur rebirth");
            var nativeDeath = new LivingDeathEvent(nativePlayer, nativePlayer.damageSources().generic());
            check((Boolean)intercept.invoke(null, nativeDeath, nativePlayer) && nativeDeath.isCanceled() && TrueTransfurCompat.isRebirthing(nativePlayer),
                    "Native dark wolves retain the author's rebirth sequence");
            check(!PlayerSocialService.canInteract(human, nativePlayer, nativePlayer)
                    && !PlayerSocialService.canInteract(nativePlayer, human, human), "Downed rebirth rejects both directions of player social actions");
            float progress = nativePlayer.getPersistentData().getCompound("TrueTransfurReborn").getFloat("Progress");
            reborn.getMethod("handlePress", ServerPlayer.class).invoke(null, nativePlayer);
            check(nativePlayer.getPersistentData().getCompound("TrueTransfurReborn").getFloat("Progress") > progress, "Native rebirth QTE remains responsive");
            reborn.getMethod("clear", ServerPlayer.class).invoke(null, nativePlayer); nativePlayer.setHealth(nativePlayer.getMaxHealth());
            CompoundTag sleep = new CompoundTag(); sleep.putString("Phase", "SLEEPING");
            nativePlayer.getPersistentData().put("TrueTransfurReborn", sleep); nativePlayer.setInvisible(true); identity(nativePlayer, false);
            reborn.getMethod("onLogin", ServerPlayer.class).invoke(null, nativePlayer);
            check(!nativePlayer.getPersistentData().contains("TrueTransfurReborn") && !nativePlayer.isInvisible(),
                    "Switching to human clears old rebirth and sleep invisibility");
            sleep.putString("Phase", "PUP"); nativePlayer.getPersistentData().put("TrueTransfurReborn", sleep);
            Field delayed = reborn.getDeclaredField("HEAL_LATER"); delayed.setAccessible(true);
            Map<UUID, Integer> heals = (Map<UUID, Integer>)delayed.get(null); heals.put(nativePlayer.getUUID(), 1);
            reborn.getMethod("tick", ServerPlayer.class).invoke(null, nativePlayer);
            check(!nativePlayer.getPersistentData().contains("TrueTransfurReborn") && !heals.containsKey(nativePlayer.getUUID()),
                    "Human-origin stale pup progress and queued healing are cleared");
            level.getGameRules().getRule(ChangedSynergyGameRules.NATIVE_ORIGINS).set(false, server);
            check((Boolean)intercept.invoke(null, humanDeath, human), "Turning origin mode off restores ordinary TrueTransfur rebirth");
            reborn.getMethod("clear", ServerPlayer.class).invoke(null, human); human.setHealth(human.getMaxHealth());
        }
        level.getGameRules().getRule(ChangedSynergyGameRules.NATIVE_ORIGINS).set(false, server);
        ProcessTransfur.getPlayerTransfurVariant(human).setTemporaryForSuit(true);
        check(!TrueTransfurCompat.blocksReversal(human), "Temporary wrapping is exempt from permanent reversal rules");
        TrueTransfurCompat.clearTemporarySuit(human);
        check(ProcessTransfur.getPlayerTransfurVariant(human) == null && human.isAlive(), "Temporary wrapping releases without killing the player");
        level.getGameRules().getRule(ChangedSynergyGameRules.NATIVE_ORIGINS).set(true, server); identity(nativePlayer, true);
        if(TrueTransfurCompat.supportsSeparateInstinct())humanInstinctControl(human);
        Class mode = Class.forName("com.lugert.true_transfur.transfur.ControlMode");
        Object instinct = Enum.valueOf(mode, "INSTINCT");
        try {
            var change = controller.getMethod("switchControlMode", ServerPlayer.class, mode);
            for (int cycle = 0; cycle < 3; cycle++) {
                check((Boolean)change.invoke(null, nativePlayer, instinct), "Actual release enters physical Instinct cycle " + cycle);
                var visible = TrueTransfurCompat.interactionBody(nativePlayer);
                check(visible instanceof ChangedEntity && TrueTransfurCompat.bodyOwner((ChangedEntity)visible) == nativePlayer,
                        "Visible Instinct body maps to the real player cycle " + cycle);
                var body = (ChangedEntity)visible;
                var hostilityProbe = net.ltxprogrammer.changed.init.ChangedEntities.GAS_WOLF_MALE.get().create(level);
                try {
                    hostilityProbe.setLastHurtByMob(body);
                    hostilityProbe.setLastHurtMob(body);
                    net.parkabird.changedsynergy.ai.HuntMemory.seeTarget(hostilityProbe, nativePlayer);
                    check(net.parkabird.changedsynergy.ai.LatexSocialMemory.hasHostilityToward(hostilityProbe, nativePlayer),
                            "Instinct-body revenge belongs to its real player cycle " + cycle);
                    net.parkabird.changedsynergy.ai.LatexSocialMemory.clearOwnerHostility(hostilityProbe, nativePlayer);
                    check(hostilityProbe.getLastHurtByMob() == null && hostilityProbe.getLastHurtMob() == null
                                    && !net.parkabird.changedsynergy.ai.HuntMemory.targets(hostilityProbe, nativePlayer),
                            "Peace clears real Instinct-body revenge and player pursuit together cycle " + cycle);
                } finally { hostilityProbe.discard(); }
                body.setCustomNameVisible(true);
                nativePlayer.tickCount = 0;
                PlayerSocialService.onPlayerTick(new net.minecraftforge.event.TickEvent.PlayerTickEvent(
                        net.minecraftforge.event.TickEvent.Phase.END, nativePlayer));
                check(!body.isCustomNameVisible(), "Actual Instinct sync hides overhead label cycle " + cycle);
                check(body.getCustomName() != null && body.getCustomName().getString().equals(nativePlayer.getDisplayName().getString()),
                        "Actual Instinct sync keeps player identity cycle " + cycle + ": " + body.getCustomName()
                                + " / " + nativePlayer.getDisplayName());
                check(TrueTransfurCompat.isInstinctShell(nativePlayer) && !LatexSocialMemory.isSocialLatex(body), "Technical shell hidden; proxy excluded from NPC identity");
                if (cycle == 0) {
                    instinctPvpReputation(server, human, nativePlayer, body);
                    instinctPvpSafety(server, human, nativePlayer, body);
                }
                var npc = (ChangedEntity)ChangedTransfurVariants.DARK_LATEX_WOLF_MALE.get().getEntityType().create(level);
                npc.setPos(human.position());
                check(!TargetingConditions.forCombat().ignoreLineOfSight().test(npc, nativePlayer), "NPC targeting ignores the technical shell");
                check(PlayerSocialService.canInteract(human, nativePlayer, body), "Visible body remains socially interactable");
                human.getPersistentData().putLong("SynergyNextPlayerSocialAction", 0);
                human.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.APPLE, 2));
                int before = nativePlayer.getInventory().countItem(Items.APPLE);
                PlayerSocialService.act(human, nativePlayer.getUUID(), body, "social_gift");
                check(human.getMainHandItem().getCount() == 1 && nativePlayer.getInventory().countItem(Items.APPLE) == before + 1,
                        "Proxy gifting transfers one item to the real inventory");
                for (int tick = 0; tick < 25; tick++) body.tick();
                check(body.isAlive(), "Real Instinct AI ticks without inactive-follow null-link failure");
                TrueTransfurCompat.ensurePlayerControl(nativePlayer);
                check(body.isRemoved() && !TrueTransfurCompat.isInstinctShell(nativePlayer)
                        && !PlayerSocialService.canInteract(human, nativePlayer, body), "Returning to Player mode cleans the exact old body");
                nativePlayer.setPos(2.5, 180, .5);
            }
        } catch (NoSuchMethodException oldDirectInstinct) {
            check(!TrueTransfurCompat.supportsSeparateInstinct(), "Legacy backend has no separate body API");
            var originalForm = ProcessTransfur.getPlayerTransfurVariant(nativePlayer);
            controller.getMethod("setControlMode", ServerPlayer.class, mode).invoke(null, nativePlayer, instinct);
            check(TrueTransfurCompat.isInstinctControlled(nativePlayer), "Legacy native player enters direct Instinct control");
            check(!TrueTransfurCompat.isInstinctShell(nativePlayer)
                    && TrueTransfurCompat.interactionBody(nativePlayer) == nativePlayer,
                    "Legacy Instinct player remains their own visible body");
            check(PlayerSocialService.canInteract(human, nativePlayer, nativePlayer), "Legacy direct body supports player social actions");
            human.getPersistentData().putLong("SynergyNextPlayerSocialAction", 0);
            human.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.APPLE, 2));
            int before = nativePlayer.getInventory().countItem(Items.APPLE);
            PlayerSocialService.act(human, nativePlayer.getUUID(), nativePlayer, "social_gift");
            check(human.getMainHandItem().getCount() == 1 && nativePlayer.getInventory().countItem(Items.APPLE) == before + 1,
                    "Legacy direct-body gift transfers exactly once");
            TrueTransfurCompat.ensurePlayerControl(nativePlayer);
            check(!TrueTransfurCompat.isInstinctControlled(nativePlayer)
                    && ProcessTransfur.getPlayerTransfurVariant(nativePlayer) == originalForm,
                    "Legacy mode exit preserves the same native form");
        }
        if(ModList.get().isLoaded("changed_addon"))archiveSnapshot(nativePlayer);
        else check(NativeOriginCatalog.entries(registries).stream().noneMatch(entry->entry.form().getNamespace().equals("changed_addon")),
                "Archive never requires unavailable Addon forms");
        choiceRequests(nativePlayer);
        for (var player : List.of(human, nativePlayer)) { TrueTransfurCompat.ensurePlayerControl(player); player.discard(); onlinePlayers(server).remove(player); }
    }
    private static void instinctPvpReputation(MinecraftServer server, ServerPlayer human, ServerPlayer owner, ChangedEntity body) throws Exception {
        var rule = server.overworld().getGameRules().getRule(ChangedSynergyGameRules.NATIVE_PVP_REPUTATION);
        boolean previous = rule.get();
        var faction = ChangedTransfurVariants.DARK_LATEX_WOLF_MALE.get().getEntityType().create(server.overworld());
        var source = human.damageSources().playerAttack(human);
        String hit = "SynergyNativeHit:" + human.getStringUUID();
        try {
            rule.set(true, server);
            int standing = net.parkabird.changedsynergy.ai.FactionReputation.score(faction, human);
            NativeOriginCombatEvents.hurt(new net.minecraftforge.event.entity.living.LivingDamageEvent(body, source, 1));
            check(net.parkabird.changedsynergy.ai.FactionReputation.score(faction, human) == standing - 2,
                    "Hitting an actual native Instinct body changes the attacker's standing with its owner's faction");
            NativeOriginCombatEvents.hurt(new net.minecraftforge.event.entity.living.LivingDamageEvent(body, source, 1));
            check(net.parkabird.changedsynergy.ai.FactionReputation.score(faction, human) == standing - 2,
                    "Repeated physical-body damage shares the real owner's reputation cooldown");
            NativeOriginCombatEvents.killed(new LivingDeathEvent(body, source));
            check(net.parkabird.changedsynergy.ai.FactionReputation.score(faction, human) == standing - 2,
                    "Physical-body death is not charged before TrueTransfur forwards it to its owner");
            owner.getPersistentData().remove(hit); human.setLastHurtByMob(body);
            NativeOriginCombatEvents.hurt(new net.minecraftforge.event.entity.living.LivingDamageEvent(body, source, 1));
            check(net.parkabird.changedsynergy.ai.FactionReputation.score(faction, human) == standing - 2,
                    "Self-defense against a native physical body remains exempt");
            human.setLastHurtByMob(null);
            rule.set(false, server);
            NativeOriginCombatEvents.hurt(new net.minecraftforge.event.entity.living.LivingDamageEvent(body, source, 1));
            check(net.parkabird.changedsynergy.ai.FactionReputation.score(faction, human) == standing - 2,
                    "Disabling native PvP reputation also disables physical-body penalties");
            rule.set(true, server);
            var peer = player(server, "NativePvpPeer"); identity(peer, true);
            var instinctSource = body.damageSources().mobAttack(body);
            int ownerStanding = net.parkabird.changedsynergy.ai.FactionReputation.score(faction, owner);
            NativeOriginCombatEvents.hurt(new net.minecraftforge.event.entity.living.LivingDamageEvent(peer, instinctSource, 1));
            check(net.parkabird.changedsynergy.ai.FactionReputation.score(faction, owner) == ownerStanding - 2,
                    "Damage dealt by the actual Instinct body is attributed to the real attacking player");
            NativeOriginCombatEvents.killed(new LivingDeathEvent(peer, instinctSource));
            check(net.parkabird.changedsynergy.ai.FactionReputation.score(faction, owner) == ownerStanding - 17,
                    "Forwarded player death attributes one kill penalty to an Instinct attacker");
            peer.getPersistentData().remove("SynergyNativeHit:" + owner.getStringUUID()); body.setLastHurtByMob(peer);
            NativeOriginCombatEvents.hurt(new net.minecraftforge.event.entity.living.LivingDamageEvent(peer, instinctSource, 1));
            check(net.parkabird.changedsynergy.ai.FactionReputation.score(faction, owner) == ownerStanding - 17,
                    "Instinct retaliation checks the visible body's self-defense history");
            body.setLastHurtByMob(null); peer.discard(); onlinePlayers(server).remove(peer);
            var orphan = ChangedTransfurVariants.DARK_LATEX_WOLF_MALE.get().getEntityType().create(server.overworld());
            orphan.getPersistentData().putBoolean("TrueTransfurControlled", true);
            orphan.getPersistentData().putUUID("TrueTransfurOwner", UUID.randomUUID());
            NativeOriginCombatEvents.hurt(new net.minecraftforge.event.entity.living.LivingDamageEvent(orphan, source, 1));
            check(net.parkabird.changedsynergy.ai.FactionReputation.score(faction, human) == standing - 2,
                    "An orphaned marked body cannot impersonate a native player for reputation penalties");
            int sameAccountStanding = net.parkabird.changedsynergy.ai.FactionReputation.score(faction, owner);
            var oldOwner = new ServerPlayer(server, server.overworld(), new GameProfile(owner.getUUID(), "OldNativeOwner"));
            NativeOriginCombatEvents.hurt(new net.minecraftforge.event.entity.living.LivingDamageEvent(body,
                    oldOwner.damageSources().playerAttack(oldOwner), 1));
            check(net.parkabird.changedsynergy.ai.FactionReputation.score(faction, owner) == sameAccountStanding
                    && !owner.getPersistentData().contains("SynergyNativeHit:" + owner.getStringUUID()),
                    "A stale player object for the same real UUID cannot incur a self-attack penalty");
        } finally {
            rule.set(previous, server); human.setLastHurtByMob(null); body.setLastHurtByMob(null);
            owner.getPersistentData().remove(hit);
        }
    }
    private static void instinctPvpSafety(MinecraftServer server, ServerPlayer human, ServerPlayer owner, ChangedEntity body) {
        boolean previousPvp = server.isPvpAllowed();
        var board = server.getScoreboard();
        var humanTeam = board.getPlayersTeam(human.getScoreboardName());
        var ownerTeam = board.getPlayersTeam(owner.getScoreboardName());
        var testTeam = board.addPlayerTeam("TT_PvP_" + UUID.randomUUID().toString().substring(0, 8));
        var humanSource = human.damageSources().playerAttack(human);
        var bodySource = body.damageSources().mobAttack(body);
        try {
            server.setPvpAllowed(false);
            check(!TrueTransfurPvpGuard.allows(body, human) && !TrueTransfurPvpGuard.allows(human, body),
                    "Server PvP-off applies in both directions between a visible Instinct body and a human");
            check(damageCancellationMatches(body, humanSource, true) && damageCancellationMatches(human, bodySource, true),
                    "Forge attack and hurt events both reject physical-body PvP while server PvP is disabled");
            float health = body.getHealth();
            check(!body.hurt(humanSource, 1) && body.getHealth() == health,
                    "A real physical-body hurt call cannot bypass the disabled server PvP rule");
            var arrow = new net.minecraft.world.entity.projectile.Arrow(server.overworld(), human);
            check(damageCancellationMatches(body, body.damageSources().arrow(arrow, human), true),
                    "An indirect player projectile cannot bypass physical-body PvP protection");
            server.setPvpAllowed(true);
            check(TrueTransfurPvpGuard.allows(body, human) && TrueTransfurPvpGuard.allows(human, body),
                    "Server PvP-on permits valid player-versus-Instinct-body combat in both directions");
            check(damageCancellationMatches(body, humanSource, false) && damageCancellationMatches(human, bodySource, false),
                    "The proxy PvP guard leaves permitted attack and hurt events uncanceled");
            board.addPlayerToTeam(human.getScoreboardName(), testTeam);
            board.addPlayerToTeam(owner.getScoreboardName(), testTeam);
            testTeam.setAllowFriendlyFire(false);
            check(!TrueTransfurPvpGuard.allows(body, human) && !TrueTransfurPvpGuard.allows(human, body),
                    "Scoreboard friendly-fire restrictions use the real Instinct owner's team in both directions");
            check(damageCancellationMatches(body, humanSource, true) && damageCancellationMatches(human, bodySource, true),
                    "Team friendly-fire restrictions cancel actual proxy attack and hurt events");
            testTeam.setAllowFriendlyFire(true);
            check(TrueTransfurPvpGuard.allows(body, human) && TrueTransfurPvpGuard.allows(human, body),
                    "Allowing team friendly fire restores both proxy PvP directions");
            check(damageCancellationMatches(body, humanSource, false) && damageCancellationMatches(human, bodySource, false),
                    "Permitted team combat remains available at both Forge damage stages");
            check(!TrueTransfurPvpGuard.allows(owner, body) && !TrueTransfurPvpGuard.allows(body, owner),
                    "A visible body and its technical player cannot attack their own real UUID");
            var orphan = ChangedTransfurVariants.DARK_LATEX_WOLF_MALE.get().getEntityType().create(server.overworld());
            orphan.getPersistentData().putBoolean("TrueTransfurControlled", true);
            orphan.getPersistentData().putUUID("TrueTransfurOwner", UUID.randomUUID());
            check(!TrueTransfurPvpGuard.allows(orphan, human),
                    "An orphaned controlled body cannot damage a real player by falling back to NPC combat");
            check(damageCancellationMatches(human, orphan.damageSources().mobAttack(orphan), true),
                    "Forge rejects an orphan proxy's attempted player attack and hurt events");
        } finally {
            board.removePlayerFromTeam(human.getScoreboardName(), testTeam);
            board.removePlayerFromTeam(owner.getScoreboardName(), testTeam);
            if (humanTeam != null) board.addPlayerToTeam(human.getScoreboardName(), humanTeam);
            if (ownerTeam != null) board.addPlayerToTeam(owner.getScoreboardName(), ownerTeam);
            board.removePlayerTeam(testTeam);
            server.setPvpAllowed(previousPvp);
        }
    }
    private static boolean damageCancellationMatches(net.minecraft.world.entity.LivingEntity target,
            net.minecraft.world.damagesource.DamageSource source, boolean expected) {
        var attack = new net.minecraftforge.event.entity.living.LivingAttackEvent(target, source, 1);
        var hurt = new net.minecraftforge.event.entity.living.LivingHurtEvent(target, source, 1);
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(attack);
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(hurt);
        return attack.isCanceled() == expected && hurt.isCanceled() == expected;
    }
    @SuppressWarnings("unchecked")
    private static void archiveSnapshot(ServerPlayer player) throws Exception {
        var modded = ChangedSynergyConfig.COMMON.includeModdedOriginSpecies;
        boolean previous = modded.get();
        try {
            modded.set(false);
            check(NativeOriginService.reopen(player), "Open an original-species archive snapshot");
            Field selecting = NativeOriginService.class.getDeclaredField("SELECTING"); selecting.setAccessible(true);
            var selection = ((Map<?, ?>)selecting.get(null)).get(player.getUUID());
            Field catalogField = selection.getClass().getDeclaredField("catalog"); catalogField.setAccessible(true);
            var shown = (List<NativeOriginCatalog.Entry>)catalogField.get(selection);
            Field nonceField = selection.getClass().getDeclaredField("nonce"); nonceField.setAccessible(true);
            UUID nonce = (UUID)nonceField.get(selection);
            Field busy = selection.getClass().getDeclaredField("busy"); busy.setAccessible(true);
            var exp10 = net.minecraft.resources.ResourceLocation.parse("changed_addon:form_experiment_10");
            check(shown.stream().noneMatch(entry -> entry.form().equals(exp10)), "Exp10 is absent from the original-species archive");
            modded.set(true);
            var expanded = NativeOriginCatalog.entries(registries);
            check(NativeOriginCatalog.find(registries, exp10) != null, "Enabling modded forms can expand the live registry with Exp10");
            NativeOriginService.choose(player, new NativeOriginChoosePacket(nonce, exp10, false, false));
            check(!(Boolean)busy.get(selection), "An unseen form is rejected even after its permission is enabled");
            boolean seedFound = false;
            for (int seed = 0; seed < 100000; seed++) {
                if (exp10.equals(NativeOriginCatalog.drawRandom(expanded, 0, RandomSource.create(seed)))
                        && NativeOriginCatalog.drawRandom(shown, 0, RandomSource.create(seed)) != null) {
                    player.getRandom().setSeed(seed); seedFound = true; break;
                }
            }
            check(seedFound, "Exercise a seed that would previously draw unseen Exp10");
            NativeOriginService.choose(player, new NativeOriginChoosePacket(nonce, NativeOriginCatalog.randomRequest(0), false, false));
            Field resultField = selection.getClass().getDeclaredField("randomResult"); resultField.setAccessible(true);
            var result = (net.minecraft.resources.ResourceLocation)resultField.get(selection);
            check((Boolean)busy.get(selection) && shown.stream().anyMatch(entry -> entry.form().equals(result))
                    && !exp10.equals(result), "Full random uses exactly the displayed archive snapshot");
            Field queue = NativeOriginService.class.getDeclaredField("QUEUE"); queue.setAccessible(true);
            ((java.util.Queue<?>)queue.get(null)).clear(); busy.set(selection, false);
            var disabled = ChangedSynergyConfig.COMMON.disabledOriginForms; var oldDisabled = disabled.get();
            try {
                disabled.set(List.of(result.toString()));
                NativeOriginService.choose(player, new NativeOriginChoosePacket(nonce, result, false, false));
                check(!(Boolean)busy.get(selection), "Server exclusions take effect even for a displayed record");
            } finally { disabled.set(oldDisabled); }
            NativeOriginService.choose(player, new NativeOriginChoosePacket(nonce, null, false, true));
            check(NativeOriginService.reopen(player), "Reopening refreshes the allowed archive records");
            selection = ((Map<?, ?>)selecting.get(null)).get(player.getUUID());
            check(((List<NativeOriginCatalog.Entry>)catalogField.get(selection)).stream().anyMatch(entry -> entry.form().equals(exp10)),
                    "A newly permitted form becomes selectable only after it appears in the refreshed archive");
            NativeOriginService.choose(player, new NativeOriginChoosePacket((UUID)nonceField.get(selection), null, false, true));
        } finally { modded.set(previous); }
    }
    private static void choiceRequests(ServerPlayer player) throws Exception {
        check(NativeOriginService.reopen(player), "An existing identity can reopen the archive");
        Field selecting = NativeOriginService.class.getDeclaredField("SELECTING"); selecting.setAccessible(true);
        var selection = ((Map<?, ?>)selecting.get(null)).get(player.getUUID());
        Field nonceField = selection.getClass().getDeclaredField("nonce"); nonceField.setAccessible(true);
        UUID nonce = (UUID)nonceField.get(selection);
        Field busy = selection.getClass().getDeclaredField("busy"); busy.setAccessible(true);
        NativeOriginService.choose(player, new NativeOriginChoosePacket(UUID.randomUUID(), NativeOriginCatalog.randomRequest(2), false, false));
        check(!(Boolean)busy.get(selection), "Random choices still require the server-issued nonce");
        NativeOriginService.choose(player, new NativeOriginChoosePacket(nonce, NativeOriginCatalog.randomRequest(2), false, false));
        Field resultField = selection.getClass().getDeclaredField("randomResult"); resultField.setAccessible(true);
        var result = (net.minecraft.resources.ResourceLocation)resultField.get(selection);
        System.out.println("TT_RANDOM_SERVER_RESULT: form=" + result + " busy=" + busy.get(selection) + " alive=" + player.isAlive()
                + " backend=" + TrueTransfurCompat.supportsNativeOrigin() + " pending=" + NativeOriginService.pending(player));
        check((Boolean)busy.get(selection) && NativeOriginCatalog.categoryOf(NativeOriginCatalog.find(registries, result)) == 2, "Server chooses an allowed Dark species");
        Field queue = NativeOriginService.class.getDeclaredField("QUEUE"); queue.setAccessible(true); ((java.util.Queue<?>)queue.get(null)).clear();
        busy.set(selection, false);
        Field failed = selection.getClass().getDeclaredField("failed"); failed.setAccessible(true); failed.set(selection, result);
        NativeOriginService.choose(player, new NativeOriginChoosePacket(nonce, NativeOriginCatalog.randomRequest(2), true, false));
        check(result.equals(resultField.get(selection)) && (Boolean)busy.get(selection), "Spawn fallback preserves the first server-side random result");
        NativeOriginService.choose(player, new NativeOriginChoosePacket(nonce, null, false, true));
        check(!NativeOriginService.pending(player), "Canceling a random reselection restores the current identity");
        check(NativeOriginService.reopen(player), "A fresh random request can follow cancellation");
        selection = ((Map<?, ?>)selecting.get(null)).get(player.getUUID()); nonce = (UUID)nonceField.get(selection);
        int size = NativeOriginCatalog.entries(registries).size(); boolean seedFound = false;
        for (int seed = 0; seed < 10000; seed++) {
            player.getRandom().setSeed(seed);
            if (player.getRandom().nextInt(size + 1) == size) { player.getRandom().setSeed(seed); seedFound = true; break; }
        }
        check(seedFound, "Deterministic human draw can be exercised through the real request path");
        NativeOriginService.choose(player, new NativeOriginChoosePacket(nonce, NativeOriginCatalog.randomRequest(0), false, false));
        check(!NativeOriginService.pending(player) && !NativeOriginService.isNative(player)
                && ProcessTransfur.getPlayerTransfurVariant(player) == null && player.isAlive(), "Full random can safely commit human after a previous native choice");
    }
    static void identity(ServerPlayer player, boolean nativeOrigin) {
        var identity = new NativeOriginData.Identity(); identity.state = nativeOrigin ? "native" : "human";
        if (nativeOrigin) {
            identity.form = identity.current = ChangedTransfurVariants.DARK_LATEX_WOLF_MALE.getId().toString(); identity.faction = "dark";
        }
        var data = NativeOriginData.get(player.server); data.players.put(player.getUUID(), identity); data.setDirty();
    }
    @SuppressWarnings({"unchecked","rawtypes"})
    private static void humanInstinctControl(ServerPlayer human) throws Exception {
        var setting=ChangedSynergyConfig.COMMON.allowHumanOriginInstinct;boolean previous=setting.get();
        var controller=Class.forName("com.lugert.true_transfur.transfur.TransfurController");
        Class mode=Class.forName("com.lugert.true_transfur.transfur.ControlMode");
        Object instinct=Enum.valueOf(mode,"INSTINCT");
        var change=controller.getMethod("switchControlMode",ServerPlayer.class,mode);
        var phase=net.minecraftforge.event.TickEvent.Phase.START;
        try {
            check(!setting.get()&&!NativeOriginPolicy.allowsInstinct(human),"Human Instinct defaults off independently of permanent identity");
            setting.set(true);ProcessTransfur.setPlayerTransfurVariant(human,ChangedTransfurVariants.DARK_LATEX_WOLF_MALE.get(),null,1,false);
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(new net.minecraftforge.event.TickEvent.PlayerTickEvent(phase,human));
            var command=Class.forName("com.lugert.true_transfur.transfur.TransfurEvents").getDeclaredMethod("setMode",ServerPlayer.class,mode);command.setAccessible(true);
            check((int)command.invoke(null,human,instinct)==1,"Production TT command permits configured human-origin Instinct");
            var body=(ChangedEntity)TrueTransfurCompat.interactionBody(human);body.setNoAi(true);
            for(int i=0;i<20;i++) {
                net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(new net.minecraftforge.event.TickEvent.PlayerTickEvent(phase,human));
                net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(new net.minecraftforge.event.TickEvent.PlayerTickEvent(net.minecraftforge.event.TickEvent.Phase.END,human));
            }
            check(TrueTransfurCompat.interactionBody(human)==body&&TrueTransfurCompat.isInstinctShell(human),"Human control survives production TT ticks");
            check(!(Boolean)controller.getMethod("isTrueTransfurActive",ServerPlayer.class).invoke(null,human)
                    &&!(Boolean)controller.getMethod("isChangedTransfurred",ServerPlayer.class).invoke(null,human)
                    &&!TrueTransfurCompat.blocksReversal(human)&&!net.parkabird.changedsynergy.ai.NativeRoleplayService.active(human),
                    "Human control does not enable permanent identity, lethal reversal, neutrality or native roleplay");
            var reborn=Class.forName("com.lugert.true_transfur.transfur.RebornHandler");
            check(!(Boolean)reborn.getMethod("interceptDeath",LivingDeathEvent.class,ServerPlayer.class)
                    .invoke(null,new LivingDeathEvent(human,human.damageSources().generic()),human),"Human control does not enable rebirth");
            human.connection.connection.setListener(human.connection);
            var context=net.minecraftforge.network.NetworkEvent.Context.class;
            var ctor=context.getDeclaredConstructor(Connection.class,net.minecraftforge.network.NetworkDirection.class,int.class);ctor.setAccessible(true);
            var network=ctor.newInstance(human.connection.connection,net.minecraftforge.network.NetworkDirection.PLAY_TO_SERVER,0);
            var toggle=Class.forName("com.lugert.true_transfur.network.ToggleControlModePacket").getDeclaredMethod("lambda$handle$0",context);toggle.setAccessible(true);
            toggle.invoke(null,network);
            check(!TrueTransfurCompat.isInstinctShell(human)&&body.isRemoved()&&!human.noPhysics,"Production TT key packet returns human to PLAYER");
            toggle.invoke(null,network);
            check(TrueTransfurCompat.isInstinctShell(human),"Production TT key packet re-enters human Instinct");
            setting.set(false);net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(new net.minecraftforge.event.TickEvent.PlayerTickEvent(phase,human));
            check(!TrueTransfurCompat.isInstinctShell(human)&&!human.noPhysics&&ProcessTransfur.isPlayerTransfurred(human),"Disabling human control preserves Changed form and clears shell");
            setting.set(true);net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(new net.minecraftforge.event.TickEvent.PlayerTickEvent(phase,human));
            check((Boolean)change.invoke(null,human,instinct),"Human control can be enabled again without native identity");
            ProcessTransfur.removePlayerTransfurVariant(human);
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(new net.minecraftforge.event.TickEvent.PlayerTickEvent(phase,human));
            check(human.isAlive()&&!ProcessTransfur.isPlayerTransfurred(human)&&!human.noPhysics&&!TrueTransfurCompat.isInstinctShell(human),"Production human reversal remains safe during Instinct");
        } finally {TrueTransfurCompat.ensurePlayerControl(human);setting.set(previous);}
    }
    @SuppressWarnings("unchecked")
    static ServerPlayer player(MinecraftServer server, String name) throws Exception {
        var player = new ServerPlayer(server, server.overworld(), new GameProfile(UUID.randomUUID(), name));
        player.connection = new ServerGamePacketListenerImpl(server, new Connection(PacketFlow.SERVERBOUND), player);
        // Generic types survive production field obfuscation, unlike a Mojmap field-name string.
        boolean mapped = false;
        for (Field field : net.minecraft.server.players.PlayerList.class.getDeclaredFields()) {
            String type = field.getGenericType().getTypeName();
            if (Map.class.isAssignableFrom(field.getType()) && type.contains("UUID") && type.contains("ServerPlayer")) {
                field.setAccessible(true); ((Map<UUID, ServerPlayer>)field.get(server.getPlayerList())).put(player.getUUID(), player); mapped = true;
            }
        }
        if (!mapped) throw new AssertionError("Missing production player identity map");
        onlinePlayers(server).add(player); server.overworld().addNewPlayer(player);
        return player;
    }
    @SuppressWarnings("unchecked")
    private static List<ServerPlayer> onlinePlayers(MinecraftServer server) throws Exception {
        for (Field field : net.minecraft.server.players.PlayerList.class.getDeclaredFields()) {
            if (List.class.isAssignableFrom(field.getType()) && field.getGenericType().getTypeName().contains("ServerPlayer")) {
                field.setAccessible(true); return (List<ServerPlayer>)field.get(server.getPlayerList());
            }
        }
        throw new AssertionError("Missing production online player list");
    }
}
