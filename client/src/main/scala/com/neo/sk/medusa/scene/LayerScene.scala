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
  val scene = new Scene(group, 800,400)

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

  layerBgCanvas.setLayoutX(0)
  layerBgCanvas.setLayoutY(0)
  layerMapCanvas.setLayoutX(0)
  layerMapCanvas.setLayoutY(300)
  layerAppleCanvas.setLayoutX(500)
  layerAppleCanvas.setLayoutY(0)
  layerMySnakeCanvas.setLayoutX(500)
  layerMySnakeCanvas.setLayoutY(300)
  layerInfoCanvas.setLayoutX(1000)
  layerInfoCanvas.setLayoutY(0)
  layerAllSnakesCanvas.setLayoutX(1000)
  layerAllSnakesCanvas.setLayoutY(300)

  if(AppSettings.isView) {
    group.getChildren.add(viewCanvas)

    viewCanvas.requestFocus()
    viewCanvas.setOnKeyPressed(event => layerSceneListener.onKeyPressed(event.getCode))
  }else{
    group.getChildren.add(layerMapCanvas)
    group.getChildren.add(layerInfoCanvas)
    group.getChildren.add(layerBgCanvas)
    group.getChildren.add(layerAllSnakesCanvas)
    group.getChildren.add(layerAppleCanvas)
    group.getChildren.add(layerMySnakeCanvas)

    layerMySnakeCanvas.requestFocus()
    layerMySnakeCanvas.setOnKeyPressed(event => layerSceneListener.onKeyPressed(event.getCode))

  }


  def setLayerSceneListener(listener: LayerSceneListener): Unit = {
    layerSceneListener = listener
  }

}
