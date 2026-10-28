package velrondevs.botania.test;

import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.TerrainAdjustment;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.structures.JigsawStructure;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import velrondevs.botania.common.world.structure.SupportedSurfaceStructure;
import velrondevs.botania.common.world.structure.SupportedTemplatePiece;
import velrondevs.botania.registry.BotaniaStructures;

@GameTestHolder("botania_structure_tests")
@PrefixGameTestTemplate(false)
public final class SupportedStructureGameTests {
    private static final ResourceLocation PROBE = ResourceLocation.parse("botania_structure_tests:test/placement_probe");

    @GameTest(template = "test/platform")
    public static void nativeRegistrationWithoutHelper(GameTestHelper helper) {
        helper.assertFalse(ModList.get().isLoaded("bbtt_fixes"), "This migration suite must run without the helper mod");
        helper.assertTrue(BuiltInRegistries.STRUCTURE_TYPE.get(BotaniaStructures.SUPPORTED_ID) == BotaniaStructures.SUPPORTED,
            "Native structure type is missing");
        helper.assertTrue(BuiltInRegistries.STRUCTURE_PIECE.get(BotaniaStructures.SUPPORTED_ID) == BotaniaStructures.TEMPLATE,
            "Native piece type is missing");
        helper.assertTrue(BuiltInRegistries.STRUCTURE_TYPE.containsKey(BotaniaStructures.LEGACY_SUPPORTED_ID),
            "Legacy structure decoder alias is missing");
        helper.assertTrue(BuiltInRegistries.STRUCTURE_PIECE.containsKey(BotaniaStructures.LEGACY_SUPPORTED_ID),
            "Legacy piece decoder alias is missing");
        var level = helper.getLevel();
        var structures = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        for (String category : List.of("ruin", "plant", "mushroom", "broken_tree", "lake")) {
            var structure = structures.get(ResourceLocation.parse("botania:exploration_" + category));
            helper.assertTrue(structure instanceof SupportedSurfaceStructure, "Native type not used by " + category);
            helper.assertTrue(structure.terrainAdaptation() == TerrainAdjustment.NONE, "Terrain adaptation changed for " + category);
            var stub = ((SupportedSurfaceStructure) structure).findGenerationPoint(context(helper));
            helper.assertTrue(stub.isPresent(), "Dry flat terrain rejected for " + category);
            int anchorY = stub.get().position().getY();
            int baseY = stub.get().getPiecesBuilder().build().calculateBoundingBox().minY();
            helper.assertTrue(baseY == anchorY - (category.equals("lake") ? 1 : 0), "Foundation height changed for " + category);
        }
        helper.succeed();
    }

    @GameTest(template = "test/platform")
    public static void drySupportMatrix(GameTestHelper helper) {
        var air = Blocks.AIR.defaultBlockState();
        helper.assertTrue(SupportedSurfaceStructure.hasSupport(Blocks.GRASS_BLOCK.defaultBlockState(), air), "Grass rejected");
        helper.assertTrue(SupportedSurfaceStructure.hasSupport(Blocks.STONE.defaultBlockState(), air), "Stone rejected");
        helper.assertFalse(SupportedSurfaceStructure.hasSupport(Blocks.WATER.defaultBlockState(), air), "Water accepted as support");
        helper.assertFalse(SupportedSurfaceStructure.hasSupport(Blocks.LAVA.defaultBlockState(), air), "Lava accepted as support");
        helper.assertFalse(SupportedSurfaceStructure.hasSupport(air, air), "Air accepted as support");
        helper.assertFalse(SupportedSurfaceStructure.hasSupport(Blocks.OAK_LEAVES.defaultBlockState(), air), "Canopy accepted as ground");
        helper.assertFalse(SupportedSurfaceStructure.hasSupport(Blocks.GRASS_BLOCK.defaultBlockState(), Blocks.WATER.defaultBlockState()), "Submerged ground accepted");
        helper.assertFalse(SupportedSurfaceStructure.hasSupport(Blocks.STONE.defaultBlockState(), Blocks.STONE.defaultBlockState()), "Cliff accepted");
        helper.succeed();
    }

