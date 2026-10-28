package velrondevs.botania.common.world.structure;

import velrondevs.botania.registry.BotaniaStructures;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.TemplateStructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockIgnoreProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

public final class SupportedTemplatePiece extends TemplateStructurePiece {
    private static StructurePlaceSettings settings(Rotation rotation) {
        // Template air must not excavate surrounding terrain, plants or structures.
        return new StructurePlaceSettings().setRotation(rotation).addProcessor(BlockIgnoreProcessor.STRUCTURE_AND_AIR);
    }

    public SupportedTemplatePiece(StructureTemplateManager manager, ResourceLocation id, BlockPos pos, Rotation rotation) {
        super(BotaniaStructures.TEMPLATE, 0, manager, id, id.toString(), settings(rotation), pos);
    }

    public SupportedTemplatePiece(StructurePieceSerializationContext context, CompoundTag tag) {
        super(BotaniaStructures.TEMPLATE, tag, context.structureTemplateManager(),
            id -> settings(Rotation.valueOf(tag.getString("Rotation"))));
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        super.addAdditionalSaveData(context, tag);
        tag.putString("Rotation", getRotation().name());
    }

    @Override
    public void postProcess(WorldGenLevel level, StructureManager manager, ChunkGenerator generator,
                            RandomSource random, BoundingBox clipping, ChunkPos chunkPos, BlockPos pivot) {
        // All chunks use the same references and whole bounding box for this decision.
        var anchor = new ChunkPos(templatePosition);
        for (var start : manager.startsForStructure(anchor, structure -> true)) {
            if (!start.isValid() || !start.getBoundingBox().intersects(boundingBox)) continue;
            for (var other : start.getPieces()) {
                if (other instanceof SupportedTemplatePiece own && own.templatePosition.equals(templatePosition)
                    && own.templateName.equals(templateName)) continue;
                if (other.getBoundingBox().intersects(boundingBox)) return;
            }
        }
        super.postProcess(level, manager, generator, random, clipping, chunkPos, pivot);
    }

    @Override
    protected void handleDataMarker(String name, BlockPos pos, ServerLevelAccessor level, RandomSource random, BoundingBox box) {}
}
