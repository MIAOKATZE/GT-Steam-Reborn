"""Pure unit-block pose oracle and continuous sweep proofs for production definitions."""
import itertools
import math

from common import SCHEMA, canonical, check_finite_tree, finite, sha

EPSILON = 1e-9


def shape_cells(definition):
    layers = definition["shapeLayers"]
    offset = definition["offset"]
    if not layers or len(layers) > 64 or len(offset) != 3 or any(type(v) is not int for v in offset):
        raise ValueError("Invalid shape/offset")
    width, depth = len(layers[0][0]), len(layers[0])
    if not 0 < width <= 64 or not 0 < depth <= 64:
        raise ValueError("Shape dimensions exceed budget")
    cells = {}
    skip = set(definition["skipTokens"]) | set(definition["requiredAirTokens"])
    for layer_index, layer in enumerate(layers):
        if len(layer) != depth or any(len(row) != width for row in layer):
            raise ValueError("Ragged shape layers")
        for z, row in enumerate(layer):
            for x, token in enumerate(row):
                if token in skip:
                    continue
                if token != "~" and token not in definition["tokenMaterials"]:
                    raise ValueError("Unbound shape token: " + token)
                cells[(x - offset[0], offset[1] - layer_index, z - offset[2])] = token
    if list(cells.values()).count("~") != 1 or cells.get((0, 0, 0)) != "~":
        raise ValueError("Shape must have exactly one controller at the local origin")
    return cells


def validate_definitions(document, profile):
    if document.get("schema") != SCHEMA or document.get("coordinates") != profile["coordinates"]:
        raise ValueError("Unsupported motion schema/coordinate convention")
    check_finite_tree(document)
    if set(document) != {"schema", "coordinates", "source", "definitions"}:
        raise ValueError("Unknown top-level definition fields")
    definitions = document["definitions"]
    if not definitions or len(definitions) > 64:
        raise ValueError("Empty/oversized definition list")
    if {value["controller"] for value in definitions} != set(profile["machines"]):
        raise ValueError("Motion definitions must match the selected five controllers exactly")
    identifiers = set()
    for value in definitions:
        if set(value) != {"id", "controller", "schema", "shapeLayers", "offset", "tokenMaterials", "skipTokens", "requiredAirTokens", "groups"}:
            raise ValueError("Unknown machine definition fields")
        spec = profile["machines"][value["controller"]]
        if value.get("schema") != SCHEMA or value["id"] != spec["id"] or value["id"] in identifiers:
            raise ValueError("Unknown/duplicate definition id")
        identifiers.add(value["id"])
        if any(not isinstance(token, str) or len(token) != 1 or token == "~" for token in value["skipTokens"] + value["requiredAirTokens"]):
            raise ValueError("Invalid skip/required-air token")
        for token, material in value["tokenMaterials"].items():
            if len(token) != 1 or set(material) != {"role", "blockChoices"}:
                raise ValueError("Invalid native token binding")
            if material["role"] not in profile["nativeRoleMaterials"] or material["blockChoices"] != profile["nativeRoleMaterials"][material["role"]]:
                raise ValueError("Unknown/empty native GT265 block/meta/tier binding")
        cells = shape_cells(value)
        if len(cells) != spec["cells"]:
            raise ValueError("Approved static shape count differs: " + value["id"])
        groups = value["groups"]
        if not groups or len(groups) > 8 or len({g["id"] for g in groups}) != len(groups):
            raise ValueError("Empty/duplicate/oversized group list")
        membership = set()
        for group in groups:
            if set(group) != {"id", "kind", "axis", "travel", "periodMs", "pivot", "members"}:
                raise ValueError("Unknown motion group fields")
            if group["kind"] not in ("translate", "rotate_y") or group["axis"] not in ("x", "y", "z"):
                raise ValueError("Unsupported motion kind/axis")
            if group["kind"] == "rotate_y" and group["axis"] != "y":
                raise ValueError("Rotation must explicitly use the Y axis")
            if type(group["periodMs"]) is not int or not 2 <= group["periodMs"] <= 60000:
                raise ValueError("Non-positive/oversized integer period")
            if len(group["pivot"]) != 3:
                raise ValueError("Invalid pivot")
            for number in group["pivot"]:
                finite(number, "pivot")
            travel = finite(group["travel"], "travel")
            if abs(abs(travel) - spec["travel"]) > EPSILON:
                raise ValueError("Approved travel differs: " + value["id"])
            if not group["members"]:
                raise ValueError("Empty group")
            for member in group["members"]:
                if set(member) != {"x", "y", "z", "material"}:
                    raise ValueError("Unknown member fields")
                point = tuple(member[key] for key in ("x", "y", "z"))
                if any(type(v) is not int for v in point) or point not in cells or point in membership:
                    raise ValueError("Unknown/duplicate/out-of-shape member")
                if member["material"] not in ("gear", "pipe", "frame"):
                    raise ValueError("Unbound native member material")
                role = {"gear": "gearbox", "pipe": "pipe", "frame": "frame"}[member["material"]]
                if value["tokenMaterials"][cells[point]]["role"] != role:
                    raise ValueError("Native moving material differs from its static shape token")
                membership.add(point)
        if len(membership) != spec["members"]:
            raise ValueError("Approved unit-block membership differs: " + value["id"])
    return definitions


