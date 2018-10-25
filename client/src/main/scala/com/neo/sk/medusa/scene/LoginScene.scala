package com.neo.sk.medusa.scene

import javafx.geometry.Insets
import javafx.scene.canvas.Canvas
import javafx.scene.{Group, Scene}
import javafx.scene.control.Button
import javafx.scene.layout.{GridPane, Pane}
import javafx.scene.paint.{Color, Paint}

import akka.actor.typed.ActorRef
import com.neo.sk.medusa.BootTest.wsClient
import com.neo.sk.medusa.actor.WSClient
import com.neo.sk.medusa.actor.WSClient.ConnectGame

/**
	* Created by wangxicheng on 2018/10/24.
	*/
class LoginScene(wsClient: ActorRef[WSClient.WsCommand]) {
	
	val width = 500
	val height = 500
	val group = new Group
	val button = new Button("连接")
	val canvas = new Canvas(width, height)
	val canvasCtx = canvas.getGraphicsContext2D
	button.setLayoutX(230)
	button.setLayoutY(240)
	
	canvasCtx.setFill(Color.rgb(153, 255, 153))
	canvasCtx.fillRect(0, 0, width, height)
	group.getChildren.add(canvas)
	group.getChildren.add(button)
	val scene = new Scene(group)
	
	button.setOnAction( e => {
		val id = System.currentTimeMillis().toString
		val name = "name" + System.currentTimeMillis().toString
		val accessCode = "jgfkldpwer"
		wsClient ! ConnectGame(id, name, accessCode)
	})
	
//	def drawScanUrl(scanUrl: String) = {
//
//	}
	
}
