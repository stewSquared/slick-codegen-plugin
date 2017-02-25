import scala.concurrent.ExecutionContext.Implicits.global

import slick.dbio.DBIO
import slick.driver.JdbcProfile
import slick.jdbc.meta.MTable
import slick.{model => m}

object SchemaParser {
  def references(dbModel: m.Model,
                 tcMappings: Map[(String, String), (String, String)])
    : Map[(String, String), (m.Table, m.Column)] = {
    def getTableColumn(tc: (String, String)): (m.Table, m.Column) = {
      val (tableName, columnName) = tc
      val table = dbModel.tables
        .find(_.name.asString == tableName)
        .getOrElse(throw new RuntimeException("No table " + tableName))
      val column = table.columns
        .find(_.name == columnName)
        .getOrElse(throw new RuntimeException(
          "No column " + columnName + " in table " + tableName))
      (table, column)
    }

    tcMappings.map {
      case (from, to) => ({ getTableColumn(from); from }, getTableColumn(to))
    }
  }

  def parse(schemaTableNames: List[String]): Map[String, List[String]] =
    schemaTableNames
      .map(_.split('.'))
      .groupBy(_.head)
      .mapValues(_.flatMap(_.tail))

  def createModel(
      jdbcProfile: JdbcProfile,
      mappedSchemasOpt: Option[Map[String, List[String]]]): DBIO[m.Model] = {
    import slick.jdbc.meta.MQName

    val filteredTables = mappedSchemasOpt.map { mappedSchemas =>
      MTable.getTables.map { (tables: Vector[MTable]) =>
        mappedSchemas.flatMap {
          case (schemaName, tableNames) =>
            tableNames.map(
              tableName =>
                tables
                  .find(table =>
                    table.name match {
                      case MQName(_, Some(`schemaName`), `tableName`) => true
                      case _ => false
                  })
                  .getOrElse(throw new IllegalArgumentException(
                    s"$schemaName.$tableName does not exist in the connected database.")))
        }.toList
      }
    }

    jdbcProfile.createModel(filteredTables)
  }
}
