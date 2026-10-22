"""Read both the canonical material schema and historical flat palettes."""


def expand_palette(document):
    if 'schemaVersion' not in document:
        return document
    if document['schemaVersion'] != 2:
        raise ValueError('Unsupported critical material schema')
    definitions = document['definitions']
    for identifier, properties in definitions.items():
        if properties['id'] != identifier:
            raise ValueError('Material definition key differs from id: ' + identifier)
    return {alias: definitions[identifier] for alias, identifier in document['aliases'].items()}


def normalize_palette(palette):
    definitions = {}
    aliases = {}
    for alias, properties in palette.items():
        identifier = properties['id']
        if identifier in definitions and definitions[identifier] != properties:
            raise ValueError('Conflicting material alias: ' + alias)
        definitions.setdefault(identifier, properties)
        aliases[alias] = identifier
    return {'schemaVersion': 2, 'definitions': definitions, 'aliases': aliases}
