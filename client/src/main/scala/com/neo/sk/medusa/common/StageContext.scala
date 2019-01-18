package com.neo.sk.medusa.common

import com.neo.sk.medusa.scene.GameScene
import javafx.event.EventHandler
import javafx.scene.Scene
import javafx.stage.{Stage, WindowEvent}

import com.neo.sk.medusa.ClientBoot
import com.neo.sk.medusa.actor.SdkServer

/**
	* Created by wangxicheng on 2018/10/24.
	*/

object StageContext{
	trait StageListener {
		def onCloseRequest(): Unit
	}
}

class StageContext(stage: Stage) {
	
	import StageContext._
	
	var stageListener: StageListener = _
	
	stage.setOnCloseRequest(_ => stageListener.onCloseRequest())

	case class syncWindowSize(windowWidth: Double, windowHeight: Double)
	def getWindowSize = {
		val windowWidth = stage.getWidth
		val windowHeight = stage.getHeight
		syncWindowSize(windowWidth, windowHeight)
	}
	
	def switchScene(scene: Scene, title: String = "Medusa", flag: Boolean) = {
		stage.setScene(scene)
		stage.setTitle(title)
		stage.sizeToScene()
		stage.centerOnScreen()
		if(flag) {
			stage.setFullScreen(true)
		}
		stage.getWidth
		if(AppSettings.isView) {
			stage.show()
		}
	}

	def setStageListener(listener: StageListener): Unit = {
		stageListener = listener
	}

	def closeStage(): Unit = {
		ClientBoot.sdkServer ! SdkServer.Shutdown
		stage.close()
		System.exit(0)
	}
}
