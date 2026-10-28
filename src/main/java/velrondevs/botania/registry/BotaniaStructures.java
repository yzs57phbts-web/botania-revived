package velrondevs.botania.registry;

import java.util.function.BiConsumer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.neoforged.fml.ModList;
import velrondevs.botania.common.world.structure.SupportedSurfaceStructure;
import velrondevs.botania.common.world.structure.SupportedTemplatePiece;

public final class BotaniaStructures {
    public static final ResourceLocation SUPPORTED_ID = ResourceLocation.fromNamespaceAndPath("botania", "supported_template");
    public static final ResourceLocation LEGACY_SUPPORTED_ID = ResourceLocation.fromNamespaceAndPath("bbtt_fixes", "supported_template");
    public static final StructureType<SupportedSurfaceStructure> SUPPORTED = () -> SupportedSurfaceStructure.CODEC;
    public static final StructurePieceType TEMPLATE = SupportedTemplatePiece::new;

    private BotaniaStructures() {
    }

    public static void registerStructureTypes(BiConsumer<StructureType<?>, ResourceLocation> register) {
        register.accept(SUPPORTED, SUPPORTED_ID);
        if (!ModList.get().isLoaded("bbtt_fixes")) {
            StructureType<SupportedSurfaceStructure> legacy = () -> SupportedSurfaceStructure.CODEC;
            register.accept(legacy, LEGACY_SUPPORTED_ID);
        }
    }

    public static void registerPieceTypes(BiConsumer<StructurePieceType, ResourceLocation> register) {
        register.accept(TEMPLATE, SUPPORTED_ID);
        if (!ModList.get().isLoaded("bbtt_fixes")) {
            register.accept(SupportedTemplatePiece::new, LEGACY_SUPPORTED_ID);
        }
    }
}
