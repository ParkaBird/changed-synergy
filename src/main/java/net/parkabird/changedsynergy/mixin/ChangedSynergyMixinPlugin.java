package net.parkabird.changedsynergy.mixin;

import java.util.List;
import java.util.Set;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import net.minecraftforge.fml.loading.FMLLoader;

/** Optional compatibility repairs that need the final, merged method names. */
public final class ChangedSynergyMixinPlugin implements IMixinConfigPlugin {
    private static volatile Boolean cachedJeiCanonicalMouseSam;

    @Override public void onLoad(String mixinPackage) {}
    @Override public String getRefMapperConfig() { return null; }
    @Override public boolean shouldApplyMixin(String target, String mixin) {
        if (target == null) return true;
        if (target.startsWith("net.foxyas.changedaddon.")) return isModLoaded("changed_addon");
        if (target.startsWith("top.theillusivec4.curios.")) return isModLoaded("curios");
        if (target.startsWith("com.github.alexthe668.domesticationinnovation.")) return isModLoaded("domesticationinnovation");
        if (target.startsWith("com.katt.changedextras.")) return isModLoaded("changedextras");
        if (target.startsWith("com.lugert.true_transfur.")) return isModLoaded("true_transfur");
        if (target.startsWith("net.stonenibbler.changed_survive_protocol.")) return isModLoaded("changed_survive_protocol");
        if (target.startsWith("mezz.jei.")) return isModLoaded("jei");
        if (target.startsWith("net.bettercombat.")) return isModLoaded("bettercombat");
        if (target.startsWith("com.raiiiden.taczadditions.")) {
            return isModLoaded("taczadditions")
                    || ChangedSynergyMixinPlugin.class.getClassLoader()
                            .getResource(target.replace('.', '/') + ".class") != null;
        }
        return true;
    }

