package com.neo.sk.medusa.snake

import com.neo.sk.medusa.snake.QuadTree.Boundary
import org.slf4j.LoggerFactory

import scala.collection.mutable.ArrayBuffer

object QuadTree {

  private final val MAX_OBJECTS = 300
  private final val MAX_LEVEL = 10
  private final val safetyDistance = 100

  final val QuadFirst = 0
  final val QuadSecond = 1
  final val QuadThird = 2
  final val QuadFourth = 3

  case class Boundary(topLeft: Point, downRight: Point)

}

class QuadTree(boundary: Boundary, pLevel: Int, quadrant: Int = -1) {
  private[this] val log = LoggerFactory.getLogger(this.getClass)

  import QuadTree._

  private var objects: Map[Point, Spot] = Map[Point, Spot]()
  var pointSum = 0 //统计该子树有多少不重复点
  private val level: Int = pLevel
  private[this] val center: Point = (boundary.topLeft + boundary.downRight) / 2
  private val children = ArrayBuffer[QuadTree]()
  private var hasChildren = false

  private[this] def split(): Unit = {
    if (!hasChildren) {
      val first = new QuadTree(Boundary(boundary.topLeft, center), level + 1, 1)
      val second = new QuadTree(Boundary(Point(center.x, boundary.topLeft.y), Point(boundary.downRight.x, center.y)), level + 1, 2)
      val third = new QuadTree(Boundary(Point(boundary.topLeft.x, center.y), Point(center.x, boundary.downRight.y)), level + 1, 3)
      val fourth = new QuadTree(Boundary(center, boundary.downRight), level + 1, 4)
      children += (first, second, third, fourth)
      hasChildren = true
    }
  }

  private[this] def getIndex(p: Point): Set[Int] = {
    var index = Set[Int]()
    if (p.x < center.x - safetyDistance && p.y < center.y - safetyDistance)
      index += QuadFirst
    else if (p.x > center.x + safetyDistance && p.y < center.y - safetyDistance)
      index += QuadSecond
    else if (p.x < center.x - safetyDistance && p.y > center.y + safetyDistance)
      index += QuadThird
    else if (p.x > center.x + safetyDistance && p.y > center.y + safetyDistance)
      index += QuadFourth
    else if (p.x > center.x - safetyDistance && p.x < center.x + safetyDistance && p.y < center.y - safetyDistance) {
      index += QuadFirst
      index += QuadSecond
    }
    else if (p.x < center.x - safetyDistance && p.y > center.y - safetyDistance && p.y < center.y + safetyDistance) {
      index += QuadFirst
      index += QuadThird
    }
    else if (p.x > center.x + safetyDistance && p.y > center.y - safetyDistance && p.y < center.y + safetyDistance) {
      index += QuadSecond
      index += QuadFourth
    }
    else if (p.y > center.y + safetyDistance && p.x > center.x - safetyDistance && p.x < center.x + safetyDistance) {
      index += QuadThird
      index += QuadFourth
    }
    else {
      index += QuadFirst
      index += QuadSecond
      index += QuadThird
      index += QuadFourth
    }
    index

  }

  def insert(o: (Point, Spot)): Unit = {
    //log.info(s"当前节点数 $pointSum  是否符合 ${objects.size} 子节点和数${getAllObjects.size}")
    if (hasChildren) {
      //有子节点，插到子节点上
      val index = getIndex(o._1)
      if (index.nonEmpty) {
        //fixme 需要检查子节点是否存在该节点
        pointSum += 1
      }
      index.foreach {
        i =>
          if (children(i) != null) {
            children(i).insert(o)
          }
      }
    } else {
      //没有插到本节点,大于最大值进行分裂
      if (!objects.contains(o._1)) {
        pointSum += 1
      }
      objects += (o._1 -> o._2)
      //log.info(s"第 ${level-1} 层 第 $quadrant 象限 节点数： $pointSum 实际数${objects.size}")
      if (objects.size > MAX_OBJECTS && level < MAX_LEVEL) {
        log.info(s"节点需要分裂,当前层数---$level 原第 $quadrant 象限")
        this.split()
        objects.foreach {
          obj =>
            getIndex(obj._1).foreach(i => children(i).insert(obj))
        }
        objects = Map[Point, Spot]().empty
      }
    }
  }

  def insert(obj: Map[Point, Spot]): Unit = obj.foreach(this.insert)

  def insert(obj: List[(Point, Spot)]): Unit = obj.foreach(this.insert)


  def retrieve(p: Point): Map[Point, Spot] = {
    var results: Map[Point, Spot] = Map[Point, Spot]()
    if (!hasChildren) {
      results = this.objects
    } else {
      val index = getIndex(p)
      index.foreach {
        i =>
          results ++= children(i).retrieve(p)
      }
    }
    results
  }

  def retrieve(o: (Point, Spot)): Map[Point, Spot] = this.retrieve(o._1)

  def getAllObjects: Map[Point, Spot] = {
    var childrenObject: Map[Point, Spot] = Map[Point, Spot]()
    if (hasChildren) {
      children.foreach(c => childrenObject ++= c.getAllObjects)
    } else {
      childrenObject = this.objects
    }
    childrenObject
  }

  def remove(p: Point): Unit = {
    //fixme 移除结点会造成大量空节点
    var flag = false //判断是否包含该节点
    if (hasChildren) {
      val index = getIndex(p)
      index.foreach { i =>
        if (!flag) {
          //根节点不存在,子节点存在,进行删除
          if (children(i).contains(p)) {
            pointSum -= 1
            flag = true
          }
        }
        children(i).remove(p)
      }
    } else if (objects.contains(p)) {
      objects -= p
      pointSum -= 1
      flag = true
      // log.info(s"第 ${level-1} 层 第 $quadrant 象限 删除节点! 节点数： $pointSum 实际数${objects.size}")
    }
    if (flag) {
      //说明该节点得到删除，判断是否符合四叉树条件
      if (hasChildren) {
        if (pointSum < MAX_OBJECTS / 2) {
          //子树的点数小于最大值,则需要调整该树
          objects = getAllObjects
          pointSum = objects.size
          log.info(s"第 $level 层  删除后调整子树 $pointSum")
          hasChildren = false
        }
      }

    }
  }

  def remove(tuple: (Point, Spot)): Unit = this.remove(tuple._1)

  def remove(obj: Map[Point, Spot]): Unit = obj.foreach(this.remove)

  def contains(p: Point): Boolean = {
    if (this.objects.exists(_._1 eq p)) true
    else if (hasChildren) {
      this.children.exists(_.contains(p))
    } else false
  }

  def get(p: Point): Option[Spot] = {
    if (objects.contains(p)) Some(objects(p))
    else if (hasChildren) {
      val index = getIndex(p)
      val r = index.map(i => children(i).get(p))
      if (r.nonEmpty) r.find(_.isDefined).getOrElse(None) else None
    } else None
  }

  def foreach(f: (Point, Spot) => Unit): Unit = {
    if (hasChildren) {
      children.foreach(qt => qt.foreach(f))
    } else {
      objects.foreach(o => f(o._1, o._2))
    }
  }

  def filter(f: (Point, Spot) => Boolean): Map[Point, Spot] = {
    var result=Map[Point,Spot]()
    if( hasChildren ){
       children.foreach(c=> result++= c.filter(f) )
    }else{
     result ++= objects.filter(o=>f(o._1,o._2))
    }
    result
  }

}