    @GameTest(template = "test/platform")
    public static void foundationUsesSurfaceHeight(GameTestHelper helper) {
        var context = context(helper);
        var settings = new Structure.StructureSettings(HolderSet.direct(helper.getLevel().getBiome(helper.absolutePos(BlockPos.ZERO))),
            Map.of(), GenerationStep.Decoration.SURFACE_STRUCTURES, TerrainAdjustment.NONE);
        int surface = context.chunkGenerator().getFirstFreeHeight(context.chunkPos().getMiddleBlockX(),
            context.chunkPos().getMiddleBlockZ(), Heightmap.Types.OCEAN_FLOOR_WG, context.heightAccessor(), context.randomState());
        for (boolean groundLayer : List.of(false, true)) {
            var structure = new SupportedSurfaceStructure(settings, List.of(PROBE), groundLayer);
            var stub = structure.findGenerationPoint(context);
            helper.assertTrue(stub.isPresent(), "Flat dry terrain rejected");
            helper.assertTrue(stub.get().getPiecesBuilder().build().calculateBoundingBox().minY() == surface - (groundLayer ? 1 : 0),
                "Foundation offset changed");
        }
        helper.succeed();
    }

    @GameTest(template = "test/platform")
    public static void oldTypeAndPieceNbtLoadAndSaveCanonically(GameTestHelper helper) {
        var level = helper.getLevel();
        var operations = RegistryOps.create(JsonOps.INSTANCE, level.registryAccess());
        var structure = level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(ResourceLocation.parse("botania:exploration_ruin"));
        JsonObject oldJson = Structure.DIRECT_CODEC.encodeStart(operations, structure).getOrThrow().getAsJsonObject();
        oldJson.addProperty("type", BotaniaStructures.LEGACY_SUPPORTED_ID.toString());
        var restored = Structure.DIRECT_CODEC.parse(operations, oldJson).getOrThrow();
        helper.assertTrue(restored instanceof SupportedSurfaceStructure, "Old structure type could not decode");
        helper.assertTrue(Structure.DIRECT_CODEC.encodeStart(operations, restored).getOrThrow().getAsJsonObject()
            .get("type").getAsString().equals(BotaniaStructures.SUPPORTED_ID.toString()), "Old type did not re-save canonically");
        var context = StructurePieceSerializationContext.fromLevel(level);
        for (Rotation rotation : Rotation.values()) {
            var piece = piece(helper, helper.absolutePos(new BlockPos(7, 3, 7)), rotation);
            var tag = piece.createTag(context);
            helper.assertTrue(tag.getString("id").equals(BotaniaStructures.SUPPORTED_ID.toString()), "New piece uses old ID");
            tag.putString("id", BotaniaStructures.LEGACY_SUPPORTED_ID.toString());
            var pieces = new ListTag();
            pieces.add(tag);
            var loaded = PiecesContainer.load(pieces, context).pieces();
            helper.assertTrue(loaded.size() == 1 && loaded.getFirst() instanceof SupportedTemplatePiece, "Actual legacy piece registry decode dropped the piece");
            var migrated = (SupportedTemplatePiece) loaded.getFirst();
            helper.assertTrue(migrated.getRotation() == rotation, "Rotation lost in old NBT");
            helper.assertTrue(migrated.getBoundingBox().equals(piece.getBoundingBox()), "Footprint changed in old NBT");
            helper.assertTrue(migrated.createTag(context).getString("id").equals(BotaniaStructures.SUPPORTED_ID.toString()),
                "Migrated piece did not save the native ID");
        }
        helper.succeed();
    }

    @GameTest(template = "test/platform")
    public static void rotatedTemplatePreservesAirAndIgnoresItsOwnStart(GameTestHelper helper) {
        var origin = helper.absolutePos(new BlockPos(7, 3, 7));
        for (Rotation rotation : Rotation.values()) {
            var piece = piece(helper, origin, rotation);
            prepareProbe(helper, origin, rotation);
            withStart(helper, origin, List.of(piece), () -> place(helper, piece, origin));
            helper.assertTrue(helper.getLevel().getBlockState(origin).is(Blocks.GOLD_BLOCK), "Solid block was not placed for " + rotation);
            helper.assertTrue(helper.getLevel().getBlockState(origin.offset(new BlockPos(1, 0, 0).rotate(rotation))).is(Blocks.OBSIDIAN),
                "Template air excavated the world for " + rotation);
            helper.assertTrue(helper.getLevel().getBlockState(origin.offset(new BlockPos(2, 0, 0).rotate(rotation))).is(Blocks.OBSIDIAN),
                "Structure marker replaced the world for " + rotation);
        }
        helper.succeed();
    }

