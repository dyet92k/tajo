/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package tajo.engine.planner;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import tajo.catalog.Column;
import tajo.catalog.Schema;
import tajo.datum.Datum;
import tajo.datum.DatumFactory;
import tajo.storage.Tuple;
import tajo.storage.TupleRange;
import tajo.storage.VTuple;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

public class UniformRangePartition extends RangePartitionAlgorithm {
  private int variableId;
  private BigDecimal[] cardForEachDigit;
  private BigDecimal[] colCards;

  /**
   *
   * @param schema
   * @param range
   * @param inclusive true if the end of the range is inclusive
   */
  public UniformRangePartition(Schema schema, TupleRange range, boolean inclusive) {
    super(schema, range, inclusive);
    colCards = new BigDecimal[schema.getColumnNum()];
    for (int i = 0; i < schema.getColumnNum(); i++) {
      colCards[i] = computeCardinality(schema.getColumn(i).getDataType(), range.getStart().get(i),
          range.getEnd().get(i), inclusive);
    }

    cardForEachDigit = new BigDecimal[colCards.length];
    for (int i = 0; i < colCards.length ; i++) {
      if (i == 0) {
        cardForEachDigit[i] = colCards[i];
      } else {
        cardForEachDigit[i] = cardForEachDigit[i - 1].multiply(colCards[i]);
      }
    }
  }

  public UniformRangePartition(Schema schema, TupleRange range) {
    this(schema, range, true);
  }

  @Override
  public TupleRange[] partition(int partNum) {
    Preconditions.checkArgument(partNum > 0,
        "The number of partitions must be positive, but the given number: "
        + partNum);
    Preconditions.checkArgument(totalCard.compareTo(new BigDecimal(partNum)) >= 0,
        "the number of partition cannot exceed total cardinality (" + totalCard + ")");

    int varId;
    for (varId = 0; varId < cardForEachDigit.length; varId++) {
      if (cardForEachDigit[varId].compareTo(new BigDecimal(partNum)) >= 0)
        break;
    }
    this.variableId = varId;

    BigDecimal [] reverseCardsForDigit = new BigDecimal[variableId+1];
    for (int i = variableId; i >= 0; i--) {
      if (i == variableId) {
        reverseCardsForDigit[i] = colCards[i];
      } else {
        reverseCardsForDigit[i] = reverseCardsForDigit[i+1].multiply(colCards[i]);
      }
    }

    List<TupleRange> ranges = Lists.newArrayList();
    BigDecimal term = reverseCardsForDigit[0].divide(
        new BigDecimal(partNum), RoundingMode.CEILING);
    BigDecimal reminder = reverseCardsForDigit[0];
    Tuple last = range.getStart();
    while(reminder.compareTo(new BigDecimal(0)) > 0) {
      if (reminder.compareTo(term) <= 0) { // final one is inclusive
        ranges.add(new TupleRange(schema, last, range.getEnd()));
      } else {
        Tuple next = increment(last, term.longValue(), variableId);
        ranges.add(new TupleRange(schema, last, next));
      }
      last = ranges.get(ranges.size() - 1).getEnd();
      reminder = reminder.subtract(term);
    }

    return ranges.toArray(new TupleRange[ranges.size()]);
  }

  public boolean isOverflow(int colId, Datum last, BigDecimal inc) {
    Column column = schema.getColumn(colId);
    BigDecimal candidate;
    boolean overflow = false;
    switch (column.getDataType().getType()) {
      case BIT: {
        candidate = inc.add(new BigDecimal(last.asByte()));
        return new BigDecimal(range.getEnd().get(colId).asByte()).compareTo(candidate) < 0;
      }
      case CHAR: {
        candidate = inc.add(new BigDecimal((int)last.asChar()));
        return new BigDecimal((int)range.getEnd().get(colId).asChar()).compareTo(candidate) < 0;
      }
      case INT2: {
        candidate = inc.add(new BigDecimal(last.asInt2()));
        return new BigDecimal(range.getEnd().get(colId).asInt2()).compareTo(candidate) < 0;
      }
      case INT4: {
        candidate = inc.add(new BigDecimal(last.asInt4()));
        return new BigDecimal(range.getEnd().get(colId).asInt4()).compareTo(candidate) < 0;
      }
      case INT8: {
        candidate = inc.add(new BigDecimal(last.asInt8()));
        return new BigDecimal(range.getEnd().get(colId).asInt8()).compareTo(candidate) < 0;
      }
      case FLOAT4: {
        candidate = inc.add(new BigDecimal(last.asFloat4()));
        return new BigDecimal(range.getEnd().get(colId).asFloat4()).compareTo(candidate) < 0;
      }
      case FLOAT8: {
        candidate = inc.add(new BigDecimal(last.asFloat8()));
        return new BigDecimal(range.getEnd().get(colId).asFloat8()).compareTo(candidate) < 0;
      }
      case TEXT: {
        candidate = inc.add(new BigDecimal((int)(last.asChars().charAt(0))));
        return new BigDecimal(range.getEnd().get(colId).asChars().charAt(0)).compareTo(candidate) < 0;
      }
    }
    return overflow;
  }

