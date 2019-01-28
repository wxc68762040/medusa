/*
 * Copyright 2018 seekloud (https://github.com/seekloud)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.seekloud.medusa.scene

import org.seekloud.medusa.common.AppSettings
import javafx.scene.canvas.{Canvas, GraphicsContext}
import javafx.scene.input.KeyCode
import javafx.scene.paint.Color
import javafx.scene.text.{Font, FontPosture, FontWeight, Text}
import javafx.scene.{Group, Scene}

import org.seekloud.medusa.ClientBoot
import org.seekloud.medusa.common.AppSettings._
import javafx.scene.paint.Color
import org.seekloud.medusa.controller.GameController


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
  val scene = new Scene(group, 1600,600)

  val layerWidth = AppSettings.layerCanvasW
  val layerHeight = AppSettings.layerCanvasH

  val viewWidth = AppSettings.viewCanvasW
  val viewHeight = AppSettings.viewCanvasH

  val layerMapCanvas = new Canvas(layerWidth,layerHeight)
  val layerInfoCanvas = new Canvas(layerWidth,layerHeight)
  val layerBgCanvas = new Canvas(layerWidth,layerHeight)
  val layerAppleCanvas = new Canvas(layerWidth,layerHeight)
  val layerKernelCanvas = new Canvas(layerCanvasW,layerHeight)
  val layerAllSnakesCanvas = new Canvas(layerWidth,layerHeight)
  val layerMySnakeCanvas = new Canvas(layerWidth,layerHeight)

  val viewCanvas = new Canvas(800,400)
  val canvasCtx: GraphicsContext = viewCanvas.getGraphicsContext2D
  val actionCanvas = new Canvas(395,200)

  layerMapCanvas.setLayoutX(805)
  layerMapCanvas.setLayoutY(0)
  layerBgCanvas.setLayoutX(1210)
  layerBgCanvas.setLayoutY(0)
  layerAppleCanvas.setLayoutX(805)
  layerAppleCanvas.setLayoutY(205)
  layerKernelCanvas.setLayoutX(1210)
  layerKernelCanvas.setLayoutY(205)
  layerAllSnakesCanvas.setLayoutX(805)
  layerAllSnakesCanvas.setLayoutY(410)
  layerMySnakeCanvas.setLayoutX(1210)
  layerMySnakeCanvas.setLayoutY(410)
  layerInfoCanvas.setLayoutX(400)
  layerInfoCanvas.setLayoutY(405)
  actionCanvas.setLayoutX(0)
  actionCanvas.setLayoutY(405)

  viewCanvas.setLayoutX(0)
  viewCanvas.setLayoutY(0)

  if(GameController.grid.myId=="" && AppSettings.isLayer){
    canvasCtx.setFont(Font.font("Helvetica", FontWeight.BOLD ,FontPosture.ITALIC,30))
    canvasCtx.setFill(Color.BLACK)
    canvasCtx.fillText(s"Waiting for bot to join!",layerWidth/2,layerHeight/2)
    canvasCtx.fillText(s"Please do not worry!",layerWidth/2+100,layerHeight/2+50)
    canvasCtx.fillText(s"If you are bored!",layerWidth/2+200,layerHeight/2+100)
    canvasCtx.fillText(s"请玩一会手机!",layerWidth/2+300,layerHeight/2+150)
  }
  group.getChildren.add(viewCanvas)
  group.getChildren.add(actionCanvas)
//  viewCanvas.requestFocus()
//  viewCanvas.setOnKeyPressed(event => layerSceneListener.onKeyPressed(event.getCode))

  group.getChildren.add(layerMapCanvas)
  group.getChildren.add(layerInfoCanvas)
  group.getChildren.add(layerBgCanvas)
  group.getChildren.add(layerAllSnakesCanvas)
  group.getChildren.add(layerAppleCanvas)
  group.getChildren.add(layerMySnakeCanvas)
  group.getChildren.add(layerKernelCanvas)

  viewCanvas.requestFocus()
  viewCanvas.setOnKeyPressed{event =>
    val keyCode = event.getCode
    layerSceneListener.onKeyPressed(keyCode)
  }



  def setLayerSceneListener(listener: LayerSceneListener): Unit = {
    layerSceneListener = listener
  }

}
