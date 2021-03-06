package com.sdu.sql.entry;

import com.sdu.calcite.function.FunctionKind;
import com.sdu.calcite.function.ScalarFunction;
import com.sdu.calcite.function.UserDefinedFunction;
import lombok.Getter;
import lombok.Setter;


public class SduScalarFunction extends SduFunction {

  @Setter
  @Getter
  private ScalarFunction scalarFunction;

  @Override
  public UserDefinedFunction getUserDefinedFunction() {
    return scalarFunction;
  }

  @Override
  public FunctionKind getKind() {
    return FunctionKind.SCALAR;
  }

  static SduScalarFunction fromUserDefinedFunction(ScalarFunction scalarFunction) {
    SduScalarFunction sduScalarFunction = new SduScalarFunction();
    sduScalarFunction.setScalarFunction(scalarFunction);
    sduScalarFunction.setDeterministic(scalarFunction.isDeterministic());
    return sduScalarFunction;
  }
}
