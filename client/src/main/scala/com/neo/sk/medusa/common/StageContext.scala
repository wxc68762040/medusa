package com.neo.sk.medusa.common

import javafx.scene.Scene
import javafx.stage.Stage

/**
	* Created by wangxicheng on 2018/10/24.
	*/
class StageContext(stage: Stage) {
	
	def switchScene(scene: Scene, title: String = "Medusa") = {
		stage.setScene(scene)
		stage.setTitle(title)
		stage.sizeToScene()
		stage.show()
	}


}
