"""Run the ore center's native MUI2 test against the registered localhost dedicated server."""

import argparse
from contextlib import ExitStack
import json
from pathlib import Path
import re
import socket
import subprocess
import sys
import time
import uuid

REPO = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(REPO.parent / 'tools/testlab'))
import client_prepare
import client_qa
import host
import preparation
from review_transport import ReviewTransport


def running(process):
    return process is not None and process.poll() is None


def wait_for(predicate, process, deadline, label):
    while running(process) and time.monotonic() < deadline:
        if predicate():
            return
        time.sleep(.5)
    if predicate():
        return
    raise RuntimeError(f'{label} did not complete; process exit={process.poll()}')


def ready(port):
    try:
        with socket.create_connection(('127.0.0.1', port), timeout=.2):
            return True
    except OSError:
        return False


def memory_budget():
    query = '(Get-CimInstance Win32_OperatingSystem).FreeVirtualMemory'
    free_commit_kib = subprocess.check_output(['powershell.exe', '-NoProfile', '-NonInteractive', '-Command', query],
                                             text=True, timeout=20, creationflags=subprocess.CREATE_NO_WINDOW)
    return {'availablePhysicalBytes': host.available_memory(), 'availableCommitBytes': int(free_commit_kib.strip()) * 1024}


def server_ready(evidence, run_id, port):
    checks = evidence / 'server-checks.json'
    if checks.is_file():
        report = host.read(checks)
        if report.get('status') != 'PASS':
            raise RuntimeError(f'Dedicated fixture failed: {report.get("error")}')
    marker = evidence / 'server-ready.json'
    return marker.is_file() and host.read(marker).get('runId') == run_id and ready(port)


def wait_for_client(client, log_path, deadline):
    with log_path.open('rb') as log:
        def completed():
            for line in log:
                if b'/FATAL] [BetterCrashes]' in line or line.startswith(b'---- Minecraft Crash Report ----'):
                    detail = line.decode('utf-8', errors='replace').strip()
                    raise RuntimeError(f'Native client crash: {detail}; full trace at {log_path}')
            return not running(client)

        wait_for(completed, client, deadline, 'Native client completion')


def verify_automated_result(evidence, run_id, artifacts, client, receipt, preset_load=False, profile_frames=False):
    client_report = host.read(evidence / 'client-checks.json')
    if client_report.get('status') != 'PASS':
        raise RuntimeError(f'Native client failed in phase {client_report.get("phase")}: {client_report.get("error")}')
    server_report = host.read(evidence / 'server-checks.json')
    for name, report in (('client', client_report), ('server', server_report)):
        if report.get('runId') != run_id or report.get('status') != 'PASS':
            raise RuntimeError(f'{name} did not report PASS for this run: {report}')
        for artifact in artifacts:
            if report.get('artifacts', {}).get(artifact['modId'], {}).get('jarSha256') != artifact['sha256']:
                raise RuntimeError(f'{name} loaded a different {artifact["modId"]} jar')
    if profile_frames:
        frames = [evidence / f'native-frame-{name}.png' for name in ('overview', 'idle', 'active')]
        if client.returncode != 0 or not all(frame.is_file() for frame in frames):
            raise RuntimeError('Native frame profile did not capture all three views')
        receipt['frames'] = [{'path': str(frame), 'sha256': host.sha(frame)} for frame in frames]
        receipt['frameProfile'] = client_report['frameProfile']
        return
    screenshot = evidence / ('native-preset-stats.png' if preset_load else 'reopened-native-graph.png')
    if client.returncode != 0 or not screenshot.is_file():
        raise RuntimeError('Native client did not exit cleanly with its reopened graph frame')
    receipt['frame'] = {'path': str(screenshot), 'sha256': host.sha(screenshot)}
    if preset_load:
        recipe = evidence / 'native-preset-recipe.png'
        if not recipe.is_file():
            raise RuntimeError('Native GT recipe frame missing')
        receipt['recipeFrame'] = {'path': str(recipe), 'sha256': host.sha(recipe)}
        crown = [evidence / ('native-preset-crown-' + pose + '.png') for pose in ('a', 'b')]
        if not all(image.is_file() for image in crown) or host.sha(crown[0]) == host.sha(crown[1]):
            raise RuntimeError('Native crown did not provide two distinct frames')
        receipt['crownFrames'] = [{'path': str(image), 'sha256': host.sha(image)} for image in crown]


