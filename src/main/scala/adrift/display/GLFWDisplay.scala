package adrift.display

import adrift.display.glutil.{Image, SpriteBatch, Texture}
import adrift._
import org.lwjgl.glfw.GLFW._
import org.lwjgl.glfw.GLFWErrorCallback
import org.lwjgl.opengl.GL
import org.lwjgl.opengl.GL11._

import scala.collection.mutable

class GLFWDisplay extends Display {
  val CHAR_W = 16
  val CHAR_H = 16
  var window: Long = 0
  var upper: Texture = _
  var lower: Texture = _
  var spriteBatch: SpriteBatch = _
  private val pendingActions = mutable.Buffer.empty[Action]

  def init(): Unit = {
    GLFWErrorCallback.createPrint(System.err).set()

    if (!glfwInit()) {
      throw new IllegalStateException("Unable to initialize GLFW")
    }

    glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE)
    glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE)

    window = glfwCreateWindow(640, 480, "Adrift", 0, 0)
    if (window == 0)
      throw new RuntimeException("Failed to create GLFW window")

    glfwSetKeyCallback(window, (window: Long, key: Int, scancode: Int, action: Int, mods: Int) => {
      if (action == GLFW_PRESS || action == GLFW_REPEAT) {
        key match {
          case GLFW_KEY_LEFT => pendingActions.append(Action.PlayerMove(-1, 0))
          case GLFW_KEY_RIGHT => pendingActions.append(Action.PlayerMove(1, 0))
          case GLFW_KEY_UP => pendingActions.append(Action.PlayerMove(0, -1))
          case GLFW_KEY_DOWN => pendingActions.append(Action.PlayerMove(0, 1))
          case _ =>
        }
      }
    })
    glfwSetWindowCloseCallback(window, (_: Long) => pendingActions.append(Action.Quit))

    glfwMakeContextCurrent(window)
    glfwSwapInterval(1)

    GL.createCapabilities()

    upper = Texture.fromImage(loadc64("c64_upp.gif"))
    lower = Texture.fromImage(loadc64("c64_low.gif"))
    spriteBatch = SpriteBatch.create
  }

  private def loadc64(name: String): Image = {
    val image = Image.fromFile(name)
    // TODO: would be better to just do this transform in an image editor
    for (i <- 0 until image.bytes.limit() by 4) {
      val isWhite = image.bytes.get(i) == 0
      if (isWhite) {
        image.bytes.put(i, 0xff.toByte)
        image.bytes.put(i+1, 0xff.toByte)
        image.bytes.put(i+2, 0xff.toByte)
        image.bytes.put(i+3, 0xff.toByte)
      } else {
        image.bytes.put(i, 0x00.toByte)
        image.bytes.put(i+1, 0x00.toByte)
        image.bytes.put(i+2, 0x00.toByte)
        image.bytes.put(i+3, 0x00.toByte)
      }
    }
    image
  }

  def charForTerrain(terrain: Terrain): Int = terrain match {
    case Empty => 32
    case Floor => 46
    case Grass => 44
    case Tree => 65
    case GlassWall => 95
  }

  def render(state: GameState): Unit = {
    val map: Grid[Terrain] = state.map
    val player: (Int, Int) = state.player
    val windowWidthChars = 80
    val windowHeightChars = 48
    val worldHeightChars = windowHeightChars - 1
    glfwSetWindowSize(window, windowWidthChars * CHAR_W, windowHeightChars * CHAR_H)
    glfwShowWindow(window)
    glClearColor(0, 0, 0, 1)

    spriteBatch.resize(CHAR_W * windowWidthChars, CHAR_H * windowHeightChars)

    def drawChar(tex: Texture, x: Int, y: Int, c: Int): Unit = {
      val cx = c % 32
      val cy = c / 32
      spriteBatch.drawRegion(tex, cx * 8, cy * 8, 8, 8, x * CHAR_W, y * CHAR_H, CHAR_W, CHAR_H)
    }

    glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT)

    val left = player._1 - windowWidthChars/2
    val right = left + windowWidthChars
    val top = player._2 - worldHeightChars/2
    val bottom = top + worldHeightChars

    spriteBatch.begin()
    for (y <- top until bottom; x <- left until right) {
      if (x == player._1 && y == player._2) drawChar(upper, x - left, y - top, 0)
      else if (map.contains(x, y)) drawChar(upper, x - left, y - top, charForTerrain(map(x, y)))
    }
    val message = s"Here: ${(map.apply _).tupled(player)}"
    for ((c, i) <- message.zipWithIndex) {
      val tc = c match {
        case x if x >= 'A' && x <= 'Z' => x
        case x if x >= 'a' && x <= 'z' => x - 'a' + 1
        case x => x
      }
      drawChar(lower, i, worldHeightChars, tc)
    }
    spriteBatch.end()

    glfwSwapBuffers(window)
  }

  override def update(state: GameState): Unit = render(state)

  override def waitForAction: Action = {
    if (pendingActions.nonEmpty)
      return pendingActions.remove(0)
    while (pendingActions.isEmpty)
      glfwWaitEvents()
    pendingActions.remove(0)
  }

  override def running: Boolean = !glfwWindowShouldClose(window)
}
