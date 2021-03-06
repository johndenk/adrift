package adrift

import java.awt.{Dimension, Graphics}
import java.nio.file._
import java.nio.file.attribute.BasicFileAttributes

import adrift.display.{Display, GLFWDisplay}
import adrift.worldgen.{Coordinates, GArchitect, WorldGen}
import com.sun.nio.file.SensitivityWatchEventModifier
import javax.sound.sampled.AudioSystem
import javax.swing.{JFrame, JPanel}

import scala.util.Random
import javax.swing.SwingUtilities

object Main {
  def onFileChanged(path: Path)(f: (Path) => Unit): Unit = {
    val watcher = FileSystems.getDefault.newWatchService()
    val keyToPath = collection.mutable.HashMap.empty[WatchKey, Path]
    val depth = 0
    Files.find(path, depth, (_: Path, attrs: BasicFileAttributes) => { attrs.isDirectory }).forEach((path) => {
      keyToPath.put(path.register(
        watcher,
        Array[WatchEvent.Kind[_]](
          StandardWatchEventKinds.ENTRY_CREATE,
          StandardWatchEventKinds.ENTRY_DELETE,
          StandardWatchEventKinds.ENTRY_MODIFY
        ),
        SensitivityWatchEventModifier.HIGH
      ), path)
    })
    val t = new Thread(() => {
      while (true) {
        val k = watcher.take()
        val root = keyToPath(k)
        val es = k.pollEvents()
        import scala.collection.JavaConverters._
        for (e <- es.asScala) {
          e.context() match {
            case p: Path =>
              val fullPath = root.resolve(p)
              f(fullPath)
          }
        }
        k.reset()
      }
    })
    t.setDaemon(true)
    t.start()
  }

  def architect(): Unit = {
    import GArchitect._
    import java.awt.Color
    var pop = Seq.fill(20)(placeRooms(roomTypes))
    var generation = 0

    val frame = new JFrame("Adrift")
    frame.setDefaultCloseOperation(3)

    val colors = Seq(
      Color.BLACK,
      Color.BLUE,
      Color.RED,
      Color.GREEN,
      Color.MAGENTA,
      Color.PINK,
      Color.YELLOW,
      Color.CYAN,
    )

    val panel = new JPanel() {
      override def paint(g: Graphics): Unit = {
        val indiv = pop.last
        for (room <- indiv) {
          val Coordinates(x, y) = room.coords
          val t = roomTypes.indexOf(room.roomType)
          assert(t >= 0)
          val color = colors(t % colors.size)
          g.setColor(color)
          g.fillRect(x * 10, y * 10, 5, 5)
        }
        for (report <- reporter.report.lastOption) {
          g.setColor(Color.BLACK)
          g.drawString(generation + " " + report._2.rawMetrics.toString(), 5, 15)
        }
      }
    }
    panel.setPreferredSize(new Dimension(SC_horizontal * 10, SC_vertical * 10))
    frame.getContentPane.add(panel)
    frame.pack()

    frame.setVisible(true)

    while (true) {
      pop = runGeneration(pop)
      generation += 1
      panel.repaint()
    }
  }

  def main(args: Array[String]): Unit = {
    //SwingUtilities.invokeLater(() => architect())
    architect()
    return

    //jsyn(); return

    //javax(); return

    val dataPath = Paths.get("data")
    val data = Data.parse(dataPath)

    val savePath = Paths.get("save.bson")
    val state =
      if (Files.exists(savePath)) {
        val start = System.nanoTime()
        val json = Bson.decode(Files.newInputStream(savePath))
        println(f"Parse took ${(System.nanoTime() - start) / 1e6}%.1f ms")
        val start2 = System.nanoTime()
        val state = Serialization.load(data, json)
        println(f"Load took ${(System.nanoTime() - start2) / 1e6}%.1f ms")
        state
      } else {
        implicit val random: Random = new Random(52)
        val state = WorldGen(data).generateWorld
        val start = System.nanoTime()
        Bson.encode(Serialization.save(state), Files.newOutputStream(savePath))
        println(f"Save took ${(System.nanoTime() - start) / 1e6}%.1f ms")
        state
      }

    state.recalculateFOV()
    val display: Display = new GLFWDisplay
    display.init()
    display.update(state)

    onFileChanged(dataPath) { _ =>
      display.postAction(Action.ReloadData(Data.parse(dataPath)))
    }

    while (display.running) {
      val action = display.waitForAction
      state.receive(action)
      display.update(state)
    }
  }

  private def javaxbased() = {
    val sound = AudioSystem.getAudioInputStream(Paths.get("hum.wav").toFile)
    val clip = AudioSystem.getClip

    clip.open(sound)
    clip.start()

    val sound2 = AudioSystem
      .getAudioInputStream(Paths.get("264014__cell31-sound-productions__door-metal-groans-ext.wav").toFile)
    val clip2 = AudioSystem.getClip
    clip2.open(sound2)
    clip2.start()

    Thread.sleep(clip.getMicrosecondLength / 1000)
  }

  private def jsynbased() = {
    import com.jsyn.JSyn
    import com.jsyn.unitgen._
    import com.jsyn.util._
    val synth = JSyn.createSynthesizer()

    synth.start()

    val out = new LineOut
    synth.add(out)

    val sample = SampleLoader.loadFloatSample(Paths.get("155720__chrisw92__machine-hum-clean.wav").toFile)
    val player = new FixedRateStereoReader
    synth.add(player)
    player.dataQueue.queue(sample)

    val filter = new FilterStateVariable
    synth.add(filter)
    filter.frequency.set(660)
    filter.resonance.set(1)

    player.output.connect(0, filter.input, 0)
    filter.output.connect(0, out.input, 0)
    filter.output.connect(0, out.input, 1)


    val sample2 = SampleLoader
      .loadFloatSample(Paths.get("264014__cell31-sound-productions__door-metal-groans-ext.wav").toFile)
    val player2 = new VariableRateStereoReader
    player2.rate.set(sample2.getFrameRate)
    synth.add(player2)
    player2.dataQueue.queue(sample2)
    player2.output.connect(0, out.input, 0)
    player2.output.connect(0, out.input, 1)
    /*

    val noise = new WhiteNoise
    synth.add(noise)
    val filter = new FilterStateVariable
    synth.add(filter)

    noise.output.connect(filter.input)
    filter.output.connect(0, out.input, 0)
    filter.output.connect(0, out.input, 1)

    */
    out.start()

    Thread.sleep((sample.getNumFrames / sample.getFrameRate * 1000).ceil.toLong)

    synth.stop()
  }
}
