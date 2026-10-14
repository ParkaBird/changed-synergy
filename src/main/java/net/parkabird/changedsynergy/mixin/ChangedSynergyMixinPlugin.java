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
import org.objectweb.asm.tree.VarInsnNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/** Bounds Addon's optional key hint after its generated handler has been merged. */
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