def scalar(group, time_ms):
    """The production Core Sampler track, with explicit integer-ms wrapping and units."""
    period = group["periodMs"]
    local = time_ms % period
    if group["kind"] == "rotate_y":
        return math.tau * local / period
    half = period // 2
    progress = local / half if local <= half else (period - local) / (period - half)
    return group["travel"] * progress * progress * (3 - 2 * progress)


def _point(group, point, time_ms, enabled=True):
    if not enabled:
        return tuple(point)
    amount = scalar(group, time_ms)
    if group["kind"] == "translate":
        axis = "xyz".index(group["axis"])
        return tuple(number + (amount if index == axis else 0) for index, number in enumerate(point))
    px, py, pz = group["pivot"]
    x, y, z = point[0] - px, point[1] - py, point[2] - pz
    return (px + math.cos(amount) * x + math.sin(amount) * z, py + y,
            pz - math.sin(amount) * x + math.cos(amount) * z)


def orient(point, facing, mirror):
    x, y, z = point
    if mirror:
        x = -x
    return {"north": (x, y, z), "east": (-z, y, x), "south": (-x, y, -z), "west": (z, y, -x)}[facing]


def golden(document, profile):
    definitions = validate_definitions(document, profile)
    cases, scalar_cases = [], []
    for definition in definitions:
        for group in definition["groups"]:
            for phase in profile["phases"]:
                time_ms = int(group["periodMs"] * phase)
                scalar_cases.append({"id": f'{definition["id"]}/{group["id"]}/{time_ms}',
                                     "definition": definition["id"], "group": group["id"], "timeMs": time_ms,
                                     "values": [scalar(group, time_ms)]})
        for phase, facing, mirror, quality in itertools.product(profile["phases"], profile["facings"], profile["mirrors"], profile["qualities"]):
            points = []
            for group in definition["groups"]:
                time_ms = int(group["periodMs"] * phase)
                for member in group["members"]:
                    center = tuple(member[key] for key in ("x", "y", "z"))
                    # All eight corners preserve full unit size, group pivot and the exact same time sample.
                    for corner in itertools.product((-0.5, 0.5), repeat=3):
                        point = _point(group, tuple(center[i] + corner[i] for i in range(3)), time_ms, quality != "OFF")
                        points.extend(orient(point, facing, mirror))
            cases.append({"id": f'{definition["id"]}/phase={phase}/{facing}/mirror={str(mirror).lower()}/{quality}', "values": points})
    return {"schema": SCHEMA, "status": "PASS", "kind": "pose_oracle", "definitionContentSha256": sha(canonical(document)),
            "cases": cases, "scalarCases": scalar_cases,
            "boundary": "Pure full-size unit-block corners; four horizontal facings and horizontal mirror. Controller+0.5 is applied once by the world backend. No world/GL/client claim."}


def _overlap(a, b):
    return all(min(a[i + 3], b[i + 3]) - max(a[i], b[i]) > EPSILON for i in range(3))


def _box(point, travel=(0, 0, 0)):
    return tuple(point[i] + min(0, travel[i]) - .5 for i in range(3)) + tuple(point[i] + max(0, travel[i]) + .5 for i in range(3))


