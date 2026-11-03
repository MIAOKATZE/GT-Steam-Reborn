"""Packaged full-cube fixture contracts. Does not claim Minecraft/StructureLib execution."""
import json
import math
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
DOCUMENT = ROOT / 'src/main/resources/assets/gtsr/machinevisual/motions.json'


def cells(definition):
    a, b, c = definition['offset']
    return {(x-a, b-y, z-c): token
            for y, layer in enumerate(definition['shapeLayers'])
            for z, row in enumerate(layer) for x, token in enumerate(row)
            if token != ' '}


class RebuiltTurbineFixtures(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.definitions = {d['id']: d for d in json.loads(DOCUMENT.read_text(encoding='utf8'))['definitions']}

    def test_all_approved_dimensions_counts_and_controller_offsets(self):
        expected = [('kinetic_processing_array', (8, 9, 9), 377, 32, 9, 17, [6, 6, 4]),
                    ('heavy_steam_turbine', (12, 10, 8), 422, 0, 8, 13, [2, 7, 1])]
        expected += [('mega_steam_turbine_'+str(k), (51, 29, 78+12*k), 16412+5144*k,
                      2712+1872*k, 280, 606+176*k, [25, 26, 0]) for k in range(5)]
        for identifier, size, count, upgrades, glass, moving, offset in expected:
            with self.subTest(identifier=identifier):
                d = self.definitions[identifier]
                fixture = cells(d)
                self.assertEqual(size, (len(d['shapeLayers'][0][0]), len(d['shapeLayers']), len(d['shapeLayers'][0])))
                self.assertEqual(offset, d['offset'])
                self.assertEqual('~', fixture[(0, 0, 0)])
                self.assertEqual(1, list(fixture.values()).count('~'))
                self.assertEqual(count, sum(t != 'A' for t in fixture.values()))
                self.assertEqual(upgrades, list(fixture.values()).count('U'))
                self.assertEqual(glass, list(fixture.values()).count('G'))
                self.assertEqual(moving, sum(len(g['members']) for g in d['groups']))
                for token in set(fixture.values())-{'~', 'A'}:
                    self.assertTrue(d['tokenMaterials'][token]['blockChoices'])

    def test_every_dynamic_rest_cube_is_a_real_required_block(self):
        for identifier, d in self.definitions.items():
            if identifier not in ['kinetic_processing_array', 'heavy_steam_turbine'] and not identifier.startswith('mega_'):
                continue
            fixture = cells(d)
            seen = set()
            for group in d['groups']:
                for member in group['members']:
                    p = tuple(member[axis] for axis in 'xyz')
                    self.assertNotIn(p, seen)
                    seen.add(p)
                    self.assertEqual('S' if member['material'] == 'shaft' else 'R', fixture[p])
                    self.assertNotEqual('B', fixture[p], 'moving blocks must never accept hatches')

    def test_continuous_z_sweeps_require_air_and_miss_all_fixed_blocks(self):
        for identifier, d in self.definitions.items():
            if not identifier.startswith('mega_') and identifier != 'heavy_steam_turbine':
                continue
            fixture = cells(d)
            for group in d['groups']:
                self.assertEqual(('rotate_z', 'z'), (group['kind'], group['axis']))
                members = {tuple(m[a] for a in 'xyz') for m in group['members']}
                px, py, _ = group['pivot']
                slices = {}
                for x, y, z in members:
                    slices[z] = max(slices.get(z, 0), (abs(x-px)+.5)**2+(abs(y-py)+.5)**2)
                for z, radius2 in slices.items():
                    radius = math.sqrt(radius2)
                    for x in range(math.floor(px-radius+.5+1e-7), math.ceil(px+radius+.5-1e-7)):
                        for y in range(math.floor(py-radius+.5+1e-7), math.ceil(py+radius+.5-1e-7)):
                            if max(0, abs(x-px)-.5)**2+max(0, abs(y-py)-.5)**2 >= radius2-1e-7:
                                continue
                            self.assertIn(fixture.get((x,y,z)), ['R','S','A'], (identifier, x,y,z))

    def test_hatches_have_enough_externally_accessible_fixed_positions(self):
        neighbours = [(1,0,0),(-1,0,0),(0,1,0),(0,-1,0),(0,0,1),(0,0,-1)]
        for identifier in ['kinetic_processing_array', 'heavy_steam_turbine']+[f'mega_steam_turbine_{k}' for k in range(5)]:
            fixture = cells(self.definitions[identifier])
            accessible = [p for p,t in fixture.items() if t == 'B' and any(
                fixture.get(tuple(p[i]+delta[i] for i in range(3)), ' ') in [' ', 'A'] for delta in neighbours)]
            self.assertGreaterEqual(len(accessible), 5 if identifier == 'kinetic_processing_array' else 3)

    def test_old_structure_envelopes_cannot_satisfy_new_required_geometry(self):
        # Every possible old KPA block fits this old 7x5x7 envelope relative to its controller.
        # Every old vertical Mega variant fits width/depth13. New required geometry exceeds those bounds.
        kpa = cells(self.definitions['kinetic_processing_array'])
        self.assertTrue(any(t not in ['A','~'] and (abs(x)>3 or y>2 or z>4 or z<-2)
                            for (x,y,z),t in kpa.items()))
        for k in range(5):
            mega = cells(self.definitions[f'mega_steam_turbine_{k}'])
            self.assertTrue(any(t not in ['A','~'] and (abs(x)>6 or z>12 or z<0)
                                for (x,y,z),t in mega.items()))


if __name__ == '__main__':
    unittest.main()
