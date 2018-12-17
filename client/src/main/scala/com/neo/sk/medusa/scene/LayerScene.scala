package com.neo.sk.medusa.scene

import com.neo.sk.medusa.common.AppSettings
import javafx.scene.canvas.Canvas
import javafx.scene.input.KeyCode
import javafx.scene.{Group, Scene}
import com.neo.sk.medusa.common.AppSettings._


/**
  * User: gaohan
  * Date: 2018/12/6
  * Time: 4:19 PM
  */

object LayerScene{
  trait LayerSceneListener {
    def onKeyPressed(e: KeyCode): Unit
  }

}
class LayerScene {

  import LayerScene._
  var layerSceneListener: LayerSceneListener = _
  val group = new Group()
  val scene = new Scene(group, 1300,900)

  val layerWidth = AppSettings.layerCanvasW
  val layerHeight = AppSettings.layerCanvasH

  val viewWidth = AppSettings.viewCanvasW
  val viewHeight = AppSettings.viewCanvasH

  val layerMapCanvas = new Canvas(layerWidth,layerHeight)
  val layerInfoCanvas = new Canvas(layerWidth,layerHeight)
  val layerBgCanvas = new Canvas(layerWidth,layerHeight)
  val layerAppleCanvas = new Canvas(layerWidth,layerHeight)
  val layerAllSnakesCanvas = new Canvas(layerWidth,layerHeight)
  val layerMySnakeCanvas = new Canvas(layerWidth,layerHeight)

  val viewCanvas = new Canvas(800,400)

  layerBgCanvas.setLayoutX(800)
  layerBgCanvas.setLayoutY(0)
  layerInfoCanvas.setLayoutX(1200)
  layerInfoCanvas.setLayoutY(0)
  layerMySnakeCanvas.setLayoutX(800)
  layerMySnakeCanvas.setLayoutY(400)
  layerAllSnakesCanvas.setLayoutX(1200)
  layerAllSnakesCanvas.setLayoutY(400)
  layerMapCanvas.setLayoutX(800)
  layerMapCanvas.setLayoutY(600)
  layerAppleCanvas.setLayoutX(1200)
  layerAppleCanvas.setLayoutY(600)


  viewCanvas.setLayoutX(0)
  viewCanvas.setLayoutY(100)

  group.getChildren.add(viewCanvas)

  viewCanvas.requestFocus()
  viewCanvas.setOnKeyPressed(event => layerSceneListener.onKeyPressed(event.getCode))

  group.getChildren.add(layerMapCanvas)
  group.getChildren.add(layerInfoCanvas)
  group.getChildren.add(layerBgCanvas)
  group.getChildren.add(layerAllSnakesCanvas)
  group.getChildren.add(layerAppleCanvas)
  group.getChildren.add(layerMySnakeCanvas)

  layerMySnakeCanvas.requestFocus()
  layerMySnakeCanvas.setOnKeyPressed(event => layerSceneListener.onKeyPressed(event.getCode))



  def setLayerSceneListener(listener: LayerSceneListener): Unit = {
    layerSceneListener = listener
  }

}
