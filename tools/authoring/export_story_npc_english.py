"""Export the current English conversations and matching role barks for editing."""
from pathlib import Path
import json
import sys

ROOT = Path(__file__).resolve().parents[2]
CONDITIONS = {
    'always': 'All visitors', 'human': 'Human visitor', 'transformed': 'Transformed visitor',
    'day': 'Daytime visitor', 'night_human': 'Human visitor at night',
    'night_transformed': 'Transformed visitor at night',
    'human_join': 'Human applicant', 'transformed_join': 'Transformed applicant',
    'cannot_join': 'Admission currently unavailable', 'can_join': 'Eligible applicant',
    'can_reversible': 'Reversible transformation available',
    'can_permanent': 'Permanent transformation available with TrueTransfur',
    'member': 'Member of this camp', 'stranger': 'Not a member of this camp',
    'understands': 'Visitor understands mental speech', 'chief': 'Chief conversation',
}
BRANCHES = {
    'root': 'First meeting', 'return': 'Returning visitor', 'about': 'Who are you?',
    'voices': 'Sharing a body', 'home': 'Life at home', 'past': 'The past',
    'furniture': 'Furniture and keepsakes', 'enrol': 'Joining the camp',
    'explain_form': 'Transformation choices', 'confirm_permanent': 'Permanent transformation: second confirmation',
    'welcome_human': 'Welcome: human', 'welcome_latex': 'Welcome: already transformed',
    'welcome_copy': 'Welcome: reversible transformation', 'welcome_permanent': 'Welcome: permanent transformation',
    'membership': 'Current membership', 'confirm_leave': 'Confirm departure',
    'left': 'Membership ended', 'goodbye': 'Farewell', 'tail': 'Ears and tail',
    'farewell': 'The farewell note', 'returned': 'Returning to the laboratory',
    'experiment': 'Research in the lower rooms', 'muntjacs': 'The Muntjac report',
    'crystal': 'Crystal notes', 'initials': 'Initials on the records',
    'neighbors': 'Mountain neighbors', 'observations': 'Astronomical records', 'old_note': 'The old note',
}


def export(destination):
    authored = json.loads((ROOT / 'tools/authoring/story_npc_dialogue.json').read_text(encoding='utf-8'))['profiles']
    words = json.loads((ROOT / 'src/main/resources/assets/changed_synergy/lang/en_us.json').read_text(encoding='utf-8'))
    out = ['# Synergy: NPC Dialogue and Role Lines', '',
           'English editing copy. The three Chiefs speak for their own households, not an entire regional faction. The laboratory Wanderer does not recruit.', '',
           'Paragraph breaks within spoken dialogue mark deliberate changes of beat. A thought accompanies the preceding spoken passage and is visible only when understood. Smaller interfaces may add further page breaks without changing the words.', '',
           'Branch labels identify where edited passages belong. Replies list their destination and any availability condition. Animation parameters and translation keys are omitted.', '']
    lines = choices = barks = 0
    for name in ('canopy_chief', 'hyena_chief', 'observatory_chief', 'lab_researcher'):
        profile = authored[name]
        title = profile['title']['en_us']
        out += [f'## {title}', '',
                f'Home: {profile["template"].replace("_", " ").title()}. Personality: {profile["trait"].lower()}, polite.', '']
        for node_id, node in profile['nodes'].items():
            out += [f'### {BRANCHES.get(node_id, node_id.replace("_", " ").title())} [{node_id}]', '']
            for line in node['lines']:
                lines += 1
                kind = 'Thought, if understood' if line['channel'] == 'mental' else 'Spoken'
                out += [f'**{kind} — {CONDITIONS.get(line.get("condition", "always"), line.get("condition"))}**', '']
                beats = ['']
                for part in line['parts']:
                    if part.get('beat') and beats[-1]:
                        beats.append('')
                    beats[-1] += part['text']['en_us']
                for text in beats:
                    if text.strip():
                        text=text.strip()
                        if line['channel']=='mental' and ((text.startswith('(') and text.endswith(')')) or (text.startswith('（') and text.endswith('）'))):
                            text=text[1:-1]
                        out += [text, '']
            out += ['**Replies**', '']
            for choice in node['choices']:
                choices += 1
                condition = choice.get('condition', 'always')
                details = [] if condition == 'always' else [CONDITIONS.get(condition, condition)]
                if choice.get('requires'):
                    details.append('after completing ' + ' and '.join(BRANCHES.get(k, k) for k in choice['requires']))
                target = 'End conversation' if choice.get('effect') == 'close' else BRANCHES.get(choice['next'], choice['next']) + f' [{choice["next"]}]'
                out.append(f'- {choice["text"]["en_us"]} → {target}' + (f' ({"; ".join(details)})' if details else ''))
            out.append('')
        out += ['### Role lines', '']
        if profile['chief']:
            for context in ('welcome', 'care', 'organize', 'warning'):
                key = f'dialogue.changed_synergy.context.chief.{name}.{context}'
                out += [f'**{context.title()}**', '', words[key], '']
                barks += 1
        else:
            out += ['The Wanderer has no researcher-specific role barks. These are the existing generic Wanderer discovery lines:', '']
            for key, text in words.items():
                if key.startswith('dialogue.changed_synergy.role.role_wanderer_discovery.'):
                    out += [text, '']
                    barks += 1
    destination.parent.mkdir(parents=True, exist_ok=True)
    destination.write_text('\n'.join(out).rstrip() + '\n', encoding='utf-8')
    print(f'Exported {lines} conditional dialogue lines, {choices} replies and {barks} role lines to {destination}')


if __name__ == '__main__':
    export(Path(sys.argv[1]))