def _relative_collision(a, b, travel_a, travel_b):
    lower, upper = 0.0, 1.0
    for index in range(3):
        start = b[index] - a[index]
        delta = travel_b[index] - travel_a[index]
        if abs(delta) < EPSILON:
            if abs(start) >= 1 - EPSILON:
                return False
        else:
            first, last = sorted(((-1 - start) / delta, (1 - start) / delta))
            lower, upper = max(lower, first), min(upper, last)
    return upper - lower > EPSILON


def continuous_sweep(document, profile):
    definitions = validate_definitions(document, profile)
    results = []
    for definition in definitions:
        cells = shape_cells(definition)
        groups = definition["groups"]
        dynamic = {(member["x"], member["y"], member["z"]) for group in groups for member in group["members"]}
        fixed = set(cells) - dynamic
        collisions, world_checks = [], set()
        radial_margin = None
        contacts = 0
        translations = []
        for group in groups:
            members = [tuple(member[key] for key in ("x", "y", "z")) for member in group["members"]]
            if group["kind"] == "translate":
                travel = tuple(group["travel"] if axis == group["axis"] else 0 for axis in "xyz")
                translations.append((members, travel, group["periodMs"]))
                for member in members:
                    swept = _box(member, travel)
                    for static in fixed:
                        if _overlap(swept, _box(static)):
                            collisions.append({"group": group["id"], "member": member, "fixed": static})
                        elif all(min(swept[i + 3], static[i] + .5) - max(swept[i], static[i] - .5) >= -EPSILON for i in range(3)):
                            contacts += 1
                    ranges = [range(math.floor(swept[i] + .5), math.ceil(swept[i + 3] + .5)) for i in range(3)]
                    for point in itertools.product(*ranges):
                        if point not in dynamic and _overlap(swept, _box(point)):
                            world_checks.add(point)
            else:
                px, _, pz = group["pivot"]
                radius = max(math.hypot(member[0] - px + dx, member[2] - pz + dz)
                             for member in members for dx, dz in itertools.product((-.5, .5), repeat=2))
                nearest = math.inf
                for member in members:
                    for static in fixed:
                        if abs(member[1] - static[1]) >= 1 - EPSILON:
                            if abs(member[1] - static[1]) == 1:
                                contacts += 1
                            continue
                        distance = math.hypot(max(abs(static[0] - px) - .5, 0), max(abs(static[2] - pz) - .5, 0))
                        nearest = min(nearest, distance)
                radial_margin = nearest - radius
                if radial_margin < -EPSILON:
                    collisions.append({"group": group["id"], "radialMargin": radial_margin})
                for y in {member[1] for member in members}:
                    for x in range(math.floor(px - radius - .5), math.ceil(px + radius + .5) + 1):
                        for z in range(math.floor(pz - radius - .5), math.ceil(pz + radius + .5) + 1):
                            if (x, y, z) not in dynamic and math.hypot(max(abs(x - px) - .5, 0), max(abs(z - pz) - .5, 0)) < radius:
                                world_checks.add((x, y, z))
        for (members_a, travel_a, period_a), (members_b, travel_b, period_b) in itertools.combinations(translations, 2):
            if period_a != period_b:
                raise ValueError("Cross-group continuous proof requires a shared clock")
            for a, b in itertools.product(members_a, members_b):
                if _relative_collision(a, b, travel_a, travel_b):
                    collisions.append({"dynamicA": a, "dynamicB": b})
        results.append({"id": definition["id"], "members": len(dynamic), "staticCells": len(fixed),
                        "status": "FAIL" if collisions else "PASS", "collisions": collisions,
                        "radialMarginBlocks": radial_margin, "fixedFaceOrEdgeContacts": contacts,
                        "worldOccupancyRequired": [list(point) for point in sorted(world_checks)],
                        "clearanceClaim": "No positive-volume overlap over the full continuous path; fixed face contact is allowed, not positive mechanical tolerance.",
                        "worldOccupancyStatus": "NOT_RUN"})
    return {"schema": SCHEMA, "status": "PASS" if all(value["status"] == "PASS" for value in results) else "FAIL",
            "kind": "continuous_geometry_contract", "definitionContentSha256": sha(canonical(document)), "machines": results,
            "boundary": "Exact swept AABB/relative paths for translations and a sufficient full-circle radial bound for rotation; orthogonal facing/mirror preserves overlap. Actual loaded-world blockers, supports, covers, legal facing and GL are NOT_RUN."}
