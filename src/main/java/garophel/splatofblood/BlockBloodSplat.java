package garophel.splatofblood;

import net.minecraft.block.Block;
import net.minecraft.block.ITileEntityProvider;
import net.minecraft.block.material.EnumPushReaction;
import net.minecraft.block.material.MapColor;
import net.minecraft.block.material.Material;
import net.minecraft.block.material.MaterialLogic;
import net.minecraft.block.state.IBlockState;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

public class BlockBloodSplat extends Block implements ITileEntityProvider {
	
	private AxisAlignedBB aabb = new AxisAlignedBB(0d, 0d, 0d, 1f, 1f/16f, 1f);

	public BlockBloodSplat() {
		super(new MaterialLogic(MapColor.foliageColor) {
			@Override
			public EnumPushReaction getMobilityFlag() {
				return EnumPushReaction.BLOCK;
			}
		});
		setCreativeTab(CreativeTabs.tabMisc);
		setUnlocalizedName(SplatOfBlood.MODID + ":bloodsplat");
		setHardness(50f); // Obsidian
		setResistance(2000f); // Obsidian
	}
	
	@Override
	public AxisAlignedBB getSelectedBoundingBox(IBlockState blockState, World worldIn, BlockPos pos) {
		return NULL_AABB;
	}
	
	@Override
	public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess source, BlockPos pos) {
		return aabb;
	}
	
	@Override
	public TileEntity createNewTileEntity(World worldIn, int meta) {
		return new TileBloodSplat();
	}
	
	@Override
	public void breakBlock(World world, BlockPos pos, IBlockState state) {
		if(!world.isRemote) {
			TileEntity te = world.getTileEntity(pos);
			if(te != null && te instanceof TileBloodSplat) ((TileBloodSplat)te).breakSplat();
		}
//		super.breakBlock(world, pos, state);
	}
	
	@Override
	public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer plr, EnumHand hand, ItemStack heldItem, EnumFacing side, float hitX, float hitY, float hitZ) {
		if(!world.isRemote) {
			TileEntity te = world.getTileEntity(pos);
			if(te != null && te instanceof TileBloodSplat) {
				if(plr.isSneaking()) {
					if(!((TileBloodSplat)te).removeSplat()) plr.addChatMessage(new TextComponentString("The blood splat can't be removed."));
				} else {
					((TileBloodSplat)te).recoverItems(plr);
				}
			}
			
		}
		
		return true;
//		return super.onBlockActivated(world, pos, state, plr, hand, heldItem, side, hitX, hitY, hitZ);
	}
	
	@Override
	public boolean hasTileEntity(IBlockState state) {
		return true;
	}
	
	@Override
	public boolean isOpaqueCube(IBlockState state) {
		return false;
	}
	
	@Override
	public boolean isFullCube(IBlockState state) {
		return false;
	}
	
	@Override
	public BlockRenderLayer getBlockLayer() {
		return BlockRenderLayer.CUTOUT;
	}
	
	public static class ItemBlockBloodSplat extends ItemBlock {

		public ItemBlockBloodSplat(Block block) {
			super(block);
			setMaxDamage(0);
			setHasSubtypes(false);
			setMaxStackSize(1);
		}
		
		@Override
		public int getMetadata(int damage) {
			return 0;
		}
	}
}
