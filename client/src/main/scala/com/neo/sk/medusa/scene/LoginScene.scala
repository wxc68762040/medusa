package com.neo.sk.medusa.scene

import java.io.ByteArrayInputStream

import javafx.geometry.Insets
import javafx.scene.canvas.Canvas
import javafx.scene.{Group, Scene}
import javafx.scene.control.{Button, TextField}
import javafx.scene.control.{Button, Label, TextField}
import javafx.scene.layout.{GridPane, Pane}
import javafx.scene.paint.{Color, Paint}
import akka.actor.typed.ActorRef
import akka.japi.Effect
import com.neo.sk.medusa.ClientBoot
import com.neo.sk.medusa.actor.WSClient
import com.neo.sk.medusa.common.StageContext
import com.neo.sk.medusa.controller.GameController
import javafx.scene.image.Image
import com.neo.sk.medusa.controller.LoginController
import com.sun.xml.internal.messaging.saaj.util.ByteInputStream
import javafx.collections.FXCollections
import javafx.scene.control.cell.PropertyValueFactory
import javafx.scene.effect.{BoxBlur, DropShadow}
import javafx.scene.text.Font
import javafx.scene.text.Text
import javafx.scene.text.FontWeight
import javafx.scene.text.FontPosture

/**
	* Created by wangxicheng on 2018/10/24.
	*/
object LoginScene {
	trait LoginSceneListener {

		def onButtonHumanLogin() //用户登录
		def onButtonBotLogin()   //Bot登录

		def onButtonHumanScan()  //用户扫码登录
		def onButtonHumanEmail() //用户邮箱登录
		
		def onButtonHumanJoin(account: String, pwd: String)  //用户加入游戏
		def onButtonBotJoin(botId: String, botKey: String)    //Bot加入游戏

	}
}
class LoginScene() {

	import LoginScene._
	
	val width = 500
	val height = 500
	val group = new Group
	//val joinButton = new Button("Join")
	val humanLoginButton = new Button("HumanLogin")
	val botLoginButton = new Button("BotLogin")
	val scanButton = new Button("扫码登录")
	val emailButton = new Button("邮箱登录")
	val humanJoinButton = new Button("HumanJoin")
	val botJoinButton = new Button("BotJoin")

	//humanJoinButton.setDisable(true)

  val idLabel = new Label("BotID:")
  val botId = new TextField()

	val keyLable = new Label("BotKey:")
	val botKey = new TextField()

	val accountLabel = new Label("Account:")
	val accountInput = new TextField()

	val passwordLabel = new Label("PassWord:")
	val pwdInput = new TextField()
  val warningText = new Text()
	val canvas = new Canvas(width, height)
	val canvasCtx = canvas.getGraphicsContext2D
	var loginSceneListener: LoginSceneListener = _


	humanLoginButton.setLayoutX(130)
	humanLoginButton.setLayoutY(240)
	humanLoginButton.setStyle("-fx-background-radius: 5; -fx-border-radius: 5; -fx-effect: dropShadow(three-pass-box, #528B8B, 10.0, 0, 0, 0); -fx-font:17 Helvetica; -fx-font-weight: bold; -fx-font-posture:italic")
	
	botLoginButton.setLayoutX(280)
	botLoginButton.setLayoutY(240)
	botLoginButton.setStyle("-fx-background-radius: 5; -fx-border-radius: 5; -fx-effect: dropShadow(three-pass-box, #528B8B, 10.0, 0, 0, 0); -fx-font:17 Helvetica; -fx-font-weight: bold; -fx-font-posture:italic")

	scanButton.setLayoutX(130)
	scanButton.setLayoutY(240)
	scanButton.setStyle("-fx-background-radius: 5; -fx-border-radius: 5; -fx-effect: dropShadow(three-pass-box, #528B8B, 10.0, 0, 0, 0); -fx-font:17 Helvetica; -fx-font-weight: bold; -fx-font-posture:italic")

	emailButton.setLayoutX(280)
	emailButton.setLayoutY(240)
	emailButton.setStyle("-fx-background-radius: 5; -fx-border-radius: 5; -fx-effect: dropShadow(three-pass-box, #528B8B, 10.0, 0, 0, 0); -fx-font:17 Helvetica; -fx-font-weight: bold; -fx-font-posture:italic")

	botId.setLayoutX(150)
	botId.setLayoutY(200)
	botId.setStyle("-fx-background-radius: 5; -fx-border-radius: 5; -fx-effect: dropShadow(three-pass-box, #528B8B, 10.0, 0, 0, 0); -fx-font:17 Helvetica; -fx-font-weight: bold; -fx-font-posture:italic")

	botKey.setLayoutX(150)
	botKey.setLayoutY(240)
	botKey.setStyle("-fx-background-radius: 5; -fx-border-radius: 5; -fx-effect: dropShadow(three-pass-box, #528B8B, 10.0, 0, 0, 0); -fx-font:17 Helvetica; -fx-font-weight: bold; -fx-font-posture:italic")

	idLabel.setLayoutX(80)
	idLabel.setLayoutY(210)
	idLabel.setTextFill(Color.WHITE)
	idLabel.setStyle("-fx-font:17 Helvetica; -fx-font-weight: bold; -fx-font-posture:italic")

	keyLable.setLayoutX(80)
	keyLable.setLayoutY(250)
	keyLable.setTextFill(Color.WHITE)
	keyLable.setStyle("-fx-font:17 Helvetica; -fx-font-weight: bold; -fx-font-posture:italic")

	botJoinButton.setLayoutX(200)
	botJoinButton.setLayoutY(300)
	botJoinButton.setStyle("-fx-background-radius: 5; -fx-border-radius: 5; -fx-effect: dropShadow(three-pass-box, #528B8B, 10.0, 0, 0, 0); -fx-font:17 Helvetica; -fx-font-weight: bold; -fx-font-posture:italic")