    @GameTest(template = "test/platform")
    public static void realStructureReferencesRejectIntersections(GameTestHelper helper) {
        var origin = helper.absolutePos(new BlockPos(7, 3, 7));
        var piece = piece(helper, origin, Rotation.NONE);
        var blocker = piece(helper, origin.east(), Rotation.NONE);
        prepareProbe(helper, origin, Rotation.NONE);
        withStart(helper, origin, List.of(blocker), () -> place(helper, piece, origin));
        helper.assertTrue(helper.getLevel().getBlockState(origin).is(Blocks.OBSIDIAN), "Intersecting structure was overwritten");
        helper.succeed();
    }

    @GameTest(template = "test/platform")
    public static void floatingIslandTemplatesAreUnchanged(GameTestHelper helper) {
        var level = helper.getLevel();
        helper.assertTrue(level.registryAccess().registryOrThrow(Registries.STRUCTURE)
            .get(ResourceLocation.parse("botania:exploration_island")) instanceof JigsawStructure, "Island was converted to a grounded template");
        for (int index : List.of(1, 2)) {
            var template = level.getStructureManager().getOrCreate(ResourceLocation.parse("botania:exploration/island_" + index));
            helper.assertTrue(template.getSize().getY() == 25, "Island height changed");
            var settings = new StructurePlaceSettings();
            var grass = template.filterBlocks(BlockPos.ZERO, settings, Blocks.GRASS_BLOCK);
            helper.assertTrue(grass.size() == 42 && grass.stream().allMatch(info -> info.pos().getY() == 19), "Island turf or removed bottom platform changed");
            var water = template.filterBlocks(BlockPos.ZERO, settings, Blocks.WATER);
            helper.assertTrue(water.size() == 26 && water.stream().anyMatch(info -> info.pos().getY() == 0)
                && water.stream().anyMatch(info -> info.pos().getY() == 19), "Island waterfall changed");
        }
        helper.succeed();
    }

    private static Structure.GenerationContext context(GameTestHelper helper) {
        var level = helper.getLevel();
        var generator = level.getChunkSource().getGenerator();
        return new Structure.GenerationContext(level.registryAccess(), generator, generator.getBiomeSource(),
            level.getChunkSource().randomState(), level.getStructureManager(), level.getSeed(),
            new ChunkPos(helper.absolutePos(new BlockPos(4, 2, 4))), level, biome -> true);
    }

    private static SupportedTemplatePiece piece(GameTestHelper helper, BlockPos origin, Rotation rotation) {
        return new SupportedTemplatePiece(helper.getLevel().getStructureManager(), PROBE, origin, rotation);
    }

    private static void prepareProbe(GameTestHelper helper, BlockPos origin, Rotation rotation) {
        for (int x = 0; x < 3; x++) helper.getLevel().setBlock(origin.offset(new BlockPos(x, 0, 0).rotate(rotation)), Blocks.OBSIDIAN.defaultBlockState(), 3);
    }

    private static void place(GameTestHelper helper, SupportedTemplatePiece piece, BlockPos origin) {
        var level = helper.getLevel();
        piece.postProcess(level, level.structureManager(), level.getChunkSource().getGenerator(), RandomSource.create(2710L),
            new BoundingBox(origin.getX() - 8, origin.getY() - 1, origin.getZ() - 8, origin.getX() + 8, origin.getY() + 4, origin.getZ() + 8),
            new ChunkPos(origin), origin);
    }

    private static void withStart(GameTestHelper helper, BlockPos origin, List<SupportedTemplatePiece> pieces, Runnable action) {
        var level = helper.getLevel();
        var chunkPos = new ChunkPos(origin);
        var chunk = level.getChunk(chunkPos.x, chunkPos.z);
        var starts = new HashMap<>(chunk.getAllStarts());
        Map<Structure, LongSet> references = new HashMap<>();
        chunk.getAllReferences().forEach((structure, refs) -> references.put(structure, new LongOpenHashSet(refs)));
        var structure = level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(ResourceLocation.parse("botania:exploration_ruin"));
        try {
            var start = new StructureStart(structure, chunkPos, 0, new PiecesContainer(List.copyOf(pieces)));
            chunk.setStartForStructure(structure, start);
            chunk.addReferenceForStructure(structure, chunkPos.toLong());
            action.run();
        } finally {
            chunk.setAllStarts(starts);
            chunk.setAllReferences(references);
        }
    }
}