def run(args):
    server_item = host.registered(args.registry, 'gtsr')
    client_item = host.registered(args.registry, 'gtsr', 'client')
    server_root, client_root = server_item['_path'], client_item['_path']
    host.server_world(server_item)
    artifacts = host.bundle_artifacts(args.bundle, 'gtsr')
    for item, name in ((server_item, 'server'), (client_item, 'client')):
        if host.recovery_pending(item) or (item['_path'] / '.testlab-host/lease.json').exists():
            raise RuntimeError(f'Registered {name} is leased or has recovery pending')
        java = Path(item[name + '_java']).with_name('java.exe')
        version = subprocess.run([str(java), '-version'], capture_output=True, text=True, check=True, timeout=20,
                                 creationflags=subprocess.CREATE_NO_WINDOW).stderr
        if not re.search(r'version "26(?:[."-])', version):
            raise RuntimeError(f'Registered {name} Java is not 26')
        host.inventory(item['_path'] / 'mods')
    props = host.properties(server_root / 'server.properties')
    port = int(props['server-port'])
    if ready(port):
        raise RuntimeError('Registered server port is already listening')
    budget = memory_budget()
    if budget['availableCommitBytes'] < (args.heap_gib + 5) * 1024 ** 3:
        raise RuntimeError('Insufficient available Windows commit for the selected client, server, and reserve')
    if args.execute and budget['availablePhysicalBytes'] < (args.heap_gib + 3) * 1024 ** 3:
        raise RuntimeError('Available physical memory is below the combined client and server heaps')
    if not args.execute:
        print(f'Preflight complete for GTSR client + dedicated server at 127.0.0.1:{port}: {budget}')
        return 0

    run_id = str(uuid.uuid4())
    evidence = host.unredirected(args.evidence.absolute())
    evidence.mkdir(parents=True, exist_ok=False)
    case = ('ore_integration_center.world_frame_profile' if args.profile_frames else
            'ore_integration_center.preset_load_tcp' if args.preset_load else
            'ore_integration_center.native_review' if args.review_port else 'ore_integration_center.graph_tcp')
    receipt = {'runId': run_id, 'lane': 'client-server', 'case': case,
               'status': 'ERROR', 'serverInstance': str(server_root), 'clientInstance': str(client_root),
               'endpoint': f'127.0.0.1:{port}', 'actorUuid': client_qa.ACTOR_UUID, 'memory': budget, 'cleanup': {}}
    server, client, desktop = None, None, None
    try:
        workspace = REPO / 'build/ore-center-client-server'
        prepare_args = [sys.argv[0], '--module', 'gtsr', '--registry', str(args.registry), '--workspace', str(workspace),
                        '--jar', next(a['path'] for a in artifacts if a['modId'] == 'gtsr')]
        original_args = sys.argv
        try:
            sys.argv = prepare_args
            client_prepare.main(REPO)
        finally:
            sys.argv = original_args
        spec = host.read(workspace / 'client-launch.json')
        client_command = client_qa.command_with(spec, {
            '--username': client_qa.ACTOR, '--uuid': client_qa.ACTOR_UUID.replace('-', ''),
            '--width': '2560' if args.large_view else '1600',
            '--height': '1334' if args.large_view else '900',
        })
        client_command = [arg for arg in client_command if not arg.startswith(('-Xmx', '-Dgtsr.uiqa.enabled='))]
        with ExitStack() as stack:
            if args.review_port:
                review = ReviewTransport(evidence, REPO.parent / 'GT-MUI2工作台/native/live', args.review_port,
                                         next(a['sha256'] for a in artifacts if a['modId'] == 'gtsr'))
                stack.enter_context(review.open())
                receipt['review'] = review.origin
                print(f'Native review: {review.origin} (starting)', flush=True)
            server_owner, server_lock = stack.enter_context(host.lease(server_root, run_id, evidence))
            client_owner, client_lock = stack.enter_context(host.lease(client_root, run_id, evidence))
            world = client_qa.create_world(server_root, run_id)

            def clean_world():
                try:
                    if running(server):
                        raise RuntimeError('Owned server still running')
                    client_qa.delete_world(world, server_root, run_id)
                    receipt['cleanup']['worldDeleted'] = True
                except BaseException:
                    host.write(server_root / '.testlab-host/business-recovery-pending.json',
                               {'runId': run_id, 'world': str(world), 'evidence': str(evidence)})
                    raise

            stack.callback(clean_world)
            props.update({'level-name': world.name, 'level-type': 'FLAT', 'online-mode': 'false', 'white-list': 'true',
                          'op-permission-level': '2', 'server-ip': '127.0.0.1', 'enable-rcon': 'false',
                          'enable-query': 'false', 'spawn-protection': '0', 'spawn-monsters': 'false',
                          'spawn-animals': 'false', 'spawn-npcs': 'false',
                          'view-distance': '10' if args.large_view or args.review_port or args.preset_load else '5'})
            server_settings = {
                'server.properties': ('\n'.join(f'{key}={value}' for key, value in props.items()) + '\n').encode(),
                'ops.json': b'[]',
                'whitelist.json': json.dumps([{'uuid': client_qa.ACTOR_UUID, 'name': client_qa.ACTOR}]).encode(),
            }
            for name, empty in (('usercache.json', b'[]'), ('usernamecache.json', b'{}')):
                path = server_root / name
                server_settings[name] = path.read_bytes() if path.exists() else empty
            stack.enter_context(client_qa.settings(server_root, server_settings, evidence / 'server-settings', lambda: running(server)))
            distance = 10 if args.profile_frames else 3
            client_settings = {'options.txt': ('fullscreen:false\npauseOnLostFocus:false\nsoundCategory_master:0.0\n'
                                                f'guiScale:{3 if args.large_view else 2}\nlang:{args.language}\nrenderDistance:{distance}\n'
                                                + ('enableVsync:false\nmaxFps:260\n' if args.profile_frames else '')).encode('ascii')}
            for name in ('usernamecache.json', 'optionsnf.txt'):
                path = client_root / name
                if path.is_file():
                    client_settings[name] = path.read_bytes()
            stack.enter_context(client_qa.settings(client_root, client_settings, evidence / 'client-settings', lambda: running(client)))
            stack.enter_context(preparation.prepared(server_root, preparation.changes(server_root, 'gtsr'), run_id,
                                                     evidence / 'server-config', lambda: running(server)))
            stack.enter_context(preparation.prepared(client_root, preparation.changes(client_root, 'gtsr'), run_id,
                                                     evidence / 'client-config', lambda: running(client)))
            stack.enter_context(host.installed(server_root, artifacts, evidence / 'server-jars', lambda: server))
            stack.enter_context(host.installed(client_root, artifacts, evidence / 'client-jars', lambda: client))
            common = ['-Dgtsr.graphTcp.run=' + run_id, '-Dgtsr.graphTcp.receipts=' + str(evidence),
                      '-Dgtsr.graphTcp.actor=' + client_qa.ACTOR_UUID]
            if args.large_view:
                common.append('-Dgtsr.graphTcp.fullCenter=true')
            if args.preset_load:
                common.append('-Dgtsr.graphTcp.presetLoad=true')
            if args.profile_frames:
                common.append('-Dgtsr.graphTcp.profileFrames=true')
            if args.review_port:
                common += ['-Dgtsr.graphTcp.interactive=true']
            server_command = [server_item['server_java'], '-Xms1G', '-Xmx3G', '-Djava.awt.headless=true',
                              '-Duser.language=en', '-Dgtsr.graphTcp.world=' + str(world), *common,
                              '@java9args.txt', '-jar', 'lwjgl3ify-forgePatches.jar', 'nogui']
            server_log = stack.enter_context((evidence / 'server-console.log').open('wb'))
            server = subprocess.Popen(server_command, cwd=server_root, stdin=subprocess.PIPE, stdout=server_log,
                                      stderr=subprocess.STDOUT, creationflags=subprocess.CREATE_NO_WINDOW)

            def stop_server():
                def stop_command():
                    server.stdin.write(b'stop\n')
                    server.stdin.flush()

                receipt['cleanup']['serverForcedStop'] = host.stop_owned_process(server, stop_command, timeout=90)
                server.stdin.close()

            stack.callback(stop_server)
            server_owner['child'] = host.identity(server.pid)
            host.write(server_lock, server_owner)
            receipt['serverProcess'] = server_owner['child']
            wait_for(lambda: server_ready(evidence, run_id, port), server,
                     time.monotonic() + args.deadline, 'Dedicated server and owned probe readiness')
            api, desktop, startup = host.create_private_desktop('MiaoGTSR-' + run_id)
            receipt['hiddenDesktop'] = {'name': startup.lpDesktop, 'switched': False, 'foregroundFallback': False}

            def close_desktop():
                if not running(client):
                    receipt['cleanup']['desktopClosed'] = bool(api.CloseDesktop(desktop))

            stack.callback(close_desktop)
            client_log_path = evidence / 'client-console.log'
            client_log = stack.enter_context(client_log_path.open('wb'))
            client_command[1:1] = ['-Xmx' + str(args.heap_gib) + 'G', *common,
                                   '-Dgtsr.graphTcp.port=' + str(port)]
            client = subprocess.Popen(client_command, cwd=client_root, stdout=client_log, stderr=subprocess.STDOUT,
                                      startupinfo=startup, creationflags=subprocess.CREATE_NO_WINDOW)
            stack.callback(lambda: receipt['cleanup'].update(clientForcedStop=host.stop_owned_process(
                client, lambda: host.write(evidence / 'stop.json', {'runId': run_id}), timeout=30)))
            client_owner['child'] = host.identity(client.pid)
            host.write(client_lock, client_owner)
            receipt['clientProcess'] = client_owner['child']
            wait_for_client(client, client_log_path, time.monotonic() + args.deadline)
            if args.review_port:
                state = host.read(evidence / 'review-state.json')
                closed = host.read(evidence / 'review-closed.json')
                if client.returncode != 0 or state.get('frame', 0) < 1 or closed.get('runId') != run_id:
                    raise RuntimeError('Native review did not display and close cleanly')
                receipt['reviewFrames'] = state['frame']
                receipt['browserInputBatches'] = state['seq']
            else:
                verify_automated_result(evidence, run_id, artifacts, client, receipt, args.preset_load, args.profile_frames)
        if receipt['cleanup'] != {'clientForcedStop': False, 'desktopClosed': True,
                                  'serverForcedStop': False, 'worldDeleted': True}:
            raise RuntimeError(f'Cleanup incomplete: {receipt["cleanup"]}')
        for item in (client_item, server_item):
            if host.recovery_pending(item) or (item['_path'] / '.testlab-host/lease.json').exists():
                raise RuntimeError(f'Restoration left a lease or recovery marker at {item["_path"]}')
        receipt['status'] = 'PASS'
    except BaseException as error:
        receipt['error'] = str(error)
    finally:
        receipt['finishedAt'] = host.stamp()
        host.write(evidence / 'receipt.json', receipt)
        print(f'{receipt["status"]}: {evidence / "receipt.json"}')
    return 0 if receipt['status'] == 'PASS' else 1


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--registry', type=Path, default=host.REGISTRY)
    parser.add_argument('--bundle', type=Path, required=True)
    parser.add_argument('--evidence', type=Path, required=True)
    parser.add_argument('--heap-gib', type=int, choices=[4, 5], default=4)
    parser.add_argument('--deadline', type=int, default=900)
    parser.add_argument('--language', choices=['en_US', 'zh_CN'], default='en_US')
    parser.add_argument('--large-view', action='store_true', help='match the 2560×1334 GUI scale 3 crash case')
    modes = parser.add_mutually_exclusive_group()
    modes.add_argument('--preset-load', action='store_true', help='run the production workload and verify its live recipe and stats')
    modes.add_argument('--profile-frames', action='store_true', help='sample cold, idle and active views in the real client')
    parser.add_argument('--execute', action='store_true')
    parser.add_argument('--review-port', type=int, choices=[20742],
                        help='serve the interactive native panel on the configured local browser port')
    args = parser.parse_args()
    if (args.preset_load or args.profile_frames) and args.review_port:
        parser.error('Preset workload and interactive review are separate cases')
    if args.profile_frames:
        args.preset_load = True
    return run(args)


if __name__ == '__main__':
    sys.exit(main())
