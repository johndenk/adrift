package adrift

sealed trait Action {

}

object Action {
  case class PlayerMove(dx: Int, dy: Int) extends Action
  case class Disassemble(itemLocation: ItemLocation) extends Action
  case class PickUp(itemLocation: ItemLocation) extends Action
  case class PutDown(itemLocation: ItemLocation) extends Action
  case object Quit extends Action
}