# Future community design memo

This document records design ideas for development after the current beta.
It is not a promise that every idea will ship, and it must not be used as a
list of features that are already available.

## Release boundary

The first stable release remains focused on bug fixes, save safety,
multiplayer reliability, performance and compatibility. Large new community
mechanics belong to a later beta cycle.

The dedicated Changed Vanilla compatibility pass is now in progress. Changed
Vanilla is an official Changed addon and reuses much of Changed's assimilation
system, so the stable release supports it deliberately instead of merely
depending on incidental compatibility.

The compatibility pass should verify that:

- Changed Vanilla conversions are not replaced by Synergy's ordinary-mob
  absorption behavior;
- the configuration that permits undead and other ordinary mobs to be
  transfurred remains compatible with Changed Vanilla's converted variants;
- naturally spawned and converted creatures receive valid Synergy identities,
  social profiles, factions and community membership;
- conversion preserves entity continuity where appropriate and cannot
  duplicate or erase relationship data;
- behavior remains correct on dedicated servers and with multiple players;
- missing or unsupported Changed Vanilla versions fail safely without making
  it a required dependency.

## Existing foundation

Mixed-species communities already exist. Community membership is based on
faction, regional group, facility section and proximity rather than exact
species. For example, compatible latex deer and latex squirrels in the same
forest region can share a community, settlement and supplies.

Bonded companions and nearby friendly creatures can help during combat, but
there is no complete squad-recruitment or war-band command system yet.

## Species relationships inside a community

Give species recognizable social tendencies without making every individual
behave identically.

Possible interactions include:

- playful scuffles over food, useful resources or a human's attention;
- species-specific greetings, games, comfort behavior and personal-space
  preferences;
- cooperative work between species with complementary abilities;
- mild rivalry that uses emotes, positioning and short animations rather than
  real damage;
- individual personality and existing relationships overriding a species'
  usual tendency.

Species compatibility should normally be a preference score, not an absolute
blacklist. Highly compatible species would form mixed communities readily.
Wary combinations would need time, shared work or a common threat. Truly
incompatible combinations may form separate nearby communities unless an
explicit story or relationship state justifies cooperation.

## Relations between communities

Communities should eventually recognize one another as neighbors instead of
isolated records. Their relationship could range through unfamiliar, wary,
cooperative, allied and hostile states.

Relationship changes may consider:

- faction and species tendencies;
- competition for territory or resources;
- past attacks, theft and property damage;
- successful trade, mutual defense and player mediation;
- existing friendships or rivalries between members.

Hostility should not create constant uncontrolled combat. Communities need
cooldowns, disengagement rules, territorial limits and population safeguards.

## Trade between communities

Inter-community trade should use resources that were actually gathered and
delivered. Examples include a beach community exchanging fish or dried
seaweed with a forest community for apples, honey or wood.

Trade should:

- move real stock between community inventories;
- respect minimum food and construction reserves;
- depend on safe routes and an available carrier;
- pause or fail when the route becomes dangerous;
- improve trust only after a delivery succeeds;
- avoid generating rare items from random offer refreshes.

## Claiming existing structures

Suitable communities may claim and adapt existing structures, such as:

- a bee-related latex community occupying a hive area;
- an aquatic or otter community moving into a shipwreck;
- latex wolves occupying an illager outpost after its former owners are gone.

A claimed structure could become the community center, hold its real supply
cache and influence where members sleep, work and guard. Damaging the home or
taking protected supplies should count as trespassing, theft or vandalism when
the player knows the claim exists.

Access should depend on trust. A wary community may stop a visitor at the
entrance, while allies may enter and use designated shared spaces. Claims need
clear boundaries and must not take ownership of a player's established base by
accident.

## Player form and first impressions

An already-transfurred player's current form may affect a community's initial
trust. A community may be more comfortable with a familiar species and more
cautious around a traditional rival.

Form should only influence the starting impression. Conduct, reputation,
gifts, shared work and personal relationships must be able to overcome it.
Disguises and temporary forms need separate handling so they cannot provide a
permanent reputation exploit.

## Followers and war bands

A future leadership system may allow trusted or bonded creatures to travel and
fight as a group. It should grow out of relationships rather than turning every
friendly creature into a disposable soldier.

Potential constraints include:

- a small configurable active group limit;
- explicit follow, hold, retreat and defend commands;
- consent or a high relationship requirement for recruitment;
- morale and self-preservation so followers can disengage;
- server-side validation and pathfinding budgets;
- rules preventing allied communities from being emptied by one player;
- formation spacing so large bodies do not block one another.

Latexified illagers from Changed Vanilla could be a strong thematic fit for an
organized combat group, provided their upstream behavior and community
classification can be integrated safely.

## Bonded-companion restoration

Dark Latex bonded companions may eventually leave a unique broken mask when
they die. The mask would retain the companion's identity and bond data. The
player could repair it with Dark Latex crystals, wear it and voluntarily enter
a compatible transfur state to provide an anchor from which the companion can
reform.

The restored companion should remain the same individual, including its UUID
mapping, name, personality, memories and relationship. The process needs strict
server-side protections:

- only one valid relic may exist for a dead companion;
- the relic cannot be used while that companion is alive;
- copying inventory data must not duplicate the companion;
- only the bonded player may complete the restoration;
- the ritual must take place in a safe location;
- interrupted rituals must fail cleanly without consuming or duplicating the
  relic;
- the restored companion returns with limited health and temporary protection.

Other factions should use faction-specific relics and restoration rituals
rather than all dropping masks. Possible concepts include a condensed white
core, an aquatic memory pearl, a light-latex crystal or fur sample, an organic
cocoon, or a damaged experimental core. These should share one underlying
bond-relic system so save validation and anti-duplication rules remain
consistent.

## Creative references

The social direction of Synergy was partly inspired by Changed fan fiction:

- *Changed - Awakening* influenced friendly latex characters, reversible
  transfur and telepathic communication;
- *Passage Protocol* influenced non-permanent transfur;
- *Lead and Latex* and *Interchangeable* are potential references for future
  exploration of sentient latex communities and territorial encounters.

These works are creative references only. Synergy should not copy their text,
characters or setting-specific material.

## Suggested implementation order after stable

1. Validate Changed Vanilla compatibility in real worlds and collect field
   reports.
2. Add data-driven species affinity and harmless intra-community interactions.
3. Introduce community awareness, diplomacy and limited real-stock trade.
4. Add safe structure claims and trust-based access control.
5. Prototype small follower groups before considering larger war bands.
6. Build the shared bond-relic backend, then implement the Dark Latex mask
   ritual as its first faction-specific restoration path.
