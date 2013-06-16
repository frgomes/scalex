package ornicar.scalex
package index

import scala.reflect.internal.util.FakePos
import scala.tools.nsc
import scala.util.Try

/**
 * based on scala/src/scaladoc/scala/tools/nsc/Scaladoc.scala
 */
private[scalex] object Indexer {

  def apply(config: api.Index) {
    process(config.args)
  }

  private def process(args: List[String]): Boolean = {
    var reporter: nsc.reporters.ConsoleReporter = null
    val settings = new Settings(
      msg ⇒ reporter.error(FakePos("scalex"), msg + "\n  scalex index -help  gives more information"),
      reporter.printMessage)
    reporter = new nsc.reporters.ConsoleReporter(settings) {
      // need to do this so that the Global instance doesn't trash all the
      // symbols just because there was an error
      override def hasErrors = false
    }
    val command = new Command(args.toList, settings)
    def hasFiles = command.files.nonEmpty

    val outputFile = if (settings.outputFile.isDefault || settings.outputFile.value == ".") {
      val default = "database.scalex"
      reporter.warning(null, "No destination set (-output-file), will output to " + default)
      default
    }
    else settings.outputFile.value
    if (settings.version.value)
      reporter.echo("scalex 3")
    else if (settings.Xhelp.value)
      reporter.echo(command.xusageMsg)
    else if (settings.Yhelp.value)
      reporter.echo(command.yusageMsg)
    else if (settings.showPlugins.value)
      reporter.warning(null, "Plugins are not available when using Scalex")
    else if (settings.showPhases.value)
      reporter.warning(null, "Phases are restricted when using Scalex")
    else if (settings.help.value || !hasFiles)
      reporter.echo(command.usageMsg)
    else try {
      val factory = new nsc.doc.DocFactory(reporter, settings)
      factory makeUniverse Left(command.files) map { universe ⇒
        val database = Universer(universe)
        Storage.write(outputFile, database)
        reporter.echo("Scalex database saved to " + outputFile)
      } getOrElse {
        reporter.error(null, "No universe found")
      }
    }
    catch {
      case ex @ nsc.FatalError(msg) ⇒
        if (settings.debug.value) ex.printStackTrace()
        reporter.error(null, "fatal error: " + msg)
    }
    finally reporter.printSummary()

    // not much point in returning !reporter.hasErrors when it has
    // been overridden with constant false.
    true
  }
}