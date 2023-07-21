/*
 * BluSunrize
 * Copyright (c) 2020
 *
 * This code is licensed under "Blu's License of Common Sense"
 * Details can be found in the license file in the root folder of this project
 */

package blusunrize.immersiveengineering.common.blocks.metal;

import blusunrize.immersiveengineering.api.IEEnums.IOSideConfig;
import blusunrize.immersiveengineering.api.Lib;
import blusunrize.immersiveengineering.api.utils.CapabilityReference;
import blusunrize.immersiveengineering.api.utils.DirectionalBlockPos;
import blusunrize.immersiveengineering.api.wires.ConnectionPoint;
import blusunrize.immersiveengineering.api.wires.WireType;
import blusunrize.immersiveengineering.api.wires.redstone.CapabilityRedstoneNetwork;
import blusunrize.immersiveengineering.api.wires.redstone.CapabilityRedstoneNetwork.RedstoneBundleConnection;
import blusunrize.immersiveengineering.api.wires.redstone.IRedstoneConnector;
import blusunrize.immersiveengineering.api.wires.redstone.RedstoneNetworkHandler;
import blusunrize.immersiveengineering.common.blocks.IEBlockInterfaces.IBlockBounds;
import blusunrize.immersiveengineering.common.blocks.IEBlockInterfaces.IBlockOverlayText;
import blusunrize.immersiveengineering.common.blocks.IEBlockInterfaces.IScrewdriverInteraction;
import blusunrize.immersiveengineering.common.blocks.IEBlockInterfaces.IStateBasedDirectional;
import blusunrize.immersiveengineering.common.blocks.PlacementLimitation;
import blusunrize.immersiveengineering.common.blocks.generic.ConnectorBlock;
import blusunrize.immersiveengineering.common.blocks.generic.ImmersiveConnectableBlockEntity;
import blusunrize.immersiveengineering.common.blocks.ticking.IEServerTickableBE;
import blusunrize.immersiveengineering.common.blocks.wooden.LogicUnitBlockEntity;
import blusunrize.immersiveengineering.common.register.IEBlockEntities;
import blusunrize.immersiveengineering.common.util.Utils;
import com.google.common.collect.ImmutableList;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

import static blusunrize.immersiveengineering.api.wires.WireType.REDSTONE_CATEGORY;

