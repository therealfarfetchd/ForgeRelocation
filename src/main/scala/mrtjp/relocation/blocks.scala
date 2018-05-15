/*
 * Copyright (c) 2014.
 * Created by MrTJP.
 * All rights reserved.
 */
package mrtjp.relocation

import java.util.{List => JList}

import codechicken.lib.vec.{Cuboid6, Vector3}
import mrtjp.core.block.{MTBlockTile, MultiTileBlock}
import mrtjp.relocation.handler.RelocationMod
import net.minecraft.block.SoundType
import net.minecraft.block.material.Material
import net.minecraft.block.state.IBlockState
import net.minecraft.entity.{Entity, MoverType}
import net.minecraft.util.math.{AxisAlignedBB, BlockPos, RayTraceResult, Vec3d}
import net.minecraft.util.{EnumBlockRenderType, ResourceLocation}
import net.minecraft.world.World

import scala.collection.JavaConversions._

class BlockMovingRow extends MultiTileBlock(Material.IRON) {
  setHardness(-1F)
  setSoundType(SoundType.GROUND)
  setCreativeTab(null)
  setRegistryName(new ResourceLocation(RelocationMod.modID, "blockmovingrow"))
  addTile(classOf[TileMovingRow], 0)

  override def getRenderType(state: IBlockState): EnumBlockRenderType = EnumBlockRenderType.INVISIBLE

  override def collisionRayTrace(blockState: IBlockState, worldIn: World, pos: BlockPos, start: Vec3d, end: Vec3d): RayTraceResult =
    if (MovementManager2.isMoving(worldIn, pos)) null else super.collisionRayTrace(blockState, worldIn, pos, start, end)
}

object TileMovingRow {
  var noBounds = false

  def setBlockForRow(w: World, r: BlockRow) {
    w.setBlockState(r.pos, RelocationMod.blockMovingRow.getDefaultState, 3)
  }

  def getBoxFor(w: World, r: BlockRow, progress: Double): Cuboid6 = {
    val p = r.pos.offset(r.moveDir)
    val bl = w.getBlockState(p)

    if (bl == RelocationMod.blockMovingRow) return Cuboid6.full.copy()

    bl.getCollisionBoundingBox(w, p) match {
      case aabb: AxisAlignedBB => new Cuboid6(aabb).subtract(new Vector3(r.pos.getX, r.pos.getY, r.pos.getZ))
        .add(Vector3.fromVec3i(r.moveDir.getDirectionVec) * progress)
      case _ => Cuboid6.full.copy
    }
  }
}

class TileMovingRow extends MTBlockTile {
  var prevProg = 0.0

  override def updateServer(): Unit = {
    if (!MovementManager2.isMoving(world, pos)) world.setBlockToAir(pos)
  }

  override def getBlock: BlockMovingRow = RelocationMod.blockMovingRow

  override def getBlockBounds: Cuboid6 = {
    if (TileMovingRow.noBounds) new Cuboid6()
    else {
      val s = MovementManager2.getEnclosedStructure(world, pos)
      if (s != null) {
        val r = s.rows.find(_.contains(pos)).get
        TileMovingRow.getBoxFor(world, r, s.progress)
      } else Cuboid6.full
    }
  }

  override def getCollisionBounds: Cuboid6 = getBlockBounds

  def pushEntities(r: BlockRow, progress: Double) {
    val box = Cuboid6.full.copy.add(Vector3.fromVec3i(r.preMoveBlocks.head))
      .add(Vector3.fromVec3i(r.moveDir.getDirectionVec).multiply(progress)).aabb()

    val dp = (if (progress >= 1.0) progress + 0.1 else progress) - prevProg
    val d = new Vector3(Vector3.fromVec3i(r.moveDir.getDirectionVec)) * dp
    world.getEntitiesWithinAABBExcludingEntity(null, box) match {
      case list: JList[_] =>
        for (e <- list.asInstanceOf[JList[Entity]]) e.move(MoverType.PISTON, d.x, d.y max 0, d.z)
      case _ =>
    }

    prevProg = progress
  }
}