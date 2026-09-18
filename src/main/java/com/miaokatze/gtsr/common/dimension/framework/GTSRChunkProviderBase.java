package com.miaokatze.gtsr.common.dimension.framework;

import java.util.List;
import java.util.Random;

import net.minecraft.block.Block;
import net.minecraft.block.BlockFalling;
import net.minecraft.entity.EnumCreatureType;
import net.minecraft.init.Blocks;
import net.minecraft.util.IProgressUpdate;
import net.minecraft.world.ChunkPosition;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.terraingen.ChunkProviderEvent;
import net.minecraftforge.event.terraingen.PopulateChunkEvent;

import cpw.mods.fml.common.eventhandler.Event.Result;

/**
 * 新维度 IChunkProvider 模板（dim1 S1，蓝本 GT5U ChunkProviderModded.java:37/98-130/409-474）。
 * <p>
 * provideChunk 主流程：确定性 chunk 种子 → {@link #generateTerrain}（子类钩子）→
 * {@link #replaceBlocksForBiome}（ChunkProviderEvent.ReplaceBiomeBlocks + 群系 surface 应用）→
 * Chunk 组装（biome 数组 + 天空光照）。populate：确定性种子 + PopulateChunkEvent Pre/Post +
 * {@link #onPopulate} 钩子。S1 默认 generateTerrain 生成基岩底 + 石层平台，保证空群系表下
 * 维度可运行；S2/S6a 子类覆写为专属噪声地形。
 * <p>
 * 结构生成不经本类：IWorldGenerator 由 S4/S6 注册（本切片硬边界）。
 */
public class GTSRChunkProviderBase implements IChunkProvider {

    protected final World worldObj;
    /** 确定性种子（构造入参，已含 def.seedSalt），populate 每次重播种用。 */
    private final long seed;
    protected final Random rand;

    public GTSRChunkProviderBase(World world, long seed) {
        this.worldObj = world;
        this.seed = seed;
        this.rand = new Random(seed);
    }

