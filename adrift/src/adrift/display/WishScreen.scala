package adrift.display

import java.nio.file.{Files, Paths}

import adrift._
import org.lwjgl.glfw.GLFW

class WishScreen(display: GLFWDisplay, state: GameState) extends Screen {
  val setTemp = raw"settemp (-?[0-9.]+)".r
  val setGas = raw"setgas (\S+) (-?[0-9.]+)".r
  val item = raw"item (.+)".r
  var input = ""
  override def key(key: Int, scancode: Int, action: Int, mods: Int): Unit = {
    if (action == GLFW.GLFW_PRESS || action == GLFW.GLFW_REPEAT) {
      key match {
        case GLFW.GLFW_KEY_BACKSPACE if input.length > 0 =>
          input = input.init
        case GLFW.GLFW_KEY_ENTER =>
          input match {
            case "wtw" =>
              state.walkThroughWalls = !state.walkThroughWalls
            case "showtemp" =>
              state.showTempDebug = !state.showTempDebug
            case "showgas" =>
              state.showGasDebug = !state.showGasDebug
            case setGas("oxygen", pp) =>
              state.gasComposition(state.player) = state.gasComposition(state.player).copy(oxygen = pp.toFloat)
            case setTemp(t) =>
              state.temperature(state.player) = t.toFloat
            case item(name) =>
              if (state.data.itemGroups.contains(name)) {
                state.sampleItem(state.data.itemGroups(name).choose).foreach(item => {
                  state.items.put(item, OnFloor(state.player._1, state.player._2))
                })
              } else if (state.data.items.contains(name)) {
                val item = state.data.items(name).generateItem()
                state.items.put(item, OnFloor(state.player._1, state.player._2))
              }
            case "save" =>
              val savePath = Paths.get("save.bson")
              Bson.encode(Serialization.save(state), Files.newOutputStream(savePath))
            case "reload" =>
              try {
                state.data = Data.parse(Paths.get("data"))
              } catch {
                case e: Throwable =>
                  println(e)
                  state.message = Some(s"Couldn't reload: ${e.getMessage}")
              }
            case _ =>
          }
          display.popScreen()
        case _ =>
      }
    }
  }

  override def char(char: Int): Unit = {
    input += char.toChar
  }

  override def render(renderer: GlyphRenderer): Unit = {
    renderer.frame(2, 2, 40, "Wish", Seq(""))
    renderer.drawString(3, 3, input, maxWidth = 38)
    renderer.drawString(3 + input.length, 3, "\u00db")
  }
}
