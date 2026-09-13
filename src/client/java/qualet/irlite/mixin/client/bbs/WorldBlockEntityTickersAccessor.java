package qualet.irlite.mixin.client.bbs;

import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.TickingBlockEntity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

@Mixin(Level.class)
public interface WorldBlockEntityTickersAccessor
{
    @Accessor("blockEntityTickers")
    List<TickingBlockEntity> irlite$getBlockEntityTickers();
}
