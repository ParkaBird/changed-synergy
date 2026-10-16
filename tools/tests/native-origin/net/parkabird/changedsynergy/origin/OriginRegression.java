package net.parkabird.changedsynergy.origin;

import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraftforge.event.TickEvent;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.ltxprogrammer.changed.init.ChangedRegistry;
import net.ltxprogrammer.changed.process.ProcessTransfur;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.parkabird.changedsynergy.ChangedSynergyMod;
import net.parkabird.changedsynergy.ChangedSynergyConfig;
import net.parkabird.changedsynergy.ai.FactionReputation;
import net.parkabird.changedsynergy.ai.LatexSocialMemory;
import net.parkabird.changedsynergy.compat.TrueTransfurCompat;
import net.parkabird.changedsynergy.init.ChangedSynergyGameRules;

/** Real Forge/Changed/TrueTransfur assertions, isolated from ordinary builds. */
@Mod.EventBusSubscriber(modid = ChangedSynergyMod.MOD_ID)
public final class OriginRegression {
    private static OriginSpawnSearch birthSearch;
    private static java.util.ArrayDeque<NativeOriginCatalog.Entry> births = new java.util.ArrayDeque<>();
    private static NativeOriginCatalog.Entry birthEntry;
    private static long birthBegan;
    private static int birthIndex;
    private static final BlockPos REMOTE = new BlockPos(1_000_000, 64, 1_000_000);
    @SubscribeEvent public static void run(ServerStartedEvent event) {
        try {
            // Keep fixture mutations independent of the private server's previous config and file watcher.
            ChangedSynergyConfig.SPEC.setConfig(com.electronwill.nightconfig.core.CommentedConfig.inMemory());
            var server = event.getServer(); var level = server.overworld();
            var nativePlayer = new ServerPlayer(server, level, new GameProfile(UUID.randomUUID(), "NativeTest"));
            var human = new ServerPlayer(server, level, new GameProfile(UUID.randomUUID(), "HumanTest"));
            for (ServerPlayer player : new ServerPlayer[]{nativePlayer, human}) player.connection =
                    new ServerGamePacketListenerImpl(server, new Connection(PacketFlow.SERVERBOUND), player);
            var catalog = NativeOriginCatalog.entries(level.registryAccess());
            for (var biomeKey : java.util.List.of(
                    net.parkabird.changedsynergy.world.LatexTerritoryBiomes.DARK_LATEX_PLAINS,
                    net.parkabird.changedsynergy.world.LatexTerritoryBiomes.WHITE_LATEX_FOREST)) {
                var biome = level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.BIOME)
                        .getHolderOrThrow(biomeKey).value();
                var actual = new java.util.HashSet<String>();
                for (var category : net.minecraft.world.entity.MobCategory.values()) {
                    for (var spawn : biome.getMobSettings().getMobs(category).unwrap()) {
                        String id = net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getKey(spawn.type).toString();
                        actual.add(id);
                        System.out.println("TERRITORY_SPAWN_TABLE: " + biomeKey.location() + " " + category + " " + id
                                + " weight=" + spawn.getWeight().asInt() + " pack=" + spawn.minCount + "-" + spawn.maxCount);
                    }
                }
                boolean dark = biomeKey.equals(net.parkabird.changedsynergy.world.LatexTerritoryBiomes.DARK_LATEX_PLAINS);
                var expected = dark ? java.util.Set.of("changed:dark_latex_wolf_male", "changed:dark_latex_wolf_female",
                        "changed:dark_latex_wolf_pup", "changed:dark_dragon", "changed:dark_latex_yufeng", "changed:dark_latex_double_yufeng")
                        : java.util.Set.of("changed:pure_white_latex_wolf", "changed:pure_white_latex_wolf_pup",
                                "changed:latex_mutant_bloodcell_wolf", "changed:pure_white_latex_cerberus");
                check(actual.containsAll(expected), "Final biome modifiers retain every " + (dark ? "Dark" : "White") + " territory species");
                var latexTag = net.minecraft.tags.TagKey.<net.minecraft.world.entity.EntityType<?>>create(
                        net.minecraft.core.registries.Registries.ENTITY_TYPE, ResourceLocation.parse("changed:latexes"));
                check(actual.stream().map(id -> net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getValue(ResourceLocation.parse(id)))
                        .noneMatch(type -> type.is(latexTag) && !expected.contains(net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getKey(type).toString())),
                        "Unrelated latexes remain excluded from " + (dark ? "Dark" : "White") + " territory");
            }
            var disabled = ChangedSynergyConfig.COMMON.disabledOriginForms;
            var previousDisabled = disabled.get();
            try {
                check(disabled.get().isEmpty(), "No additional forms disabled by default");
                var console = server.createCommandSourceStack();
                var commands = server.getCommands();
                String first = catalog.get(0).form().toString(), second = catalog.get(1).form().toString();
                check(commands.performPrefixedCommand(console, "changedsynergy config set BEHAVIOUR.DisabledOriginForms "
                        + first + ", " + second) == 1, "Console accepts comma-separated full form IDs");
                check(disabled.get().size() == 2 && NativeOriginCatalog.find(level.registryAccess(), catalog.get(0).form()) == null
                        && NativeOriginCatalog.entries(level.registryAccess()).size() == catalog.size() - 2,
                        "Blocked original forms disappear from catalog and server choice lookup");
                check(commands.performPrefixedCommand(console,
                        "changedsynergy config set BEHAVIOUR.DisabledOriginForms invalid-id") == 0
                        && disabled.get().size() == 2, "Invalid IDs leave the existing denylist unchanged");
                check(commands.performPrefixedCommand(console,
                        "changedsynergy config set BEHAVIOUR.DisabledOriginForms []") == 1
                        && NativeOriginCatalog.entries(level.registryAccess()).equals(catalog), "Empty command list restores original choices");
                disabled.set(java.util.List.of("futuremod:form_missing"));
                check(NativeOriginCatalog.entries(level.registryAccess()).equals(catalog), "Unavailable mod IDs are harmless");
            } finally { disabled.set(previousDisabled); ChangedSynergyConfig.SPEC.save(); }
            check(!ChangedSynergyConfig.COMMON.includeModdedOriginSpecies.get()
                    && catalog.stream().allMatch(entry -> entry.form().getNamespace().equals("changed")), "Modded origins disabled by default");
            try {
                ChangedSynergyConfig.COMMON.includeModdedOriginSpecies.set(true);
                var expanded = NativeOriginCatalog.entries(level.registryAccess());
                check(expanded.stream().anyMatch(entry -> !entry.form().getNamespace().equals("changed")), "Enabling option includes actual Addon playable forms");
                var modded = expanded.stream().filter(form -> !form.form().getNamespace().equals("changed")).findFirst().orElseThrow();
                disabled.set(java.util.List.of(modded.form().toString()));
                check(NativeOriginCatalog.find(level.registryAccess(), modded.form()) == null, "Denylist also excludes other mods' forms");
                disabled.set(previousDisabled);
                check(expanded.stream().noneMatch(entry -> !entry.form().toString().equals("changed:form_dark_latex_wolf_partial")
                        && entry.form().getPath().matches(".*(partial|custom|benign|special|boss).*")),
                        "Expanded catalog still rejects boss and restricted forms");
                check(String.join(".", ChangedSynergyConfig.COMMON.includeModdedOriginSpecies.getPath()).equals("BEHAVIOUR.IncludeModdedOriginSpecies"),
                        "Expanded origins available through server config commands");
            } finally { ChangedSynergyConfig.COMMON.includeModdedOriginSpecies.set(false); disabled.set(previousDisabled); }
            check(catalog.size() > 65, "All original playable species included: " + catalog.size());
            check(catalog.stream().noneMatch(entry -> !entry.form().toString().equals("changed:form_dark_latex_wolf_partial")
                    && entry.form().getPath().matches(".*(partial|custom|benign|special).*")), "Exclusions");
            check(catalog.stream().anyMatch(entry -> entry.form().getPath().contains("pup")), "Juvenile forms retained");
            check(catalog.stream().anyMatch(entry -> entry.form().getPath().contains("fusion")), "Fusion forms retained");
            var phages = catalog.stream().filter(form -> {
                var id = net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getKey(form.variant().getEntityType());
                return id != null && id.getNamespace().equals("changed") && id.getPath().startsWith("phage_latex_wolf_");
            }).toList();
            check(phages.size() == 2 && phages.stream().allMatch(form -> form.faction().equals("dark")),
                    "Both Phage Wolf origins belong to Dark");
            for (var phage : phages) {
                var creature = phage.variant().getEntityType().create(level);
                check(net.parkabird.changedsynergy.ai.HunterFaction.of(creature)
                        == net.parkabird.changedsynergy.ai.HunterFaction.DARK
                        && FactionReputation.groupId(creature).equals("dark"), "Phage NPCs use Dark reputation");
            }
            var phagePlayer = new ServerPlayer(server, level, new GameProfile(UUID.randomUUID(), "PhageTest"));
            phagePlayer.connection = new ServerGamePacketListenerImpl(server, new Connection(PacketFlow.SERVERBOUND), phagePlayer);
            var oldPhage = new NativeOriginData.Identity();
            oldPhage.state = "native"; oldPhage.form = oldPhage.current = phages.get(0).form().toString();
            oldPhage.faction = "light"; oldPhage.region = "plains";
            NativeOriginData.get(server).players.put(phagePlayer.getUUID(), oldPhage);
            NativeOriginService.login(new net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent(phagePlayer));
            check(oldPhage.faction.equals("dark") && oldPhage.region.isEmpty()
                    && oldPhage.form.equals(oldPhage.current), "Existing native Phage citizenship corrected without changing form");
            var entry = NativeOriginCatalog.find(level.registryAccess(), ResourceLocation.parse("changed:form_dark_latex_wolf/male"));
            check(entry != null && entry.faction().equals("dark"), "Dark membership");
            var identity = new NativeOriginData.Identity(); identity.state = "native";
            identity.form = identity.current = entry.form().toString(); identity.faction = entry.faction(); identity.region = entry.region();
            var data = NativeOriginData.get(server); data.players.put(nativePlayer.getUUID(), identity);
            var humanIdentity = new NativeOriginData.Identity(); humanIdentity.state = "human";
            data.players.put(human.getUUID(), humanIdentity);
            level.getGameRules().getRule(ChangedSynergyGameRules.NATIVE_ORIGINS).set(true, server);
            NativeOriginService.restore(nativePlayer);
            var instance = ProcessTransfur.getPlayerTransfurVariant(nativePlayer);
            check(instance != null && instance.willSurviveTransfur && !instance.isTemporaryFromSuit(), "Native body initialized alive and permanent");
            ProcessTransfur.removePlayerTransfurVariant(nativePlayer);
            check(nativePlayer.isAlive() && ProcessTransfur.getPlayerTransfurVariant(nativePlayer) == instance, "Native reverse blocked before lethal backend");
            ChangedEntity mob = entry.variant().getEntityType().create(level);
            check(NativeOriginService.sameFaction(mob, nativePlayer), "Citizenship recognized");
            check(LatexSocialMemory.shouldRemainNeutral(mob, nativePlayer), "Unprovoked member neutral");
            FactionReputation.initializeOriginStanding(nativePlayer, NativeOriginService.account(identity));
            check(FactionReputation.score(mob, nativePlayer) == 20, "Recognition without alliance");
            check(NativeOriginData.load(data.save(new CompoundTag())).players.get(nativePlayer.getUUID()).form.equals(identity.form), "World UUID identity round trip");
            var clone = new ServerPlayer(server, level, nativePlayer.getGameProfile());
            check(NativeOriginService.isNative(clone), "Identity independent of player instance/death clone");
            check(server.getCommands().getDispatcher().getRoot().getChild("gamerule")
                    .getChild("changedSynergyNativeOrigins") != null, "Origin gamerule registered in vanilla command tree");
            check(NativeOriginService.reopen(nativePlayer), "Operator can reopen a committed native origin");
            check(nativePlayer.hasEffect(net.minecraft.world.effect.MobEffects.INVISIBILITY),
                    "Selection grants temporary invisibility");
            var zombie = net.minecraft.world.entity.EntityType.ZOMBIE.create(level);
            zombie.setTarget(nativePlayer);
            check(zombie.getTarget() == null, "Mobs cannot acquire a selecting player as their target");
            check(NativeOriginService.isNative(nativePlayer), "Opening optional selection retains the committed identity");
            NativeOriginService.choose(nativePlayer, new net.parkabird.changedsynergy.network.NativeOriginChoosePacket(
                    selectionNonce(nativePlayer), null, false, true));
            check(!NativeOriginService.pending(nativePlayer) && NativeOriginService.identity(nativePlayer) == identity
                    && ProcessTransfur.getPlayerTransfurVariant(nativePlayer) == instance && nativePlayer.isAlive(),
                    "Optional cancellation preserves origin, body and life");
            check(!nativePlayer.hasEffect(net.minecraft.world.effect.MobEffects.INVISIBILITY),
                    "Cancellation removes only selection invisibility");
            nativePlayer.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                    net.minecraft.world.effect.MobEffects.INVISIBILITY, 600));
            var existingInvisibility = nativePlayer.getEffect(net.minecraft.world.effect.MobEffects.INVISIBILITY);
            check(NativeOriginService.reopen(nativePlayer), "Operator can reopen after cancellation");
            NativeOriginService.choose(nativePlayer, new net.parkabird.changedsynergy.network.NativeOriginChoosePacket(UUID.randomUUID(), null, false, false));
            check(NativeOriginService.pending(nativePlayer) && ProcessTransfur.getPlayerTransfurVariant(nativePlayer) == instance,
                    "Wrong nonce cannot change an existing body");
            NativeOriginService.choose(nativePlayer, new net.parkabird.changedsynergy.network.NativeOriginChoosePacket(
                    selectionNonce(nativePlayer), null, false, false));
            var resetIdentity = NativeOriginService.identity(nativePlayer);
            check(!NativeOriginService.pending(nativePlayer) && resetIdentity.state.equals("human") && resetIdentity.form.isEmpty()
                    && resetIdentity.faction.isEmpty() && ProcessTransfur.getPlayerTransfurVariant(nativePlayer) == null && nativePlayer.isAlive(),
                    "Confirming human removes the native body and citizenship without lethal reversal");
            check(nativePlayer.getEffect(net.minecraft.world.effect.MobEffects.INVISIBILITY) == existingInvisibility,
                    "Pre-existing invisibility survives selection and confirmation");
            nativePlayer.removeEffect(net.minecraft.world.effect.MobEffects.INVISIBILITY);
            data.players.put(nativePlayer.getUUID(), identity); NativeOriginService.restore(nativePlayer);
            NativeOriginService.open(human);
            NativeOriginService.choose(human, new net.parkabird.changedsynergy.network.NativeOriginChoosePacket(selectionNonce(human), null, false, true));
            check(NativeOriginService.pending(human), "Required first selection cannot be cancelled by a forged request");
            NativeOriginService.choose(human, new net.parkabird.changedsynergy.network.NativeOriginChoosePacket(selectionNonce(human), null, false, false));
            check(!human.hasEffect(net.minecraft.world.effect.MobEffects.INVISIBILITY),
                    "Required human confirmation also restores visibility");
            NativeOriginService.open(human);
            NativeOriginService.logout(new net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent(human));
            check(!NativeOriginService.pending(human) && !human.hasEffect(net.minecraft.world.effect.MobEffects.INVISIBILITY),
                    "Logout clears temporary selection invisibility");
            var secondary = ChangedSynergyConfig.COMMON.allowSecondaryTransfur;
            boolean oldSecondary = secondary.get();
            try {
                secondary.set(true);
                LatexSocialMemory.markProvoked(mob, nativePlayer);
                check(!LatexSocialMemory.mayInitiateHostileGrab(mob, nativePlayer), "Provoked native cannot enter secondary grab");
                level.getGameRules().getRule(ChangedSynergyGameRules.NATIVE_ORIGINS).set(false, server);
                check(!LatexSocialMemory.allowsHostilePlayerTransfur(clone), "Native protection survives disabled origin rule and body restoration gap");
                level.getGameRules().getRule(ChangedSynergyGameRules.NATIVE_ORIGINS).set(true, server);
                check(LatexSocialMemory.allowsHostilePlayerTransfur(human), "Human first transfur allowed");
                ProcessTransfur.setPlayerTransfurVariant(human, entry.variant(), null, 1, false);
                LatexSocialMemory.markProvoked(mob, human);
                check(LatexSocialMemory.mayInitiateHostileGrab(mob, human), "Ordinary transformed enemy eligible while enabled");
                secondary.set(false);
                check(!LatexSocialMemory.mayInitiateHostileGrab(mob, human), "Disabling option rejects existing transformed enemy immediately");
                check(!mob.tryTransfurTarget(nativePlayer) && !mob.tryTransfurTarget(human), "Blocked NPC conversion falls back to melee");
                ProcessTransfur.removePlayerTransfurVariant(human);
                check(LatexSocialMemory.allowsHostilePlayerTransfur(human), "Secondary toggle does not disable first transfur");
                check(String.join(".", secondary.getPath()).equals("BEHAVIOUR.AllowSecondaryTransfur"), "Server config command path");
            } finally {
                secondary.set(oldSecondary);
            }
            check(!NativeOriginPolicy.allows(human) && NativeOriginPolicy.allows(nativePlayer), "Selective backend policy");
            if (TrueTransfurCompat.supportsNativeOrigin()) {
                var controller = Class.forName("com.lugert.true_transfur.transfur.TransfurController");
                var cap = (Capability)Class.forName("com.lugert.true_transfur.transfur.TransfurCapabilities").getField("TRANSFUR_DATA").get(null);
                for (ServerPlayer player : new ServerPlayer[]{nativePlayer, human}) {
                    Object flag = player.getCapability(cap).resolve().orElseThrow();
                    flag.getClass().getMethod("setTransfurred", boolean.class).invoke(flag, true);
                }
                ProcessTransfur.setPlayerTransfurVariant(human, entry.variant(), null, 1, false);
                check(!(Boolean)controller.getMethod("isTrueTransfurActive", ServerPlayer.class).invoke(null, human), "Actual TrueTransfur active flag gated");
                check(!(Boolean)controller.getMethod("isChangedTransfurred", ServerPlayer.class).invoke(null, human), "Actual TrueTransfur neutrality/initialization gated");
                ProcessTransfur.removePlayerTransfurVariant(human);
                check(human.isAlive() && ProcessTransfur.getPlayerTransfurVariant(human) == null, "Human reverse remains safe with TT installed");
                level.getGameRules().getRule(ChangedSynergyGameRules.NATIVE_ORIGINS).set(false, server);
                check((Boolean)controller.getMethod("isTrueTransfurActive", ServerPlayer.class).invoke(null, human), "Gamerule off restores backend default");
            }
            level.getGameRules().getRule(ChangedSynergyGameRules.NATIVE_ORIGINS).set(false, server);
            check(NativeOriginPolicy.allows(human), "Disabled rule allows default backend for every origin");
            level.getGameRules().getRule(ChangedSynergyGameRules.NATIVE_ORIGINS).set(true, server);
            var buf = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
            var packet = new net.parkabird.changedsynergy.network.NativeOriginStatePacket(0, UUID.randomUUID(), true, true,
                    net.minecraft.network.chat.Component.literal("Test"), catalog);
            net.parkabird.changedsynergy.network.NativeOriginStatePacket.encode(packet, buf);
            check(net.parkabird.changedsynergy.network.NativeOriginStatePacket.decode(buf).equals(packet), "Catalog packet round trip"); buf.release();
            var largeCatalog = java.util.stream.IntStream.range(0, 300).mapToObj(i -> catalog.get(i % catalog.size())).toList();
            var largePacket = new net.parkabird.changedsynergy.network.NativeOriginStatePacket(0, UUID.randomUUID(), true, true,
                    net.minecraft.network.chat.Component.empty(), largeCatalog);
            var largeBuf = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
            try {
                net.parkabird.changedsynergy.network.NativeOriginStatePacket.encode(largePacket, largeBuf);
                check(net.parkabird.changedsynergy.network.NativeOriginStatePacket.decode(largeBuf).equals(largePacket),
                        "Expanded modded catalog above 256 records is transmitted intact");
            } finally { largeBuf.release(); }
            var invalidCatalogBuf = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
            try {
                invalidCatalogBuf.writeVarInt(0); invalidCatalogBuf.writeUUID(UUID.randomUUID());
                invalidCatalogBuf.writeBoolean(true); invalidCatalogBuf.writeBoolean(true);
                invalidCatalogBuf.writeComponent(net.minecraft.network.chat.Component.empty());
                invalidCatalogBuf.writeVarInt(net.parkabird.changedsynergy.network.NativeOriginStatePacket.MAX_ENTRIES + 1);
                boolean rejected = false;
                try { net.parkabird.changedsynergy.network.NativeOriginStatePacket.decode(invalidCatalogBuf); }
                catch (IllegalArgumentException expected) { rejected = true; }
                check(rejected, "Excessive catalog count is rejected before allocating or reading entries");
            } finally { invalidCatalogBuf.release(); }
            var choice = new net.parkabird.changedsynergy.network.NativeOriginChoosePacket(UUID.randomUUID(), null, false, true);
            var choiceBuf = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
            net.parkabird.changedsynergy.network.NativeOriginChoosePacket.encode(choice, choiceBuf);
            check(net.parkabird.changedsynergy.network.NativeOriginChoosePacket.decode(choiceBuf).equals(choice), "Cancel request packet round trip"); choiceBuf.release();
            check(!level.hasChunkAt(REMOTE), "Birth search starts in an unloaded remote region");
            var locatingField = OriginSpawnSearch.class.getDeclaredField("locating"); locatingField.setAccessible(true);
            var stalledSearch = new OriginSpawnSearch(level, entry, false, REMOTE);
            locatingField.set(stalledSearch, new java.util.concurrent.CompletableFuture<BlockPos>());
            for (int tick = 0; tick <= 1200; tick++) stalledSearch.tick();
            check(stalledSearch.finished && stalledSearch.result == null && !level.hasChunkAt(REMOTE),
                    "Stalled background locator expires without generating terrain or blocking later queued players");
            var failedSearch = new OriginSpawnSearch(level, entry, false, REMOTE);
            var failedLocator = new java.util.concurrent.CompletableFuture<BlockPos>();
            failedLocator.completeExceptionally(new IllegalStateException("Unavailable biome source"));
            locatingField.set(failedSearch, failedLocator); failedSearch.tick();
            check(failedSearch.finished && failedSearch.result == null,
                    "Exceptional noise locator produces safe no-habitat result instead of a server-tick failure");
            var cancelledSearch = new OriginSpawnSearch(level, entry, false, REMOTE);
            cancelledSearch.cancel(); cancelledSearch.tick();
            check(cancelledSearch.finished && locatingField.get(cancelledSearch) == null,
                    "Cancelled selection never starts a new background locator");
            var unloadedProbe = new OriginSpawnSearch(level, entry, true, REMOTE);
            var candidate = OriginSpawnSearch.class.getDeclaredField("candidate"); candidate.setAccessible(true);
            candidate.set(unloadedProbe, REMOTE);
            var inspect = OriginSpawnSearch.class.getDeclaredMethod("inspect"); inspect.setAccessible(true);
            inspect.invoke(unloadedProbe);
            check(unloadedProbe.result == null && !level.hasChunkAt(REMOTE)
                    && !level.hasChunkAt(REMOTE.offset(-3, 0, -3)),
                    "Unloaded terrain and adjacent edge probes never generate chunks synchronously");
            births.add(entry);
            births.add(catalog.stream().filter(form -> form.faction().equals("white")).findFirst().orElseThrow());
            birthBegan = System.nanoTime();
            System.out.println("NATIVE_ORIGIN_REGRESSION_PASS: " + catalog.size() + " forms; identity, kinship, reversal, backend scope and packets");
        } catch (Throwable failure) {
            System.err.println("NATIVE_ORIGIN_REGRESSION_FAIL"); failure.printStackTrace();
            event.getServer().halt(false); throw new RuntimeException(failure);
        }
    }
    @SubscribeEvent public static void births(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || birthBegan == 0) return;
        try {
            if (System.nanoTime() - birthBegan > 180_000_000_000L) throw new AssertionError("Remote territory search timed out");
            var level = event.getServer().overworld();
            if (birthSearch == null) {
                birthEntry = births.poll();
                if (birthEntry == null) {
                    System.out.println("NATIVE_ORIGIN_BIRTH_REGRESSION_PASS: " + birthIndex + " remote unloaded-territory births");
                    birthBegan = 0; event.getServer().halt(false); return;
                }
                birthSearch = new OriginSpawnSearch(level, birthEntry, false, REMOTE);
            }
            birthSearch.tick();
            if (!birthSearch.finished) return;
            if (birthSearch.result == null) {
                for (String name : java.util.List.of("probes", "loads", "habitat", "candidate")) {
                    var field = OriginSpawnSearch.class.getDeclaredField(name); field.setAccessible(true);
                    System.err.println("BIRTH_SEARCH_DIAGNOSTIC: " + name + "=" + field.get(birthSearch));
                }
                System.err.println("BIRTH_SEARCH_BIOMES: " + level.getChunkSource().getGenerator().getBiomeSource().possibleBiomes().stream()
                        .map(biome -> biome.unwrapKey().orElseThrow().location()).toList());
                var field = OriginSpawnSearch.class.getDeclaredField("habitat"); field.setAccessible(true);
                var habitat = (BlockPos)field.get(birthSearch);
                if (habitat != null) System.err.println("BIRTH_SEARCH_EXACT_BIOME: " + level.getChunkSource().getGenerator().getBiomeSource()
                        .getNoiseBiome(habitat.getX() >> 2, habitat.getY() >> 2, habitat.getZ() >> 2, level.getChunkSource().randomState().sampler())
                        .unwrapKey().orElseThrow().location());
            }
            check(birthSearch.result != null, "Remote birth found for " + birthEntry.faction());
            var biome = level.getBiome(BlockPos.containing(birthSearch.result)).unwrapKey().orElseThrow().location();
            check(biome.getPath().equals(birthEntry.faction().equals("dark") ? "dark_latex_plains" : "white_latex_forest"),
                    "Loaded birthplace remains inside actual " + birthEntry.faction() + " territory: " + biome);
            check(OriginSpawnSearch.safe(level, birthEntry.variant(), birthSearch.result, false, null), "Remote body fits a safe birthplace");
            birthIndex++; birthSearch = null;
        } catch (Throwable failure) {
            System.err.println("NATIVE_ORIGIN_BIRTH_REGRESSION_FAIL"); failure.printStackTrace();
            birthBegan = 0; event.getServer().halt(false); throw new RuntimeException(failure);
        }
    }
    private static UUID selectionNonce(ServerPlayer player) throws ReflectiveOperationException {
        var selecting = NativeOriginService.class.getDeclaredField("SELECTING"); selecting.setAccessible(true);
        Object selection = ((java.util.Map<?, ?>)selecting.get(null)).get(player.getUUID());
        var nonce = selection.getClass().getDeclaredField("nonce"); nonce.setAccessible(true);
        return (UUID)nonce.get(selection);
    }
    private static void check(boolean value, String description) {
        if (!value) throw new AssertionError(description);
        System.out.println("ORIGIN_PASS: " + description);
    }
}
