"""Packaged full-cube fixture contracts. Does not claim Minecraft/StructureLib execution."""
import json
import math
from collections import deque
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
        expected = [('kinetic_processing_array', (8, 9, 9), 397, 9, 17, 17, [6, 6, 4]),
                    ('heavy_steam_turbine', (12, 10, 8), 422, 0, 8, 13, [2, 7, 1])]
        expected += [('mega_steam_turbine_'+str(k), (51, 29, 79+12*k), 18606+5122*k,
                      162, 880+160*k, 1566+312*k, [25, 26, 1]) for k in range(5)]
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

    def test_all_voltage_panels_have_faces_reachable_from_exterior_air(self):
        choices = [['GregTechAPI.sBlockCasings1', tier] for tier in range(1, 10)]
        choices += [['GregTechAPI.sBlockCasingsNH', tier] for tier in range(10, 13)]
        for identifier in ['kinetic_processing_array']+[f'mega_steam_turbine_{k}' for k in range(5)]:
            d = self.definitions[identifier]
            fixture = cells(d)
            self.assertEqual(choices, d['tokenMaterials']['U']['blockChoices'])
            # Flood the actual unoccupied cubes from outside the machine. Internal air pockets alone do not qualify.
            solid = {p for p,t in fixture.items() if t != 'A'}
            lower = tuple(min(p[i] for p in solid)-1 for i in range(3))
            upper = tuple(max(p[i] for p in solid)+1 for i in range(3))
            neighbours = [(1,0,0),(-1,0,0),(0,1,0),(0,-1,0),(0,0,1),(0,0,-1)]
            reached = {lower}
            pending = deque([lower])
            while pending:
                p = pending.popleft()
                for delta in neighbours:
                    q = tuple(p[i]+delta[i] for i in range(3))
                    if q not in solid and q not in reached and all(lower[i] <= q[i] <= upper[i] for i in range(3)):
                        reached.add(q)
                        pending.append(q)
            for (x, y, z), token in fixture.items():
                if token != 'U':
                    continue
                self.assertTrue(any((x+dx,y+dy,z+dz) in reached for dx,dy,dz in neighbours),
                                (identifier, x,y,z, 'no exterior service face'))
            if identifier.startswith('mega_'):
                self.assertTrue(all(abs(x) <= 20 and -1 <= y <= 2 and 0 <= z <= 10
                                    for (x,y,z), token in fixture.items() if token == 'U'))

    def test_material_choices_are_shared_by_shell_pipe_and_gear_roles(self):
        for identifier in ['kinetic_processing_array']+[f'mega_steam_turbine_{k}' for k in range(5)]:
            materials = self.definitions[identifier]['tokenMaterials']
            for token in ['B', 'H', 'R']:
                self.assertEqual([['GregTechAPI.sBlockCasings2', 0],
                                  ['GregTechAPI.sBlockCasings4', 2],
                                  ['GregTechAPI.sBlockCasings4', 0]], materials[token]['blockChoices'])
            for token in ['C', 'S']:
                self.assertEqual([['GregTechAPI.sBlockCasings2', meta] for meta in [13,14,15]],
                                 materials[token]['blockChoices'])
            self.assertEqual([['GregTechAPI.sBlockCasings2', 3], ['GregTechAPI.sBlockCasings2', 4],
                              ['BlocksGTSR.tungstenSteelGearbox', 0]],
                             materials['D']['blockChoices'])
            self.assertEqual([['GregTechAPI.sBlockFrames', 'Materials.'+m+'.mMetaItemSubID']
                              for m in ['Steel','Titanium','TungstenSteel']],
                             materials['E']['blockChoices'])

    def test_kpa_keeps_nine_front_glass_and_adds_eight_chamber_side_glass(self):
        fixture = cells(self.definitions['kinetic_processing_array'])
        front = [(x,y,-4) for x in [-5,-4,-3] for y in [0,1,2]]
        sides = [(x,y,z) for x in [-6,-2] for y in [0,1] for z in [-3,-2]]
        for p in front+sides:
            self.assertEqual('G', fixture[p], p)
        for x,y,z in sides:
            outward = -1 if x == -6 else 1
            self.assertNotIn(fixture.get((x+outward,y,z), ' '), ['B','H','C','D','E','R','S','U'])

    def test_mega_keeps_low_pipe_bundles_and_removes_overhead_routes_for_every_k(self):
        for k in range(5):
            fixture = cells(self.definitions[f'mega_steam_turbine_{k}'])
            def local(x,y,z):
                return (-x,y-2,z+31)
            # r13 retains the low two-by-two manifold and removes the overhead network.
            for x in range(-17,18):
                for y in range(4,6):
                    for z in range(-29,-27):
                        self.assertNotIn(fixture.get(local(x,y,z), ' '), [' ','A'])
            self.assertFalse(any(token == 'C' and y >= 22 for (x,y,z),token in fixture.items()))

    def test_kpa_chimney_has_a_two_by_two_required_air_bore_and_full_walls(self):
        fixture = cells(self.definitions['kinetic_processing_array'])
        for y in range(2, 7):
            for x in [-1, 0]:
                for z in [1, 2]:
                    self.assertEqual('A', fixture[(x,y,z)])
            for x in [-2,-1,0,1]:
                for z in range(4):
                    if x in [-2,1] or z in [0,3]:
                        self.assertIn(fixture.get((x,y,z)), ['H','C','D'])

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
            hatch_token = 'U' if identifier.startswith('mega_') else 'B'
            accessible = [p for p,t in fixture.items() if t == hatch_token and any(
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
