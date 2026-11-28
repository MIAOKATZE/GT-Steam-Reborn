"""GTSR fixture budgets and native gallery selection."""
from pathlib import Path
import re
CONFIG = ('config/gtsr/gtsr.cfg', 'testlab', 'enabled', 1048577)
CLIENT_RUNNER = 'tools/testlab/client_qa/run.py'
CLIENT_WORKSPACE = 'mui2-client-qa'
CLIENT_METADATA = ('client-launch.json', 'qa-compile-receipt.json')
PHYSICAL_CHUNKS = 169


def extra_changes(root, client, physical):
    if not physical:
        return []
    return [{'path': str(Path(root) / 'config/forgeChunkLoading.cfg'), 'category': 'gtsr',
        'properties': {'maximumChunksPerTicket': ('I', str(PHYSICAL_CHUNKS)), 'maximumTicketCount': ('I', '4')},
        'reason': 'GTSR physical fixture needs 169 chunks; only gtsr section, no defaults/gregtech/global expansion'}]


def physical_case(args):
    return args.lane == 'server' and not args.case.startswith(('ui', 'status'))


def validate_server(root, physical, preparing, blockers):
    if not physical or preparing:
        return
    forge = Path(root) / 'config/forgeChunkLoading.cfg'
    text = forge.read_text(encoding='utf-8-sig') if forge.is_file() else ''
    section = re.search(r'(?ms)^\s*"?gtsr"?\s*\{(.*?)^\s*\}', text)
    chunks = re.search(r'(?m)^\s*I:maximumChunksPerTicket\s*=\s*(\d+)', section[1]) if section else None
    if not chunks or int(chunks[1]) < PHYSICAL_CHUNKS:
        blockers.append('GTSR physical fixture requires per-mod 169 chunk budget; use --prepare (defaults/gregtech remain unchanged)')


def client_mode(args, config, preparing, blockers):
    mode = args.case if args.case in ('ui', 'gallery', 'gallery-batch') else 'ui' if args.case == 'all' else None
    if mode is None:
        blockers.append('GTSR client task must select ui/gallery/gallery-batch; restart uses the project recovery driver')
    if mode in ('gallery', 'gallery-batch') and not getattr(args, 'client_plan', None):
        blockers.append('Gallery client task requires explicit frozen --client-plan')
    return mode
