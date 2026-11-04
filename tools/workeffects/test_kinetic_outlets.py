"""Check the packaged four-column chimney against every orthogonal orientation; no game/GL claim."""
import hashlib
import itertools
import json
import math
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]


class KineticOutlets(unittest.TestCase):
    def test_four_columns_and_all_transformed_spawn_cells(self):
        motions = ROOT / 'src/main/resources/assets/gtsr/machinevisual/motions.json'
        provenance = ROOT / 'src/main/resources/assets/gtsr/workeffects/provenance.json'
        definition = next(d for d in json.loads(motions.read_text(encoding='utf8'))['definitions']
                          if d['id'] == 'kinetic_processing_array')
        document = json.loads(provenance.read_text(encoding='utf8'))
        records = document['machines']
        source = next(r for r in records if r['machine'] == 'KineticProcessingArray')
        a, b, c = definition['offset']
        cells = {(x-a, b-y, z-c): token for y, layer in enumerate(definition['shapeLayers'])
                 for z, row in enumerate(layer) for x, token in enumerate(row) if token != ' '}
        chimney = [e['preciseABC'] for e in source['emitters'] if ':chimney-' in e['id']]
        self.assertEqual({(-1, -6.6, 1), (-1, -6.6, 2), (0, -6.6, 1), (0, -6.6, 2)},
                         set(map(tuple, chimney)))
        for x, _, z in chimney:
            for y in range(2, 7):
                self.assertEqual('A', cells[(x, y, z)], 'the complete 2x2 flue must remain air')
        occupied = {p for p, token in cells.items() if token != 'A'}
        air_checks = 0
        for permutation in itertools.permutations(range(3)):
            for signs in itertools.product((-1, 1), repeat=3):
                transform = lambda p: tuple(signs[i]*p[permutation[i]] for i in range(3))
                world_blocks = {transform(p) for p in occupied}
                for emitter in source['emitters']:
                    point = emitter['preciseABC']
                    position = transform((point[0], -point[1], point[2]))
                    for dx, dz in itertools.product((-.06, .06), repeat=2):
                        cell = (math.floor(position[0]+.5+dx), math.floor(position[1]+.5),
                                math.floor(position[2]+.5+dz))
                        self.assertNotIn(cell, world_blocks, emitter['id'])
                        air_checks += 1
        self.assertEqual(1152, air_checks)
        receipt = {'status': 'PASS', 'airChecks': air_checks, 'orientations': 48,
                   'chimneyColumns': 4, 'requiredFlueAirCells': 20,
                   'sha256': {str(p.relative_to(ROOT)): hashlib.sha256(p.read_bytes()).hexdigest()
                              for p in (motions, provenance)}, 'game': 'NOT_RUN'}
        output = ROOT / 'build/v2111-kinetic-outlets.json'
        output.parent.mkdir(exist_ok=True)
        output.write_text(json.dumps(receipt, indent=2)+'\n', encoding='utf8')


if __name__ == '__main__':
    unittest.main()