  public long incrementAndGetReminder(int colId, Datum last, long inc) {
    Column column = schema.getColumn(colId);
    long reminder = 0;
    switch (column.getDataType().getType()) {
      case BIT: {
        long candidate = last.asByte() + inc;
        byte end = range.getEnd().get(colId).asByte();
        reminder = candidate - end;
        break;
      }
      case CHAR: {
        long candidate = last.asChar() + inc;
        char end = range.getEnd().get(colId).asChar();
        reminder = candidate - end;
        break;
      }
      case INT4: {
        int candidate = (int) (last.asInt4() + inc);
        int end = range.getEnd().get(colId).asInt4();
        reminder = candidate - end;
        break;
      }
      case INT8: {
        long candidate = last.asInt8() + inc;
        long end = range.getEnd().get(colId).asInt8();
        reminder = candidate - end;
        break;
      }
      case FLOAT4: {
        float candidate = last.asFloat4() + inc;
        float end = range.getEnd().get(colId).asFloat4();
        reminder = (long) (candidate - end);
        break;
      }
      case FLOAT8: {
        double candidate = last.asFloat8() + inc;
        double end = range.getEnd().get(colId).asFloat8();
        reminder = (long) Math.ceil(candidate - end);
        break;
      }
      case TEXT: {
        char candidate = ((char)(last.asChars().charAt(0) + inc));
        char end = range.getEnd().get(colId).asChars().charAt(0);
        reminder = (char) (candidate - end);
        break;
      }
    }

    // including zero
    return reminder - 1;
  }

  /**
   *
   * @param last
   * @param inc
   * @return
   */
  public Tuple increment(final Tuple last, final long inc, final int baseDigit) {
    BigDecimal [] incs = new BigDecimal[last.size()];
    boolean [] overflowFlag = new boolean[last.size()];
    BigDecimal [] result;
    BigDecimal value = new BigDecimal(inc);

    BigDecimal [] reverseCardsForDigit = new BigDecimal[baseDigit + 1];
    for (int i = baseDigit; i >= 0; i--) {
      if (i == baseDigit) {
        reverseCardsForDigit[i] = colCards[i];
      } else {
        reverseCardsForDigit[i] = reverseCardsForDigit[i+1].multiply(colCards[i]);
      }
    }

    for (int i = 0; i < baseDigit; i++) {
      result = value.divideAndRemainder(reverseCardsForDigit[i + 1]);
      incs[i] = result[0];
      value = result[1];
    }
    int finalId = baseDigit;
    incs[finalId] = value;
    for (int i = finalId; i >= 0; i--) {
      if (isOverflow(i, last.get(i), incs[i])) {
        if (i == 0) {
          throw new RangeOverflowException(range, last, incs[i].longValue());
        }
        long rem = incrementAndGetReminder(i, last.get(i), value.longValue());
        incs[i] = new BigDecimal(rem);
        incs[i - 1] = incs[i-1].add(new BigDecimal(1));
        overflowFlag[i] = true;
      } else {
        if (i > 0) {
          incs[i] = value;
          break;
        }
      }
    }

    for (int i = 0; i < incs.length; i++) {
      if (incs[i] == null) {
        incs[i] = new BigDecimal(0);
      }
    }

    Tuple end = new VTuple(schema.getColumnNum());
    Column column;
    for (int i = 0; i < last.size(); i++) {
      column = schema.getColumn(i);
      switch (column.getDataType().getType()) {
        case CHAR:
          if (overflowFlag[i]) {
            end.put(i, DatumFactory.createChar((char) (range.getStart().get(i).asChar() + incs[i].longValue())));
          } else {
            end.put(i, DatumFactory.createChar((char) (last.get(i).asChar() + incs[i].longValue())));
          }
          break;
        case BIT:
          if (overflowFlag[i]) {
            end.put(i, DatumFactory.createBit(
                (byte) (range.getStart().get(i).asByte() + incs[i].longValue())));
          } else {
            end.put(i, DatumFactory.createBit((byte) (last.get(i).asByte() + incs[i].longValue())));
          }
          break;
        case INT2:
          if (overflowFlag[i]) {
            end.put(i, DatumFactory.createInt2(
                (short) (range.getStart().get(i).asInt2() + incs[i].longValue())));
          } else {
            end.put(i, DatumFactory.createInt2((short) (last.get(i).asInt2() + incs[i].longValue())));
          }
          break;
        case INT4:
          if (overflowFlag[i]) {
            end.put(i, DatumFactory.createInt4(
                (int) (range.getStart().get(i).asInt4() + incs[i].longValue())));
          } else {
            end.put(i, DatumFactory.createInt4((int) (last.get(i).asInt4() + incs[i].longValue())));
          }
          break;
        case INT8:
          if (overflowFlag[i]) {
            end.put(i, DatumFactory.createInt8(
                range.getStart().get(i).asInt4() + incs[i].longValue()));
          } else {
            end.put(i, DatumFactory.createInt8(last.get(i).asInt8() + incs[i].longValue()));
          }
          break;
        case FLOAT4:
          if (overflowFlag[i]) {
            end.put(i, DatumFactory.createFloat4(
                range.getStart().get(i).asFloat4() + incs[i].longValue()));
          } else {
            end.put(i, DatumFactory.createFloat4(last.get(i).asFloat4() + incs[i].longValue()));
          }
          break;
        case FLOAT8:
          if (overflowFlag[i]) {
            end.put(i, DatumFactory.createFloat8(
                range.getStart().get(i).asFloat8() + incs[i].longValue()));
          } else {
            end.put(i, DatumFactory.createFloat8(last.get(i).asFloat8() + incs[i].longValue()));
          }
          break;
        case TEXT:
          if (overflowFlag[i]) {
            end.put(i, DatumFactory.createText(((char) (range.getStart().get(i).asChars().charAt(0)
                + incs[i].longValue())) + ""));
          } else {
            end.put(i, DatumFactory.createText(
                ((char) (last.get(i).asChars().charAt(0) + incs[i].longValue())) + ""));
          }
          break;
        default:
          throw new UnsupportedOperationException(column.getDataType() + " is not supported yet");
      }
    }

    return end;
  }
}