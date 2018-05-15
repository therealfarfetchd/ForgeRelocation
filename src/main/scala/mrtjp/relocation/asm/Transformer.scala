/*
 * Copyright (c) 2015.
 * Created by MrTJP.
 * All rights reserved.
 */
package mrtjp.relocation.asm

import java.util.{Map => JMap}

import mrtjp.relocation.handler.RelocationMod
import net.minecraft.launchwrapper.{IClassTransformer, Launch}
import net.minecraftforge.fml.common.asm.transformers.deobf.FMLDeobfuscatingRemapper.{INSTANCE => mapper}
import net.minecraftforge.fml.relauncher.{IFMLCallHook, IFMLLoadingPlugin}
import org.objectweb.asm.Opcodes.{FLOAD, _}
import org.objectweb.asm.tree._
import org.objectweb.asm.{ClassReader, ClassWriter}

import scala.collection.JavaConversions._
import scala.language.implicitConversions

/**
  * Add "Dfml.coreMods.load=mrtjp.relocation.asm.RelocationPlugin" to launch configs
  * to enable in a development workspace.
  */
@IFMLLoadingPlugin.TransformerExclusions(value = Array("mrtjp.relocation.asm", "scala"))
class RelocationPlugin extends IFMLLoadingPlugin with IFMLCallHook {
  override def getASMTransformerClass = Array("mrtjp.relocation.asm.Transformer")
  override def getSetupClass = "mrtjp.relocation.asm.RelocationPlugin"
  override def getModContainerClass: Null = null
  override def getAccessTransformerClass: Null = null
  override def injectData(data: JMap[String, AnyRef]) {}
  override def call(): Void = null
}

class Transformer extends IClassTransformer {
  type MethodChecker = (String, MethodNode) => Boolean
  type InsTransformer = MethodNode => Unit

  lazy val deobfEnv: Boolean = (Launch.blackboard get "fml.deobfuscatedEnvironment").asInstanceOf[Boolean]
  lazy val blockClass: String = mapper.unmap("net/minecraft/block/Block")
  lazy val teClass: String = mapper.unmap("net/minecraft/tileentity/TileEntity")

  def transformBlockRender(m: MethodNode): Unit = {
    val old = m.instructions.toArray.collectFirst { case i: MethodInsnNode => i }.get
    m.instructions.insert(old, Seq(
      new VarInsnNode(ALOAD, 2),
      new MethodInsnNode(INVOKESTATIC, "mrtjp/relocation/ASMHacks",
        "getRenderType", "(Lnet/minecraft/block/state/IBlockState;Lnet/minecraft/util/math/BlockPos;)Lnet/minecraft/util/EnumBlockRenderType;", false)
    ))
    m.instructions.remove(old)
  }

  def transformTERender(m: MethodNode): Unit = {
    m.instructions.insert(Seq(
      new VarInsnNode(ALOAD, 1),
      new VarInsnNode(DLOAD, 2),
      new VarInsnNode(DLOAD, 4),
      new VarInsnNode(DLOAD, 6),
      new VarInsnNode(FLOAD, 8),
      new MethodInsnNode(INVOKESTATIC, "mrtjp/relocation/ASMHacks", "getTERenderPosition", "(Lnet/minecraft/tileentity/TileEntity;DDDF)Lcodechicken/lib/vec/Vector3;", false),
      new InsnNode(DUP),
      new InsnNode(DUP),
      new FieldInsnNode(GETFIELD, "codechicken/lib/vec/Vector3", "x", "D"),
      new VarInsnNode(DSTORE, 2),
      new FieldInsnNode(GETFIELD, "codechicken/lib/vec/Vector3", "y", "D"),
      new VarInsnNode(DSTORE, 4),
      new FieldInsnNode(GETFIELD, "codechicken/lib/vec/Vector3", "z", "D"),
      new VarInsnNode(DSTORE, 6)
    ))
  }

  def transformSelectionBoxRender(m: MethodNode): Unit = {
        val start = m.instructions.toArray.collectFirst { case i: MethodInsnNode => i }.get
        m.instructions.insertBefore(start, Seq(
          new VarInsnNode(ALOAD, 1),
          new VarInsnNode(ALOAD, 2),
          new VarInsnNode(FLOAD, 4),
          new MethodInsnNode(INVOKESTATIC, "mrtjp/relocation/ASMHacks", "setupDrawSelectionBox", "(Lnet/minecraft/entity/player/EntityPlayer;Lnet/minecraft/util/math/RayTraceResult;F)V", false)
        ))
        val end = m.instructions.toArray.collect { case i: MethodInsnNode => i }.last
        m.instructions.insert(end, Seq(
          new MethodInsnNode(INVOKESTATIC, "mrtjp/relocation/ASMHacks", "finishDrawSelectionBox", "()V", false)
        ))
  }

