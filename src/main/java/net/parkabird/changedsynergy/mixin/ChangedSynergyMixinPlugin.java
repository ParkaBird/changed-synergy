package net.parkabird.changedsynergy.mixin;

import java.util.List;
import java.util.Set;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/** Optional compatibility repairs that need the final, merged method names. */
public final class ChangedSynergyMixinPlugin implements IMixinConfigPlugin {
    @Override public void onLoad(String mixinPackage) {}
    @Override public String getRefMapperConfig() { return null; }
    @Override public boolean shouldApplyMixin(String target, String mixin) {
        return true;
    }
    @Override public void acceptTargets(Set<String> mine, Set<String> others) {}
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String target, ClassNode node, String mixin, IMixinInfo info) {}

    @Override
    public void postApply(String target, ClassNode node, String mixin, IMixinInfo info) {
        boolean jeiInterface = mixin.endsWith(".JeiMouseOverInterfaceMixin");
        if (jeiInterface || mixin.endsWith(".JeiMouseOverMappingMixin")) {
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
}
