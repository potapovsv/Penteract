/*
// This software is subject to the terms of the Eclipse Public License v1.0
// Agreement, available at the following URL:
// http://www.eclipse.org/legal/epl-v10.html.
// You must accept the terms of that agreement to use this software.
//
// Copyright (C) 2021 Sergei Semenkov
// All Rights Reserved.
*/
package mondrian.olap.fun;

import mondrian.calc.*;
import mondrian.calc.impl.*;
import mondrian.mdx.ResolvedFunCall;
import mondrian.olap.*;
import mondrian.olap.type.*;
import mondrian.server.Execution;
import mondrian.server.Locus;
import mondrian.util.CancellationChecker;

import org.eclipse.collections.impl.list.mutable.FastList;

import java.util.*;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

class NonEmptyFunDef extends FunDefBase {
    private static final Logger LOGGER = LogManager.getLogger( NonEmptyFunDef.class );
    static final ReflectiveMultiResolver Resolver =
            new ReflectiveMultiResolver(
                    "NonEmpty",
                    "NonEmpty(<Set1>[, <Set2>])",
                    "Returns the set of tuples that are not empty from a specified set, based on the cross product " +
                            "of the specified set with a second set.",
                    new String[] {"fxx", "fxxx"},
                    NonEmptyFunDef.class);

    public NonEmptyFunDef(FunDef dummyFunDef) {
        super(dummyFunDef);
    }

    public Type getResultType(Validator validator, Exp[] args) {
        return args[0].getType();
    }

    public Calc compileCall(ResolvedFunCall call, ExpCompiler compiler) {
        final ListCalc listCalc1 = compiler.compileList(call.getArg(0));
        ListCalc listCalc2 = null;
        if(call.getArgCount() == 2) {
            listCalc2 = compiler.compileList(call.getArg(1));
        }

        return new NonEmptyListCalcImpl(call, listCalc1, listCalc2);
    }

    private static class NonEmptyListCalcImpl extends AbstractListCalc {
        private final ListCalc listCalc1;
        private final ListCalc listCalc2;

        public NonEmptyListCalcImpl(
                ResolvedFunCall call,
                ListCalc listCalc1,
                ListCalc listCalc2)
        {
            super(call, new Calc[]{listCalc1, listCalc2});
            this.listCalc1 = listCalc1;
            this.listCalc2 = listCalc2;
        }

        public TupleList evaluateList(Evaluator evaluator) {
                final int savepoint = evaluator.savepoint();
                try {
                    evaluator.setNonEmpty(false);
                    //   evaluator.setNonEmpty(true);
                    TupleList rightTuples = null;
                    // Вынесли проверку за цикл
                    boolean hasRightTuples = false;
                    if (this.listCalc2 != null) {
                        rightTuples = listCalc2.evaluateList(evaluator);
                        hasRightTuples = rightTuples != null && !rightTuples.isEmpty();
                    }

                    evaluator.setNonEmpty(true);

                    TupleList leftTuples = listCalc1.evaluateList(evaluator);
                    if (leftTuples.isEmpty()) {
                        return TupleCollections.emptyList(leftTuples.getArity());
                    }
                    
                    // ОПТИМИЗАЦИЯ: FastList вместо ArrayList
                    TupleList result = new ListTupleList(leftTuples.getArity(), FastList.newList());
                    
                    long startTime = System.nanoTime();
                    if (LOGGER.isDebugEnabled()) {
                        LOGGER.debug("evaluateList: Start  leftTuples.size=" + leftTuples.size() );
                        LOGGER.debug("evaluateList: Start  rightTuples.size=" + rightTuples.size() );
                    }   
                    int i=0;long t1;long t2;long t3;long t4;long t5;long t6;
                    long tot1=0;long tot2=0;;long tot3=0;;long tot4=0;;long tot21=0;;long tot31=0;;
                     t1 = System.nanoTime();
                    if (hasRightTuples) {
                        for (List<Member> leftTuple : leftTuples) {
                            t1 = System.nanoTime();
                            evaluator.setContext(leftTuple);
                            t2 = System.nanoTime();
                            for (List<Member> rightTuple : rightTuples) {
                            // if (LOGGER.isDebugEnabled()) {
                            //         LOGGER.debug("evaluateList: Start  leftTuples.size=" + leftTuple.getFirst().toString() );
                            //         LOGGER.debug("evaluateList: Start  rightTuples.size=" + rightTuple.getFirst().toString());
                            //     }                                  
                                t3 = System.nanoTime();
                                evaluator.setContext(rightTuple);
                                t4 = System.nanoTime();
                                i++;
                                Object tupleResult = evaluator.evaluateCurrent();
                                t5 = System.nanoTime();
                                if (tupleResult != null) {
                                    result.add(leftTuple);
                                    break; // Найдено, дальше не проверяем
                                }
                                tot3 = tot3 + (t4 - t3);
                                tot4 = tot4 + (t5 - t4);
                            }
                            t6 = System.nanoTime();
                            tot1 = tot1 + (t2 - t1);
                            tot2 = tot2 + (t6 - t2);
                        }
                    } else {
                        for (List<Member> leftTuple : leftTuples) {
                            evaluator.setContext(leftTuple);
                            Object tupleResult = evaluator.evaluateCurrent();
                            i++;
                            if (tupleResult != null) {
                                result.add(leftTuple);
                            }
                        }
                    }
                    
                    if (LOGGER.isDebugEnabled()) {
                        LOGGER.debug("evaluateList:: End time= " + (System.nanoTime() - startTime)/1000000 + " ms");
                        LOGGER.debug("evaluateList: Start  Iteration=" + rightTuples.size() );
                        LOGGER.debug("evaluateList: Timing tot1:" + tot1/1000000 + " ms" );
                        LOGGER.debug("evaluateList: Timing tot2:" + tot2/1000000 + " ms" );
                        LOGGER.debug("evaluateList: Timing tot3:" + tot3/1000000 + " ms" );
                        LOGGER.debug("evaluateList: Timing tot4:" + tot4/1000000 + " ms" );
                    } 
                    return result;
                } finally {
                    evaluator.restore(savepoint);
                }
            }

        public boolean dependsOn(Hierarchy hierarchy) {
            return anyDependsButFirst(getCalcs(), hierarchy);
        }
    }
}

