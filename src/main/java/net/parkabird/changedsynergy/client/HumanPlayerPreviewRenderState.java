package net.parkabird.changedsynergy.client;

/** Limits the human-skin render override to the player model in negotiation UI. */
public final class HumanPlayerPreviewRenderState {
    private static final ThreadLocal<Boolean> ACTIVE =
            ThreadLocal.withInitial(() -> false);

    private HumanPlayerPreviewRenderState() {
    }

    public static boolean isActive() {
        return ACTIVE.get();
    }

    public static void render(Runnable render) {
        boolean previous = ACTIVE.get();
        ACTIVE.set(true);
        try {
            render.run();
        } finally {
            ACTIVE.set(previous);
        }
    }
}
