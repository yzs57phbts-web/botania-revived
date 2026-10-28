package velrondevs.botania.common.world.structure;

import velrondevs.botania.registry.BotaniaStructures;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;

/** Decorative templates require actual dry ground across their whole footprint. */
public final class SupportedSurfaceStructure extends Structure {
    public static final MapCodec<SupportedSurfaceStructure> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
        settingsCodec(instance),
        ResourceLocation.CODEC.listOf().fieldOf("templates").forGetter(value -> value.templates),
        Codec.BOOL.optionalFieldOf("ground_layer", false).forGetter(value -> value.groundLayer)
    ).apply(instance, SupportedSurfaceStructure::new));
    private final List<ResourceLocation> templates;
    private final boolean groundLayer;

    public SupportedSurfaceStructure(StructureSettings settings, List<ResourceLocation> templates, boolean groundLayer) {
        super(settings);
        if (templates.isEmpty()) throw new IllegalArgumentException("At least one template is required");
        this.templates = List.copyOf(templates);
        this.groundLayer = groundLayer;
    }

    @Override
    public Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        ResourceLocation id = templates.get(context.random().nextInt(templates.size()));
        var template = context.structureTemplateManager().get(id);
        if (template.isEmpty()) return Optional.empty();
        Rotation rotation = Rotation.getRandom(context.random());
        int x = context.chunkPos().getMiddleBlockX();
        int z = context.chunkPos().getMiddleBlockZ();
        int surface = context.chunkGenerator().getFirstFreeHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG,
            context.heightAccessor(), context.randomState());
        var piece = new SupportedTemplatePiece(context.structureTemplateManager(), id,
            new BlockPos(x, surface - (groundLayer ? 1 : 0), z), rotation);
        var box = piece.getBoundingBox();
        for (int px = box.minX(); px <= box.maxX(); px++) {
            for (int pz = box.minZ(); pz <= box.maxZ(); pz++) {
                var column = context.chunkGenerator().getBaseColumn(px, pz, context.heightAccessor(), context.randomState());
                if (!hasSupport(column.getBlock(surface - 1), column.getBlock(surface))) return Optional.empty();
            }
        }
        return Optional.of(new GenerationStub(new BlockPos(x, surface, z), builder -> builder.addPiece(piece)));
    }

    public static boolean hasSupport(net.minecraft.world.level.block.state.BlockState below,
                                     net.minecraft.world.level.block.state.BlockState above) {
        return below.isSolid() && below.getFluidState().isEmpty() && !below.is(net.minecraft.tags.BlockTags.LEAVES)
            && above.canBeReplaced() && above.getFluidState().isEmpty();
    }

    @Override
    public StructureType<?> type() { return BotaniaStructures.SUPPORTED; }
}
