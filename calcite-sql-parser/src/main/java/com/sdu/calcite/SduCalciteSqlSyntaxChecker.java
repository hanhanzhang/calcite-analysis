package com.sdu.calcite;

import static java.lang.String.valueOf;
import static org.apache.calcite.config.CalciteConnectionProperty.CASE_SENSITIVE;

import com.google.common.collect.Maps;
import com.sdu.calcite.catelog.SduCalciteFunctionOperatorTable;
import com.sdu.calcite.catelog.SduCalciteInternalOperatorTable;
import com.sdu.calcite.catelog.SduCalciteTable;
import com.sdu.calcite.plan.SduCalciteSqlOptimizer;
import com.sdu.sql.entry.SduInsert;
import com.sdu.sql.entry.SduSqlStatement;
import com.sdu.sql.entry.SduTable;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.apache.calcite.avatica.util.Casing;
import org.apache.calcite.config.CalciteConnectionConfigImpl;
import org.apache.calcite.jdbc.CalciteSchema;
import org.apache.calcite.plan.ConventionTraitDef;
import org.apache.calcite.plan.RelOptCluster;
import org.apache.calcite.plan.volcano.VolcanoPlanner;
import org.apache.calcite.prepare.CalciteCatalogReader;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.RelRoot;
import org.apache.calcite.rel.type.RelDataTypeFactory;
import org.apache.calcite.rex.RexBuilder;
import org.apache.calcite.sql.SqlNode;
import org.apache.calcite.sql.SqlOperatorTable;
import org.apache.calcite.sql.parser.SqlParseException;
import org.apache.calcite.sql.parser.SqlParser;
import org.apache.calcite.sql.util.ChainedSqlOperatorTable;
import org.apache.calcite.sql2rel.SqlToRelConverter;
import org.apache.calcite.tools.FrameworkConfig;
import org.apache.calcite.tools.Frameworks;

public class SduCalciteSqlSyntaxChecker {

  private SduCalciteSqlSyntaxChecker() {

  }

  private static CalciteSchema createCalciteSchema(List<SduTable> tables) {
    CalciteSchema schema = CalciteSchema.createRootSchema(false, false);
    for (SduTable table : tables) {
      schema.add(table.getName(), new SduCalciteTable(table.getColumns(), table.getProperties()));
    }
    return schema;
  }

  private static FrameworkConfig createFrameworkConfig(SduCalciteTypeFactory typeFactory, SduSqlStatement sqlStatement) {
    // register table schema
    CalciteSchema schema = createCalciteSchema(sqlStatement.getTables());

    // register function schema
    SqlOperatorTable functionOperatorTable = ChainedSqlOperatorTable.of(new SduCalciteInternalOperatorTable(),
        new SduCalciteFunctionOperatorTable(typeFactory, sqlStatement.getFunctionCatalog()));

    // sql parser config
    SqlParser.Config parserConf = SqlParser.configBuilder()
        // 禁止转为大写
        .setUnquotedCasing(Casing.UNCHANGED)
        .setParserFactory(new SduCalciteSqlParserFactory())
        .build();

    // SqlNode Convert RelNode Config
    SqlToRelConverter.Config sqlToRelConvertConf = SqlToRelConverter.configBuilder()
        .withTrimUnusedFields(false)
        // TableScan --> LogicalTableScan(即: TableScan类型是LogicalTableScan)
        .withConvertTableAccess(false)
        .withInSubQueryThreshold(Integer.MAX_VALUE)
        .build();

    return Frameworks.newConfigBuilder()
        .defaultSchema(schema.plus())
        .parserConfig(parserConf)
        .typeSystem(typeFactory.getTypeSystem())
        .operatorTable(functionOperatorTable)
        .sqlToRelConverterConfig(sqlToRelConvertConf)
        .build();
  }

  /**
   * The method use to obtain schema, such as table, function and so on.
   * */
  private static CalciteCatalogReader createCatalogReader(
      CalciteSchema rootSchema, List<String> defaultSchema, RelDataTypeFactory typeFactory,
      SqlParser.Config parserConfig) {
    Properties props = new Properties();
    props.setProperty(CASE_SENSITIVE.camelName(), valueOf(parserConfig.caseSensitive()));
    return new CalciteCatalogReader(
        rootSchema, defaultSchema, typeFactory, new CalciteConnectionConfigImpl(props));
  }

  private static SduCalciteRelBuilder createCalciteRelBuilder(FrameworkConfig frameworkConfig, SduCalciteTypeFactory typeFactory) {
    VolcanoPlanner planner = new VolcanoPlanner();
    planner.addRelTraitDef(ConventionTraitDef.INSTANCE);
    RelOptCluster cluster = SduRelOptClusterFactory.create(planner, new RexBuilder(typeFactory));
    CalciteSchema calciteSchema = CalciteSchema.from(frameworkConfig.getDefaultSchema());
    CalciteCatalogReader relOptSchema = createCatalogReader(
        calciteSchema, Collections.emptyList(), typeFactory,
        frameworkConfig.getParserConfig());
    return new SduCalciteRelBuilder(frameworkConfig.getContext(), cluster, relOptSchema);

  }

  private static SduCalciteSqlPlanner createSduCalciteSqlPlanner(SduSqlStatement sqlStatement) {
    SduCalciteTypeFactory typeFactory = new SduCalciteTypeFactory();
    FrameworkConfig config = createFrameworkConfig(typeFactory, sqlStatement);
    SduCalciteRelBuilder relBuilder = createCalciteRelBuilder(config, typeFactory);
    return new SduCalciteSqlPlanner(config, relBuilder, typeFactory);
  }

  public static Map<SduInsert, RelNode> sqlSyntaxOptimizer(SduSqlStatement sqlStatement,
      SduCalciteSqlOptimizer optimizer) throws SqlParseException {

    if (sqlStatement.getInserts() == null) {
      return Collections.emptyMap();
    }

    SduCalciteSqlPlanner planner = createSduCalciteSqlPlanner(sqlStatement);

    Map<SduInsert, RelNode> ans = Maps.newHashMap();
    for (SduInsert sduInsert : sqlStatement.getInserts()) {
      if (sduInsert.getSqlNode() == null) {
        SqlNode sqlNode = planner.parse(sduInsert.getSqlText());
        sduInsert.setSqlNode(sqlNode);
      }

      RelRoot relRoot = planner.validateAndRel(sduInsert.getSqlNode());
      // 优化语法树
      RelNode relNode = optimizer.optimize(relRoot.rel, planner.getBuilder());

      ans.put(sduInsert, relNode);
    }

    return ans;
  }

}