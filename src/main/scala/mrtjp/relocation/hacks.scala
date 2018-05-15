/*
 * Copyright (c) 2014.
 * Created by MrTJP.
 * All rights reserved.
 */
package mrtjp.relocation

import java.util
import java.util.{List => JList}

import codechicken.lib.vec.Vector3
import mrtjp.core.math.MathLib
import net.minecraft.block.Block
import net.minecraft.block.state.IBlockState
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.entity.Entity
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.tileentity.TileEntity
import net.minecraft.util.EnumBlockRenderType
import net.minecraft.util.math.{AxisAlignedBB, BlockPos, RayTraceResult, Vec3d}
import net.minecraft.world.World
import net.minecraftforge.fml.relauncher.{Side, SideOnly}

import scala.collection.JavaConversions._

object ASMHacks {
  var raytraceNoRecurse = false

  @SideOnly(Side.CLIENT)
  def getTERenderPosition(te: TileEntity, x: Double, y: Double, z: Double, partialTicks: Float): Vector3 = {
    val vec = new Vector3(x, y, z)
    if (te.getWorld != null && MovementManager2.isMoving(te.getWorld, te.getPos)) {
      val s = MovementManager2.getEnclosedStructure(te.getWorld, te.getPos)
      val offset = MovingRenderer.renderPos(s, partialTicks)
      vec.add(
        MathLib.clamp(-1F, 1F, offset.x.toFloat),
        MathLib.clamp(-1F, 1F, offset.y.toFloat),
        MathLib.clamp(-1F, 1F, offset.z.toFloat)
      )
    }
    vec
  }

  @SideOnly(Side.CLIENT)
  def setupDrawSelectionBox(player: EntityPlayer, rtr: RayTraceResult, partialTicks: Float): Unit = {
    GlStateManager.pushMatrix()
    val pos = rtr.getBlockPos
    if (MovementManager2.isMoving(player.world, pos)) {
      val s = MovementManager2.getEnclosedStructure(player.world, pos)
      val offset = MovingRenderer.renderPos(s, partialTicks)
      GlStateManager.translate(offset.x, offset.y, offset.z)
    }
  }

  @SideOnly(Side.CLIENT)
  def finishDrawSelectionBox(): Unit = {
    GlStateManager.popMatrix()
  }

  def collisionRayTrace(state: IBlockState, world: World, pos: BlockPos, start: Vec3d, end: Vec3d): RayTraceResult = {
    val stopOnLiquid = false
    val ignoreBlockWithoutBoundingBox = true

    if (MovementManager2.isMoving(world, pos)) {
      val s = MovementManager2.getEnclosedStructure(world, pos)
      val offset = MovingRenderer.renderPos(s, 1).vec3() // FIXME this will crash on the server
      val other = pos.offset(s.moveDir.getOpposite)

      var result = Set(state.collisionRayTrace(world, pos, start.subtract(offset), end.subtract(offset)))

      if (MovementManager2.isMoving(world, other) && (MovementManager2.getEnclosedStructure(world, other) eq s)) {
        val ostate = world.getBlockState(other)
        if ((!ignoreBlockWithoutBoundingBox || ostate.getCollisionBoundingBox(world, other) != Block.NULL_AABB) &&
          ostate.getBlock.canCollideCheck(ostate, stopOnLiquid)) {
          result += ostate.collisionRayTrace(world, other, start.subtract(offset), end.subtract(offset))
        }
      }
      result = result.filter(_ != null)
      result.foreach(it => it.hitVec = it.hitVec.add(offset))
      if (result.isEmpty) null
      else result.minBy(_.hitVec.subtract(start).lengthSquared())
    } else state.collisionRayTrace(world, pos, start, end)
  }

  def getCollisionBoxes(state: IBlockState, world: World, pos: BlockPos, aabb: AxisAlignedBB, outList: JList[AxisAlignedBB], entity: Entity, unused: Boolean): Unit = {
    if (MovementManager2.isMoving(world, pos)) {
      val list = new util.ArrayList[AxisAlignedBB]()
      val s = MovementManager2.getEnclosedStructure(world, pos)
      val offset = MovingRenderer.renderPos(s, 1) // FIXME this will crash on the server
      state.addCollisionBoxToList(world, pos, aabb, list, entity, unused)
      outList.addAll(list.map(_.offset(offset.vec3())))
    } else {
      state.addCollisionBoxToList(world, pos, aabb, outList, entity, unused)
    }
  }

  @SideOnly(Side.CLIENT)
  def getRenderType(state: IBlockState, pos: BlockPos): EnumBlockRenderType =
    if (MovingRenderer.renderHack && MovementManager2.isMoving(Minecraft.getMinecraft.world, pos)) EnumBlockRenderType.INVISIBLE
    else state.getRenderType
}