public class ConnectorBundledBlockEntity extends ImmersiveConnectableBlockEntity implements IEServerTickableBE, IStateBasedDirectional,
		IScrewdriverInteraction, IBlockBounds, IBlockOverlayText, IRedstoneConnector
{
	public IOSideConfig ioMode = IOSideConfig.NONE;
	public boolean connectedToLogicUnit = false;
	
	public static final List<IBundledProvider> EXTRA_SOURCES = new ArrayList<>();

	public ConnectorBundledBlockEntity(BlockPos pos, BlockState state)
	{
		this(IEBlockEntities.CONNECTOR_BUNDLED.get(), pos, state);
	}

	public ConnectorBundledBlockEntity(BlockEntityType<? extends ConnectorBundledBlockEntity> type, BlockPos pos, BlockState state)
	{
		super(type, pos, state);
	}

	private final CapabilityReference<RedstoneBundleConnection> attached = CapabilityReference.forBlockEntityAt(
			this, this::getAttachedFace, CapabilityRedstoneNetwork.REDSTONE_BUNDLE_CONNECTION
	);
	private boolean dirtyExtraSource = false;

	private DirectionalBlockPos getAttachedFace()
	{
		return new DirectionalBlockPos(worldPosition.relative(getFacing()), getFacing().getOpposite());
	}

	@Override
	public void tickServer()
	{
		RedstoneBundleConnection connection = attached.getNullable();
		if((connection!=null&&connection.pollDirty())||dirtyExtraSource)
		{
			getHandler().updateValues();
			dirtyExtraSource = false;
		}
	}

	public byte getValue(int redstoneChannel)
	{
		return getHandler().getValue(redstoneChannel);
	}

	private RedstoneNetworkHandler getHandler()
	{
		return Objects.requireNonNull(
				globalNet.getLocalNet(worldPosition).getHandler(RedstoneNetworkHandler.ID, RedstoneNetworkHandler.class)
		);
	}

	@Override
	public void onChange(ConnectionPoint cp, RedstoneNetworkHandler handler)
	{
		if(!level.isClientSide)
		{
			BlockEntity te = getLevelNonnull().getBlockEntity(getAttachedFace().position());
			connectedToLogicUnit = (te instanceof LogicUnitBlockEntity);
			RedstoneBundleConnection connection = attached.getNullable();
			if(connection!=null)
				connection.onChange(handler.getValuesExcluding(cp), getFacing().getOpposite());
			BlockState stateHere = level.getBlockState(worldPosition);
			markContainingBlockForUpdate(stateHere);
			markBlockForUpdate(worldPosition.relative(getFacing()), level.getBlockState(worldPosition.relative(getFacing())));
		}
	}

	@Override
	public void updateInput(byte[] signals, ConnectionPoint cp)
	{
		RedstoneBundleConnection connection = attached.getNullable();
		if(connection!=null)
			connection.updateInput(signals, getFacing().getOpposite());
		DirectionalBlockPos attachedTo = getAttachedFace();
		for(IBundledProvider source : EXTRA_SOURCES)
		{
			byte[] provided = source.getEmittedState(level, attachedTo.position(), attachedTo.side());
			if(provided!=null)
			{
				for(int color = 0; color < 16; color++)
					signals[color] = (byte)Math.max(signals[color], provided[color]);
			}
		}
	}

	@Override
	public boolean canConnectCable(WireType cableType, ConnectionPoint target, Vec3i offset)
	{
		return REDSTONE_CATEGORY.equals(cableType.getCategory());
	}

	@Override
	public Property<Direction> getFacingProperty()
	{
		return ConnectorBlock.DEFAULT_FACING_PROP;
	}

	@Override
	public PlacementLimitation getFacingLimitation()
	{
		return PlacementLimitation.SIDE_CLICKED;
	}

	@Override
	public boolean mirrorFacingOnPlacement(LivingEntity placer)
	{
		return true;
	}

	@Override
	public boolean canHammerRotate(Direction side, Vec3 hit, LivingEntity entity)
	{
		return false;
	}

	@Override
	public void writeCustomNBT(CompoundTag nbt, boolean descPacket)
	{
		super.writeCustomNBT(nbt, descPacket);
		nbt.putInt("ioMode", ioMode.ordinal());
		nbt.putBoolean("connectedToLogicUnit", connectedToLogicUnit);
	}

	@Override
	public void readCustomNBT(@Nonnull CompoundTag nbt, boolean descPacket)
	{
		super.readCustomNBT(nbt, descPacket);
		ioMode = IOSideConfig.VALUES[nbt.getInt("ioMode")];
		connectedToLogicUnit = nbt.getBoolean("connectedToLogicUnit");
	}

	@Override
	public Vec3 getConnectionOffset(ConnectionPoint here, ConnectionPoint other, WireType type)
	{
		Direction side = getFacing().getOpposite();
		double conRadius = type.getRenderDiameter()/2;
		return new Vec3(.5-conRadius*side.getStepX(), .5-conRadius*side.getStepY(), .5-conRadius*side.getStepZ());
	}

	@Override
	public VoxelShape getBlockBounds(@Nullable CollisionContext ctx)
	{
		return EnergyConnectorBlockEntity.getConnectorBounds(getFacing(), .625f);
	}

	@Override
	public Collection<ResourceLocation> getRequestedHandlers()
	{
		return ImmutableList.of(RedstoneNetworkHandler.ID);
	}

	@Override
	protected void onNeighborBlockChange(BlockPos otherPos)
	{
		super.onNeighborBlockChange(otherPos);
		DirectionalBlockPos attachedTo = getAttachedFace();
		if(!otherPos.equals(attachedTo.position())||attached.isPresent())
			return;
		BlockEntity te = getLevelNonnull().getBlockEntity(otherPos);
		connectedToLogicUnit = (te instanceof LogicUnitBlockEntity);
		byte[] overrideState = null;
		for(IBundledProvider source : EXTRA_SOURCES)
		{
			overrideState = source.getEmittedState(level, attachedTo.position(), attachedTo.side());
			if(overrideState!=null)
				break;
		}
		RedstoneNetworkHandler handler = getHandler();
		for(int color = 0; color < 16&&!dirtyExtraSource; ++color)
		{
			final byte current = handler.getValue(color);
			if(overrideState!=null)
				dirtyExtraSource = current==overrideState[color];
			else
				dirtyExtraSource = current!=0;
		}
	}

	@Override
	public void setRemovedIE()
	{
		super.setRemovedIE();
		RedstoneBundleConnection connection = attached.getNullable();
		if(connection!=null)
			connection.onChange(new byte[16], getFacing().getOpposite());
	}

	public interface IBundledProvider
	{
		@Nullable
		byte[] getEmittedState(Level w, BlockPos emittingBlock, Direction emittingSide);
	}
	
	protected void updateAfterConfigure()
	{
		setChanged();
		getHandler().updateValues();
		this.markContainingBlockForUpdate(null);
		level.blockEvent(getBlockPos(), this.getBlockState().getBlock(), 254, 0);
	}
	
	@Override
	public InteractionResult screwdriverUseSide(Direction side, Player player, InteractionHand hand, Vec3 hitVec)
	{
		if (!connectedToLogicUnit)
			return InteractionResult.FAIL;
		ioMode = IOSideConfig.next(ioMode);
		updateAfterConfigure();
		return InteractionResult.SUCCESS;
	}
	
	@Override
	public Component[] getOverlayText(Player player, HitResult mop, boolean hammer)
	{
		if(!Utils.isScrewdriver(player.getItemInHand(InteractionHand.MAIN_HAND)))
			return null;
		if (!connectedToLogicUnit)
			return null;
		return new Component[]{
				Component.translatable(Lib.DESC_INFO+"logicUnitInteraction", I18n.get(Lib.DESC_INFO+"blockSide.io."+this.ioMode.getSerializedName()+".bundled"))
		};
	}
	
	@Override
	public boolean useNixieFont(Player player, HitResult mop)
	{
		return false;
	}
}