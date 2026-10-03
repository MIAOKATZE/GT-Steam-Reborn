"""Fresh production sources required by the normal native terrain fixture."""
def native_sources(root):
    base = root / 'src/main/java/com/miaokatze/gtsr/common/dimension'
    paths = [root / 'tools/prosperity/NativeTerrainFixture.java',
             base / 'framework/GTSRBiomeAuthority.java',
             base / 'framework/GTSRBiomeBase.java',
             base / 'prosperity/ProsperityTerrainProfile.java',
             base / 'prosperity/TerrainVariants.java',
             base / 'prosperity/ChunkProviderProsperityRuins.java',
             base / 'prosperity/river/GTSRVoronoiRiverField.java',
             base / 'prosperity/river/GTSRRiverPlacer.java',
             base / 'framework/structure/GTSRWorldgenHash.java']
    paths += sorted((base / 'framework/genlayer').glob('*.java'))
    paths += [base / ('prosperity/biome/' + name + '.java') for name in (
        'BiomeRustedSteppe', 'BiomeGearworkForest', 'BiomeBrassWastes',
        'BiomeFumaroleSwamp', 'BiomeSanzuRiver', 'BiomeWitheredRiverbed')]
    for name in ('RemasterSite', 'RemasterPlanner', 'RemasterData', 'RemasterCityTerrain',
                 'RemasterSavedPlacement', 'RemasterCityBridge', 'RemasterEntryApron'):
        related = base / ('prosperity/remaster/' + name + '.java')
        if related.exists():
            paths.append(related)
    return paths


def isolated_sourcepath(folder):
    path = folder / 'empty-source'
    path.mkdir(exist_ok=True)
    return path.as_posix()
