package com.sdu.calcite.catelog;

import com.sdu.calcite.entry.SduAggregateFunction;
import com.sdu.calcite.entry.SduFunction;
import com.sdu.calcite.entry.SduScalarFunction;
import com.sdu.calcite.entry.SduTableFunction;
import com.sdu.calcite.function.FunctionKind;
import com.sdu.calcite.types.SduTypeFactory;
import com.sdu.calcite.util.UserDefinedFunctionUtils;
import java.util.List;
import java.util.Optional;
import org.apache.calcite.sql.SqlFunction;
import org.apache.calcite.sql.SqlFunctionCategory;
import org.apache.calcite.sql.SqlIdentifier;
import org.apache.calcite.sql.SqlOperator;
import org.apache.calcite.sql.SqlOperatorTable;
import org.apache.calcite.sql.SqlSyntax;
import org.apache.calcite.sql.validate.SqlNameMatcher;

public class SduFunctionOperatorTable implements SqlOperatorTable {

  private final SduFunctionCatalog functionCatalog;
  private final SduTypeFactory typeFactory;

  public SduFunctionOperatorTable(SduFunctionCatalog functionCatalog, SduTypeFactory typeFactory) {
    this.functionCatalog = functionCatalog;
    this.typeFactory = typeFactory;
  }

  @Override
  public void lookupOperatorOverloads(SqlIdentifier opName, SqlFunctionCategory category,
      SqlSyntax syntax, List<SqlOperator> operatorList, SqlNameMatcher nameMatcher) {
    if (!opName.isSimple()) {
      return;
    }

    if (isNotUserFunction(category)) {
      return;
    }

    final String name = opName.getSimple();
    functionCatalog.lookupFunction(name)
        .flatMap((SduFunction sduFunction) -> convertToSqlFunction(typeFactory, name, sduFunction))
        .ifPresent(operatorList::add);
  }

  @Override
  public List<SqlOperator> getOperatorList() {
    return null;
  }

  private boolean isNotUserFunction(SqlFunctionCategory category) {
    return category != null && !category.isUserDefinedNotSpecificFunction();
  }

  private static Optional<SqlFunction> convertToSqlFunction(SduTypeFactory typeFactory, String name, SduFunction function) {
    FunctionKind kind = function.getKind();
    switch (kind) {
      case TABLE:
        SduTableFunction tableFunction = (SduTableFunction) function;
        return Optional.of(UserDefinedFunctionUtils.convertTableSqlFunction(typeFactory, name, tableFunction));
      case SCALAR:
        SduScalarFunction scalarFunction = (SduScalarFunction) function;
        return Optional.of(UserDefinedFunctionUtils.convertScalarSqlFunction(typeFactory, name, scalarFunction));
      case AGGREGATE:
        SduAggregateFunction aggFunction = (SduAggregateFunction) function;
        return Optional.of(UserDefinedFunctionUtils.convertAggregateFunction(typeFactory, name, aggFunction));
      default:
        throw new RuntimeException("Unsupported sql function kind: " + kind);
    }
  }

}