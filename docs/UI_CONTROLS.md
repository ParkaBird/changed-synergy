# Wheel controls and interface motion

Tap the ability-wheel key to open the existing click-driven menu. Hold it for about 150 ms to aim by mouse direction; release to select the highlighted ability. The center is a cancel zone. Esc cancels as well. Selecting an ability keeps Changed's normal activation rules and menu subflows.

Switching between ability and relationship wheels keeps the frame at full size. The same horizontal spring continues between the center and right-side anchors, preserving its position and velocity while the server opens the next menu. If the menu request fails, the current wheel becomes usable again.

Only the ability wheel uses hold/release selection. Scrolling past its first or last page switches to the relationship wheel, which stays open for clicking after release. Returning to the ability wheel requires a fresh key press before hold selection can resume. Paging clears the old highlight; move the mouse again before selecting. Releasing during paging or a wheel handoff cancels the choice.

Keep holding the wheel key to walk, jump, crouch or sprint while either player wheel is open. Scrolling between the two wheels does not require releasing the key, including when the hold starts in an already-open relationship wheel. Releasing there leaves the click-driven relationship menu open. Ordinary sticky menus keep their existing movement behavior.

Both held and ordinary click-driven wheels use a small spring-driven 3D tilt toward the cursor. The highlighted frame and icon move outward together by six GUI pixels and grow by 4.5%, without changing directional selection or click behavior. Tooltips and input directions stay stable. Relationship panels retain their dark background and text style, with soft, layered theme-colored shadows replacing hard outlines. Their shadows breathe subtly without expanding into adjacent panels. Their staggered slide/fade entrance uses a fixed layout anchor instead of the moving wheel position.

Unselected frames retain their original native artwork and color. Only selected frames fade into the brighter highlight, then fade back to the original appearance when the cursor leaves. Current-ability and follow-mode highlights retain their original meaning. The transition preserves opening/closing opacity and used-action dimming; Reduced UI Motion switches highlights immediately.

UI springs preserve momentum during rapid retargeting and settle on exact coordinates without a final pixel wobble. The client setting **Reduced UI Motion** (`UI MOTION.ReducedUiMotion`) removes prominent travel, tilt, breathing and stagger delays. Game timers, cooldowns, QTE judgment, actual progress values and creature animations are unchanged.

The primary and secondary ability HUDs use a slower slide of roughly half a second. Their activation highlight fades in and out, while the inactive appearance stays native. Primary and Addon secondary highlights have independent state and reset when the selected ability or form changes. Reduced UI Motion skips the highlight fade. Activity timeout, cooldown fills and charge progress still use the original timing.
