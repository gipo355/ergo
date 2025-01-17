package scorex.testkit.properties.state

import org.scalacheck.Gen
import org.scalatest.matchers.should.Matchers
import org.scalatest.propspec.AnyPropSpec
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks
import scorex.core.transaction.state.MinimalState
import scorex.testkit.TestkitHelpers
import scorex.testkit.generators.{CoreGenerators, SemanticallyInvalidModifierProducer, SemanticallyValidModifierProducer}

trait StateTests[ST <: MinimalState[ST]]
  extends AnyPropSpec
    with ScalaCheckPropertyChecks
    with Matchers
    with CoreGenerators
    with TestkitHelpers
    with SemanticallyValidModifierProducer[ST]
    with SemanticallyInvalidModifierProducer[ST] {

  val checksToMake = 10

  val stateGen: Gen[ST]
}
