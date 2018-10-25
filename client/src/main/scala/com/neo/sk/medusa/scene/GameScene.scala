package com.neo.sk.medusa.scene

import javafx.animation.{Animation, KeyFrame, Timeline}
import javafx.event.{ActionEvent, EventHandler}
import javafx.scene.{Group, Scene}
import javafx.scene.canvas.Canvas
import javafx.scene.control.Button
import javafx.scene.paint.Color
import javafx.util.Duration

import com.neo.sk.medusa.controller.GridOnClient

/**
	* Created by wangxicheng on 2018/10/25.
	*/
class GameScene() {
	
	val width = 500
	val height = 500
	val group = new Group
	val canvas = new Canvas(width, height)
	val canvasCtx = canvas.getGraphicsContext2D
	
	canvasCtx.setFill(Color.rgb(153, 255, 153))
	canvasCtx.fillRect(0, 0, width, height)
	group.getChildren.add(canvas)
	val scene = new Scene(group)
	
	
}
