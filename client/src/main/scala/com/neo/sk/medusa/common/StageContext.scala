package com.neo.sk.medusa.common

import javafx.event.EventHandler
import javafx.scene.Scene
import javafx.stage.{Stage, WindowEvent}

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
	
	def switchScene(scene: Scene, title: String = "Medusa") = {
		stage.setScene(scene)
		stage.setTitle(title)
		stage.sizeToScene()
		stage.centerOnScreen()
		stage.getWidth
		stage.show()
	}

	def setStageListener(listener: StageListener): Unit = {
		stageListener = listener
	}

	def closeStage(): Unit = {
		stage.close()
		System.exit(0)
	}
}