    /**
     * 地形填充钩子（子类覆写）。S1 默认：y=0 基岩 + y=1..64 石层，其余空气。
     * Block[] 下标约定 x<<12 | z<<8 | y（与原版 ChunkProviderGenerate 一致）。
     */
    protected void generateTerrain(int chunkX, int chunkZ, Block[] blocks, byte[] metadata,
        BiomeGenBase[] biomesForGeneration) {
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                final int column = x << 12 | z << 8;
                blocks[column] = Blocks.bedrock;
                for (int y = 1; y <= 64; y++) {
                    blocks[column | y] = Blocks.stone;
                }
            }
        }
    }

    /** populate 钩子（子类覆写）：装饰/湖/植被等，本切片留空（结构走 IWorldGenerator 通道）。 */
    protected void onPopulate(Random random, int chunkX, int chunkZ) {}

    /**
     * 原始 Block[] 数组"空"判定（<b>框架级约定</b>，v1.20.30 终验修复）：provideChunk 以
     * {@code new Block[65536]} 建列——未写入槽位为 <b>null</b>（{@code Blocks.air} 实例仅存在于
     * 已写入槽位；{@code new Chunk(world, blocks, ...)} 构造时才把 null 视作空气）。
     * 任何在 Chunk 组装前扫描"裸露面/上方空气"的子类必须走本判定，
     * 直接比较 {@code == Blocks.air} 会把空气误判为实体（dim78/dim79 表层替换 no-op 事故根因）。
     */
    protected static boolean isAirOrEmpty(Block block) {
        return block == null || block == Blocks.air;
    }

    /**
     * 群系 surface 应用（GT5U replaceBlocksForBiome 同款）：POST ReplaceBiomeBlocks 事件后
     * 按列调用 biome.genTerrainBlocks（topBlock/fillerBlock 替换）。
     */
    protected void replaceBlocksForBiome(int chunkX, int chunkZ, Block[] blocks, byte[] metadata,
        BiomeGenBase[] biomes) {
        ChunkProviderEvent.ReplaceBiomeBlocks event = new ChunkProviderEvent.ReplaceBiomeBlocks(
            this,
            chunkX,
            chunkZ,
            blocks,
            metadata,
            biomes,
            null);
        MinecraftForge.EVENT_BUS.post(event);
        if (event.getResult() == Result.DENY) {
            return;
        }
        for (int x = 0; x < 16; ++x) {
            for (int z = 0; z < 16; ++z) {
                BiomeGenBase biome = biomes[z + x * 16];
                biome.genTerrainBlocks(
                    this.worldObj,
                    this.rand,
                    blocks,
                    metadata,
                    chunkX * 16 + x,
                    chunkZ * 16 + z,
                    0.0D);
            }
        }
    }

    @Override
    public Chunk provideChunk(int chunkX, int chunkZ) {
        this.rand.setSeed(chunkX * 341873128712L + chunkZ * 132897987541L);
        Block[] blocks = new Block[65536];
        byte[] metadata = new byte[65536];
        generateTerrain(chunkX, chunkZ, blocks, metadata, null);
        BiomeGenBase[] biomes = this.worldObj.getWorldChunkManager()
            .loadBlockGeneratorData(null, chunkX * 16, chunkZ * 16, 16, 16);
        replaceBlocksForBiome(chunkX, chunkZ, blocks, metadata, biomes);

        Chunk chunk = new Chunk(this.worldObj, blocks, metadata, chunkX, chunkZ);
        byte[] chunkBiomes = chunk.getBiomeArray();
        for (int i = 0; i < chunkBiomes.length; ++i) {
            chunkBiomes[i] = (byte) biomes[i].biomeID;
        }
        chunk.generateSkylightMap();
        return chunk;
    }

    @Override
    public void populate(IChunkProvider chunkProvider, int chunkX, int chunkZ) {
        BlockFalling.fallInstantly = false;
        // 确定性 populate 种子：世界种子派生长奇数混合 chunk 坐标（GT5U/原版范式，掺入维度 salt）
        this.rand.setSeed(this.seed);
        long a = this.rand.nextLong() / 2L * 2L + 1L;
        long b = this.rand.nextLong() / 2L * 2L + 1L;
        this.rand.setSeed(chunkX * a + chunkZ * b ^ this.seed);
        boolean hasVillage = false;

        MinecraftForge.EVENT_BUS
            .post(new PopulateChunkEvent.Pre(chunkProvider, this.worldObj, this.rand, chunkX, chunkZ, hasVillage));

        onPopulate(this.rand, chunkX, chunkZ);

        MinecraftForge.EVENT_BUS
            .post(new PopulateChunkEvent.Post(chunkProvider, this.worldObj, this.rand, chunkX, chunkZ, hasVillage));

        BlockFalling.fallInstantly = false;
    }

    @Override
    public boolean chunkExists(int chunkX, int chunkZ) {
        return true;
    }

    @Override
    public Chunk loadChunk(int chunkX, int chunkZ) {
        return provideChunk(chunkX, chunkZ);
    }

    @Override
    public boolean saveChunks(boolean all, IProgressUpdate progress) {
        return true;
    }

    @Override
    public void saveExtraData() {}

    @Override
    public boolean unloadQueuedChunks() {
        return false;
    }

    @Override
    public boolean canSave() {
        return true;
    }

    @Override
    public String makeString() {
        return "GTSRBaseLevelSource";
    }

    @Override
    public List<BiomeGenBase.SpawnListEntry> getPossibleCreatures(EnumCreatureType creatureType, int x, int y, int z) {
        BiomeGenBase biome = this.worldObj.getBiomeGenForCoords(x, z);
        return biome != null ? biome.getSpawnableList(creatureType) : null;
    }

    @Override
    public ChunkPosition func_147416_a(World world, String structureName, int x, int y, int z) {
        return null;
    }

    @Override
    public int getLoadedChunkCount() {
        return 0;
    }

    @Override
    public void recreateStructures(int chunkX, int chunkZ) {}
}
