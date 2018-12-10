package com.neo.sk.medusa.snake

import org.scalajs.dom
import org.scalajs.dom.html.Canvas

object GameRoom {
  private[this] val mapCanvas = dom.document.getElementById("GameRoom").asInstanceOf[Canvas]
  private[this] val mapCtx = mapCanvas.getContext("2d").asInstanceOf[dom.CanvasRenderingContext2D]

  def drawMask(): Unit ={

  }
}