    private static boolean isModLoaded(String modId) {
        var loading = FMLLoader.getLoadingModList();
        return loading != null && loading.getModFileById(modId) != null;
    }
    @Override public void acceptTargets(Set<String> mine, Set<String> others) {}
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String target, ClassNode node, String mixin, IMixinInfo info) {}

    @Override
    public void postApply(String target, ClassNode node, String mixin, IMixinInfo info) {
        if (mixin.endsWith(".AbilityHudSpringMixin")) {
            for (var method : node.methods) for (var instruction : method.instructions.toArray()) {
                if (instruction instanceof MethodInsnNode call && call.getOpcode() == Opcodes.INVOKESTATIC
                        && call.owner.equals("net/ltxprogrammer/changed/util/Transition")
                        && call.name.equals("easeInOutSine") && call.desc.equals("(F)F")) {
                    boolean secondary = !method.name.equals("lambda$renderSelectedAbility$0");
                    method.instructions.insertBefore(call, new InsnNode(secondary ? Opcodes.ICONST_1 : Opcodes.ICONST_0));
                    call.owner = "net/parkabird/changedsynergy/client/AbilityHudMotion";
                    call.name = "slide"; call.desc = "(FZ)F";
                    method.maxStack++;
                } else if (instruction instanceof MethodInsnNode call && call.getOpcode() == Opcodes.INVOKESTATIC
                        && call.owner.equals("net/ltxprogrammer/changed/client/gui/AbilityOverlay")
                        && call.name.equals("renderBackground")) {
                    boolean secondary = !method.name.equals("lambda$renderSelectedAbility$0");
                    method.instructions.insertBefore(call, new InsnNode(secondary ? Opcodes.ICONST_1 : Opcodes.ICONST_0));
                    call.owner = "net/parkabird/changedsynergy/client/AbilityHudMotion";
                    call.desc = call.desc.replace(")V", "Z)V");
                    method.maxStack++;
                }
            }
            return;
        }
        boolean jeiInterface = mixin.endsWith(".JeiMouseOverInterfaceMixin");
        if (jeiInterface || mixin.endsWith(".JeiMouseOverMappingMixin")) {
            // Lambda classes are generated after Mixin. Their factory's SAM name
            // must name the abstract method, not the SRG default alias.
            if (!jeiInterface && jeiHasCanonicalMouseSam()) for (var method : node.methods) for (var instruction : method.instructions) {
                if (instruction instanceof InvokeDynamicInsnNode factory
                        && factory.name.equals("m_5953_")
                        && factory.bsm.getOwner().equals("java/lang/invoke/LambdaMetafactory")
                        && factory.desc.endsWith(")Lmezz/jei/common/input/IMouseOverable;")
                        && factory.bsmArgs.length > 0 && factory.bsmArgs[0] instanceof Type sam
                        && sam.getDescriptor().equals("(DD)Z")) factory.name = "isMouseOver";
            }
            // JEI 221 mixes both names in its implementations AND call sites.
            // Concrete aliases serve rendering; the interface alias serves input.
            String alias = jeiInterface ? "m_5953_" : "isMouseOver";
            String original = jeiInterface ? "isMouseOver" : "m_5953_";
            if (node.methods.stream().anyMatch(method -> method.name.equals(alias)
                    && method.desc.equals("(DD)Z"))) return;
            if (node.methods.stream().noneMatch(method -> method.name.equals(original)
                    && method.desc.equals("(DD)Z")
                    && (method.access & (Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC)) == Opcodes.ACC_PUBLIC
                    && (jeiInterface || (method.access & Opcodes.ACC_ABSTRACT) == 0))) return;
            MethodNode bridge = new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_SYNTHETIC,
                    alias, "(DD)Z", null, null);
            bridge.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
            bridge.instructions.add(new VarInsnNode(Opcodes.DLOAD, 1));
            bridge.instructions.add(new VarInsnNode(Opcodes.DLOAD, 3));
            bridge.instructions.add(new MethodInsnNode(
                    jeiInterface ? Opcodes.INVOKEINTERFACE : Opcodes.INVOKEVIRTUAL,
                    node.name, original, "(DD)Z", jeiInterface));
            bridge.instructions.add(new InsnNode(Opcodes.IRETURN));
            bridge.maxStack = 5;
            bridge.maxLocals = 5;
            node.methods.add(bridge);
            return;
        }
        if (!mixin.endsWith(".GrabQteSyncMixin")) return;
        for (var method : node.methods) {
            if (!method.name.endsWith("$tickSendKeyBindInfo")
                    || !method.desc.equals("(Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V")) continue;
            // Mixin's wildcard/regex selectors omit merged injector methods.
            // Guard only this optional hint, never tickIdle or native escape handling.
            InsnList guard = new InsnList();
            LabelNode done = new LabelNode();
            guard.add(new VarInsnNode(Opcodes.ALOAD, 0));
            guard.add(new FieldInsnNode(Opcodes.GETFIELD, node.name, "currentEscapeKey",
                    "Lnet/ltxprogrammer/changed/ability/AbstractAbilityInstance$KeyReference;"));
            guard.add(new JumpInsnNode(Opcodes.IFNULL, done));
            guard.add(new VarInsnNode(Opcodes.ALOAD, 0));
            guard.add(new FieldInsnNode(Opcodes.GETFIELD, node.name, "grabbedEntity",
                    "Lnet/minecraft/world/entity/LivingEntity;"));
            LabelNode present = new LabelNode();
            guard.add(new JumpInsnNode(Opcodes.IFNONNULL, present));
            guard.add(done);
            guard.add(new InsnNode(Opcodes.RETURN));
            guard.add(present);
            method.instructions.insert(guard);
        }
    }

    private static boolean jeiHasCanonicalMouseSam() {
        Boolean cached = cachedJeiCanonicalMouseSam;
        if (cached != null) return cached;
        boolean resolved = false;
        try (var bytes = ChangedSynergyMixinPlugin.class.getClassLoader()
                .getResourceAsStream("mezz/jei/common/input/IMouseOverable.class")) {
            if (bytes != null) {
                ClassNode contract = new ClassNode();
                new ClassReader(bytes).accept(contract, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                resolved = contract.methods.stream()
                        .anyMatch(method -> method.name.equals("isMouseOver") && method.desc.equals("(DD)Z")
                                && (method.access & Opcodes.ACC_ABSTRACT) != 0);
            }
        } catch (java.io.IOException unavailable) {
            resolved = false;
        }
        cachedJeiCanonicalMouseSam = resolved;
        return resolved;
    }
}
