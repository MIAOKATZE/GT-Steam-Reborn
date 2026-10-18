"""Map Minecraft member references in isolated javac probe classes using the local MCP mappings."""
from pathlib import Path
import csv
import struct
import zipfile


def parse(data):
    count = int.from_bytes(data[8:10], 'big')
    entries = [None]
    pos = 10
    while len(entries) < count:
        tag = data[pos]
        start = pos
        pos += 1
        if tag == 1:
            size = int.from_bytes(data[pos:pos + 2], 'big')
            pos += 2 + size
        elif tag in (3, 4, 9, 10, 11, 12, 17, 18):
            pos += 4
        elif tag in (5, 6):
            pos += 8
        elif tag in (7, 8, 16, 19, 20):
            pos += 2
        elif tag == 15:
            pos += 3
        else:
            raise ValueError(f'Unexpected constant tag {tag}')
        entries.append(data[start:pos])
        if tag in (5, 6):
            entries.append(None)
    return entries, data[pos:]


def utf(cp, index):
    return cp[index][3:].decode('utf-8')


def class_name(cp, index):
    return utf(cp, int.from_bytes(cp[index][1:3], 'big'))


class Mapper:
    def __init__(self, root):
        mappings = Path.home() / '.gradle/caches/minecraft/de/oceanlabs/mcp/mcp_stable/12'
        fields = {}
        methods = {}
        # RFG combines stable mapping names with Forge's legacy patched source names.
        forge = Path.home() / '.gradle/caches/minecraft/net/minecraftforge/forge/1.7.10-10.13.4.1614-1.7.10/unpacked/conf'
        for directory in (mappings, forge):
            for filename, target in (('fields.csv', fields), ('methods.csv', methods)):
                for row in csv.DictReader((directory / filename).open()):
                    target.setdefault(row['searge'], set()).add(row['name'])
        self.members = {}
        for line in (mappings / 'joined.srg').read_text().splitlines():
            bits = line.split()
            if bits[0] == 'FD:':
                owner, srg = bits[2].rsplit('/', 1)
                for name in fields.get(srg, {srg}):
                    self.members[owner, name, None] = srg
            elif bits[0] == 'MD:':
                owner, srg = bits[3].rsplit('/', 1)
                for name in methods.get(srg, {srg}):
                    self.members[owner, name, bits[4]] = srg
        self.jar = zipfile.ZipFile(root / 'build/rfg/recompiled_minecraft-1.7.10.jar')
        self.parents = {}

    def parent(self, owner):
        if owner not in self.parents:
            try:
                cp, body = parse(self.jar.read(owner + '.class'))
                index = int.from_bytes(body[4:6], 'big')
                self.parents[owner] = class_name(cp, index) if index else None
            except KeyError:
                self.parents[owner] = None
        return self.parents[owner]

    def name(self, owner, name, desc):
        while owner:
            mapped = self.members.get((owner, name, desc))
            if mapped:
                return mapped
            owner = self.parent(owner)
        return name

    def remap(self, data):
        cp, body = parse(data)
        changes = []
        for index, entry in enumerate(cp[:]):
            if entry is None or entry[0] not in (9, 10, 11):
                continue
            owner_index, nat_index = struct.unpack('>HH', entry[1:])
            owner = class_name(cp, owner_index)
            if not owner.startswith('net/minecraft/'):
                continue
            name_index, desc_index = struct.unpack('>HH', cp[nat_index][1:])
            old = utf(cp, name_index)
            new = self.name(owner, old, None if entry[0] == 9 else utf(cp, desc_index))
            if old == new:
                continue
            text = new.encode()
            new_name_index = len(cp)
            cp.append(b'\x01' + struct.pack('>H', len(text)) + text)
            new_nat_index = len(cp)
            cp.append(b'\x0c' + struct.pack('>HH', new_name_index, desc_index))
            cp[index] = bytes([entry[0]]) + struct.pack('>HH', owner_index, new_nat_index)
            changes.append({'owner': owner, 'from': old, 'to': new})
        return data[:8] + struct.pack('>H', len(cp)) + b''.join(e for e in cp[1:] if e is not None) + body, changes
