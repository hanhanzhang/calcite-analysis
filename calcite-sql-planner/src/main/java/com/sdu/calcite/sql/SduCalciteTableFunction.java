package com.sdu.calcite.sql;

import static com.sdu.sql.parse.UserDefinedFunctionUtils.createEvalOperandTypeChecker;
import static com.sdu.sql.parse.UserDefinedFunctionUtils.createEvalOperandTypeInference;

import com.sdu.calcite.plan.SduCalciteTypeFactory;
import com.sdu.sql.entry.SduTableFunction;
import org.apache.calcite.sql.SqlIdentifier;
import org.apache.calcite.sql.parser.SqlParserPos;
import org.apache.calcite.sql.type.ReturnTypes;
import org.apache.calcite.sql.validate.SqlUserDefinedTableFunction;

public class SduCalciteTableFunction extends SqlUserDefinedTableFunction {

  private final SduTableFunction sduTableFunction;

  public SduCalciteTableFunction(
      String name,
      SduCalciteTypeFactory typeFactory,
      SduTableFunction sduTableFunction,
      SduCalciteTableFunctionImpl functionImpl) {
    super(
        new SqlIdentifier(name, SqlParserPos.ZERO),
        ReturnTypes.CURSOR,
        createEvalOperandTypeInference(typeFactory, sduTableFunction),
        createEvalOperandTypeChecker(typeFactory, sduTableFunction),
        null,
        functionImpl);

    this.sduTableFunction = sduTableFunction;
  }

  @Override
  public boolean isDeterministic() {
    return sduTableFunction.isDeterministic();
  }

}