	accountInput.setLayoutX(150)
	accountInput.setLayoutY(200)
	accountInput.setStyle("-fx-background-radius: 5; -fx-border-radius: 5; -fx-effect: dropShadow(three-pass-box, #528B8B, 10.0, 0, 0, 0); -fx-font:17 Helvetica; -fx-font-weight: bold; -fx-font-posture:italic")

	pwdInput.setLayoutX(150)
	pwdInput.setLayoutY(240)
	pwdInput.setStyle("-fx-background-radius: 5; -fx-border-radius: 5; -fx-effect: dropShadow(three-pass-box, #528B8B, 10.0, 0, 0, 0); -fx-font:17 Helvetica; -fx-font-weight: bold; -fx-font-posture:italic")

	accountLabel.setLayoutX(75)
	accountLabel.setLayoutY(210)
	accountLabel.setTextFill(Color.WHITE)
	accountLabel.setStyle("-fx-font:17 Helvetica; -fx-font-weight: bold; -fx-font-posture:italic")

	passwordLabel.setLayoutX(75)
	passwordLabel.setLayoutY(250)
	passwordLabel.setTextFill(Color.WHITE)
	passwordLabel.setStyle("-fx-font:17 Helvetica; -fx-font-weight: bold; -fx-font-posture:italic")

	humanJoinButton.setLayoutX(200)
	humanJoinButton.setLayoutY(300)
	humanJoinButton.setStyle("-fx-background-radius: 5; -fx-border-radius: 5; -fx-effect: dropShadow(three-pass-box, #528B8B, 10.0, 0, 0, 0); -fx-font:17 Helvetica; -fx-font-weight: bold; -fx-font-posture:italic")




	//canvasCtx.setFill(Color.rgb(153, 255, 153))
	val bgColor = new Color(0.003, 0.176, 0.176, 1.0)
	canvasCtx.setFill(bgColor)
	canvasCtx.fillRect(0, 0, width, height)
	canvasCtx.setFont(Font.font("Helvetica", FontWeight.BOLD ,FontPosture.ITALIC,28))
	canvasCtx.setFill(Color.web("rgb(250, 250, 250)"))
	canvasCtx.fillText(s"Welcome to medusa!",100,125)
	group.getChildren.add(canvas)
	group.getChildren.add(humanLoginButton)
	group.getChildren.add(botLoginButton)


	val scene = new Scene(group)


	//joinButton.setOnAction(_ => loginSceneListener.onButtonJoin())

	humanLoginButton.setOnAction(_ => loginSceneListener.onButtonHumanLogin())
	scanButton.setOnAction(_ => loginSceneListener.onButtonHumanScan())
	emailButton.setOnAction(_ => loginSceneListener.onButtonHumanEmail())
	botLoginButton.setOnAction(_ => loginSceneListener.onButtonBotLogin())

	humanJoinButton.setOnAction{ _ =>
		val account = accountInput.getText()
		val pwd = pwdInput.getText()
		if (account.trim() == "") {
			warningText.setText("email不能为空")
		} else if (pwd.trim() == "") {
			warningText.setText("password不能为空")
		} else {
			loginSceneListener.onButtonHumanJoin(account, pwd)
		}
	}

	botJoinButton.setOnAction { _ =>
		val Id = botId.getText()
		val Key = botKey.getText()
		if (Id.trim() == "") {
			warningText.setText("botId不能为空")
		} else if (Key.trim() == "") {
			warningText.setText("botKey不能为空")
		} else {
			loginSceneListener.onButtonBotJoin(Id, Key)
		}
	}


	def drawHumanLogin = {
		ClientBoot.addToPlatform{
			group.getChildren.remove(humanLoginButton)
			group.getChildren.remove(botLoginButton)
			group.getChildren.addAll(scanButton,emailButton)
		}
	}

	def drawBotLogin ={
		ClientBoot.addToPlatform{
			group.getChildren.remove(humanLoginButton)
			group.getChildren.remove(botLoginButton)
			group.getChildren.addAll(idLabel, botId)
			group.getChildren.addAll(keyLable, botKey)
			group.getChildren.add(botJoinButton)
		}
	}


	def drawScanUrl(imageStream:ByteArrayInputStream) = {
		ClientBoot.addToPlatform{
			group.getChildren.remove(emailButton)
			group.getChildren.remove(scanButton)
			val img = new Image(imageStream)
			canvasCtx.drawImage(img, 100, 100)
			canvasCtx.setFont(Font.font("Helvetica", FontWeight.BOLD ,FontPosture.ITALIC,28))
			canvasCtx.fillText(s"请扫码登录！", 160, 70)
		}
	}

	def humanEmail = {
		ClientBoot.addToPlatform{
			group.getChildren.remove(emailButton)
			group.getChildren.remove(scanButton)
			group.getChildren.addAll(accountLabel, accountInput)
			group.getChildren.addAll(passwordLabel,pwdInput)
			group.getChildren.add(humanJoinButton)
		}

	}

	def readyToJoin = {
		ClientBoot.addToPlatform {
			canvasCtx.setFill(bgColor)
			canvasCtx.fillRect(0, 0, width, height)
			canvasCtx.setFont(Font.font("Helvetica", FontWeight.BOLD ,FontPosture.ITALIC,28))
			canvasCtx.setFill(Color.web("rgb(250, 250, 250)"))
			canvasCtx.fillText(s"Welcome to medusa!",100,125)

		}
	}
	
	def setLoginSceneListener(listener: LoginSceneListener) {
		loginSceneListener = listener
	}
}
