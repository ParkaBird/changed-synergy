# Changed: Synergy 1.1.5-Beta.2-hotfix.1

Player-visible fixes and quality-of-life improvements since **1.1.5-Beta.2**.

### Communities and Story NPCs

- **Reversible transformation rite reversal**: Players who joined a community through a voluntary reversible transfur rite can now speak with their Chief to request returning to human form. Confirming starts an authored Chief holding-reversal sequence matching the radial-wheel reversal flow. Leaving or being expelled from a community also automatically reverts any borrowed reversible camp form.
- **Chief play exclusion**: Community Chiefs (**Weaver**, **Matriarch**, **Archivist**, and **Pathfinder**) no longer offer or accept the **Play** action in the interaction wheel, and are completely excluded from autonomous peer play interactions with both players and other camp residents.
- **Stationary dialogue**: Story NPCs and Chiefs now stay properly rooted in place during active conversations instead of drifting or sliding away mid-dialogue.

### Commands

- **Admin telepathy management**: Added `/changedsynergy telepathy inspect <player>`, `/changedsynergy telepathy grant <player>`, and `/changedsynergy telepathy revoke <player>` (operator permission level 2) to inspect, grant, or revoke permanent latex telepathic network understanding.

### World Generation and Performance

- **Faster `/locate structure` searches**: Optimized terrain lookup for community structures with early biome pre-checks and sparser clearing sampling, and relaxed strict slope limits on the **Forest Treehouse** and **Hyena's Hideout** (supported by existing foundation fill). This resolves severe server-thread freezes and long search stalls when using `/locate` to find community landmarks.

Servers and clients should both update to **1.1.5-Beta.2-hotfix.1**.
