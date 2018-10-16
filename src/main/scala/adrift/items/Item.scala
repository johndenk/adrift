package adrift.items

import java.io.Reader

import io.circe.Json

import scala.collection.mutable

/*
Items have some generic type (a battery, a screwdriver, a tomato) and some specific state (half charged, found in the
elevator, moldy).

Items are made out of other specific items, which in turn have their own generic type and specific state.

The kinds of things in an item's specific state differ based on the type. For example, it doesn't make sense for a
tomato to be half-charged, or for a battery to be moldy (unless it was some kind of bio-battery...).

Some items have one or more "functions", i.e. things you can do with them. They could be either direct functions (turn
on the heater, listen to the recording, saw the log in half) or indirect functions (use the plasma torch in crafting,
wear the haz-mat suit, provide light).

For items with a function, all the parts of that item must recursively be functional for the item itself to be
functional. If an item is comprised of other items, it is functional iff all its components are functional. An item
can't be made out of functional things and yet itself be non-functional.

Items can be damaged in a variety of ways. Some damage is trivially easy to repair (e.g. recharge a battery), while
other kinds of damage require specialty tools just to diagnose, let alone repair.

=> so not only do items have specific statuses, the player might also have a varying amount of information about an
item's status. Do they know that the turbolift isn't working because one of the power relays has a broken fuse? Or can
they just tell that the power supply seems to be out of order? (Or just that the buttons don't seem to do anything?)


*****

Where can items be?
- on the ground
- in your hands
- in a container on the ground
- worn on your body
- in a container worn on your body
- inside your body (implants)
- part of another item

What can you do with items?
Simple:
- pick up
- put down
- store in container
Less simple:
- eat
- drink
- disassemble
- assemble
- wear

 */

trait ItemKind {
  def name: String
  def description: String
  def parts: Seq[((ItemKind, Int),Operation)]
}

case class ItemKindForReal(
  name: String,
  description: String,
  parts: Seq[((ItemKind, Int), Operation)]
) extends ItemKind

sealed trait YamlObject
object YamlObject {
  case class ItemKind(
    name: String,
    description: String,
    parts: Seq[ItemPart] = Seq.empty
  ) extends YamlObject

  case class ItemPart(
    `type`: String,
    disassembled_with: String = "hand",
    count: Int = 1
  )

  case class Operation(id: String) extends YamlObject
}

object Yaml {
  import io.circe.generic.extras.Configuration
  import io.circe.generic.extras.auto._
  import io.circe.yaml

  implicit val configuration: Configuration = Configuration.default.withDefaults

  class ParseError(error: String, cause: Throwable) extends RuntimeException(error, cause)

  def parse(reader: Reader): Map[String, ItemKind] = {
    val objsByType = yaml.parser.parse(reader)
      .flatMap(_.as[Seq[Json]])
      .map(_.groupBy(_.hcursor.get[String]("type").right.get))
      .fold(throw _, identity)
    val items = objsByType("item").map(itemObj => itemObj.as[YamlObject.ItemKind].fold(ex => throw new ParseError(s"$itemObj", ex), identity))
    parseItems(items)
  }

  def parseItems(items: Seq[YamlObject.ItemKind]): Map[String, ItemKind] = {
    val itemsById = items.groupBy(_.name).map {
      case (k, v) =>
        assert(v.size == 1, s"more than one item with the name '$k'")
        k -> v.head
    }
    val lazyMap = mutable.Map.empty[String, ItemKind]
    def itemForId(id: String): ItemKind = {
      lazyMap.getOrElseUpdate(id, {
        val i = itemsById(id)
        ItemKindForReal(
          i.name,
          i.description,
          i.parts.map { p =>
            ((itemForId(p.`type`), p.count), HandleOp() /* TODO */)
          }
        )
      })
    }
    itemsById.map { case (k, _) => k -> itemForId(k) }
  }
}

trait ItemCondition {
  def functional: Boolean = false
}

case class Item(
  kind: ItemKind,
  conditions: Seq[ItemCondition],
  parts: Seq[Item]
) {
  def functional: Boolean = conditions.forall(_.functional) && parts.forall(_.functional)
}

case class Charge(kwh: Double, maxKwh: Double) extends ItemCondition {
  override def functional: Boolean = kwh > 0
}
case class BrokenWire() extends ItemCondition
case class Cracked() extends ItemCondition
case class BurntOut() extends ItemCondition
case class Rusted() extends ItemCondition


trait Tool {
  def provides: Operation
}
trait Operation{}
case class HandleOp() extends Operation
case class ScrewOp() extends Operation
case class BoltOp() extends Operation
case class PryOp() extends Operation
case class HammerOp() extends Operation
case class CutOp() extends Operation
case class SolderOp() extends Operation