  def transformRayTraceBlocks(m: MethodNode): Unit = {
//    val label = new LabelNode()
//    m.instructions.insert(List(
//      new MethodInsnNode(INVOKESTATIC, "mrtjp/relocation/ASMHacks", "raytraceNoRecurse", "()Z", false),
//      new JumpInsnNode(IFNE, label),
//
//      // exec our own code here
//      new VarInsnNode(ALOAD, 0),
//      new VarInsnNode(ALOAD, 1),
//      new VarInsnNode(ALOAD, 2),
//      new VarInsnNode(ILOAD, 3),
//      new VarInsnNode(ILOAD, 4),
//      new VarInsnNode(ILOAD, 5),
//      new MethodInsnNode(INVOKESTATIC, "mrtjp/relocation/ASMHacks", "rayTraceBlocks", "(Lnet/minecraft/world/World;Lnet/minecraft/util/math/Vec3d;Lnet/minecraft/util/math/Vec3d;ZZZ)Lnet/minecraft/util/math/RayTraceResult;", false),
//      new InsnNode(ARETURN),
//
//      label,
//      new InsnNode(ICONST_0),
//      new MethodInsnNode(INVOKESTATIC, "mrtjp/relocation/ASMHacks", "raytraceNoRecurse_$eq", "(Z)V", false)
//    ))
    // IBlockProperties#collisionRayTrace
    m.instructions.toArray.collect { case i: MethodInsnNode => i }
      .filter(it =>
        it.desc == "(Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/util/math/Vec3d;Lnet/minecraft/util/math/Vec3d;)Lnet/minecraft/util/math/RayTraceResult;" &&
          it.owner == "net/minecraft/block/state/IBlockState"
      )
      .foreach(old => {
        m.instructions.insert(old, new MethodInsnNode(INVOKESTATIC, "mrtjp/relocation/ASMHacks", "collisionRayTrace", "(Lnet/minecraft/block/state/IBlockState;Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/util/math/Vec3d;Lnet/minecraft/util/math/Vec3d;)Lnet/minecraft/util/math/RayTraceResult;", false))
        m.instructions.remove(old)
      })
  }

  def transformGetCollisionBoxes(m: MethodNode): Unit = {
    // IBlockState#addCollisionBoxToList
    val old = m.instructions.toArray.collect { case i: MethodInsnNode => i }
      .find(it => it.owner == "net/minecraft/block/state/IBlockState" &&
        it.desc == "(Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/util/math/AxisAlignedBB;Ljava/util/List;Lnet/minecraft/entity/Entity;Z)V").get

    val index = m.instructions.indexOf(old)
    m.instructions.insert(old, new MethodInsnNode(INVOKESTATIC, "mrtjp/relocation/ASMHacks", "getCollisionBoxes", "(Lnet/minecraft/block/state/IBlockState;Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/util/math/AxisAlignedBB;Ljava/util/List;Lnet/minecraft/entity/Entity;Z)V", false))
    m.instructions.remove(old)
  }

  val classData = Map[String, (MethodChecker, MethodChecker, InsTransformer)](
    "net.minecraft.client.renderer.BlockRendererDispatcher" -> ((
      (_: String, m: MethodNode) => m.name == "renderBlock",
      (n: String, m: MethodNode) => mapper.mapMethodName(n, m.name, m.desc) == "func_175018_a",
      transformBlockRender
    )),
    "net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher" -> ((
      (_: String, m: MethodNode) => m.name == "render" && m.desc == "(Lnet/minecraft/tileentity/TileEntity;DDDFIF)V",
      (n: String, m: MethodNode) => mapper.mapMethodName(n, m.name, m.desc) == "func_192854_a",
      transformTERender
    )),
    "net.minecraft.client.renderer.RenderGlobal" -> ((
      (_: String, m: MethodNode) => m.name == "drawSelectionBox",
      (n: String, m: MethodNode) => mapper.mapMethodName(n, m.name, m.desc) == "func_72731_b",
      transformSelectionBoxRender
    )),
    "net.minecraft.world.World" -> ((
      (_: String, m: MethodNode) => m.name == "rayTraceBlocks" && m.desc == "(Lnet/minecraft/util/math/Vec3d;Lnet/minecraft/util/math/Vec3d;ZZZ)Lnet/minecraft/util/math/RayTraceResult;",
      (n: String, m: MethodNode) => mapper.mapMethodName(n, m.name, m.desc) == "func_147447_a",
      transformRayTraceBlocks
    ))
    //    ,
    //    "net.minecraft.world.World" -> ((
    //      (_: String, m: MethodNode) => m.name == "getCollisionBoxes" && m.desc == "(Lnet/minecraft/entity/Entity;Lnet/minecraft/util/math/AxisAlignedBB;ZLjava/util/List;)Z",
    //      (n: String, m: MethodNode) => mapper.mapMethodName(n, m.name, m.desc) == "func_191504_a",
    //      transformGetCollisionBoxes
    //    ))
  )

  var matched: Set[String] = Set[String]()

  override def transform(name: String, tName: String, data: Array[Byte]): Array[Byte] = {
    if (classData.keys.contains(tName) && !matched.contains(tName)) {
      RelocationMod.log.info(s"transforming: $tName")
      val (ch1, ch2, tr) = classData(tName)

      val node = new ClassNode
      val reader = new ClassReader(data)
      reader.accept(node, 0)

      for (m <- node.methods)
        if ((deobfEnv && ch1(name, m)) || (!deobfEnv && ch2(name, m))) {
          RelocationMod.log.info(s"$name $tName ${m.name} ${m.desc}")
          tr(m)
        }

      matched += tName

      val writer = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES)
      node.accept(writer)
      writer.toByteArray
    }
    else data
  }

  implicit def insns2InsnList(insns: Seq[AbstractInsnNode]): InsnList = {
    val il = new InsnList
    insns.foreach(il.add)
    il
  }
}